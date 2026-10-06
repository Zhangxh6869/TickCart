package com.tickcart.common.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单相关消息生产者。
 *
 * <p>投递失败只记录日志、不向上抛：MQ 不可用时下单等主链路必须照常完成，
 * 代价仅是失去「超时自动关单」，用户仍可手动取消订单。</p>
 */
@Component
public class OrderMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderMessageProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 投递订单超时延迟消息。
     *
     * <p>消息进入 {@code trade.order.timeout} 后滞留 TTL，过期即成为死信转入关单队列。
     * 即便此刻订单事务尚未提交（或最终回滚），消费侧也是「按订单号回查状态」，
     * 订单不存在或已非待付款都会安全跳过，因此不会产生脏操作。</p>
     *
     * @param orderNo 订单号
     */
    public void sendOrderTimeout(String orderNo) {
        try {
            rabbitTemplate.convertAndSend(
                    OrderMqConfig.EXCHANGE_TRADE,
                    OrderMqConfig.RK_ORDER_TIMEOUT,
                    new OrderTimeoutMessage(orderNo));
            log.info("已投递订单超时消息：{}", orderNo);
        } catch (Exception e) {
            log.error("投递订单超时消息失败（不影响下单）：{}", orderNo, e);
        }
    }
}
