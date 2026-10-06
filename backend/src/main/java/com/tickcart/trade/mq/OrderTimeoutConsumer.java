package com.tickcart.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.tickcart.common.mq.OrderMqConfig;
import com.tickcart.common.mq.OrderTimeoutMessage;
import com.tickcart.trade.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单超时关单消费者。
 *
 * <p>监听死信队列 {@code trade.order.dlx}：消息是延迟队列里滞留超时后转过来的，
 * 因此「收到消息」即代表「订单已超过支付时限」。</p>
 *
 * <p>约定（对齐详细设计 6.3）：手动 ack；处理失败重试 3 次（重投到重试队列退避后再回本队列），
 * 仍失败则丢弃并打 ERROR 日志等待人工介入；业务侧幂等由 {@code OrderService.closeTimeout} 保证。</p>
 *
 * <p>消息体由本类显式用 {@link ObjectMapper} 反序列化，而不是交给
 * {@code MessageConverter#fromMessage}：后者在没有目标类型时会退化成 Map，
 * 而显式解析能让「解析失败」也走同一套 ack / 重试逻辑，不会把消息卡在队列里反复投递。</p>
 */
@Component
public class OrderTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutConsumer.class);

    /** 最大重试次数 */
    private static final int MAX_RETRY = 3;

    private final OrderService orderService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OrderTimeoutConsumer(OrderService orderService,
                                RabbitTemplate rabbitTemplate,
                                ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = OrderMqConfig.QUEUE_ORDER_TIMEOUT_DLX)
    public void onOrderTimeout(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String orderNo = null;
        try {
            OrderTimeoutMessage payload = objectMapper.readValue(message.getBody(), OrderTimeoutMessage.class);
            orderNo = payload.orderNo();
            orderService.closeTimeout(orderNo);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            int retry = retryCountOf(message) + 1;
            if (retry <= MAX_RETRY) {
                log.warn("订单超时关单失败，第 {}/{} 次重试：orderNo={}, cause={}",
                        retry, MAX_RETRY, orderNo, e.getMessage());
                republishForRetry(message, retry);
                channel.basicAck(deliveryTag, false);
            } else {
                // 重试耗尽：丢弃消息，避免无限循环拖垮消费者；该订单需人工介入
                log.error("订单超时关单重试 {} 次仍失败，已丢弃消息：orderNo={}", MAX_RETRY, orderNo, e);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    /** 读取已重试次数，缺失或非法时按 0 处理。 */
    private int retryCountOf(Message message) {
        Object value = message.getMessageProperties().getHeader(OrderMqConfig.HEADER_RETRY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /**
     * 把原始消息重投到重试队列（携带累加后的重试次数）。
     *
     * <p>只搬运原始 body：不复制 x-death 等死信头（RabbitMQ 会校验其格式），
     * 也让重投路径与首次投递保持完全一致。</p>
     */
    private void republishForRetry(Message message, int retry) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader(OrderMqConfig.HEADER_RETRY, retry);
        rabbitTemplate.send(OrderMqConfig.EXCHANGE_DLX, OrderMqConfig.RK_ORDER_TIMEOUT_RETRY,
                new Message(message.getBody(), properties));
    }
}
