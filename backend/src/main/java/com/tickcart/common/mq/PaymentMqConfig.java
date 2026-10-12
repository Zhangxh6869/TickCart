package com.tickcart.common.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付渠道回调的延迟队列声明。
 *
 * <p>交换机**复用** {@link OrderMqConfig} 已声明的 {@code trade.exchange} / {@code trade.dlx}
 * （经 {@code @Qualifier} 注入，不重复声明），只新增自己的队列与绑定：</p>
 *
 * <pre>
 *   trade.exchange ──payment.notify──▶ trade.payment.notify
 *                                          │ 滞留 TTL（模拟渠道处理耗时）后成为死信
 *                                          ▼
 *   trade.dlx ──payment.notify.dead──▶ trade.payment.dlx ──▶ 消费者推进支付结果
 *
 *   trade.dlx ──payment.notify.retry──▶ trade.payment.notify.retry（滞留后重新死信回上面那个队列）
 * </pre>
 *
 * <p>TTL + 死信这套写法与订单超时关单完全一致（见 {@link OrderMqConfig} 的类注释），
 * 区别只在 TTL 是「秒级模拟耗时」而非「15 分钟支付时限」。</p>
 */
@Configuration
public class PaymentMqConfig {

    /** 延迟队列：渠道回调消息在此滞留 TTL 后进入死信 */
    public static final String QUEUE_PAYMENT_NOTIFY = "trade.payment.notify";

    /** 死信队列：真正的渠道回调消息，由消费者监听 */
    public static final String QUEUE_PAYMENT_NOTIFY_DLX = "trade.payment.dlx";

    /** 重试队列：处理失败的消息在此短暂滞留后重新回到死信队列 */
    public static final String QUEUE_PAYMENT_NOTIFY_RETRY = "trade.payment.notify.retry";

    /** 延迟队列路由键 */
    public static final String RK_PAYMENT_NOTIFY = "payment.notify";

    /** 死信队列路由键 */
    public static final String RK_PAYMENT_NOTIFY_DEAD = "payment.notify.dead";

    /** 重试队列路由键 */
    public static final String RK_PAYMENT_NOTIFY_RETRY = "payment.notify.retry";

    /** 消息头：已重试次数（与订单超时消费者共用同一个头名，便于统一理解） */
    public static final String HEADER_RETRY = OrderMqConfig.HEADER_RETRY;

    /** 重试投递的滞留时长（毫秒），与订单超时保持一致 */
    public static final int RETRY_TTL_MS = OrderMqConfig.RETRY_TTL_MS;

    /** 模拟渠道的处理耗时（毫秒）：渠道确认后延迟多久才回调 */
    private final long notifyDelayMs;

    private final DirectExchange tradeExchange;
    private final DirectExchange tradeDlx;

    public PaymentMqConfig(@Value("${tickcart.payment.notify-delay-ms:1000}") long notifyDelayMs,
                           @Qualifier("tradeExchange") DirectExchange tradeExchange,
                           @Qualifier("tradeDlx") DirectExchange tradeDlx) {
        this.notifyDelayMs = notifyDelayMs;
        this.tradeExchange = tradeExchange;
        this.tradeDlx = tradeDlx;
    }

    @Bean
    public Queue paymentNotifyQueue() {
        return QueueBuilder.durable(QUEUE_PAYMENT_NOTIFY)
                .ttl((int) notifyDelayMs)
                .deadLetterExchange(OrderMqConfig.EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_PAYMENT_NOTIFY_DEAD)
                .build();
    }

    @Bean
    public Queue paymentNotifyDlxQueue() {
        return QueueBuilder.durable(QUEUE_PAYMENT_NOTIFY_DLX).build();
    }

    @Bean
    public Queue paymentNotifyRetryQueue() {
        return QueueBuilder.durable(QUEUE_PAYMENT_NOTIFY_RETRY)
                .ttl(RETRY_TTL_MS)
                .deadLetterExchange(OrderMqConfig.EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_PAYMENT_NOTIFY_DEAD)
                .build();
    }

    @Bean
    public Binding paymentNotifyBinding() {
        return BindingBuilder.bind(paymentNotifyQueue()).to(tradeExchange).with(RK_PAYMENT_NOTIFY);
    }

    @Bean
    public Binding paymentNotifyDlxBinding() {
        return BindingBuilder.bind(paymentNotifyDlxQueue()).to(tradeDlx).with(RK_PAYMENT_NOTIFY_DEAD);
    }

    @Bean
    public Binding paymentNotifyRetryBinding() {
        return BindingBuilder.bind(paymentNotifyRetryQueue()).to(tradeDlx).with(RK_PAYMENT_NOTIFY_RETRY);
    }
}
