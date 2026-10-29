package com.tickcart.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台订单概览（页头 KPI 与状态筛选计数）。
 *
 * <p>一次 {@code GROUP BY status} 就能算出全部字段，避免为 6 个状态各发一次 count。</p>
 *
 * <p><b>刻意不含「今日 N 单」</b>：本项目演示数据可能停在几天前，任何以「今天」为基准的
 * 指标都会是 0，看起来像页面坏了。这里只给「积压量 / 积压时长 / 累计成交」这类
 * 不随数据新鲜度归零的指标。</p>
 */
@Schema(description = "后台订单概览")
public class OrderOverviewVO {

    @Schema(description = "订单总数")
    private long total;

    @Schema(description = "待付款")
    private long pendingPayment;

    @Schema(description = "待发货")
    private long pendingShipment;

    @Schema(description = "待收货")
    private long pendingReceipt;

    @Schema(description = "已完成")
    private long completed;

    @Schema(description = "已取消")
    private long cancelled;

    @Schema(description = "已超时关闭")
    private long timeoutClosed;

    @Schema(description = "退款中（待审核）")
    private long refunding;

    @Schema(description = "已退款")
    private long refunded;

    @Schema(description = "待发货订单中最早一笔支付时间；无待发货订单时为 null。用于算「积压最久多久」")
    private LocalDateTime oldestPendingShipmentTime;

    @Schema(description = "已付款订单数（待发货 + 待收货 + 已完成）")
    private long paidOrderCount;

    @Schema(description = "已付款订单金额合计")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

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

    public long getCancelled() {
        return cancelled;
    }

    public void setCancelled(long cancelled) {
        this.cancelled = cancelled;
    }

    public long getTimeoutClosed() {
        return timeoutClosed;
    }

    public void setTimeoutClosed(long timeoutClosed) {
        this.timeoutClosed = timeoutClosed;
    }

    public long getRefunding() {
        return refunding;
    }

    public void setRefunding(long refunding) {
        this.refunding = refunding;
    }

    public long getRefunded() {
        return refunded;
    }

    public void setRefunded(long refunded) {
        this.refunded = refunded;
    }

    public LocalDateTime getOldestPendingShipmentTime() {
        return oldestPendingShipmentTime;
    }

    public void setOldestPendingShipmentTime(LocalDateTime oldestPendingShipmentTime) {
        this.oldestPendingShipmentTime = oldestPendingShipmentTime;
    }

    public long getPaidOrderCount() {
        return paidOrderCount;
    }

    public void setPaidOrderCount(long paidOrderCount) {
        this.paidOrderCount = paidOrderCount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }
}
