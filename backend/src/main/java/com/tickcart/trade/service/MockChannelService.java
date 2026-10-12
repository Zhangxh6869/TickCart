package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.mq.PaymentMessageProducer;
import com.tickcart.trade.entity.PaymentRecord;
import com.tickcart.trade.enums.PaymentNotifyResult;
import com.tickcart.trade.enums.PaymentStatus;
import com.tickcart.trade.mapper.PaymentRecordMapper;
import org.springframework.stereotype.Service;

/**
 * 模拟支付渠道服务。
 *
 * <p><b>本类扮演的是第三方渠道服务端的角色，不属于商城自己的业务链路。</b>
 * 真实接入微信/支付宝后，本类连同 {@code MockChannelController} 一并删除，
 * 由渠道的下单接口与异步通知取代 —— 这也是整个模拟方案里唯一"假装"的部分。</p>
 *
 * <p>职责只有一件事：受理「用户在渠道侧完成付款」这一事实，并把结果**延迟**回调给商城
 * （投递 MQ 延迟消息，模拟渠道的处理耗时，时长可配、默认 1 秒）。它不直接改订单，也没有能力改：
 * 真正推进订单的是回调消费者里的 {@code PaymentService}。因此「前端说了不算」这条
 * 信任模型在代码结构上是成立的 —— 前端只能请求渠道，不能直接改订单。</p>
 */
@Service
public class MockChannelService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentMessageProducer paymentMessageProducer;

    public MockChannelService(PaymentRecordMapper paymentRecordMapper,
                              PaymentMessageProducer paymentMessageProducer) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.paymentMessageProducer = paymentMessageProducer;
    }

    /**
     * 受理用户付款结果并安排延迟回调。
     *
     * <p>返回成功只代表「渠道已受理」，订单此刻**仍未变更** —— 结果要等延迟消息
     * 被消费者处理后才生效，前端需轮询查询。</p>
     *
     * @param payNo  支付流水号
     * @param result 用户在该渠道的支付结果
     * @throws BizException 流水号不存在、该笔支付已处于终态
     */
    public void submitResult(String payNo, PaymentNotifyResult result) {
        PaymentRecord record = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getPayNo, payNo));
        if (record == null) {
            throw BizException.notFound("支付记录不存在");
        }

        PaymentStatus status = PaymentStatus.of(record.getStatus());
        if (status.isFinal()) {
            throw BizException.conflict("该笔支付已「" + status.getLabel() + "」，无法再次提交");
        }

        paymentMessageProducer.sendPaymentNotify(payNo, result.getCode());
    }
}
