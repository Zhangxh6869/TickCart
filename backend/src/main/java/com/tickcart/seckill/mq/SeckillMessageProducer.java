package com.tickcart.seckill.mq;

import com.tickcart.common.mq.OrderMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 秒杀建单消息生产者。
 *
 * <p>与 {@code OrderMessageProducer} 不同，这里<b>把投递结果返回给调用方</b>：
 * 抢购场景下「扣了库存却没进队列」等于用户白抢，必须立刻回补并告知失败，
 * 不能像普通订单那样「投递失败只记日志」。</p>
 */
@Component
public class SeckillMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(SeckillMessageProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public SeckillMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 投递秒杀建单消息。
     *
     * @return {@code true} 投递成功；{@code false} 投递失败（调用方应回补库存）
     */
    public boolean sendOrder(SeckillOrderMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    OrderMqConfig.EXCHANGE_TRADE, SeckillMqConfig.RK_SECKILL_ORDER, message);
            log.info("已投递秒杀建单消息：requestId={}, activityId={}, userId={}",
                    message.getRequestId(), message.getActivityId(), message.getUserId());
            return true;
        } catch (Exception e) {
            log.error("投递秒杀建单消息失败：requestId={}, activityId={}, userId={}",
                    message.getRequestId(), message.getActivityId(), message.getUserId(), e);
            return false;
        }
    }
}
