package com.tickcart.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.tickcart.common.mq.OrderMqConfig;
import com.tickcart.common.mq.PaymentMqConfig;
import com.tickcart.common.mq.PaymentNotifyMessage;
import com.tickcart.trade.enums.PaymentNotifyResult;
import com.tickcart.trade.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 支付渠道回调消费者。
 *
 * <p>监听死信队列 {@code trade.payment.dlx}：消息是延迟队列里滞留 TTL 后转过来的，
 * 因此「收到消息」即代表「渠道处理完毕，可以推进支付结果」。</p>
 *
 * <p>约定与 {@link OrderTimeoutConsumer} 保持一致：手动 ack；处理失败重试 3 次
 * （重投到重试队列退避后再回本队列），仍失败则丢弃并打 ERROR 日志等待人工介入；
 * 业务侧幂等由 {@code PaymentService} 的「仅待支付可变更」保证，因此消息重复投递无副作用。</p>
 *
 * <p>消息体显式用 {@link ObjectMapper} 反序列化，理由同 {@link OrderTimeoutConsumer}：
 * 让「解析失败」也走同一套 ack / 重试逻辑，不会把坏消息卡在队列里反复投递。</p>
 */
@Component
public class PaymentNotifyConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotifyConsumer.class);

    /** 最大重试次数 */
    private static final int MAX_RETRY = 3;

    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public PaymentNotifyConsumer(PaymentService paymentService,
                                 RabbitTemplate rabbitTemplate,
                                 ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = PaymentMqConfig.QUEUE_PAYMENT_NOTIFY_DLX)
    public void onPaymentNotify(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String payNo = null;
        try {
            PaymentNotifyMessage payload = objectMapper.readValue(message.getBody(), PaymentNotifyMessage.class);
            payNo = payload.payNo();
            PaymentNotifyResult result = PaymentNotifyResult.of(payload.result());
            paymentService.applyChannelResult(payNo, result);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            int retry = retryCountOf(message) + 1;
            if (retry <= MAX_RETRY) {
                log.warn("支付渠道回调处理失败，第 {}/{} 次重试：payNo={}, cause={}",
                        retry, MAX_RETRY, payNo, e.getMessage());
                republishForRetry(message, retry);
                channel.basicAck(deliveryTag, false);
            } else {
                // 重试耗尽：丢弃消息，避免无限循环拖垮消费者；该笔支付需人工介入
                log.error("支付渠道回调重试 {} 次仍失败，已丢弃消息：payNo={}", MAX_RETRY, payNo, e);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    /** 读取已重试次数，缺失或非法时按 0 处理。 */
    private int retryCountOf(Message message) {
        Object value = message.getMessageProperties().getHeader(PaymentMqConfig.HEADER_RETRY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /**
     * 把原始消息重投到重试队列（携带累加后的重试次数）。
     *
     * <p>只搬运原始 body，不复制 x-death 等死信头，保持与首次投递一致的路径。</p>
     */
    private void republishForRetry(Message message, int retry) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader(PaymentMqConfig.HEADER_RETRY, retry);
        rabbitTemplate.send(OrderMqConfig.EXCHANGE_DLX, PaymentMqConfig.RK_PAYMENT_NOTIFY_RETRY,
                new Message(message.getBody(), properties));
    }
}
