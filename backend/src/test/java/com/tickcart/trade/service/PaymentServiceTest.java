package com.tickcart.trade.service;

import com.tickcart.common.exception.BizException;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.entity.PaymentRecord;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.mapper.PaymentRecordMapper;
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
 * <p>覆盖模拟支付的落库形态、状态防御，以及回调入口的幂等性。</p>
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
    @DisplayName("模拟支付成功：先落待支付记录，再经回调置为成功并推进订单")
    void paySuccess() {
        when(orderService.getOwned(UID, "NO1")).thenReturn(order("NO1", UID, OrderStatus.PENDING_PAYMENT));
        when(paymentRecordMapper.insert(any(PaymentRecord.class))).thenAnswer(inv -> {
            PaymentRecord p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        });
        when(paymentRecordMapper.selectOne(any()))
                .thenReturn(payment("PAY1", "NO1", PaymentRecord.STATUS_PENDING));
        when(paymentRecordMapper.updateById(any(PaymentRecord.class))).thenReturn(1);

        PaymentVO result = paymentService.pay(UID, "NO1", 1);

        assertThat(result.getOrderNo()).isEqualTo("NO1");
        assertThat(result.getAmount()).isEqualByComparingTo("198.00");
        assertThat(result.getChannel()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(PaymentRecord.STATUS_SUCCESS);
        assertThat(result.getPayTime()).isNotNull();

        // 落库的初始记录必须是「待支付」，随后由回调推进为成功
        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getPayNo()).startsWith("PAY");
        assertThat(captor.getValue().getOrderId()).isEqualTo(100L);
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentRecord.STATUS_PENDING);

        verify(orderService).markPaid("NO1");
    }

    @Test
    @DisplayName("支付金额取订单实付金额")
    void payUsesOrderPayAmount() {
        Order o = order("NO2", UID, OrderStatus.PENDING_PAYMENT);
        o.setPayAmount(new BigDecimal("66.60"));
        when(orderService.getOwned(UID, "NO2")).thenReturn(o);
        when(paymentRecordMapper.insert(any(PaymentRecord.class))).thenReturn(1);
        when(paymentRecordMapper.selectOne(any()))
                .thenReturn(payment("PAY2", "NO2", PaymentRecord.STATUS_PENDING));
        when(paymentRecordMapper.updateById(any(PaymentRecord.class))).thenReturn(1);

        PaymentVO result = paymentService.pay(UID, "NO2", 2);

        assertThat(result.getAmount()).isEqualByComparingTo("66.60");
        assertThat(result.getChannel()).isEqualTo(2);
    }

    @Test
    @DisplayName("不能支付他人的订单")
    void payForeignOrderRejected() {
        when(orderService.getOwned(UID, "NO9")).thenThrow(BizException.notFound("订单不存在"));

        assertThatThrownBy(() -> paymentService.pay(UID, "NO9", 1))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("已支付订单不能重复支付")
    void payNonPendingOrderRejected() {
        when(orderService.getOwned(UID, "NO3")).thenReturn(order("NO3", UID, OrderStatus.PENDING_SHIPMENT));

        assertThatThrownBy(() -> paymentService.pay(UID, "NO3", 1))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前为「待发货」，无法支付");
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("已取消订单不能支付")
    void payCancelledOrderRejected() {
        when(orderService.getOwned(UID, "NO4")).thenReturn(order("NO4", UID, OrderStatus.CANCELLED));

        assertThatThrownBy(() -> paymentService.pay(UID, "NO4", 1))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前为「已取消」，无法支付");
    }

    // ---------------- 支付回调 ----------------

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
}
