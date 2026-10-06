package com.tickcart.common.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列声明（对应详细设计 6.3 队列设计）。
 *
 * <p>订单超时关单采用「TTL + 死信队列」实现延迟消费：</p>
 * <pre>
 *   trade.exchange ──order.timeout──▶ trade.order.timeout
 *                                          │ 消息滞留 15min 未被消费（队列级 TTL）
 *                                          ▼ 过期后成为死信，投递到 trade.dlx
 *   trade.dlx ──order.timeout.dead──▶ trade.order.dlx ──▶ 消费者关单 + 回补库存
 * </pre>
 *
 * <p>另外声明一个仅用于重试的队列 {@code trade.order.timeout.retry}：消费者处理失败时把消息
 * 重投到这里，滞留 {@code RETRY_TTL} 后再次死信回 {@code trade.order.dlx}，以此实现「重试 3 次」
 * 的退避重投（避免 basicNack requeue 造成的忙等空转）。</p>
 */
@Configuration
public class OrderMqConfig {

    /** 业务交换机 */
    public static final String EXCHANGE_TRADE = "trade.exchange";

    /** 死信交换机 */
    public static final String EXCHANGE_DLX = "trade.dlx";

    /** 延迟队列：订单超时消息在此滞留 TTL 后进入死信 */
    public static final String QUEUE_ORDER_TIMEOUT = "trade.order.timeout";

    /** 死信队列：真正的超时关单消息，由消费者监听 */
    public static final String QUEUE_ORDER_TIMEOUT_DLX = "trade.order.dlx";

    /** 重试队列：处理失败的消息在此短暂滞留后重新回到死信队列 */
    public static final String QUEUE_ORDER_TIMEOUT_RETRY = "trade.order.timeout.retry";

    /** 延迟队列路由键 */
    public static final String RK_ORDER_TIMEOUT = "order.timeout";

    /** 死信队列路由键 */
    public static final String RK_ORDER_TIMEOUT_DEAD = "order.timeout.dead";

    /** 重试队列路由键 */
    public static final String RK_ORDER_TIMEOUT_RETRY = "order.timeout.retry";

    /** 消息头：已重试次数 */
    public static final String HEADER_RETRY = "x-retry";

    /** 重试投递的滞留时长（毫秒），即两次重试之间的间隔 */
    public static final int RETRY_TTL_MS = 10_000;

    /** 订单支付时限（毫秒），与详细设计「15min」及前端收银台倒计时一致 */
    private final long orderTimeoutTtlMs;

    public OrderMqConfig(@Value("${tickcart.order.timeout-ttl-ms:900000}") long orderTimeoutTtlMs) {
        this.orderTimeoutTtlMs = orderTimeoutTtlMs;
    }

    /** JSON 序列化：让消息体可读（默认的 Java 序列化要求实体实现 Serializable）。 */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange tradeExchange() {
        return new DirectExchange(EXCHANGE_TRADE, true, false);
    }

    @Bean
    public DirectExchange tradeDlx() {
        return new DirectExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_TIMEOUT)
                .ttl((int) orderTimeoutTtlMs)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_ORDER_TIMEOUT_DEAD)
                .build();
    }

    @Bean
    public Queue orderTimeoutDlxQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_TIMEOUT_DLX).build();
    }

    @Bean
    public Queue orderTimeoutRetryQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_TIMEOUT_RETRY)
                .ttl(RETRY_TTL_MS)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_ORDER_TIMEOUT_DEAD)
                .build();
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue()).to(tradeExchange()).with(RK_ORDER_TIMEOUT);
    }

    @Bean
    public Binding orderTimeoutDlxBinding() {
        return BindingBuilder.bind(orderTimeoutDlxQueue()).to(tradeDlx()).with(RK_ORDER_TIMEOUT_DEAD);
    }

    @Bean
    public Binding orderTimeoutRetryBinding() {
        return BindingBuilder.bind(orderTimeoutRetryQueue()).to(tradeDlx()).with(RK_ORDER_TIMEOUT_RETRY);
    }
}
