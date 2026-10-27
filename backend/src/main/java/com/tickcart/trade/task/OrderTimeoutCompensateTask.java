package com.tickcart.trade.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 待付款订单超时关单的**兜底补偿**。
 *
 * <p><b>为什么需要它</b>：正常链路是「下单时投递延迟消息 → 到期转死信 → 消费者关单」。
 * 但 {@code OrderMessageProducer} 在 MQ 不可用时只记日志、不抛异常（可用性优先），
 * 消息就此丢失，订单会永远停在「待付款」、库存永远被占用 —— 用户不手动取消就没有出口。
 * 本任务按「时间」而非「消息」兜底，MQ 挂多久都不会漏单。</p>
 *
 * <p><b>安全性从哪来</b>：不靠本任务，靠 {@code OrderService.closeTimeout} 本身 ——
 * 它是 {@code @Transactional} + 条件 UPDATE（{@code WHERE status = 待付款}），
 * 与 MQ 消费者并发时只有一个能改成功，因此<b>不会重复回补库存</b>。
 * 本任务只负责「找出该关的单并逐个触发」，不做任何状态判断。</p>
 *
 * <p><b>为什么故意不加分布式锁</b>：本任务要兜的正是「Redis / MQ 挂了」的场景，
 * 反过来依赖 Redis 做互斥会把兜底本身弄脆。多实例部署时每个实例都会扫到同一批单，
 * 但关单幂等，重复的代价只是一次多余的 SELECT。</p>
 *
 * <p><b>为什么要加宽限</b>：不是为了正确性（幂等已保证），而是避开与 MQ 消息
 * 「同一时刻」双跑 —— 消息通常就在 TTL 到期的瞬间到达，让补偿任务晚 60 秒再看，
 * 绝大多数单子已被消费者关掉，扫描结果自然为空。</p>
 */
@Component
public class OrderTimeoutCompensateTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCompensateTask.class);

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    /** 支付时限（毫秒），与 MQ 延迟队列的 TTL 同源，避免两处各写一个数字 */
    private final long timeoutTtlMs;
    /** 宽限期（毫秒）：在 TTL 之后再等一会儿才认定「消息真的没到」 */
    private final long graceMs;
    /** 单次扫描上限：避免一次捞出过多订单长时间占用连接 */
    private final int batchSize;
    /** 开关：压测或排障时可临时关掉，避免干扰观察 */
    private final boolean enabled;

    public OrderTimeoutCompensateTask(OrderService orderService,
                                      OrderMapper orderMapper,
                                      @Value("${tickcart.order.timeout-ttl-ms:900000}") long timeoutTtlMs,
                                      @Value("${tickcart.order.compensate-grace-ms:60000}") long graceMs,
                                      @Value("${tickcart.order.compensate-batch-size:100}") int batchSize,
                                      @Value("${tickcart.order.compensate-enabled:true}") boolean enabled) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.timeoutTtlMs = timeoutTtlMs;
        this.graceMs = graceMs;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    /**
     * 定时扫描并关单。
     *
     * <p>触发间隔见 {@code tickcart.order.compensate-interval-ms}（默认 60 秒）。
     * 真正的逻辑在 {@link #compensateAt(LocalDateTime)}，本方法只提供「现在」。</p>
     */
    @Scheduled(fixedDelayString = "${tickcart.order.compensate-interval-ms:60000}")
    public void compensate() {
        compensateAt(LocalDateTime.now());
    }

    /**
     * 以给定时间为「现在」执行一次补偿，便于单测注入固定时钟。
     *
     * <p>失败一律不外抛：这是运维兜底动作，不能因为它出错影响任何业务请求；
     * 没关掉的订单会在下一轮扫描里被重新捞到。</p>
     *
     * @param now 当前时间
     */
    void compensateAt(LocalDateTime now) {
        if (!enabled) {
            log.debug("超时关单补偿任务已关闭（tickcart.order.compensate-enabled=false），跳过");
            return;
        }
        if (batchSize <= 0) {
            log.warn("超时关单补偿任务批大小配置为 {}（<=0 视为无效），跳过本次扫描", batchSize);
            return;
        }

        LocalDateTime cutoff = now.minus(Duration.ofMillis(timeoutTtlMs + graceMs));
        List<Order> candidates;
        try {
            candidates = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .select(Order::getOrderNo)
                    .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.getCode())
                    .lt(Order::getCreatedAt, cutoff)
                    .orderByAsc(Order::getId)
                    .last("LIMIT " + batchSize));
        } catch (Exception e) {
            log.error("超时关单补偿：查询待关订单失败（不影响主流程，下次任务会重试）", e);
            return;
        }
        if (candidates.isEmpty()) {
            return;
        }

        int failed = 0;
        for (Order order : candidates) {
            try {
                // 幂等：订单已被消费者关掉、或已被用户支付，这里会安全跳过
                orderService.closeTimeout(order.getOrderNo());
            } catch (Exception e) {
                failed++;
                log.error("超时关单补偿：关单失败，留给下一轮重试 orderNo={}", order.getOrderNo(), e);
            }
        }
        log.info("超时关单补偿完成：扫描到 {} 单（早于 {}），失败 {} 单", candidates.size(), cutoff, failed);
    }
}
