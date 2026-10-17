package com.tickcart.auth.service;

import com.tickcart.common.config.SmsGuardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的短信验证码频控。
 *
 * <p>发送侧三道限制叠在一起：同号间隔（30 秒）、同号窗口次数（10 分钟 3 次）、
 * 同号日累计（10 次）。间隔用 {@code SET NX} 原子占位 —— 否则两个并发请求会同时
 * 通过「读一下有没有超」的检查，各发一条。</p>
 *
 * <p>校验侧按「码记录 id」计数：失败次数必须跟着具体那条码走，否则作废旧码后
 * 计数残留到新码上，用户重新获取一次就没机会了。</p>
 *
 * <p><b>降级：fail-open。</b>Redis 不可用时放行并打 WARN —— 和登录频控同一口径，
 * 验证码发不出去比被刷更伤业务。</p>
 */
@Component
public class RedisSmsCodeGuard implements SmsCodeGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisSmsCodeGuard.class);

    private static final String NS = "sms:";
    /** 失败计数 TTL 与验证码有效期对齐，码过期后计数自然消失。 */
    private static final long FAIL_TTL_SECONDS = 600L;
    private static final long DAY_SECONDS = 86400L;

    private final StringRedisTemplate redis;
    private final SmsGuardProperties props;

    public RedisSmsCodeGuard(StringRedisTemplate redis, SmsGuardProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public boolean tryAcquireSend(String phone) {
        if (!props.isEnabled()) {
            return true;
        }
        try {
            if (count(windowKey(phone)) >= props.getSendWindowLimit()) {
                return false;
            }
            if (count(dailyKey(phone)) >= props.getDailyLimit()) {
                return false;
            }
            // SET NX：原子占位，同一手机号在间隔内只有第一个请求能拿到
            Boolean acquired = redis.opsForValue().setIfAbsent(
                    intervalKey(phone), "1", Duration.ofSeconds(props.getSendIntervalSeconds()));
            return Boolean.TRUE.equals(acquired);
        } catch (DataAccessException ex) {
            failOpen("发送频控", ex);
            return true;
        }
    }

    @Override
    public void recordSent(String phone) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            incr(windowKey(phone), props.getSendWindowMinutes() * 60L);
            incr(dailyKey(phone), DAY_SECONDS);
        } catch (DataAccessException ex) {
            failOpen("发送计数", ex);
        }
    }

    @Override
    public void releaseSend(String phone) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            redis.delete(intervalKey(phone));
        } catch (DataAccessException ex) {
            failOpen("释放间隔占位", ex);
        }
    }

    @Override
    public boolean recordVerifyFailure(long codeId) {
        if (!props.isEnabled()) {
            return false;
        }
        try {
            return incr(failKey(codeId), FAIL_TTL_SECONDS) >= props.getVerifyFailLimit();
        } catch (DataAccessException ex) {
            failOpen("校验失败计数", ex);
            return false;
        }
    }

    @Override
    public void clearVerifyFailure(long codeId) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            redis.delete(failKey(codeId));
        } catch (DataAccessException ex) {
            failOpen("清除失败计数", ex);
        }
    }

    @Override
    public long getRetryAfterSeconds(String phone) {
        if (!props.isEnabled()) {
            return 0;
        }
        try {
            // 优先报「间隔还没到」，这是最常见的情况；其次是窗口/日额度用尽
            long interval = remainingSeconds(intervalKey(phone));
            if (interval > 0) {
                return interval;
            }
            if (count(windowKey(phone)) >= props.getSendWindowLimit()) {
                return remainingSeconds(windowKey(phone));
            }
            if (count(dailyKey(phone)) >= props.getDailyLimit()) {
                return remainingSeconds(dailyKey(phone));
            }
            return 0;
        } catch (DataAccessException ex) {
            failOpen("剩余时长查询", ex);
            return 0;
        }
    }

    // =========================================================================
    // Redis 细节
    // =========================================================================

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

    private long remainingSeconds(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0L : ttl;
    }

    private void failOpen(String action, DataAccessException ex) {
        log.warn("短信频控降级放行（{}）：Redis 不可用 - {}", action, ex.getMessage());
    }

    // ---- key 构造 ----

    private String intervalKey(String phone) {
        return NS + "send:interval:" + phone;
    }

    private String windowKey(String phone) {
        return NS + "send:window:" + phone;
    }

    private String dailyKey(String phone) {
        return NS + "send:daily:" + phone;
    }

    private String failKey(long codeId) {
        return NS + "fail:code:" + codeId;
    }
}
