package com.tickcart.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 当前用户各状态订单数量，供「我的」页订单宫格展示角标。
 *
 * <p>只统计宫格上的四个状态（待付款 / 待发货 / 待收货 / 已完成）。
 * 「已取消」「已超时关闭」是终态、不需要提醒，不在此返回，需要时走订单列表的对应 tab。</p>
 *
 * <p>用原始 {@code long} 而非 {@code Long}：没有该状态订单时就是 0，
 * 前端不必再为 null 兜底，也避免出现 {@code null} 角标。</p>
 */
@Schema(description = "各状态订单数量")
public class OrderCountVO {

    @Schema(description = "待付款数量")
    private long pendingPayment;

    @Schema(description = "待发货数量")
    private long pendingShipment;

    @Schema(description = "待收货数量")
    private long pendingReceipt;

    @Schema(description = "已完成数量")
    private long completed;

    public long getPendingPayment() {
        return pendingPayment;
    }

    public void setPendingPayment(long pendingPayment) {
        this.pendingPayment = pendingPayment;
    }

    public long getPendingShipment() {
        return pendingShipment;
    }

    public void setPendingShipment(long pendingShipment) {
        this.pendingShipment = pendingShipment;
    }

    public long getPendingReceipt() {
        return pendingReceipt;
    }

    public void setPendingReceipt(long pendingReceipt) {
        this.pendingReceipt = pendingReceipt;
    }

    public long getCompleted() {
        return completed;
    }

    public void setCompleted(long completed) {
        this.completed = completed;
    }
}
