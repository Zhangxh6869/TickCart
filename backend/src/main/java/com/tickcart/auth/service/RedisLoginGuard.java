package com.tickcart.auth.service;

import com.tickcart.common.config.LoginGuardProperties;
import com.tickcart.common.exception.RateLimitedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的登录频控实现。
 *
 * <p>账号维度用「窗口内失败计数 + 阶梯锁定」，IP 维度用「窗口内失败计数 + 封禁」，
 * 另外加一道每秒请求数限制——BCrypt 单次约 50~100ms，不限 QPS 时几百并发就能把 CPU 打满，
 * 这道闸门防的其实是拒绝服务，不只是爆破。</p>
 *
 * <p><b>降级策略：fail-open。</b>Redis 不可用时放行并打 WARN —— 登录不可用比短时爆破
 * 更伤业务；此时仍有 BCrypt 与图形码兜底。</p>
 */
@Component
public class RedisLoginGuard implements LoginGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginGuard.class);

    /** 所有 key 的统一命名空间，便于在 Redis 里按前缀排查/清理。 */
    private static final String NS = "auth:";

    private final StringRedisTemplate redis;
    private final LoginGuardProperties props;

    public RedisLoginGuard(StringRedisTemplate redis, LoginGuardProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public void assertQps(String ip) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            long epochSecond = Instant.now().getEpochSecond();
            String key = NS + "qps:ip:" + ip + ":" + epochSecond;
            if (incr(key, 2) > props.getIpQpsLimit()) {
                throw new RateLimitedException(1);
            }
        } catch (DataAccessException ex) {
            failOpen("QPS 限流", ex);
        }
    }

    @Override
    public void assertNotLocked(String ip, String username) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            long accountLock = remainingSeconds(accountLockKey(ip, username));
            if (accountLock > 0) {
                throw new RateLimitedException(accountLock);
            }
            long ipLock = remainingSeconds(ipLockKey(ip));
            if (ipLock > 0) {
                throw new RateLimitedException(ipLock);
            }
        } catch (DataAccessException ex) {
            failOpen("锁定检查", ex);
        }
    }

    @Override
    public boolean isVerifyRequired(String ip, String username) {
        if (!props.isEnabled()) {
            return false;
        }
        try {
            return count(accountFailKey(username)) >= props.getVerifyThreshold();
        } catch (DataAccessException ex) {
            failOpen("验证判定", ex);
            return false;
        }
    }

    @Override
    public void recordFailure(String ip, String username) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            long window = props.getFailWindowMinutes() * 60L;
            long accountFails = incr(accountFailKey(username), window);
            long ipFails = incr(ipFailKey(ip), window);

            // 账号达到锁定阈值：按 24h 内的第几轮决定锁多久（阶梯惩罚）
            if (accountFails >= props.getLockThreshold()) {
                int round = (int) incr(gateKey(username), props.getEscalateWindowHours() * 3600L);
                long lockSeconds = lockSecondsForRound(round, props);
                redis.opsForValue().set(accountLockKey(ip, username), "1", Duration.ofSeconds(lockSeconds));
                // 清掉窗口计数：本轮已经用锁表达惩罚，否则解锁瞬间又会立刻触发
                redis.delete(accountFailKey(username));
                log.warn("账号 {} 在 IP {} 登录失败达 {} 次，第 {} 轮锁定 {} 秒",
                        username, ip, accountFails, round, lockSeconds);
            }

            if (ipFails >= props.getIpFailThreshold()) {
                long ipLockSeconds = props.getIpLockMinutes() * 60L;
                redis.opsForValue().set(ipLockKey(ip), "1", Duration.ofSeconds(ipLockSeconds));
                log.warn("IP {} 登录失败达 {} 次，封禁 {} 秒", ip, ipFails, ipLockSeconds);
            }
        } catch (DataAccessException ex) {
            failOpen("失败计数", ex);
        }
    }

    @Override
    public void recordSuccess(String ip, String username) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            // 只清账号维度：IP 计数若一起清，攻击者只要掺一次自己的成功登录就能洗白整段爆破
            redis.delete(List.of(accountFailKey(username), gateKey(username)));
        } catch (DataAccessException ex) {
            failOpen("成功清零", ex);
        }
    }

    @Override
    public long getRetryAfterSeconds(String ip, String username) {
        if (!props.isEnabled()) {
            return 0;
        }
        try {
            long accountLock = remainingSeconds(accountLockKey(ip, username));
            return accountLock > 0 ? accountLock : remainingSeconds(ipLockKey(ip));
        } catch (DataAccessException ex) {
            failOpen("剩余时长查询", ex);
            return 0;
        }
    }

    // =========================================================================
    // 阶梯惩罚（抽成静态方法，便于单测直接验证规则本身）
    // =========================================================================

    /**
     * 第 {@code round} 轮触发锁定时应锁多少秒。
     *
     * <p>1 轮 → 基础时长；2 轮 → 升级时长；3 轮及以后 → 上限时长。
     * 轮次计数有独立的 24h 记忆窗口，过了窗口再从第 1 轮算起。</p>
     */
    static long lockSecondsForRound(int round, LoginGuardProperties props) {
        if (round <= 1) {
            return props.getLockMinutes() * 60L;
        }
        if (round == 2) {
            return props.getEscalateLockMinutes() * 60L;
        }
        return props.getMaxLockMinutes() * 60L;
    }

    // =========================================================================
    // Redis 细节
    // =========================================================================

    /** 自增并在首次创建时设置过期时间（固定窗口计数）。 */
    private long incr(String key, long ttlSeconds) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        return value == null ? 0L : value;
    }

    private long count(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /** 剩余存活秒数；key 不存在（-2）或无过期时间（-1）都按 0 处理。 */
    private long remainingSeconds(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0L : ttl;
    }

    private void failOpen(String action, DataAccessException ex) {
        log.warn("登录频控降级放行（{}）：Redis 不可用 - {}", action, ex.getMessage());
    }

    // ---- key 构造 ----

    private String accountFailKey(String username) {
        return NS + "fail:acct:" + username;
    }

    private String ipFailKey(String ip) {
        return NS + "fail:ip:" + ip;
    }

    /** 锁定粒度取「IP + 账号」：全局账号锁会被人用来恶意锁死他人账号。 */
    private String accountLockKey(String ip, String username) {
        return NS + "lock:acct:" + ip + ":" + username;
    }

    private String ipLockKey(String ip) {
        return NS + "lock:ip:" + ip;
    }

    private String gateKey(String username) {
        return NS + "gate:acct:" + username;
    }
}
