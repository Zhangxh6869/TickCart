package com.tickcart.common.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickcart.common.redis.RedisHealthGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 通用读缓存：Cache-Aside + 空值防穿透 + 短锁防击穿 + 故障降级。
 *
 * <p><b>为什么不用 {@code @Cacheable}</b>：注解做不到「按 id 精确失效 + 按业务整体失效」、
 * 做不到「事务提交后才删缓存」，Redis 故障时还会让异常冒泡把整个接口打成 500。
 * 这三件恰好是本项目必须的，所以手写。</p>
 *
 * <p><b>一致性模型</b>：写操作删缓存（Cache-Aside），读操作回填。删除必须在事务
 * <b>提交后</b>执行（{@link #evictAfterCommit}），否则并发读可能在提交前读到旧值并写回缓存，
 * 之后一直是旧值直到 TTL 到期。</p>
 *
 * <p><b>降级原则</b>：任何 Redis 异常都不向调用方抛出 —— 缓存是加速手段，不是正确性依赖。
 * 唯一例外是 loader 自身抛出的业务异常（如 404），那必须原样上抛，否则会把「商品不存在」吞掉。</p>
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    /** 统一前缀，与 auth:* / sms:* 区分，便于按前缀排查与清理 */
    private static final String KEY_PREFIX = "cache:";

    /** 空值标记后缀：与真实数据分开存，避免和字符串 "null" 混淆 */
    private static final String NULL_SUFFIX = ":null";

    private static final String LOCK_PREFIX = "lock:cache:";

    /** 重建锁有效期：只覆盖一次 DB 查询，不覆盖业务逻辑 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    /** 没抢到锁时的重试次数与间隔（合计最多等 150ms） */
    private static final int RETRY_TIMES = 3;
    private static final long RETRY_INTERVAL_MS = 50L;

    private final StringRedisTemplate redis;
    private final CacheProperties properties;
    private final ObjectMapper cacheMapper;
    private final RedisHealthGuard healthGuard;

    public CacheService(StringRedisTemplate redis, CacheProperties properties,
                        ObjectMapper springObjectMapper, RedisHealthGuard healthGuard) {
        this.redis = redis;
        this.properties = properties;
        this.healthGuard = healthGuard;
        // 专用序列化器：VO 往往只有 getter、字段私有，默认 ObjectMapper 能写不能读。
        // 这里让字段成为唯一来源，只影响缓存读写，不改变 HTTP 响应的 JSON 结构。
        this.cacheMapper = springObjectMapper.copy()
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    /**
     * 读缓存；未命中则调用 {@code loader} 回源并回填。
     *
     * <p>{@code loader} 返回 {@code null} 时会写入空值标记（短 TTL），
     * 期间相同 key 不再打 DB —— 这是防穿透的关键：loader 必须用「查不到返回 null」
     * 而不是「查不到抛异常」的写法，否则空值永远缓存不上。</p>
     *
     * @param ttlSeconds 缓存时长（会叠加随机抖动）
     * @return 缓存值或 loader 的返回值，可能为 null
     */
    public <T> T get(String key, long ttlSeconds, Class<T> type, Supplier<T> loader) {
        return get(key, ttlSeconds, cacheMapper.getTypeFactory().constructType(type), loader);
    }

    /** 列表版：元素类型 + 泛型 {@code List<T>}。 */
    public <T> List<T> getList(String key, long ttlSeconds, Class<T> elementType, Supplier<List<T>> loader) {
        JavaType listType = cacheMapper.getTypeFactory()
                .constructCollectionType(List.class, elementType);
        return get(key, ttlSeconds, listType, loader);
    }

    private <T> T get(String key, long ttlSeconds, JavaType type, Supplier<T> loader) {
        if (!properties.isEnabled()) {
            return loader.get();
        }
        // 熔断中：直接回源，不再为一次注定超时的连接占住线程
        if (!healthGuard.allow()) {
            return loader.get();
        }
        String fullKey = KEY_PREFIX + key;
        try {
            String cached = redis.opsForValue().get(fullKey);
            if (cached != null) {
                healthGuard.onSuccess();
                return cacheMapper.readValue(cached, type);
            }
            if (Boolean.TRUE.equals(redis.hasKey(fullKey + NULL_SUFFIX))) {
                healthGuard.onSuccess();
                return null;
            }
            healthGuard.onSuccess();
        } catch (Exception e) {
            // Redis 或反序列化出问题：直接回源，不能让详情页 500
            healthGuard.onFailure();
            log.warn("读取缓存失败，直接回源：key={}", fullKey, e);
            return loader.get();
        }
        return rebuild(fullKey, ttlSeconds, type, loader);
    }

    /**
     * 未命中时重建缓存。
     *
     * <p>用 {@code SETNX} 短锁保证同一 key 只有一个线程回源（防击穿）；没抢到锁的线程
     * 短暂轮询等待，等不到就自己回源 —— 宁可多打一次 DB，也不让用户卡住。</p>
     *
     * <p>注意：{@code loader.get()} 抛出的业务异常（如 404）不在这里捕获，
     * 会正常向上传播；只有 Redis 写失败才被吞掉。</p>
     */
    private <T> T rebuild(String fullKey, long ttlSeconds, JavaType type, Supplier<T> loader) {
        if (!tryLock(fullKey)) {
            T waited = waitForValue(fullKey, type);
            if (waited != null) {
                return waited;
            }
            return loader.get();
        }
        try {
            T value = loader.get();
            writeQuietly(fullKey, value, ttlSeconds);
            return value;
        } finally {
            unlockQuietly(fullKey);
        }
    }

    /** 抢重建锁；Redis 异常时按「没抢到」处理，走降级路径。 */
    private boolean tryLock(String fullKey) {
        try {
            return Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(LOCK_PREFIX + fullKey, "1", LOCK_TTL));
        } catch (Exception e) {
            log.warn("获取缓存重建锁失败，将直接回源：key={}", fullKey, e);
            return false;
        }
    }

    /** 没抢到锁时短暂轮询，拿不到返回 null（由调用方回源）。 */
    private <T> T waitForValue(String fullKey, JavaType type) {
        for (int i = 0; i < RETRY_TIMES; i++) {
            if (!sleepQuietly(RETRY_INTERVAL_MS)) {
                return null;
            }
            try {
                String cached = redis.opsForValue().get(fullKey);
                if (cached != null) {
                    return cacheMapper.readValue(cached, type);
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /** 写缓存；值为 null 时写空值标记。失败只记日志。 */
    private void writeQuietly(String fullKey, Object value, long ttlSeconds) {
        try {
            if (value == null) {
                redis.opsForValue().set(fullKey + NULL_SUFFIX, "1",
                        Duration.ofSeconds(properties.getNullTtlSeconds()));
                return;
            }
            redis.opsForValue().set(fullKey, cacheMapper.writeValueAsString(value),
                    Duration.ofSeconds(jitter(ttlSeconds)));
        } catch (Exception e) {
            log.warn("写入缓存失败（不影响本次结果）：key={}", fullKey, e);
        }
    }

    private void unlockQuietly(String fullKey) {
        try {
            redis.delete(LOCK_PREFIX + fullKey);
        } catch (Exception e) {
            // 锁本身有 3s TTL，删不掉最多多等一会儿
            log.warn("释放缓存重建锁失败（将依赖 TTL 自动过期）：key={}", fullKey, e);
        }
    }

    /**
     * 删除缓存（连同空值标记）。
     *
     * <p>删除失败只记日志：缓存靠 TTL 自愈，不能因为 Redis 抖动让后台改不了商品。</p>
     */
    public void evict(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            List<String> fullKeys = new ArrayList<>(keys.length * 2);
            for (String key : keys) {
                fullKeys.add(KEY_PREFIX + key);
                fullKeys.add(KEY_PREFIX + key + NULL_SUFFIX);
            }
            redis.delete(fullKeys);
        } catch (Exception e) {
            log.warn("删除缓存失败（将靠 TTL 自愈）：{}", Arrays.toString(keys), e);
        }
    }

    /**
     * 事务提交后删缓存；不在事务中则立即删。
     *
     * <p><b>为什么必须等提交</b>：若在事务内删，另一个读请求可能在提交前读到旧值并回填，
     * 缓存里就会一直是旧值。这个窗口只有几毫秒，但一旦命中就是长期脏读。
     * 「延迟双删」靠睡眠猜时间，不如 {@code afterCommit} 确定。</p>
     */
    public void evictAfterCommit(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict(keys);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict(keys);
            }
        });
    }

    /** TTL 加 ±percent% 抖动，避免同一批 key 同时过期造成雪崩。 */
    private long jitter(long ttlSeconds) {
        int percent = Math.max(0, properties.getTtlJitterPercent());
        if (percent == 0 || ttlSeconds <= 1) {
            return ttlSeconds;
        }
        long delta = Math.max(1, ttlSeconds * percent / 100);
        long offset = ThreadLocalRandom.current().nextLong(-delta, delta + 1);
        return Math.max(1, ttlSeconds + offset);
    }

    private boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
