package com.tickcart.common.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Redis 熔断器。
 *
 * <p><b>它解决什么问题</b>：Redis 挂掉时，每个「碰 Redis 的环节」都要付一次超时。
 * 一个商品详情请求会碰两次（限流器 + 缓存），即便超时已调到 500ms，单请求仍要 1.05 秒。
 * Tomcat 只有 100 个线程，`100 ÷ 1.05 ≈ 95 RPS` —— 容量塌掉约 100 倍，
 * 流量稍高就会排队、超时、重试，演变成雪崩。
 *
 * <p><b>它做什么</b>：连续失败达到阈值后进入「打开」态，之后一段时间内**根本不去连 Redis**，
 * 调用方直接走各自的降级路径（缓存回源、限流放行、吊销查询 fail-open）。
 * 到期进入「半开」态，只放一个请求去试探：成功则恢复，失败则重新打开。
 * 它不让 Redis 变快，它让服务**停止去试**。
 *
 * <p><b>两个刻意的设计</b>：
 * <ul>
 *   <li><b>只放一个探针，且探针有租约</b>。半开态用 CAS 抢租约而不是布尔标记 ——
 *       万一探针请求在调用 {@link #onSuccess()}/{@link #onFailure()} 之前就异常终止，
 *       布尔标记会永远卡住、再也恢复不了；租约到期会自动放行下一个探针，能自愈。</li>
 *   <li><b>状态切换才打日志</b>。故障期间每个请求都打一行会把日志刷爆，
 *       而「什么时候熔断、什么时候恢复」这两条才是有用的信息。</li>
 * </ul>
 *
 * <p><b>它不负责的事</b>：不判断「哪些异常算 Redis 挂了」。调用方只在自己确实
 * 因为 Redis 不可用而失败时才调 {@link #onFailure()}（反序列化错误、业务异常都不算），
 * 否则一次 GC 停顿就可能被误判成故障，把全部流量推向数据库。
 */
@Component
public class RedisHealthGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthGuard.class);

    private final boolean enabled;
    /** 连续失败多少次后打开 */
    private final int failureThreshold;
    /** 打开后维持多久才允许试探 */
    private final long openDurationMs;
    /** 探针租约：超过这个时长没有回报结果，就允许下一个探针，避免卡死 */
    private final long probeLeaseMs;
    private final LongSupplier clock;

    /** 连续失败次数；任一次成功即清零 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    /** 当前探针的租约到期时间；0 表示没有探针在飞 */
    private final AtomicLong probeLeaseUntil = new AtomicLong(0L);

    private volatile boolean open = false;
    /** 打开态到期时间；{@code now >= openUntil} 且 {@link #open} 为真即「半开」 */
    private volatile long openUntil = 0L;

    /**
     * Spring 注入用。
     *
     * <p>★ 必须显式 {@code @Autowired}：本类还有一个包级构造器（给单测注入时钟），
     * 两个构造器时 Spring 不会自动挑选，会退回找无参构造器并报
     * {@code No default constructor found}。</p>
     */
    @Autowired
    public RedisHealthGuard(@Value("${tickcart.redis.circuit.enabled:true}") boolean enabled,
                            @Value("${tickcart.redis.circuit.failure-threshold:5}") int failureThreshold,
                            @Value("${tickcart.redis.circuit.open-duration-ms:30000}") long openDurationMs,
                            @Value("${tickcart.redis.circuit.probe-lease-ms:5000}") long probeLeaseMs) {
        this(enabled, failureThreshold, openDurationMs, probeLeaseMs, System::currentTimeMillis);
    }

    /** 供单测注入可控时钟。 */
    RedisHealthGuard(boolean enabled, int failureThreshold, long openDurationMs,
                     long probeLeaseMs, LongSupplier clock) {
        this.enabled = enabled;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(1L, openDurationMs);
        this.probeLeaseMs = Math.max(1L, probeLeaseMs);
        this.clock = clock;
    }

    /**
     * 本次请求是否允许去碰 Redis。
     *
     * @return {@code false} 表示熔断中，调用方应立即走降级路径、不要发起任何 Redis 调用
     */
    public boolean allow() {
        if (!enabled || !open) {
            return true;
        }
        long now = clock.getAsLong();
        if (now < openUntil) {
            return false;
        }
        // 半开：只放一个探针，其余请求继续降级；租约到期视为探针失联，允许换一个
        while (true) {
            long lease = probeLeaseUntil.get();
            if (now < lease) {
                return false;
            }
            if (probeLeaseUntil.compareAndSet(lease, now + probeLeaseMs)) {
                return true;
            }
        }
    }

    /** Redis 调用成功：清零失败计数；若处于打开/半开态则恢复。 */
    public void onSuccess() {
        if (!enabled) {
            return;
        }
        consecutiveFailures.set(0);
        probeLeaseUntil.set(0L);
        if (open) {
            open = false;
            openUntil = 0L;
            log.info("Redis 已恢复，熔断关闭");
        }
    }

    /** Redis 调用失败（仅限连接/超时这类「Redis 不可用」的失败）。 */
    public void onFailure() {
        if (!enabled) {
            return;
        }
        probeLeaseUntil.set(0L);
        long now = clock.getAsLong();
        if (open) {
            if (now >= openUntil) {
                // 半开探测失败：重新打开，而不是回到「关闭」
                openUntil = now + openDurationMs;
                log.warn("Redis 探测失败，继续保持熔断 {} ms", openDurationMs);
            }
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            open = true;
            openUntil = now + openDurationMs;
            log.error("Redis 连续失败 {} 次，熔断 {} ms（期间不再尝试连接）", failures, openDurationMs);
        }
    }

    /** 仅供观测与单测：当前是否处于熔断（打开或半开）态。 */
    public boolean isOpen() {
        return enabled && open;
    }
}
