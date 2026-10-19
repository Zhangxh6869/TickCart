package com.tickcart.seckill.mq;

import com.tickcart.common.mq.OrderMqConfig;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 秒杀建单队列声明。
 *
 * <p>复用业务交换机 {@code trade.exchange}，只新增队列与绑定：</p>
 * <pre>
 *   trade.exchange ──seckill.order──▶ trade.seckill.order ──▶ SeckillOrderConsumer（B3）
 * </pre>
 *
 * <p>秒杀抢购成功只是「占到名额」，真正的建单由消费者异步完成 —— 这是削峰的关键：
 * 抢购接口只做 Redis 原子扣减 + 投递消息，耗时与并发量解耦。</p>
 */
@Configuration
public class SeckillMqConfig {

    /** 秒杀建单队列 */
    public static final String QUEUE_SECKILL_ORDER = "trade.seckill.order";

    /** 秒杀建单路由键 */
    public static final String RK_SECKILL_ORDER = "seckill.order";

    /** 秒杀建单重试队列：处理失败的消息在此滞留后重新回到主队列 */
    public static final String QUEUE_SECKILL_ORDER_RETRY = "trade.seckill.order.retry";

    /** 秒杀建单重试路由键 */
    public static final String RK_SECKILL_ORDER_RETRY = "seckill.order.retry";

    /** 重试间隔（毫秒）：比普通订单的 10s 更长，给 DB 抖动留出恢复时间 */
    private static final int RETRY_TTL_MS = 10_000;

    /**
     * 秒杀建单专用监听容器：prefetch 单独设 5。
     *
     * <p>建单是「DB 写 + 事务」的重操作，比普通消息慢得多；沿用全局的 10 只会让消息
     * 堆在消费者内存里，既增加内存压力，也让「失败重投」的粒度变粗。</p>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory seckillListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(5);
        // 与全局一致：手动 ack，成功/重投/丢弃由消费者自行决定
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }

    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(QUEUE_SECKILL_ORDER).build();
    }

    /**
     * 重试队列：滞留 {@link #RETRY_TTL_MS} 后死信回主队列。
     *
     * <p>用「队列 TTL + 死信」而不是 {@code basicNack(requeue=true)}：后者会立刻重投，
     * 形成忙等空转，故障时反而把消费者压死。</p>
     */
    @Bean
    public Queue seckillOrderRetryQueue() {
        return QueueBuilder.durable(QUEUE_SECKILL_ORDER_RETRY)
                .ttl(RETRY_TTL_MS)
                .deadLetterExchange(OrderMqConfig.EXCHANGE_TRADE)
                .deadLetterRoutingKey(RK_SECKILL_ORDER)
                .build();
    }

    @Bean
    public Binding seckillOrderRetryBinding() {
        return BindingBuilder.bind(seckillOrderRetryQueue())
                .to(new DirectExchange(OrderMqConfig.EXCHANGE_TRADE, true, false))
                .with(RK_SECKILL_ORDER_RETRY);
    }

    @Bean
    public Binding seckillOrderBinding() {
        // 交换机由 OrderMqConfig 声明；这里只为绑定构造一个同名的描述对象
        return BindingBuilder.bind(seckillOrderQueue())
                .to(new DirectExchange(OrderMqConfig.EXCHANGE_TRADE, true, false))
                .with(RK_SECKILL_ORDER);
    }
}
