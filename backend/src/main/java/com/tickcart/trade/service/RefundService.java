package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tickcart.common.exception.BizException;
import com.tickcart.trade.dto.RefundApplyRequest;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.entity.PaymentRecord;
import com.tickcart.trade.entity.RefundRecord;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.enums.RefundReason;
import com.tickcart.trade.enums.RefundStatus;
import com.tickcart.trade.mapper.PaymentRecordMapper;
import com.tickcart.trade.mapper.RefundRecordMapper;
import com.tickcart.trade.vo.RefundVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 退款服务（P0：未发货退款）。
 *
 * <p><b>只做一件事：让「付了钱反悔」有出口。</b>P0 限定在「待发货」——
 * 此时货还没出库，退款后立即回补库存，语义最干净。已发货（仅退款）与
 * 已完成（退货退款）在状态机上留了边，但不在本轮实现范围。</p>
 *
 * <h3>与订单状态的分工</h3>
 * <p>本类只写 {@code refund_record}；{@code t_order.status} 一律委托
 * {@link OrderService}（{@code startRefund} / {@code finishRefund} / {@code rollbackRefund}）。
 * 这样「谁改了订单状态」始终只有一个答案，状态机校验也不会散落两处。</p>
 *
 * <h3>幂等与并发</h3>
 * <ul>
 *   <li><b>重复申请</b>：{@code uk_active_order} 唯一索引兜底（{@code active_key} 进行中=orderNo，
 *       终态=NULL）。先查一次给友好提示，撞键再兜一次并发。</li>
 *   <li><b>重复审核</b>：{@code UPDATE ... WHERE status = 0}，影响 0 行说明已被处理；
 *       已退款时按幂等直接返回，已拒绝/已撤销则报冲突。</li>
 *   <li><b>库存只回补一次</b>：靠 {@link OrderService#finishRefund} 的返回值 ——
 *       抢到「退款中 → 已退款」的那一次才回补。与取消/超时关单同一套思路。</li>
 * </ul>
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    /** 退款单号前缀 */
    private static final String REFUND_NO_PREFIX = "RF";

    private static final DateTimeFormatter REFUND_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RefundRecordMapper refundRecordMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderService orderService;

    public RefundService(RefundRecordMapper refundRecordMapper,
                         PaymentRecordMapper paymentRecordMapper,
                         OrderService orderService) {
        this.refundRecordMapper = refundRecordMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.orderService = orderService;
    }

    // =========================================================================
    // 前台
    // =========================================================================

    /**
     * 申请退款（仅待发货订单）。
     *
     * <p>顺序是「先流转订单、后插退款单」：插单撞唯一键时异常会带着整个事务回滚，
     * 订单状态一并回退，不会留下「订单退款中但没有退款单」的孤儿状态。</p>
     *
     * @param userId  当前用户
     * @param orderNo 订单号
     * @param req     原因与说明
     * @return 新建的退款单
     * @throws BizException 订单不存在/非本人、状态不允许、已有进行中的申请、无可退金额
     */
    @Transactional
    public RefundVO apply(Long userId, String orderNo, RefundApplyRequest req) {
        Order order = orderService.getOwned(userId, orderNo);

        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status != OrderStatus.PENDING_SHIPMENT) {
            throw BizException.conflict("订单当前为「" + status.getLabel() + "」，暂不支持申请退款");
        }
        // 先查一次：撞唯一键报错是数据库语言，用户看不懂；这里给一句人话
        if (findActive(orderNo) != null) {
            throw BizException.conflict("该订单已有进行中的退款申请，请勿重复提交");
        }

        BigDecimal amount = resolveRefundAmount(order);
        int originStatus = orderService.startRefund(orderNo);

        RefundRecord record = new RefundRecord();
        record.setRefundNo(generateRefundNo());
        record.setOrderId(order.getId());
        record.setOrderNo(orderNo);
        record.setUserId(userId);
        record.setType(RefundRecord.TYPE_UNSHIPPED);
        record.setAmount(amount);
        record.setReasonCode(req.getReasonCode());
        record.setReason(req.getReason());
        record.setStatus(RefundStatus.PENDING_AUDIT.getCode());
        record.setOriginStatus(originStatus);
        record.setActiveKey(orderNo);
        try {
            refundRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发下的第二次申请：唯一键挡住。抛出去让事务回滚掉上面的订单状态流转
            throw BizException.conflict("该订单已有进行中的退款申请，请勿重复提交");
        }
        log.info("退款申请已提交：orderNo={}, refundNo={}, amount={}", orderNo, record.getRefundNo(), amount);
        return toVO(record);
    }

    /**
     * 用户撤销退款申请（仅待审核可撤销）。
     *
     * <p>撤销后订单回到申请前的状态，用户可以继续等发货。</p>
     */
    @Transactional
    public void cancelApply(Long userId, String orderNo) {
        orderService.getOwned(userId, orderNo);

        RefundRecord record = findActive(orderNo);
        if (record == null) {
            throw BizException.notFound("该订单没有进行中的退款申请");
        }
        int affected = refundRecordMapper.update(null, new LambdaUpdateWrapper<RefundRecord>()
                .eq(RefundRecord::getId, record.getId())
                .eq(RefundRecord::getStatus, RefundStatus.PENDING_AUDIT.getCode())
                .set(RefundRecord::getStatus, RefundStatus.CANCELLED.getCode())
                .set(RefundRecord::getAuditTime, LocalDateTime.now())
                // ★ 释放唯一键。必须用 setSql 显式写 NULL —— updateById 写不进 null
                .setSql("active_key = NULL"));
        if (affected == 0) {
            throw BizException.conflict("退款申请已被处理，无法撤销");
        }
        orderService.rollbackRefund(orderNo, record.getOriginStatus());
        log.info("退款申请已撤销：orderNo={}, refundNo={}", orderNo, record.getRefundNo());
    }

    // =========================================================================
    // 后台
    // =========================================================================

    /**
     * 同意退款：置「已退款」→ 回补库存 → 回退销量。
     *
     * <p>库存回补只在抢到订单状态流转时执行一次，所以重复点「同意」不会把库存补成两倍。</p>
     *
     * @throws BizException 退款单不存在、已被拒绝/撤销
     */
    @Transactional
    public void approve(String refundNo) {
        RefundRecord record = loadByRefundNo(refundNo);
        RefundStatus status = RefundStatus.of(record.getStatus());
        if (status == RefundStatus.REFUNDED) {
            // 幂等：管理员连点、网络重试都会到这里
            log.info("退款单已是已退款状态，跳过：{}", refundNo);
            return;
        }
        if (status != RefundStatus.PENDING_AUDIT) {
            throw BizException.conflict("退款单当前为「" + status.getLabel() + "」，无法同意");
        }

        int affected = refundRecordMapper.update(null, new LambdaUpdateWrapper<RefundRecord>()
                .eq(RefundRecord::getId, record.getId())
                .eq(RefundRecord::getStatus, RefundStatus.PENDING_AUDIT.getCode())
                .set(RefundRecord::getStatus, RefundStatus.REFUNDED.getCode())
                .set(RefundRecord::getAuditTime, LocalDateTime.now())
                .set(RefundRecord::getRefundTime, LocalDateTime.now())
                .setSql("active_key = NULL"));
        if (affected == 0) {
            // 并发：另一个人先处理了。已退款算幂等成功，其它情况报冲突
            RefundRecord latest = loadByRefundNo(refundNo);
            if (RefundStatus.of(latest.getStatus()) == RefundStatus.REFUNDED) {
                return;
            }
            throw BizException.conflict("退款申请已被处理，请刷新后重试");
        }

        if (orderService.finishRefund(record.getOrderNo())) {
            orderService.restoreStock(orderService.loadByOrderNo(record.getOrderNo()));
            orderService.reverseSales(record.getOrderNo());
            log.info("退款完成：refundNo={}, orderNo={}, amount={}",
                    refundNo, record.getOrderNo(), record.getAmount());
        } else {
            // 理论上不该发生：退款单是待审核，订单必然在退款中。真出现说明数据被人改过
            log.warn("退款单已置已退款，但订单不在退款中：refundNo={}, orderNo={}",
                    refundNo, record.getOrderNo());
        }
    }

    /**
     * 拒绝退款：置「已拒绝」→ 订单回退到申请前的状态。
     *
     * @param auditRemark 拒绝原因，必填（由 Controller 的 {@code @NotBlank} 保证）
     * @throws BizException 退款单不存在、已退款/已撤销
     */
    @Transactional
    public void reject(String refundNo, String auditRemark) {
        RefundRecord record = loadByRefundNo(refundNo);
        RefundStatus status = RefundStatus.of(record.getStatus());
        if (status == RefundStatus.REJECTED) {
            log.info("退款单已是已拒绝状态，跳过：{}", refundNo);
            return;
        }
        if (status != RefundStatus.PENDING_AUDIT) {
            throw BizException.conflict("退款单当前为「" + status.getLabel() + "」，无法拒绝");
        }

        int affected = refundRecordMapper.update(null, new LambdaUpdateWrapper<RefundRecord>()
                .eq(RefundRecord::getId, record.getId())
                .eq(RefundRecord::getStatus, RefundStatus.PENDING_AUDIT.getCode())
                .set(RefundRecord::getStatus, RefundStatus.REJECTED.getCode())
                .set(RefundRecord::getAuditRemark, auditRemark)
                .set(RefundRecord::getAuditTime, LocalDateTime.now())
                .setSql("active_key = NULL"));
        if (affected == 0) {
            throw BizException.conflict("退款申请已被处理，请刷新后重试");
        }
        orderService.rollbackRefund(record.getOrderNo(), record.getOriginStatus());
        log.info("退款申请已拒绝：refundNo={}, orderNo={}", refundNo, record.getOrderNo());
    }

    // =========================================================================
    // 查询
    // =========================================================================

    /**
     * 取订单最新一条退款单（供订单详情展示）。
     *
     * <p>按 id 倒序取第一条：被拒绝后用户再申请会落一条新记录，详情应显示最新那次。</p>
     *
     * @return 没有退款记录时返回 null
     */
    public RefundVO findLatest(String orderNo) {
        RefundRecord record = refundRecordMapper.selectOne(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getOrderNo, orderNo)
                .orderByDesc(RefundRecord::getId)
                .last("LIMIT 1"));
        return record == null ? null : toVO(record);
    }

    /** 按退款单号取（后台审核抽屉用）；不存在抛 404。 */
    public RefundVO findByRefundNo(String refundNo) {
        return toVO(loadByRefundNo(refundNo));
    }

    // =========================================================================
    // 内部
    // =========================================================================

    /**
     * 取该订单进行中的退款单。
     *
     * <p>{@code active_key} 在唯一索引上，最多命中一条，所以 {@code selectOne} 是安全的。</p>
     */
    private RefundRecord findActive(String orderNo) {
        return refundRecordMapper.selectOne(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getActiveKey, orderNo));
    }

    private RefundRecord loadByRefundNo(String refundNo) {
        RefundRecord record = refundRecordMapper.selectOne(
                new LambdaQueryWrapper<RefundRecord>().eq(RefundRecord::getRefundNo, refundNo));
        if (record == null) {
            throw BizException.notFound("退款单不存在");
        }
        return record;
    }

    /**
     * 解析退款金额。
     *
     * <p><b>取「支付成功」那条流水的金额，而不是 {@code order.pay_amount}</b>：
     * 同一订单可以有多条支付流水（用户支付失败/取消后重试），id 最大的那条未必成功。
     * 这个口径与 {@code OrderService.loadLatestPayments} 一致，两处不能分叉。</p>
     *
     * <p>理论上已支付订单必有成功流水；取不到时回退订单实付金额，并校验 > 0 ——
     * 宁可拒绝退款，也不能退一个 0 元或负数的款。</p>
     */
    private BigDecimal resolveRefundAmount(Order order) {
        PaymentRecord paid = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderNo, order.getOrderNo())
                .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_SUCCESS)
                .orderByDesc(PaymentRecord::getId)
                .last("LIMIT 1"));
        BigDecimal amount = paid != null ? paid.getAmount() : order.getPayAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("订单没有可退金额：orderNo={}, payAmount={}", order.getOrderNo(), order.getPayAmount());
            throw BizException.badRequest("该订单没有可退款的金额");
        }
        return amount;
    }

    private RefundVO toVO(RefundRecord record) {
        RefundVO vo = new RefundVO();
        vo.setRefundNo(record.getRefundNo());
        vo.setOrderNo(record.getOrderNo());
        vo.setType(record.getType());
        vo.setTypeLabel(typeLabel(record.getType()));
        vo.setAmount(record.getAmount());
        vo.setReasonCode(record.getReasonCode());
        vo.setReasonLabel(reasonLabel(record.getReasonCode()));
        vo.setReason(record.getReason());
        vo.setStatus(record.getStatus());
        vo.setStatusLabel(RefundStatus.of(record.getStatus()).getLabel());
        vo.setAuditRemark(record.getAuditRemark());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setAuditTime(record.getAuditTime());
        vo.setRefundTime(record.getRefundTime());
        return vo;
    }

    private static String typeLabel(Integer type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case RefundRecord.TYPE_UNSHIPPED -> "未发货退款";
            case RefundRecord.TYPE_REFUND_ONLY -> "仅退款";
            case RefundRecord.TYPE_RETURN_GOODS -> "退货退款";
            default -> "";
        };
    }

    /** 原因码 → 文案；历史数据里出现未知码时降级成「其他」，不让详情页报错。 */
    private static String reasonLabel(Integer reasonCode) {
        if (reasonCode == null) {
            return "";
        }
        try {
            return RefundReason.of(reasonCode).getLabel();
        } catch (BizException e) {
            return RefundReason.OTHER.getLabel();
        }
    }

    /** 生成退款单号：{@code RF} + 17 位时间戳 + 4 位随机数（与支付流水号同构）。 */
    private String generateRefundNo() {
        String timestamp = LocalDateTime.now().format(REFUND_NO_TIME);
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return REFUND_NO_PREFIX + timestamp + random;
    }
}
