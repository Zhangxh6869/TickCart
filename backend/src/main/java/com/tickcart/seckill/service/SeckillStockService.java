package com.tickcart.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickcart.seckill.cache.SeckillActivitySnapshot;
import com.tickcart.seckill.cache.SeckillKeys;
import com.tickcart.seckill.entity.SeckillActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀库存的 Redis 操作：预热、原子扣减、回补。
 *
 * <p><b>与 A2 的 {@code CacheService} 严格分离</b>：那边是读缓存（未命中可回源 DB），
 * 这边是写计数（回源即超卖）。因此这里没有任何 get-or-load 逻辑，库存只有 DECRBY 一种改法。</p>
 *
 * <p><b>扣减必须是 Lua</b>：「读库存 → 判限购 → 判库存 → 扣减 → 累加已购」五步，
 * 拆成多条命令就会在并发下超卖或超限购。Lua 在 Redis 内单线程执行，天然原子。</p>
 */
@Service
public class SeckillStockService {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockService.class);

    /** 请求级防重标记的有效期：足够覆盖一次抢购的完整处理，又不会把用户锁太久 */
    private static final Duration REQUEST_LOCK_TTL = Duration.ofSeconds(3);

    /** 活动结束后仍需保留的时长：覆盖 15 分钟超时关单 + 用户查结果 */
    private static final Duration TAIL_TTL = Duration.ofHours(1);

    /**
     * 原子扣减脚本。
     *
     * <p>KEYS[1] = 库存 key，KEYS[2] = 该用户已购数量 key；ARGV[1] = 数量，ARGV[2] = 每人限购。</p>
     *
     * <p>返回：{@code >= 0} 成功（值为扣减后的剩余库存）；{@code -1} 未预热；
     * {@code -2} 库存不足；{@code -3} 超过限购。</p>
     *
     * <p><b>先校验限购再校验库存</b>：两者都失败时，告诉用户「你买超了」比「卖光了」更准确 ——
     * 后者会让人以为还有机会再来。</p>
     */
    private static final String DEDUCT_SCRIPT = """
            local qty   = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])

            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock == nil then
              return -1
            end

            local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
            if bought + qty > limit then
              return -3
            end

            if stock < qty then
              return -2
            end

            redis.call('DECRBY', KEYS[1], qty)
            redis.call('INCRBY', KEYS[2], qty)
            return stock - qty
            """;

    /** 扣减结果：未预热 */
    public static final long RESULT_NOT_PRELOADED = -1L;
    /** 扣减结果：库存不足 */
    public static final long RESULT_SOLD_OUT = -2L;
    /** 扣减结果：超过每人限购 */
    public static final long RESULT_OVER_LIMIT = -3L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<Long> deductScript;

    public SeckillStockService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(DEDUCT_SCRIPT);
        script.setResultType(Long.class);
        this.deductScript = script;
    }

    /**
     * 预热：把活动快照与剩余库存写进 Redis。
     *
     * <p><b>两个 key 的写法刻意不同</b>：</p>
     * <ul>
     *   <li><b>库存用 SETNX</b>：它是唯一权威的写计数。直接覆盖会把「已扣减但尚未建单」的
     *       那部分库存还回去，直接造成超卖。Redis 有数据就沿用，Redis 丢了才用
     *       {@code seckill_stock - sold_count} 重建 —— 宁可少卖，不可超卖。</li>
     *   <li><b>快照用普通 SET</b>：它只是活动配置的副本，刷新是安全的，而且必须刷新 ——
     *       否则「下线后重新上线」会一直读到 enabled=0 的旧快照，用户永远抢不到。</li>
     * </ul>
     *
     * <p>Redis 异常时向上抛出：调用方（上线操作）应因此失败并回滚，
     * 而不是让一场「已上线但抢不到」的活动暴露给用户。</p>
     */
    public void preload(SeckillActivity activity) {
        // 快照每次刷新；库存只在不存在时写入
        refreshSnapshot(activity);

        Duration ttl = ttlOf(activity);
        if (ttl == null) {
            log.info("活动 {} 已结束，只刷新快照、不建库存", activity.getId());
            return;
        }

        int remaining = Math.max(nullToZero(activity.getSeckillStock()) - nullToZero(activity.getSoldCount()), 0);
        Boolean stockCreated = redis.opsForValue()
                .setIfAbsent(SeckillKeys.stock(activity.getId()), String.valueOf(remaining), ttl);

        if (Boolean.TRUE.equals(stockCreated)) {
            log.info("活动 {} 预热完成：剩余库存 {}，TTL {}s", activity.getId(), remaining, ttl.toSeconds());
        } else {
            log.debug("活动 {} 库存已存在，保留 Redis 现值（不覆盖）", activity.getId());
        }
    }

    /**
     * 刷新活动快照（不动库存）。
     *
     * <p>用于「下线」「编辑时间/限购」这类不该影响库存的变更 —— 否则抢购接口会一直读到
     * 旧快照：下线了还能抢、把结束时间改到过去却仍显示进行中。</p>
     *
     * <p>已结束的活动也刷新，TTL 取 5 分钟：只为让「已结束」这个结论尽快生效，
     * 之后自然过期。</p>
     */
    public void refreshSnapshot(SeckillActivity activity) {
        Duration ttl = ttlOf(activity);
        Duration effective = ttl != null ? ttl : Duration.ofMinutes(5);
        redis.opsForValue().set(SeckillKeys.activity(activity.getId()), toJson(activity), effective);
    }

    /**
     * 删除活动时清掉 Redis 数据。
     *
     * <p>不清的话，被删活动在 TTL 内仍能抢 —— 库存 key 还在，快照也还在。</p>
     */
    public void evict(Long activityId) {
        try {
            redis.delete(List.of(SeckillKeys.activity(activityId), SeckillKeys.stock(activityId)));
        } catch (Exception e) {
            log.error("清理秒杀活动缓存失败：activityId={}", activityId, e);
        }
    }

    /** TTL = 到活动结束 + 1 小时；已结束返回 null。 */
    private Duration ttlOf(SeckillActivity activity) {
        Duration ttl = Duration.between(LocalDateTime.now(), activity.getEndTime()).plus(TAIL_TTL);
        return (ttl.isNegative() || ttl.isZero()) ? null : ttl;
    }

    private String toJson(SeckillActivity activity) {
        try {
            return objectMapper.writeValueAsString(SeckillActivitySnapshot.from(activity));
        } catch (Exception e) {
            throw new IllegalStateException("序列化秒杀活动快照失败：" + activity.getId(), e);
        }
    }

    /**
     * 读取活动快照。
     *
     * @return 未预热或已过期时返回 {@code null}
     */
    public SeckillActivitySnapshot snapshot(Long activityId) {
        String json = redis.opsForValue().get(SeckillKeys.activity(activityId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SeckillActivitySnapshot.class);
        } catch (Exception e) {
            log.warn("秒杀活动快照解析失败，按未预热处理：activityId={}", activityId, e);
            return null;
        }
    }

    /**
     * 原子扣减库存。
     *
     * <p>Redis 异常直接向上抛（fail-close）：不知道库存就不该卖，不能像读缓存那样降级回源。</p>
     *
     * @return {@code >= 0} 成功且值为剩余库存；负数见 {@code RESULT_*} 常量
     */
    public long tryDeduct(Long activityId, Long userId, int quantity, int limitPerUser) {
        Long result = redis.execute(deductScript,
                List.of(SeckillKeys.stock(activityId), SeckillKeys.bought(activityId, userId)),
                String.valueOf(quantity), String.valueOf(limitPerUser));
        return result == null ? RESULT_NOT_PRELOADED : result;
    }

    /**
     * 回补库存（MQ 投递失败、建单失败、取消订单时调用）。
     *
     * <p><b>先判 key 存在</b>：活动已结束、key 已过期时，{@code INCRBY} 会凭空创建一个
     * 库存 key —— 脏数据一旦进来，下一场活动就可能出现「幽灵库存」。</p>
     */
    public void restore(Long activityId, Long userId, int quantity) {
        String stockKey = SeckillKeys.stock(activityId);
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(stockKey))) {
                log.info("活动 {} 库存已过期，跳过回补（活动已结束）", activityId);
                return;
            }
            redis.opsForValue().increment(stockKey, quantity);
            redis.opsForValue().decrement(SeckillKeys.bought(activityId, userId), quantity);
        } catch (Exception e) {
            // 回补失败只记 ERROR：调用方正在返回错误响应，这里再抛也改变不了结果，
            // 但必须留痕，靠 Redis 剩余与 DB sold_count 对账发现。
            log.error("回补秒杀库存失败：activityId={}, userId={}, quantity={}",
                    activityId, userId, quantity, e);
        }
    }

    /**
     * 请求级防重：同一用户在同一活动上 3 秒内只放一个请求进来。
     *
     * <p>用 SETNX 而不是分布式锁：这里只需要「短时间互斥」，不需要可重入、不需要续期。
     * 成功时<b>不主动释放</b> —— 让它在 3 秒后自然过期，可以顺带挡住「建单排队期间的连点」。</p>
     */
    public boolean tryLockRequest(Long activityId, Long userId, String requestId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                SeckillKeys.request(activityId, userId), requestId, REQUEST_LOCK_TTL));
    }

    /** 释放请求级防重标记（仅在抢购失败、允许用户立刻重试时调用）。 */
    public void unlockRequest(Long activityId, Long userId) {
        try {
            redis.delete(SeckillKeys.request(activityId, userId));
        } catch (Exception e) {
            log.warn("释放秒杀请求标记失败（将依赖 TTL 过期）：activityId={}, userId={}",
                    activityId, userId, e);
        }
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
