package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tickcart.common.exception.BizException;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.entity.PaymentRecord;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.mapper.PaymentRecordMapper;
import com.tickcart.trade.vo.PaymentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟支付服务。
 *
 * <p>虽然当前没有真实支付网关，但落库形态与真实渠道完全一致：</p>
 * <ol>
 *   <li>「发起支付」先落一条 {@code status=待支付} 的支付记录；</li>
 *   <li>随后走 {@link #handleCallback} 回调路径把记录置为成功并推进订单状态。</li>
 * </ol>
 * <p>这样将来接入微信/支付宝时，只需替换「发起支付」的返回值与「回调来源」，
 * 订单侧的状态推进逻辑无需改动。回调入口对重复通知做了幂等处理。</p>
 */
@Service
public class PaymentService {

    /** 支付流水号前缀 */
    private static final String PAY_NO_PREFIX = "PAY";

    private static final DateTimeFormatter PAY_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderService orderService;

    public PaymentService(PaymentRecordMapper paymentRecordMapper, OrderService orderService) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.orderService = orderService;
    }

    /**
     * 发起支付。
     *
     * <p>仅「待付款」订单可支付；他人的订单按不存在处理。落库后立即模拟一次成功回调，
     * 因此返回值即为支付成功后的结果。</p>
     *
     * @param userId  当前用户
     * @param orderNo 订单号
     * @param channel 支付渠道 1微信 2支付宝
     * @throws BizException 订单不存在/非本人、订单状态不可支付
     */
    @Transactional
    public PaymentVO pay(Long userId, String orderNo, Integer channel) {
        Order order = orderService.getOwned(userId, orderNo);

        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw BizException.conflict("订单当前为「" + status.getLabel() + "」，无法支付");
        }

        PaymentRecord record = new PaymentRecord();
        record.setOrderId(order.getId());
        record.setOrderNo(orderNo);
        record.setPayNo(generatePayNo());
        record.setChannel(channel);
        record.setAmount(order.getPayAmount());
        record.setStatus(PaymentRecord.STATUS_PENDING);
        paymentRecordMapper.insert(record);

        // 模拟网关：落库后立即回调成功。与真实回调走同一套逻辑，避免两条分支不一致。
        PaymentRecord settled = handleCallback(record.getPayNo(), true);

        PaymentVO vo = new PaymentVO();
        vo.setPayNo(record.getPayNo());
        vo.setOrderNo(orderNo);
        vo.setAmount(order.getPayAmount());
        vo.setChannel(channel);
        vo.setStatus(settled.getStatus());
        vo.setPayTime(settled.getCallbackTime());
        return vo;
    }

    /**
     * 支付结果回调（模拟渠道通知入口）。
     *
     * <p>幂等：若该流水号已成功，直接返回，不重复推进订单。未知流水号视为非法通知。</p>
     *
     * @param payNo   支付流水号
     * @param success 渠道通知的支付结果
     * @return 处理后的支付记录
     * @throws BizException 流水号不存在
     */
    @Transactional
    public PaymentRecord handleCallback(String payNo, boolean success) {
        PaymentRecord record = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getPayNo, payNo));
        if (record == null) {
            throw BizException.notFound("支付记录不存在");
        }
        return settle(record, success);
    }

    /**
     * 结算一条支付记录：置状态、写回调时间；成功时推进订单到「待发货」。
     *
     * <p>已成功的记录直接短路返回，保证回调可重复到达而不产生副作用。</p>
     */
    private PaymentRecord settle(PaymentRecord record, boolean success) {
        if (Integer.valueOf(PaymentRecord.STATUS_SUCCESS).equals(record.getStatus())) {
            return record;
        }
        record.setCallbackTime(LocalDateTime.now());
        record.setStatus(success ? PaymentRecord.STATUS_SUCCESS : PaymentRecord.STATUS_FAILED);
        paymentRecordMapper.updateById(record);
        if (success) {
            orderService.markPaid(record.getOrderNo());
        }
        return record;
    }

    /** 生成支付流水号：{@code PAY} + 17 位时间戳 + 4 位随机数。 */
    private String generatePayNo() {
        String timestamp = LocalDateTime.now().format(PAY_NO_TIME);
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return PAY_NO_PREFIX + timestamp + random;
    }
}
