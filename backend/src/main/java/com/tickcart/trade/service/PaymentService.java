package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tickcart.common.exception.BizException;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.entity.PaymentRecord;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.enums.PaymentNotifyResult;
import com.tickcart.trade.enums.PaymentStatus;
import com.tickcart.trade.mapper.PaymentRecordMapper;
import com.tickcart.trade.vo.PaymentStatusVO;
import com.tickcart.trade.vo.PaymentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务（商城侧）。
 *
 * <p>模拟支付也完全照搬真实支付的**通信结构**，三段彼此独立、时间上异步：</p>
 * <ol>
 *   <li>{@link #createPayment} —— 发起支付：只落一条「待支付」流水并返回，**不推进订单**；</li>
 *   <li>渠道侧（{@code MockChannelService}）受理用户付款，延迟若干秒后回调；</li>
 *   <li>{@link #applyChannelResult} —— 收到回调才落终态，成功时推进订单到「待发货」。</li>
 * </ol>
 *
 * <p>因此「用户点了确认付款」与「订单变成已支付」之间隔着一段真实存在的等待窗口，
 * 前端只能靠 {@link #queryStatus} 轮询。将来接入微信/支付宝时，需要改动的只有
 * 「发起支付返回什么」与「回调从哪里来」这两处，订单侧逻辑（含幂等与超时关单）一行不用动。</p>
 *
 * <p>幂等：渠道通知可能重复到达，而只有「待支付」的流水允许变更（见 {@link PaymentStatus#isFinal()}），
 * 所以重复通知不会重复推进订单、也不会重复累加销量（销量累加在 {@code OrderService.markPaid}
 * 抢到状态流转之后才执行）。</p>
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
     * 发起支付：为订单创建一条「待支付」流水。
     *
     * <p>只落库、不推进订单状态 —— 订单要等渠道回调到达才会变成「待发货」。
     * 每次调用都新落一条流水（用户支付失败/取消后重试时会留下多条），
     * 因此同一订单可以有多条支付记录，订单侧只认「最新一条」。</p>
     *
     * @param userId  当前用户
     * @param orderNo 订单号
     * @param channel 支付渠道 1微信 2支付宝
     * @return 待支付状态的支付结果（含 payNo，供前端带到渠道页）
     * @throws BizException 订单不存在/非本人、订单状态不可支付
     */
    @Transactional
    public PaymentVO createPayment(Long userId, String orderNo, Integer channel) {
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

        return toVO(record);
    }

    /**
     * 应用渠道通知的结果（由回调消费者调用）。
     *
     * <p>成功时推进订单到「待发货」；失败与取消只落流水终态，订单保持待付款。
     * 若此刻订单已被超时关单（用户在支付时限最后一刻付款），{@code markPaid} 会因
     * 状态已变更而抛冲突异常，整个事务回滚 —— 流水保持「待支付」，由消费者重试并最终
     * 打 ERROR 日志等待人工介入，**不会**出现「订单已关闭却标记为已支付」的脏数据。</p>
     *
     * @param payNo  支付流水号
     * @param result 渠道结果
     * @return 处理后的支付记录
     * @throws BizException 流水号不存在、订单状态已变更
     */
    @Transactional
    public PaymentRecord applyChannelResult(String payNo, PaymentNotifyResult result) {
        return apply(loadByPayNo(payNo), result);
    }

    /**
     * 支付结果回调（真实渠道形态的入口）。
     *
     * <p>保留给联调与回归脚本使用：真实渠道只回调「成功」，失败由商户主动查单得到，
     * 因此这里只有成功/失败两态。</p>
     *
     * @param payNo   支付流水号
     * @param success 渠道通知的支付结果
     * @return 处理后的支付记录
     * @throws BizException 流水号不存在
     */
    @Transactional
    public PaymentRecord handleCallback(String payNo, boolean success) {
        return apply(loadByPayNo(payNo), success ? PaymentNotifyResult.SUCCESS : PaymentNotifyResult.FAILED);
    }

    /**
     * 查询订单的支付状态（供收银台轮询）。
     *
     * @param userId  当前用户
     * @param orderNo 订单号
     * @throws BizException 订单不存在/非本人
     */
    public PaymentStatusVO queryStatus(Long userId, String orderNo) {
        Order order = orderService.getOwned(userId, orderNo);
        OrderStatus orderStatus = OrderStatus.of(order.getStatus());

        PaymentStatusVO vo = new PaymentStatusVO();
        vo.setOrderNo(orderNo);
        vo.setOrderStatus(order.getStatus());
        vo.setOrderStatusLabel(orderStatus.getLabel());

        PaymentRecord latest = latestByOrderNo(orderNo);
        if (latest != null) {
            vo.setPayNo(latest.getPayNo());
            vo.setPayStatus(latest.getStatus());
            vo.setPayStatusLabel(PaymentStatus.of(latest.getStatus()).getLabel());
            vo.setChannel(latest.getChannel());
            vo.setPayTime(latest.getCallbackTime());
        }
        return vo;
    }

    /**
     * 按流水号取支付记录。
     *
     * @throws BizException 流水号不存在
     */
    public PaymentRecord loadByPayNo(String payNo) {
        PaymentRecord record = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getPayNo, payNo));
        if (record == null) {
            throw BizException.notFound("支付记录不存在");
        }
        return record;
    }

    /**
     * 结算一条支付记录：置终态、写回调时间；成功时推进订单到「待发货」。
     *
     * <p>已处于终态的记录直接短路返回，保证回调可重复到达而不产生副作用。</p>
     */
    private PaymentRecord apply(PaymentRecord record, PaymentNotifyResult result) {
        if (PaymentStatus.of(record.getStatus()).isFinal()) {
            return record;
        }
        record.setCallbackTime(LocalDateTime.now());
        record.setStatus(result.getTarget().getCode());
        paymentRecordMapper.updateById(record);
        if (result == PaymentNotifyResult.SUCCESS) {
            orderService.markPaid(record.getOrderNo());
        }
        return record;
    }

    /** 取订单最新一条支付流水（每次发起支付都新落一条，故按 id 倒序取第一条）。 */
    private PaymentRecord latestByOrderNo(String orderNo) {
        return paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderNo, orderNo)
                .orderByDesc(PaymentRecord::getId)
                .last("LIMIT 1"));
    }

    private PaymentVO toVO(PaymentRecord record) {
        PaymentVO vo = new PaymentVO();
        vo.setPayNo(record.getPayNo());
        vo.setOrderNo(record.getOrderNo());
        vo.setAmount(record.getAmount());
        vo.setChannel(record.getChannel());
        vo.setStatus(record.getStatus());
        vo.setPayTime(record.getCallbackTime());
        return vo;
    }

    /** 生成支付流水号：{@code PAY} + 17 位时间戳 + 4 位随机数。 */
    private String generatePayNo() {
        String timestamp = LocalDateTime.now().format(PAY_NO_TIME);
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return PAY_NO_PREFIX + timestamp + random;
    }
}
