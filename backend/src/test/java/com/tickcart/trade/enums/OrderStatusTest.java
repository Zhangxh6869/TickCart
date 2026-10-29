package com.tickcart.trade.enums;

import com.tickcart.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OrderStatus} 状态机单元测试。
 */
@DisplayName("订单状态机")
class OrderStatusTest {

    @Test
    @DisplayName("待付款可流转到待发货、已取消、已超时关闭")
    void pendingPaymentTransitions() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PENDING_SHIPMENT)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.TIMEOUT_CLOSED)).isTrue();
    }

    @Test
    @DisplayName("待付款不能直接跳到已完成或待收货")
    void pendingPaymentCannotSkip() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PENDING_RECEIPT)).isFalse();
    }

    @Test
    @DisplayName("正向主链路依次流转")
    void happyPath() {
        assertThat(OrderStatus.PENDING_SHIPMENT.canTransitionTo(OrderStatus.PENDING_RECEIPT)).isTrue();
        assertThat(OrderStatus.PENDING_RECEIPT.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("终态不可再流转")
    void finalStates() {
        assertThat(OrderStatus.COMPLETED.isFinal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isFinal()).isTrue();
        assertThat(OrderStatus.TIMEOUT_CLOSED.isFinal()).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.isFinal()).isFalse();
    }

    @Test
    @DisplayName("已发货订单不能再取消")
    void shippedCannotCancel() {
        assertThat(OrderStatus.PENDING_RECEIPT.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("非法流转抛出带当前状态的业务异常")
    void assertTransitionThrows() {
        assertThatThrownBy(() -> OrderStatus.PENDING_SHIPMENT.assertTransitionTo(OrderStatus.COMPLETED, "确认收货"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前为「待发货」，无法确认收货");
    }

    @Test
    @DisplayName("状态码与标签正确映射")
    void codeMapping() {
        assertThat(OrderStatus.of(0)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(OrderStatus.of(5)).isEqualTo(OrderStatus.TIMEOUT_CLOSED);
        assertThat(OrderStatus.PENDING_PAYMENT.getLabel()).isEqualTo("待付款");
        assertThat(OrderStatus.TIMEOUT_CLOSED.getCode()).isEqualTo(5);
    }

    @Test
    @DisplayName("未知状态码抛业务异常")
    void unknownCode() {
        assertThatThrownBy(() -> OrderStatus.of(99))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知的订单状态");
    }

    @Test
    @DisplayName("P0 只开放「待发货」作为退款入口")
    void refundEntryOnlyFromPendingShipment() {
        assertThat(OrderStatus.PENDING_SHIPMENT.canTransitionTo(OrderStatus.REFUNDING)).isTrue();
        // 待收货（P1 仅退款）与已完成（P2 退货退款）的入口边留到对应阶段再加
        assertThat(OrderStatus.PENDING_RECEIPT.canTransitionTo(OrderStatus.REFUNDING)).isFalse();
        assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.REFUNDING)).isFalse();
    }

    @Test
    @DisplayName("待付款不能申请退款，应先取消")
    void pendingPaymentCannotRefund() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.REFUNDING)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.REFUNDING)).isFalse();
    }

    @Test
    @DisplayName("退款中：可同意进入已退款，也可回退到申请前的状态")
    void refundingTransitions() {
        assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.PENDING_SHIPMENT)).isTrue();
        // 退款中不是终态：还要等审核结果
        assertThat(OrderStatus.REFUNDING.isFinal()).isFalse();
    }

    @Test
    @DisplayName("退款中不能发货、不能取消、不能再次申请退款")
    void refundingBlocksShipping() {
        assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.PENDING_RECEIPT)).isFalse();
        assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.REFUNDING)).isFalse();
    }

    @Test
    @DisplayName("已退款是终态，无法再流转")
    void refundedIsFinal() {
        assertThat(OrderStatus.REFUNDED.isFinal()).isTrue();
        assertThat(OrderStatus.of(7)).isEqualTo(OrderStatus.REFUNDED);
        assertThat(OrderStatus.REFUNDED.getLabel()).isEqualTo("已退款");
        assertThat(OrderStatus.of(6).getLabel()).isEqualTo("退款中");
    }
}
