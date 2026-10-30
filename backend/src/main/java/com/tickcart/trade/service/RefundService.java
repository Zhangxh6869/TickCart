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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 退款服务。
 *
 * <p><b>只做一件事：让「付了钱反悔」有出口。</b>目前支持两档：</p>
 * <ul>
 *   <li><b>P0 未发货退款</b>（待发货）：货没出库，退款后立即回补库存</li>
 *   <li><b>P1 仅退款</b>（待收货）：货已发出，<b>不回补库存</b>（可能拒收退回、也可能用户留下了）</li>
 * </ul>
 * <p>已完成订单的「退货退款」（P2）需要退货物流与入库，不在本类范围内。</p>
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

    /**
     * 退货收货地址：商家同意「退货退款」后随退款单展示给用户。
     *
     * <p>做成配置而不是建表 —— 只有一个地址，多一张表只会多一次查询。
     * 空值时不下发，前端据此隐藏「寄回地址」区块。</p>
     */
    private final String returnAddress;

    public RefundService(RefundRecordMapper refundRecordMapper,
                         PaymentRecordMapper paymentRecordMapper,
                         OrderService orderService,
                         @Value("${tickcart.refund.return-address:}") String returnAddress) {
        this.refundRecordMapper = refundRecordMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.orderService = orderService;
        this.returnAddress = returnAddress;
    }

    // =========================================================================
    // 前台
    // =========================================================================

    /**
     * 申请退款（待发货 / 待收货）。
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

        // 先查进行中的申请：撞唯一键报错是数据库语言，用户看不懂；
        // 且「订单已是退款中」时，说「已有进行中的申请」比说「状态不对」有用得多
        if (findActive(orderNo) != null) {
            throw BizException.conflict("该订单已有进行中的退款申请，请勿重复提交");
        }

        OrderStatus status = OrderStatus.of(order.getStatus());
        int type = refundTypeOf(status);
        if (type == 0) {
            throw BizException.conflict("订单当前为「" + status.getLabel() + "」，暂不支持申请退款");
        }

        BigDecimal amount = resolveRefundAmount(order);
        int originStatus = orderService.startRefund(orderNo);

        RefundRecord record = new RefundRecord();
        record.setRefundNo(generateRefundNo());
        record.setOrderId(order.getId());
        record.setOrderNo(orderNo);
        record.setUserId(userId);
        record.setType(type);
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
        log.info("退款申请已提交：orderNo={}, refundNo={}, type={}, amount={}",
                orderNo, record.getRefundNo(), type, amount);
        return toVO(record);
    }

    /**
     * 订单状态 → 退款类型；返回 0 表示该状态不支持申请退款。
     *
     * <p><b>类型必须由后端按订单状态判定，不能让前端传</b>：它决定「退款完成时
     * 库存怎么处理」—— 未发货要回补、仅退款不能回补、退货退款要等验收才回补。
     * 让客户端决定等于把库存账交出去。</p>
     */
    private static int refundTypeOf(OrderStatus status) {
        return switch (status) {
            case PENDING_SHIPMENT -> RefundRecord.TYPE_UNSHIPPED;
            case PENDING_RECEIPT -> RefundRecord.TYPE_REFUND_ONLY;
            case COMPLETED -> RefundRecord.TYPE_RETURN_GOODS;
            default -> 0;
        };
    }

    /**
     * 用户撤销退款申请（待审核 / 待寄回可撤销）。
     *
     * <p>撤销后订单回到申请前的状态。★ <b>货已寄出（待收货）之后不允许撤销</b>：
     * 那批货已经在路上，撤了退款单，货和钱就都悬空了 —— 此时只能由商家验收或关闭。</p>
     */
    @Transactional
    public void cancelApply(Long userId, String orderNo) {
        orderService.getOwned(userId, orderNo);

        RefundRecord record = findActive(orderNo);
        if (record == null) {
            throw BizException.notFound("该订单没有进行中的退款申请");
        }
        RefundStatus status = RefundStatus.of(record.getStatus());
        if (!cancellable(status)) {
            throw BizException.conflict("退款单当前为「" + status.getLabel() + "」，无法撤销");
        }
        int affected = refundRecordMapper.update(null, new LambdaUpdateWrapper<RefundRecord>()
                .eq(RefundRecord::getId, record.getId())
                .eq(RefundRecord::getStatus, status.getCode())
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

    /** 可撤销的状态：还没寄货之前都能撤。已寄出之后再撤，货就悬空了。 */
    private static boolean cancellable(RefundStatus status) {
        return status == RefundStatus.PENDING_AUDIT || status == RefundStatus.WAIT_RETURN;
    }

    /**
     * 提交退货物流单号（待寄回 → 待收货）。
     *
     * <p>只有退货退款（type=3）需要寄回商品；未发货退款与仅退款没有这一步。</p>
     *
     * @param company    快递公司（可选）
     * @param trackingNo 物流单号（必填）
     * @throws BizException 没有进行中的申请、不是退货退款、状态不在「待寄回」
     */
    @Transactional
    public void submitTracking(Long userId, String orderNo, String company, String trackingNo) {
        orderService.getOwned(userId, orderNo);

        RefundRecord record = findActive(orderNo);
        if (record == null) {
            throw BizException.notFound("该订单没有进行中的退款申请");
        }
        if (!Integer.valueOf(RefundRecord.TYPE_RETURN_GOODS).equals(record.getType())) {
            throw BizException.conflict("该退款单不需要寄回商品");
        }
        int affected = refundRecordMapper.update(null, new LambdaUpdateWrapper<RefundRecord>()
                .eq(RefundRecord::getId, record.getId())
                .eq(RefundRecord::getStatus, RefundStatus.WAIT_RETURN.getCode())
                .set(RefundRecord::getStatus, RefundStatus.WAIT_RECEIVE.getCode())
                .set(RefundRecord::getReturnCompany, company)
                .set(RefundRecord::getReturnTrackingNo, trackingNo)
                .set(RefundRecord::getReturnTime, LocalDateTime.now()));
        if (affected == 0) {
            throw BizException.conflict("退款单当前不在「待寄回」状态，无法提交物流单号");
        }
        // ★ 不清 active_key：货还在路上，退款没结束
        log.info("退货物流已提交：orderNo={}, refundNo={}, trackingNo={}",
                orderNo, record.getRefundNo(), trackingNo);
    }

    // =========================================================================
    // 后台
    // =========================================================================

    /**
     * 同意退款申请。
     *
     * <p><b>★ 结果取决于退款类型</b>：</p>
     * <ul>
     *   <li>未发货退款 / 仅退款（type=1/2）：同意即完成 —— 置「已退款」、按类型处理库存、回退销量</li>
     *   <li>退货退款（type=3）：同意只是**开始退货流程** —— 置「待寄回」，库存与销量都还不能动</li>
     * </ul>
     *
     * <p>库存回补只在抢到订单状态流转时执行一次，所以重复点「同意」不会把库存补成两倍。</p>
     *
     * @throws BizException 退款单不存在、已被拒绝/撤销
     */
    @Transactional
    public void approve(String refundNo) {
        RefundRecord record = loadByRefundNo(refundNo);
        RefundStatus status = RefundStatus.of(record.getStatus());
        boolean returnGoods = Integer.valueOf(RefundRecord.TYPE_RETURN_GOODS).equals(record.getType());

        // 幂等：管理员连点、网络重试都会到这里
        if (status == RefundStatus.REFUNDED
                || (returnGoods && status == RefundStatus.WAIT_RETURN)) {
            log.info("退款单已处理过，跳过：refundNo={}, status={}", refundNo, status.getLabel());
            return;
        }
        if (status != RefundStatus.PENDING_AUDIT) {
            throw BizException.conflict("退款单当前为「" + status.getLabel() + "」，无法同意");
        }

        if (returnGoods) {
            approveReturnGoods(record);
            return;
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

        completeRefund(record);
    }

    /**
     * 同意退货退款：置「待寄回」，等用户寄货。
     *
     * <p>★ 这一步**不动库存、不动销量、不清 active_key**：退货才刚开始，
     * 用户可能根本不寄货。提前退款或提前回补库存都会让货凭空消失。</p>
     */
    private void approveReturnGoods(RefundRecord record) {
        int affected = refundRecordMapper.update(null, new LambdaUpdateWrapper<RefundRecord>()
                .eq(RefundRecord::getId, record.getId())
                .eq(RefundRecord::getStatus, RefundStatus.PENDING_AUDIT.getCode())
                .set(RefundRecord::getStatus, RefundStatus.WAIT_RETURN.getCode())
                .set(RefundRecord::getAuditTime, LocalDateTime.now()));
        if (affected == 0) {
            RefundRecord latest = loadByRefundNo(record.getRefundNo());
            if (RefundStatus.of(latest.getStatus()) == RefundStatus.WAIT_RETURN) {
                return;
            }
            throw BizException.conflict("退款申请已被处理，请刷新后重试");
        }
        log.info("退货退款已同意，等待用户寄回：refundNo={}, orderNo={}",
                record.getRefundNo(), record.getOrderNo());
    }

    /**
     * 确认收到退货（待收货 → 已退款），并回补库存、回退销量。
     *
     * <p>这是退货退款真正「退完」的那一刻，也是这批货的库存**唯一**被加回来的时点。</p>
     *
     * @param receiveRemark 验收备注（可选，如「包装破损，同意退款」）
     * @throws BizException 退款单不存在、状态不在「待收货」
     */
    @Transactional
    public void receive(String refundNo, String receiveRemark) {
        RefundRecord record = loadByRefundNo(refundNo);
        RefundStatus status = RefundStatus.of(record.getStatus());
        if (status == RefundStatus.REFUNDED) {
            log.info("退款单已是已退款状态，跳过：{}", refundNo);
            return;
        }
        if (status != RefundStatus.WAIT_RECEIVE) {
            throw BizException.conflict("退款单当前为「" + status.getLabel() + "」，无法确认收货");
        }

        int affected = refundRecordMapper.update(null, new LambdaUpdateWrapper<RefundRecord>()
                .eq(RefundRecord::getId, record.getId())
                .eq(RefundRecord::getStatus, RefundStatus.WAIT_RECEIVE.getCode())
                .set(RefundRecord::getStatus, RefundStatus.REFUNDED.getCode())
                .set(RefundRecord::getReceiveTime, LocalDateTime.now())
                .set(RefundRecord::getReceiveRemark, receiveRemark)
                .set(RefundRecord::getRefundTime, LocalDateTime.now())
                .setSql("active_key = NULL"));
        if (affected == 0) {
            RefundRecord latest = loadByRefundNo(refundNo);
            if (RefundStatus.of(latest.getStatus()) == RefundStatus.REFUNDED) {
                return;
            }
            throw BizException.conflict("退款申请已被处理，请刷新后重试");
        }

        // ★ 退货入库：货真的回来了，这时才回补库存
        if (orderService.finishRefund(record.getOrderNo())) {
            orderService.restoreStock(orderService.loadByOrderNo(record.getOrderNo()));
            orderService.reverseSales(record.getOrderNo());
            log.info("退货验收通过，库存已回补：refundNo={}, orderNo={}, amount={}",
                    refundNo, record.getOrderNo(), record.getAmount());
        } else {
            log.warn("退货单已置已退款，但订单不在退款中：refundNo={}, orderNo={}",
                    refundNo, record.getOrderNo());
        }
    }

    /**
     * 退款真正完成：推进订单到「已退款」、按类型处理库存、回退销量。
     *
     * <p>未发货退款与仅退款在「同意」时就走到这里；退货退款要等到「确认收货」。</p>
     */
    private void completeRefund(RefundRecord record) {
        if (orderService.finishRefund(record.getOrderNo())) {
            applyStockAction(record);
            orderService.reverseSales(record.getOrderNo());
            log.info("退款完成：refundNo={}, orderNo={}, type={}, amount={}",
                    record.getRefundNo(), record.getOrderNo(), record.getType(), record.getAmount());
        } else {
            // 理论上不该发生：退款单是待审核，订单必然在退款中。真出现说明数据被人改过
            log.warn("退款单已置已退款，但订单不在退款中：refundNo={}, orderNo={}",
                    record.getRefundNo(), record.getOrderNo());
        }
    }

    /**
     * 拒绝退款 / 关闭退货：置「已拒绝」→ 订单回退到申请前的状态。
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
        if (!rejectable(status)) {
            throw BizException.conflict("退款单当前为「" + status.getLabel() + "」，无法拒绝");
        }

        int affected = refundRecordMapper.update(null, new LambdaUpdateWrapper<RefundRecord>()
                .eq(RefundRecord::getId, record.getId())
                .eq(RefundRecord::getStatus, status.getCode())
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

    /**
     * 可拒绝 / 关闭的状态。
     *
     * <p>★ 除了「待审核」，还允许在「待寄回 / 待收货」关闭退货 —— 这是用户同意退货后
     * 长期不寄货的唯一兜底（退货超时自动关闭没做）。没有它，订单会永久卡在「退款中」，
     * 既不能确认收货也不能退款。</p>
     */
    private static boolean rejectable(RefundStatus status) {
        return status == RefundStatus.PENDING_AUDIT
                || status == RefundStatus.WAIT_RETURN
                || status == RefundStatus.WAIT_RECEIVE;
    }

    /**
     * 同意退款后的库存动作，按退款类型分流。
     *
     * <ul>
     *   <li><b>未发货退款（type=1）</b>：货没出库，立即回补</li>
     *   <li><b>仅退款（type=2）</b>：货已发出，<b>刻意不回补</b> —— 可能拒收退回、
     *       也可能用户留下了，凭空加回库存就是超卖的源头</li>
     * </ul>
     *
     * <p>★ 仅退款的代价是这批货的库存会一直少 1 件 —— 直到用户真的把货寄回、
     * 走退货退款（type=3）验收通过才补上。这是业务正确的：货确实不在仓里，
     * 加回去才会出错。这里打一条 INFO 留痕，方便日后对账时解释
     * 「为什么这个 SKU 的库存比预期少」。</p>
     */
    private void applyStockAction(RefundRecord record) {
        if (Integer.valueOf(RefundRecord.TYPE_REFUND_ONLY).equals(record.getType())) {
            log.info("仅退款不回补库存（货已发出）：orderNo={}, refundNo={}",
                    record.getOrderNo(), record.getRefundNo());
            return;
        }
        orderService.restoreStock(orderService.loadByOrderNo(record.getOrderNo()));
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
        vo.setReturnCompany(record.getReturnCompany());
        vo.setReturnTrackingNo(record.getReturnTrackingNo());
        vo.setReturnTime(record.getReturnTime());
        vo.setReceiveTime(record.getReceiveTime());
        vo.setReceiveRemark(record.getReceiveRemark());
        // 退货地址只在退货退款且未寄出时下发：寄出后再显示会让人以为还要再寄一次
        if (Integer.valueOf(RefundRecord.TYPE_RETURN_GOODS).equals(record.getType())
                && RefundStatus.of(record.getStatus()) == RefundStatus.WAIT_RETURN
                && returnAddress != null && !returnAddress.isBlank()) {
            vo.setReturnAddress(returnAddress);
        }
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
