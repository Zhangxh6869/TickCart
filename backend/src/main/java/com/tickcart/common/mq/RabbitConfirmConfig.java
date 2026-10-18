package com.tickcart.common.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * RabbitMQ 投递可靠性回调：确认（confirm）与退回（return）。
 *
 * <p><b>为什么需要</b>：{@code rabbitTemplate.convertAndSend} 只是把消息写进 TCP 连接，
 * Broker 是否收下、路由键是否匹配到队列，调用方一概不知。此前投递失败只有同步异常
 * （连接断了）会被 catch；「交换机存在但没有匹配队列」这种静默丢失完全没有痕迹 ——
 * 对订单超时消息来说，这意味着订单永远不会自动关闭。</p>
 *
 * <p>开启后：</p>
 * <ul>
 *   <li>{@link RabbitTemplate.ConfirmCallback} —— Broker 确认收到（ack）或拒收（nack）</li>
 *   <li>{@link RabbitTemplate.ReturnsCallback} —— 消息到了交换机但没有队列可路由</li>
 * </ul>
 *
 * <p><b>三个配置缺一不可</b>：{@code publisher-confirm-type: correlated}、
 * {@code publisher-returns: true}、{@code template.mandatory: true}。
 * 少了 mandatory，Broker 会直接丢弃无法路由的消息，return 回调永远不会触发。</p>
 *
 * <p><b>边界</b>：这里只做到「失败可见」（ERROR 日志 + 计数），不做本地消息表补偿。
 * 真正的「不丢」需要业务表与消息表同事务 + 定时扫描重投，属于可靠消息的完整工程，
 * 不在 3A 范围内。</p>
 */
@Component
public class RabbitConfirmConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfirmConfig.class);

    /** 被 Broker 拒收（nack）的次数，供排查与后续接入监控 */
    private final AtomicLong nackCount = new AtomicLong();
    /** 路由失败（无匹配队列）的次数 */
    private final AtomicLong returnCount = new AtomicLong();

    public RabbitConfirmConfig(RabbitTemplate rabbitTemplate) {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                return;
            }
            long total = nackCount.incrementAndGet();
            log.error("MQ 投递被 Broker 拒收（累计 {} 次）：correlation={}, cause={}",
                    total, correlationData == null ? "null" : correlationData.getId(), cause);
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            long total = returnCount.incrementAndGet();
            log.error("MQ 消息无法路由（累计 {} 次）：exchange={}, routingKey={}, reply={}",
                    total, returned.getExchange(), returned.getRoutingKey(), returned.getReplyText());
        });
    }

    public long getNackCount() {
        return nackCount.get();
    }

    public long getReturnCount() {
        return returnCount.get();
    }
}
