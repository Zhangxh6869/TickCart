package com.tickcart.common.ratelimit;

import com.tickcart.common.redis.RedisHealthGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 基于 Redis 的令牌桶限流器（Lua 原子执行）。
 *
 * <p><b>为什么是令牌桶</b>：固定窗口有边界突刺（59.9s 打满、60.1s 再打满 = 0.2 秒内两倍流量）；
 * ZSET 滑动窗口精确但每请求一个成员，热门 IP 下内存 O(N)。令牌桶只存
 * {@code tokens} + {@code ts} 两个字段，内存 O(1)，且允许合理突发。</p>
 *
 * <p><b>为什么用 Lua</b>：「读令牌 → 补令牌 → 扣减」三步必须原子，否则并发下会超发。
 * 一条脚本一次 RTT 完成，与秒杀扣库存是同一套思路。</p>
 *
 * <p><b>降级：fail-open。</b>Redis 异常时放行并打 WARN —— 限流是保护手段、不是正确性依赖，
 * fail-close 会把一次 Redis 抖动放大成全站 503。与 {@code RedisLoginGuard} 保持一致。</p>
 */
@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    /** 统一前缀，便于在 Redis 里按前缀排查/清理 */
    private static final String NS = "limit:";

    /**
     * 令牌桶脚本。
     *
     * <p>ARGV[1] 容量、ARGV[2] 每毫秒补充速率、ARGV[3] 当前毫秒、ARGV[4] TTL(ms)。</p>
     *
     * <p>返回 0 表示放行；返回正数表示还需等待的<b>毫秒</b>数。用毫秒而不是布尔值，
     * 是为了让 {@code RateLimitedException} 能给出「请 X 秒后重试」的准确提示。</p>
     */
    private static final String TOKEN_BUCKET_SCRIPT = """
            local capacity = tonumber(ARGV[1])
            local rate     = tonumber(ARGV[2])
            local now      = tonumber(ARGV[3])
            local ttl      = tonumber(ARGV[4])

            local data   = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts     = tonumber(data[2])
            if tokens == nil then tokens = capacity end
            if ts     == nil then ts     = now end

            tokens = math.min(capacity, tokens + math.max(0, now - ts) * rate)

            if tokens < 1 then
              redis.call('HSET', KEYS[1], 'ts', now)
              redis.call('PEXPIRE', KEYS[1], ttl)
              return math.ceil((1 - tokens) / rate)
            end

            redis.call('HSET', KEYS[1], 'tokens', tokens - 1, 'ts', now)
            redis.call('PEXPIRE', KEYS[1], ttl)
            return 0
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;
    private final RedisHealthGuard healthGuard;

    public RedisRateLimiter(StringRedisTemplate redis, RedisHealthGuard healthGuard) {
        this.redis = redis;
        this.healthGuard = healthGuard;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(TOKEN_BUCKET_SCRIPT);
        redisScript.setResultType(Long.class);
        this.script = redisScript;
    }

    /**
     * 尝试取一个令牌。
     *
     * @param key           桶标识（不含 {@code limit:} 前缀）
     * @param permits       一个窗口内允许的次数（桶容量）
     * @param windowSeconds 窗口长度（秒）
     * @return 0 表示放行；正数表示还需等待的毫秒数
     */
    public long tryAcquire(String key, int permits, int windowSeconds) {
        // 熔断中：连都不连，直接按 fail-open 放行。否则每个请求都要为一次注定超时的
        // 连接占住线程（500ms × 并发），把「Redis 挂了」放大成「服务不可用」。
        if (!healthGuard.allow()) {
            return 0L;
        }
        try {
            int capacity = Math.max(1, permits);
            int window = Math.max(1, windowSeconds);
            // 每毫秒补充速率。用固定小数位格式化，避免 Double.toString 出现 1.6E-5 这类科学计数法
            String rate = String.format(Locale.ROOT, "%.10f", (double) capacity / (window * 1000.0));
            long now = System.currentTimeMillis();
            // 桶补满只需一个窗口，留 2 倍余量；最短 60s，避免短窗口下 key 频繁重建
            long ttl = Math.max((long) window * 2000L, 60_000L);

            Long waitMs = redis.execute(script, List.of(NS + key),
                    String.valueOf(capacity), rate, String.valueOf(now), String.valueOf(ttl));
            healthGuard.onSuccess();
            return waitMs == null ? 0L : Math.max(0L, waitMs);
        } catch (Exception e) {
            healthGuard.onFailure();
            log.warn("限流器不可用，放行本次请求：key={}", key, e);
            return 0L;
        }
    }
}
