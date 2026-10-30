package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.common.exception.BizException;
import com.tickcart.trade.dto.RefundApplyRequest;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.entity.PaymentRecord;
import com.tickcart.trade.entity.RefundRecord;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.enums.RefundStatus;
import com.tickcart.trade.mapper.PaymentRecordMapper;
import com.tickcart.trade.mapper.RefundRecordMapper;
import com.tickcart.trade.vo.RefundVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RefundService} 单元测试。
 *
 * <p>覆盖四条主路径（申请 / 撤销 / 同意 / 拒绝）与它们的幂等、并发、越权分支。
 * 重点断言三件事：<b>金额取自支付成功流水</b>、<b>active_key 的占用与释放</b>、
 * <b>库存与销量只在抢到状态流转时动一次</b>。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("退款服务")
class RefundServiceTest {

    private static final Long UID = 2001L;
    private static final String ORDER_NO = "NO20261029123456789";
    private static final String REFUND_NO = "RF202610291234567890001";

    @Mock
    private RefundRecordMapper refundRecordMapper;
    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private RefundService refundService;

    /**
     * 纯单元测试没有 Spring 容器，而 {@code LambdaQueryWrapper.eq(SFunction, value)}
     * 与 {@code LambdaUpdateWrapper.set(SFunction, value)} 会立即解析列名，
     * 需先手动初始化实体的 TableInfo 缓存，否则报 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), RefundRecord.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PaymentRecord.class);
    }

    // ---------------- 夹具 ----------------

    private Order order(int status, BigDecimal payAmount) {
        Order o = new Order();
        o.setId(9L);
        o.setOrderNo(ORDER_NO);
        o.setUserId(UID);
        o.setChannel(0);
        o.setActivityId(0L);
        o.setStatus(status);
        o.setPayAmount(payAmount);
        return o;
    }

    private PaymentRecord paidPayment(BigDecimal amount) {
        PaymentRecord p = new PaymentRecord();
        p.setId(31L);
        p.setOrderNo(ORDER_NO);
        p.setPayNo("PAY001");
        p.setAmount(amount);
        p.setStatus(PaymentRecord.STATUS_SUCCESS);
        return p;
    }

    private RefundRecord refund(int status, int originStatus) {
        RefundRecord r = new RefundRecord();
        r.setId(7L);
        r.setRefundNo(REFUND_NO);
        r.setOrderNo(ORDER_NO);
        r.setOrderId(9L);
        r.setUserId(UID);
        r.setType(RefundRecord.TYPE_UNSHIPPED);
        r.setAmount(new BigDecimal("188.00"));
        r.setReasonCode(1);
        r.setStatus(status);
        r.setOriginStatus(originStatus);
        r.setActiveKey(RefundStatus.of(status).isFinal() ? null : ORDER_NO);
        return r;
    }

    private RefundApplyRequest applyRequest() {
        RefundApplyRequest req = new RefundApplyRequest();
        req.setReasonCode(1);
        req.setReason("拍错了规格");
        return req;
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<RefundRecord> captureRefundUpdate() {
        ArgumentCaptor<Wrapper<RefundRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(refundRecordMapper).update(ArgumentMatchers.<RefundRecord>isNull(), captor.capture());
        return (LambdaUpdateWrapper<RefundRecord>) captor.getValue();
    }

    // ---------------- 申请退款 ----------------

    @Test
    @DisplayName("申请退款：金额取支付成功流水而非订单实付，active_key 占住订单号")
    void applyTakesAmountFromSuccessfulPayment() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.PENDING_SHIPMENT.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any())).thenReturn(null);
        // 支付流水 188，订单实付 198：必须取 188（有优惠/改价时两者不同）
        when(paymentRecordMapper.selectOne(any())).thenReturn(paidPayment(new BigDecimal("188.00")));
        when(orderService.startRefund(ORDER_NO)).thenReturn(OrderStatus.PENDING_SHIPMENT.getCode());

        RefundVO vo = refundService.apply(UID, ORDER_NO, applyRequest());

        ArgumentCaptor<RefundRecord> captor = ArgumentCaptor.forClass(RefundRecord.class);
        verify(refundRecordMapper).insert(captor.capture());
        RefundRecord saved = captor.getValue();

        assertThat(saved.getAmount()).isEqualByComparingTo("188.00");
        assertThat(saved.getActiveKey()).isEqualTo(ORDER_NO);
        assertThat(saved.getOriginStatus()).isEqualTo(OrderStatus.PENDING_SHIPMENT.getCode());
        assertThat(saved.getStatus()).isEqualTo(RefundStatus.PENDING_AUDIT.getCode());
        assertThat(saved.getType()).isEqualTo(RefundRecord.TYPE_UNSHIPPED);
        assertThat(saved.getRefundNo()).startsWith("RF");

        assertThat(vo.getStatusLabel()).isEqualTo("待审核");
        assertThat(vo.getReasonLabel()).isEqualTo("不想要了");
        assertThat(vo.getTypeLabel()).isEqualTo("未发货退款");
    }

    @Test
    @DisplayName("申请退款：没有成功流水时回退订单实付金额")
    void applyFallsBackToPayAmount() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.PENDING_SHIPMENT.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);
        when(orderService.startRefund(ORDER_NO)).thenReturn(OrderStatus.PENDING_SHIPMENT.getCode());

        refundService.apply(UID, ORDER_NO, applyRequest());

        ArgumentCaptor<RefundRecord> captor = ArgumentCaptor.forClass(RefundRecord.class);
        verify(refundRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("198.00");
    }

    @Test
    @DisplayName("申请退款：可退金额为 0 时拒绝，不落退款单")
    void applyRejectsZeroAmount() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.PENDING_SHIPMENT.getCode(), BigDecimal.ZERO));
        when(refundRecordMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> refundService.apply(UID, ORDER_NO, applyRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有可退款的金额");

        // 只断言「没有推进订单状态」—— getOwned 已经调过了，不能用 verifyNoInteractions
        verify(orderService, never()).startRefund(any());
        verify(refundRecordMapper, never()).insert(ArgumentMatchers.<RefundRecord>any());
    }

    @Test
    @DisplayName("申请退款：待付款 / 已取消等状态不支持，应先走取消")
    void applyRejectsNonRefundableStatus() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.PENDING_PAYMENT.getCode(), new BigDecimal("198.00")));

        assertThatThrownBy(() -> refundService.apply(UID, ORDER_NO, applyRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("待付款")
                .hasMessageContaining("暂不支持申请退款");

        verify(refundRecordMapper, never()).insert(ArgumentMatchers.<RefundRecord>any());
    }

    @Test
    @DisplayName("申请退款：待收货订单落「仅退款」类型（决定不回补库存）")
    void applyFromShippedOrderUsesRefundOnlyType() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.PENDING_RECEIPT.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(paidPayment(new BigDecimal("188.00")));
        when(orderService.startRefund(ORDER_NO)).thenReturn(OrderStatus.PENDING_RECEIPT.getCode());

        RefundVO vo = refundService.apply(UID, ORDER_NO, applyRequest());

        ArgumentCaptor<RefundRecord> captor = ArgumentCaptor.forClass(RefundRecord.class);
        verify(refundRecordMapper).insert(captor.capture());
        // ★ 类型由后端按订单状态判定，不能让前端传：它决定库存要不要回补
        assertThat(captor.getValue().getType()).isEqualTo(RefundRecord.TYPE_REFUND_ONLY);
        assertThat(captor.getValue().getOriginStatus()).isEqualTo(OrderStatus.PENDING_RECEIPT.getCode());
        assertThat(vo.getTypeLabel()).isEqualTo("仅退款");
    }

    @Test
    @DisplayName("申请退款：已有进行中的申请时拒绝，不重复落单")
    void applyRejectsDuplicateActive() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.PENDING_SHIPMENT.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(refund(RefundStatus.PENDING_AUDIT.getCode(),
                        OrderStatus.PENDING_SHIPMENT.getCode()));

        assertThatThrownBy(() -> refundService.apply(UID, ORDER_NO, applyRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已有进行中的退款申请");

        verify(refundRecordMapper, never()).insert(ArgumentMatchers.<RefundRecord>any());
    }

    @Test
    @DisplayName("申请退款：并发下撞唯一键，转成业务冲突而不是 500")
    void applyTranslatesDuplicateKey() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.PENDING_SHIPMENT.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(paidPayment(new BigDecimal("188.00")));
        when(orderService.startRefund(ORDER_NO)).thenReturn(OrderStatus.PENDING_SHIPMENT.getCode());
        when(refundRecordMapper.insert(ArgumentMatchers.<RefundRecord>any())).thenThrow(new DuplicateKeyException("uk_active_order"));

        assertThatThrownBy(() -> refundService.apply(UID, ORDER_NO, applyRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已有进行中的退款申请");
    }

    @Test
    @DisplayName("申请退款：他人订单按不存在处理，不泄露订单归属")
    void applyRejectsOthersOrder() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenThrow(BizException.notFound("订单不存在"));

        assertThatThrownBy(() -> refundService.apply(UID, ORDER_NO, applyRequest()))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");

        verifyNoInteractions(refundRecordMapper);
    }

    // ---------------- 撤销 ----------------

    @Test
    @DisplayName("撤销退款：置已撤销、显式清空 active_key、订单回退到申请前状态")
    void cancelApplyReleasesActiveKeyAndRollsBack() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.REFUNDING.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(refund(RefundStatus.PENDING_AUDIT.getCode(),
                        OrderStatus.PENDING_SHIPMENT.getCode()));
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);

        refundService.cancelApply(UID, ORDER_NO);

        String sqlSet = captureRefundUpdate().getSqlSet();
        // ★ 必须显式写 NULL：updateById 写不进 null，不释放唯一键就再也申请不了
        assertThat(sqlSet).contains("active_key = NULL");
        assertThat(sqlSet).contains("status");
        verify(orderService).rollbackRefund(ORDER_NO, OrderStatus.PENDING_SHIPMENT.getCode());
    }

    @Test
    @DisplayName("撤销退款：没有进行中的申请时 404")
    void cancelApplyWithoutActiveRefund() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.PENDING_SHIPMENT.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> refundService.cancelApply(UID, ORDER_NO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有进行中的退款申请");
    }

    @Test
    @DisplayName("撤销退款：申请已被处理时冲突，不回退订单")
    void cancelApplyAfterAudited() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.REFUNDING.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(refund(RefundStatus.PENDING_AUDIT.getCode(),
                        OrderStatus.PENDING_SHIPMENT.getCode()));
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> refundService.cancelApply(UID, ORDER_NO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已被处理");

        verify(orderService, never()).rollbackRefund(any(), anyInt());
    }

    // ---------------- 同意退款 ----------------

    @Test
    @DisplayName("同意退款：抢到订单流转才回补库存并回退销量")
    void approveRestoresStockOnce() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(refund(RefundStatus.PENDING_AUDIT.getCode(),
                        OrderStatus.PENDING_SHIPMENT.getCode()));
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);
        when(orderService.finishRefund(ORDER_NO)).thenReturn(true);
        Order order = order(OrderStatus.REFUNDED.getCode(), new BigDecimal("198.00"));
        when(orderService.loadByOrderNo(ORDER_NO)).thenReturn(order);

        refundService.approve(REFUND_NO);

        assertThat(captureRefundUpdate().getSqlSet()).contains("active_key = NULL");
        verify(orderService).finishRefund(ORDER_NO);
        verify(orderService).restoreStock(order);
        verify(orderService).reverseSales(ORDER_NO);
    }

    @Test
    @DisplayName("同意仅退款：不回补库存（货已发出），但仍回退销量")
    void approveRefundOnlyDoesNotRestoreStock() {
        RefundRecord record = refund(RefundStatus.PENDING_AUDIT.getCode(),
                OrderStatus.PENDING_RECEIPT.getCode());
        record.setType(RefundRecord.TYPE_REFUND_ONLY);
        when(refundRecordMapper.selectOne(any())).thenReturn(record);
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);
        when(orderService.finishRefund(ORDER_NO)).thenReturn(true);

        refundService.approve(REFUND_NO);

        verify(orderService).finishRefund(ORDER_NO);
        // ★ 关键断言：货已发出，不能凭空加回库存 —— 这是超卖的源头
        verify(orderService, never()).restoreStock(any());
        verify(orderService, never()).loadByOrderNo(any());
        // 销量仍然要退：用户没拿到货
        verify(orderService).reverseSales(ORDER_NO);
    }

    @Test
    @DisplayName("拒绝仅退款：订单回退到待收货，不是待发货")
    void rejectRefundOnlyRollsBackToPendingReceipt() {
        RefundRecord record = refund(RefundStatus.PENDING_AUDIT.getCode(),
                OrderStatus.PENDING_RECEIPT.getCode());
        record.setType(RefundRecord.TYPE_REFUND_ONLY);
        when(refundRecordMapper.selectOne(any())).thenReturn(record);
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);

        refundService.reject(REFUND_NO, "已签收，不支持仅退款");

        // ★ 回退目标取自 origin_status，不能写死「待发货」
        verify(orderService).rollbackRefund(ORDER_NO, OrderStatus.PENDING_RECEIPT.getCode());
    }

    @Test
    @DisplayName("同意退款：重复提交按幂等返回，不重复回补库存")
    void approveIsIdempotentWhenAlreadyRefunded() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(refund(RefundStatus.REFUNDED.getCode(),
                        OrderStatus.PENDING_SHIPMENT.getCode()));

        refundService.approve(REFUND_NO);

        verify(refundRecordMapper, never()).update(any(), any());
        verify(orderService, never()).restoreStock(any());
        verify(orderService, never()).reverseSales(any());
    }

    @Test
    @DisplayName("同意退款：已拒绝的退款单不能再同意")
    void approveRejectsWhenAlreadyRejected() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(refund(RefundStatus.REJECTED.getCode(),
                        OrderStatus.PENDING_SHIPMENT.getCode()));

        assertThatThrownBy(() -> refundService.approve(REFUND_NO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已拒绝")
                .hasMessageContaining("无法同意");

        verify(orderService, never()).restoreStock(any());
    }

    @Test
    @DisplayName("同意退款：没抢到订单流转时只告警，不回补库存")
    void approveSkipsRestoreWhenOrderNotRefunding() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(refund(RefundStatus.PENDING_AUDIT.getCode(),
                        OrderStatus.PENDING_SHIPMENT.getCode()));
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);
        when(orderService.finishRefund(ORDER_NO)).thenReturn(false);

        refundService.approve(REFUND_NO);

        verify(orderService, never()).restoreStock(any());
        verify(orderService, never()).reverseSales(any());
    }

    // ---------------- 拒绝退款 ----------------

    @Test
    @DisplayName("拒绝退款：置已拒绝、清空 active_key、订单回退")
    void rejectRollsBackOrder() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(refund(RefundStatus.PENDING_AUDIT.getCode(),
                        OrderStatus.PENDING_SHIPMENT.getCode()));
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);

        refundService.reject(REFUND_NO, "商品已发出，请收到后申请退货退款");

        String sqlSet = captureRefundUpdate().getSqlSet();
        assertThat(sqlSet).contains("active_key = NULL");
        assertThat(sqlSet).contains("audit_remark");
        verify(orderService).rollbackRefund(ORDER_NO, OrderStatus.PENDING_SHIPMENT.getCode());
        // 拒绝不涉及库存与销量
        verify(orderService, never()).restoreStock(any());
        verify(orderService, never()).reverseSales(any());
    }

    @Test
    @DisplayName("拒绝退款：已退款的不允许再拒绝")
    void rejectAfterRefunded() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(refund(RefundStatus.REFUNDED.getCode(),
                        OrderStatus.PENDING_SHIPMENT.getCode()));

        assertThatThrownBy(() -> refundService.reject(REFUND_NO, "不同意"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法拒绝");

        verify(orderService, never()).rollbackRefund(any(), anyInt());
    }

    // ---------------- P2 退货退款 ----------------

    /** 造一张退货退款单（type=3）。 */
    private RefundRecord returnRefund(int status, int originStatus) {
        RefundRecord r = refund(status, originStatus);
        r.setType(RefundRecord.TYPE_RETURN_GOODS);
        return r;
    }

    @Test
    @DisplayName("申请退款：已完成订单落「退货退款」类型")
    void applyFromCompletedOrderUsesReturnGoodsType() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.COMPLETED.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any())).thenReturn(null);
        when(paymentRecordMapper.selectOne(any())).thenReturn(paidPayment(new BigDecimal("188.00")));
        when(orderService.startRefund(ORDER_NO)).thenReturn(OrderStatus.COMPLETED.getCode());

        RefundVO vo = refundService.apply(UID, ORDER_NO, applyRequest());

        ArgumentCaptor<RefundRecord> captor = ArgumentCaptor.forClass(RefundRecord.class);
        verify(refundRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(RefundRecord.TYPE_RETURN_GOODS);
        assertThat(captor.getValue().getOriginStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());
        assertThat(vo.getTypeLabel()).isEqualTo("退货退款");
    }

    @Test
    @DisplayName("同意退货退款：只进「待寄回」，不动库存、不动销量、不清 active_key")
    void approveReturnGoodsWaitsForShipment() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(returnRefund(RefundStatus.PENDING_AUDIT.getCode(),
                        OrderStatus.COMPLETED.getCode()));
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);

        refundService.approve(REFUND_NO);

        String sqlSet = captureRefundUpdate().getSqlSet();
        assertThat(sqlSet).contains("status");
        // ★ 退货才刚开始：这三件事一件都不能做
        assertThat(sqlSet).doesNotContain("active_key = NULL");
        assertThat(sqlSet).doesNotContain("refund_time");
        verify(orderService, never()).finishRefund(any());
        verify(orderService, never()).restoreStock(any());
        verify(orderService, never()).reverseSales(any());
    }

    @Test
    @DisplayName("提交退货物流：待寄回 → 待收货，写入单号与寄出时间")
    void submitTrackingMovesToWaitReceive() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.REFUNDING.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(returnRefund(RefundStatus.WAIT_RETURN.getCode(),
                        OrderStatus.COMPLETED.getCode()));
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);

        refundService.submitTracking(UID, ORDER_NO, "顺丰", "SF1234567890");

        String sqlSet = captureRefundUpdate().getSqlSet();
        assertThat(sqlSet).contains("return_tracking_no").contains("return_company").contains("return_time");
        // 货还在路上，不能清 active_key
        assertThat(sqlSet).doesNotContain("active_key = NULL");
    }

    @Test
    @DisplayName("提交退货物流：未发货退款/仅退款不需要寄回")
    void submitTrackingRejectedForNonReturnRefund() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.REFUNDING.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(refund(RefundStatus.WAIT_RETURN.getCode(), OrderStatus.PENDING_RECEIPT.getCode()));

        assertThatThrownBy(() -> refundService.submitTracking(UID, ORDER_NO, "顺丰", "SF1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不需要寄回商品");

        verify(refundRecordMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("确认收到退货：置已退款 + 回补库存 + 回退销量")
    void receiveRestoresStock() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(returnRefund(RefundStatus.WAIT_RECEIVE.getCode(),
                        OrderStatus.COMPLETED.getCode()));
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);
        when(orderService.finishRefund(ORDER_NO)).thenReturn(true);
        Order order = order(OrderStatus.REFUNDED.getCode(), new BigDecimal("198.00"));
        when(orderService.loadByOrderNo(ORDER_NO)).thenReturn(order);

        refundService.receive(REFUND_NO, "外包装破损，商品完好");

        String sqlSet = captureRefundUpdate().getSqlSet();
        assertThat(sqlSet).contains("receive_time").contains("receive_remark").contains("active_key = NULL");
        // ★ 货真的回来了，这时才回补库存
        verify(orderService).restoreStock(order);
        verify(orderService).reverseSales(ORDER_NO);
    }

    @Test
    @DisplayName("确认收到退货：重复提交按幂等返回，不重复回补库存")
    void receiveIsIdempotent() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(returnRefund(RefundStatus.REFUNDED.getCode(), OrderStatus.COMPLETED.getCode()));

        refundService.receive(REFUND_NO, null);

        verify(refundRecordMapper, never()).update(any(), any());
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    @DisplayName("确认收到退货：还没寄出时不能收货")
    void receiveRejectedBeforeShipped() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(returnRefund(RefundStatus.WAIT_RETURN.getCode(),
                        OrderStatus.COMPLETED.getCode()));

        assertThatThrownBy(() -> refundService.receive(REFUND_NO, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("待寄回")
                .hasMessageContaining("无法确认收货");

        verify(orderService, never()).restoreStock(any());
    }

    @Test
    @DisplayName("撤销退货退款：待寄回可撤销，订单回到已完成")
    void cancelReturnBeforeShipped() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.REFUNDING.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(returnRefund(RefundStatus.WAIT_RETURN.getCode(),
                        OrderStatus.COMPLETED.getCode()));
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);

        refundService.cancelApply(UID, ORDER_NO);

        verify(orderService).rollbackRefund(ORDER_NO, OrderStatus.COMPLETED.getCode());
    }

    @Test
    @DisplayName("撤销退货退款：货已寄出后不允许撤销，否则货和钱都悬空")
    void cancelRejectedAfterShipped() {
        when(orderService.getOwned(UID, ORDER_NO))
                .thenReturn(order(OrderStatus.REFUNDING.getCode(), new BigDecimal("198.00")));
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(returnRefund(RefundStatus.WAIT_RECEIVE.getCode(),
                        OrderStatus.COMPLETED.getCode()));

        assertThatThrownBy(() -> refundService.cancelApply(UID, ORDER_NO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("待收货")
                .hasMessageContaining("无法撤销");

        verify(orderService, never()).rollbackRefund(any(), anyInt());
    }

    @Test
    @DisplayName("关闭退货：待收货也可拒绝，作为用户长期不寄货的兜底")
    void rejectFromWaitReceiveClosesReturn() {
        when(refundRecordMapper.selectOne(any()))
                .thenReturn(returnRefund(RefundStatus.WAIT_RECEIVE.getCode(),
                        OrderStatus.COMPLETED.getCode()));
        when(refundRecordMapper.update(ArgumentMatchers.<RefundRecord>isNull(), any())).thenReturn(1);

        refundService.reject(REFUND_NO, "超时未收到退货，关闭本次售后");

        verify(orderService).rollbackRefund(ORDER_NO, OrderStatus.COMPLETED.getCode());
        verify(orderService, never()).restoreStock(any());
    }

    // ---------------- 查询 ----------------

    @Test
    @DisplayName("订单详情：没有退款记录时返回 null")
    void findLatestReturnsNullWithoutRecord() {
        when(refundRecordMapper.selectOne(any())).thenReturn(null);

        assertThat(refundService.findLatest(ORDER_NO)).isNull();
    }

    @Test
    @DisplayName("订单详情：有退款记录时带上状态与原因文案")
    void findLatestMapsLabels() {
        RefundRecord record = refund(RefundStatus.REJECTED.getCode(),
                OrderStatus.PENDING_SHIPMENT.getCode());
        record.setAuditRemark("商品已发出");
        when(refundRecordMapper.selectOne(any())).thenReturn(record);

        RefundVO vo = refundService.findLatest(ORDER_NO);

        assertThat(vo).isNotNull();
        assertThat(vo.getStatusLabel()).isEqualTo("已拒绝");
        assertThat(vo.getAuditRemark()).isEqualTo("商品已发出");
        assertThat(vo.getAmount()).isEqualByComparingTo("188.00");
    }
}
