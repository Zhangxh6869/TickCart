package com.tickcart.common.mq;

import com.tickcart.common.api.ResultCode;
import com.tickcart.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 支付渠道消息生产者。
 *
 * <p>与 {@link OrderMessageProducer} 的关键区别：**投递失败必须向上抛**。
 * 订单超时消息投丢只损失「自动关单」，用户还能手动取消；而渠道回调消息投丢意味着
 * 「用户已经付款、订单却永远停在待付款」，必须在接口层立刻失败让前端提示重试。</p>
 */
@Component
public class PaymentMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentMessageProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public PaymentMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 投递支付渠道回调消息（延迟生效）。
     *
     * <p>消息进入 {@code trade.payment.notify} 后滞留 TTL 才成为死信转给回调消费者，
     * 因此「投递成功」只代表渠道已受理，不代表支付已完成。</p>
     *
     * @param payNo  支付流水号
     * @param result 渠道结果取值：success / fail / cancel
     * @throws BizException 投递失败（MQ 不可用）
     */
    public void sendPaymentNotify(String payNo, String result) {
        try {
            rabbitTemplate.convertAndSend(
                    OrderMqConfig.EXCHANGE_TRADE,
                    PaymentMqConfig.RK_PAYMENT_NOTIFY,
                    new PaymentNotifyMessage(payNo, result));
            log.info("已投递支付渠道回调消息：payNo={}, result={}", payNo, result);
        } catch (Exception e) {
            log.error("投递支付渠道回调消息失败：payNo={}, result={}", payNo, result, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "支付渠道暂时不可用，请稍后重试");
        }
    }
}
