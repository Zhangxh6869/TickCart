package com.tickcart.trade.service;

import com.tickcart.common.exception.BizException;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.entity.PaymentRecord;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.enums.PaymentNotifyResult;
import com.tickcart.trade.mapper.PaymentRecordMapper;
import com.tickcart.trade.vo.PaymentStatusVO;
import com.tickcart.trade.vo.PaymentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PaymentService} 单元测试。
 *
 * <p>覆盖三段式支付的边界：发起只落「待支付」且不推进订单、渠道回调落终态、
 * 回调幂等、以及轮询用的状态查询。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("支付服务")
class PaymentServiceTest {

    private static final Long UID = 1001L;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentService paymentService;

    private Order order(String orderNo, Long userId, OrderStatus status) {
        Order o = new Order();
        o.setId(100L);
        o.setOrderNo(orderNo);
        o.setUserId(userId);
        o.setChannel(0);
        o.setStatus(status.getCode());
        o.setTotalAmount(new BigDecimal("198.00"));
        o.setPayAmount(new BigDecimal("198.00"));
        return o;
    }

    private PaymentRecord payment(String payNo, String orderNo, int status) {
        PaymentRecord p = new PaymentRecord();
        p.setId(1L);
        p.setOrderId(100L);
        p.setOrderNo(orderNo);
        p.setPayNo(payNo);
        p.setChannel(1);
        p.setAmount(new BigDecimal("198.00"));
        p.setStatus(status);
        return p;
    }

    // ---------------- 发起支付 ----------------

    @Test
    @DisplayName("发起支付：只落待支付流水，不推进订单")
    void createPaymentOnlyPersistsPendingRecord() {
        when(orderService.getOwned(UID, "NO1")).thenReturn(order("NO1", UID, OrderStatus.PENDING_PAYMENT));
        when(paymentRecordMapper.insert(any(PaymentRecord.class))).thenAnswer(inv -> {
            PaymentRecord p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        });

        PaymentVO result = paymentService.createPayment(UID, "NO1", 1);

        assertThat(result.getOrderNo()).isEqualTo("NO1");
        assertThat(result.getAmount()).isEqualByComparingTo("198.00");
        assertThat(result.getChannel()).isEqualTo(1);
        // 关键：此时订单还没有支付，流水必须是「待支付」且支付时间为空
        assertThat(result.getStatus()).isEqualTo(PaymentRecord.STATUS_PENDING);
        assertThat(result.getPayTime()).isNull();

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getPayNo()).startsWith("PAY");
        assertThat(captor.getValue().getOrderId()).isEqualTo(100L);
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentRecord.STATUS_PENDING);

        // 发起阶段绝不能推进订单，也不能更新流水
        verify(orderService, never()).markPaid(any());
        verify(paymentRecordMapper, never()).updateById(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("发起支付：金额取订单实付金额")
    void createPaymentUsesOrderPayAmount() {
        Order o = order("NO2", UID, OrderStatus.PENDING_PAYMENT);
        o.setPayAmount(new BigDecimal("66.60"));
        when(orderService.getOwned(UID, "NO2")).thenReturn(o);
        when(paymentRecordMapper.insert(any(PaymentRecord.class))).thenReturn(1);

        PaymentVO result = paymentService.createPayment(UID, "NO2", 2);

        assertThat(result.getAmount()).isEqualByComparingTo("66.60");
        assertThat(result.getChannel()).isEqualTo(2);
    }

    @Test
    @DisplayName("不能支付他人的订单")
    void createPaymentForeignOrderRejected() {
        when(orderService.getOwned(UID, "NO9")).thenThrow(BizException.notFound("订单不存在"));

        assertThatThrownBy(() -> paymentService.createPayment(UID, "NO9", 1))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("已支付订单不能重复支付")
    void createPaymentNonPendingOrderRejected() {
        when(orderService.getOwned(UID, "NO3")).thenReturn(order("NO3", UID, OrderStatus.PENDING_SHIPMENT));

        assertThatThrownBy(() -> paymentService.createPayment(UID, "NO3", 1))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前为「待发货」，无法支付");
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("已取消订单不能支付")
    void createPaymentCancelledOrderRejected() {
        when(orderService.getOwned(UID, "NO4")).thenReturn(order("NO4", UID, OrderStatus.CANCELLED));

        assertThatThrownBy(() -> paymentService.createPayment(UID, "NO4", 1))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前为「已取消」，无法支付");
    }

    // ---------------- 渠道回调 ----------------

    @Test
    @DisplayName("渠道回调成功：流水置成功并推进订单")
    void channelResultSuccessAdvancesOrder() {
        when(paymentRecordMapper.selectOne(any()))
                .thenReturn(payment("PAY1", "NO1", PaymentRecord.STATUS_PENDING));
        when(paymentRecordMapper.updateById(any(PaymentRecord.class))).thenReturn(1);

        PaymentRecord settled = paymentService.applyChannelResult("PAY1", PaymentNotifyResult.SUCCESS);

        assertThat(settled.getStatus()).isEqualTo(PaymentRecord.STATUS_SUCCESS);
        assertThat(settled.getCallbackTime()).isNotNull();
        verify(orderService).markPaid("NO1");
    }

    @Test
    @DisplayName("渠道回调失败：流水置失败，订单保持待付款")
    void channelResultFailedKeepsOrderPending() {
        when(paymentRecordMapper.selectOne(any()))
                .thenReturn(payment("PAY6", "NO6", PaymentRecord.STATUS_PENDING));
        when(paymentRecordMapper.updateById(any(PaymentRecord.class))).thenReturn(1);

        paymentService.applyChannelResult("PAY6", PaymentNotifyResult.FAILED);

        verify(paymentRecordMapper).updateById(ArgumentMatchers.<PaymentRecord>argThat(
                p -> p.getStatus() == PaymentRecord.STATUS_FAILED && p.getCallbackTime() != null));
        verify(orderService, never()).markPaid(any());
    }

    @Test
    @DisplayName("用户取消支付：流水置已关闭，订单保持待付款")
    void channelResultCancelledKeepsOrderPending() {
        when(paymentRecordMapper.selectOne(any()))
                .thenReturn(payment("PAY7", "NO7", PaymentRecord.STATUS_PENDING));
        when(paymentRecordMapper.updateById(any(PaymentRecord.class))).thenReturn(1);

        paymentService.applyChannelResult("PAY7", PaymentNotifyResult.CANCELLED);

        verify(paymentRecordMapper).updateById(ArgumentMatchers.<PaymentRecord>argThat(
                p -> p.getStatus() == PaymentRecord.STATUS_CLOSED && p.getCallbackTime() != null));
        verify(orderService, never()).markPaid(any());
    }

    @Test
    @DisplayName("回调幂等：已成功的记录不再重复处理")
    void callbackIdempotent() {
        when(paymentRecordMapper.selectOne(any()))
                .thenReturn(payment("PAY5", "NO5", PaymentRecord.STATUS_SUCCESS));

        paymentService.handleCallback("PAY5", true);

        verify(paymentRecordMapper, never()).updateById(any(PaymentRecord.class));
        verify(orderService, never()).markPaid(any());
    }

    @Test
    @DisplayName("回调幂等：已关闭的记录也不会被后续通知改写")
    void callbackIdempotentOnClosedRecord() {
        when(paymentRecordMapper.selectOne(any()))
                .thenReturn(payment("PAY8", "NO8", PaymentRecord.STATUS_CLOSED));

        PaymentRecord result = paymentService.applyChannelResult("PAY8", PaymentNotifyResult.SUCCESS);

        assertThat(result.getStatus()).isEqualTo(PaymentRecord.STATUS_CLOSED);
        verify(paymentRecordMapper, never()).updateById(any(PaymentRecord.class));
        verify(orderService, never()).markPaid(any());
    }

    @Test
    @DisplayName("回调失败：记录置失败，订单保持待付款")
    void callbackFailed() {
        when(paymentRecordMapper.selectOne(any()))
                .thenReturn(payment("PAY6", "NO6", PaymentRecord.STATUS_PENDING));
        when(paymentRecordMapper.updateById(any(PaymentRecord.class))).thenReturn(1);

        paymentService.handleCallback("PAY6", false);

        verify(paymentRecordMapper).updateById(ArgumentMatchers.<PaymentRecord>argThat(
                p -> p.getStatus() == PaymentRecord.STATUS_FAILED && p.getCallbackTime() != null));
        verify(orderService, never()).markPaid(any());
    }

    @Test
    @DisplayName("回调未知流水号时拒绝")
    void callbackUnknownPayNo() {
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> paymentService.handleCallback("PAY_X", true))
                .isInstanceOf(BizException.class)
                .hasMessage("支付记录不存在");
    }

    // ---------------- 支付状态查询 ----------------

    @Test
    @DisplayName("查询支付状态：带出订单状态与最新流水状态")
    void queryStatusReturnsBothSides() {
        when(orderService.getOwned(UID, "NO1")).thenReturn(order("NO1", UID, OrderStatus.PENDING_PAYMENT));
        when(paymentRecordMapper.selectOne(any()))
                .thenReturn(payment("PAY1", "NO1", PaymentRecord.STATUS_FAILED));

        PaymentStatusVO vo = paymentService.queryStatus(UID, "NO1");

        assertThat(vo.getOrderNo()).isEqualTo("NO1");
        assertThat(vo.getOrderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.getCode());
        assertThat(vo.getOrderStatusLabel()).isEqualTo("待付款");
        assertThat(vo.getPayNo()).isEqualTo("PAY1");
        assertThat(vo.getPayStatus()).isEqualTo(PaymentRecord.STATUS_FAILED);
        assertThat(vo.getPayStatusLabel()).isEqualTo("支付失败");
        assertThat(vo.getChannel()).isEqualTo(1);
    }

    @Test
    @DisplayName("查询支付状态：尚未发起过支付时流水字段为空")
    void queryStatusWithoutRecord() {
        when(orderService.getOwned(UID, "NO2")).thenReturn(order("NO2", UID, OrderStatus.PENDING_PAYMENT));
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);

        PaymentStatusVO vo = paymentService.queryStatus(UID, "NO2");

        assertThat(vo.getPayNo()).isNull();
        assertThat(vo.getPayStatus()).isNull();
        assertThat(vo.getOrderStatusLabel()).isEqualTo("待付款");
    }

    @Test
    @DisplayName("查询支付状态：订单已支付时订单状态为待发货")
    void queryStatusAfterPaid() {
        when(orderService.getOwned(UID, "NO3")).thenReturn(order("NO3", UID, OrderStatus.PENDING_SHIPMENT));
        when(paymentRecordMapper.selectOne(any()))
                .thenReturn(payment("PAY3", "NO3", PaymentRecord.STATUS_SUCCESS));

        PaymentStatusVO vo = paymentService.queryStatus(UID, "NO3");

        assertThat(vo.getOrderStatus()).isEqualTo(OrderStatus.PENDING_SHIPMENT.getCode());
        assertThat(vo.getOrderStatusLabel()).isEqualTo("待发货");
        assertThat(vo.getPayStatus()).isEqualTo(PaymentRecord.STATUS_SUCCESS);
    }
}
