package com.tickcart.seckill.mq;

import com.tickcart.common.mq.OrderMqConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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

    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(QUEUE_SECKILL_ORDER).build();
    }

    @Bean
    public Binding seckillOrderBinding() {
        // 交换机由 OrderMqConfig 声明；这里只为绑定构造一个同名的描述对象
        return BindingBuilder.bind(seckillOrderQueue())
                .to(new DirectExchange(OrderMqConfig.EXCHANGE_TRADE, true, false))
                .with(RK_SECKILL_ORDER);
    }
}
