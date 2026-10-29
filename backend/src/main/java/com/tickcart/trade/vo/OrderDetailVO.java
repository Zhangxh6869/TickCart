package com.tickcart.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情（含收货信息快照与状态时间轴）。
 */
@Schema(description = "订单详情")
public class OrderDetailVO {

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "订单状态码")
    private Integer status;

    @Schema(description = "订单状态文案")
    private String statusLabel;

    @Schema(description = "订单总金额")
    private BigDecimal totalAmount;

    @Schema(description = "实付金额")
    private BigDecimal payAmount;

    @Schema(description = "收货人（快照）")
    private String receiver;

    @Schema(description = "收货人手机号（快照）")
    private String receiverPhone;

    @Schema(description = "收货地址（快照）")
    private String receiverAddr;

    @Schema(description = "买家备注")
    private String remark;

    @Schema(description = "下单时间")
    private LocalDateTime createdAt;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "发货时间")
    private LocalDateTime shipTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "取消/关闭时间")
    private LocalDateTime cancelTime;

    @Schema(description = "商品明细")
    private List<OrderItemVO> items;

    /**
     * 退款单（最新一条）。
     *
     * <p>没有申请过退款时为 null —— 前端据此决定「显示退款进度卡片」还是「显示申请入口」。
     * 后台详情同样带这个字段（{@code AdminOrderDetailVO} 继承本类），
     * 审核抽屉不用再发一次请求。</p>
     */
    @Schema(description = "退款单（最新一条，无退款时为 null）")
    private RefundVO refund;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getReceiverAddr() {
        return receiverAddr;
    }

    public void setReceiverAddr(String receiverAddr) {
        this.receiverAddr = receiverAddr;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPayTime() {
        return payTime;
    }

    public void setPayTime(LocalDateTime payTime) {
        this.payTime = payTime;
    }

    public LocalDateTime getShipTime() {
        return shipTime;
    }

    public void setShipTime(LocalDateTime shipTime) {
        this.shipTime = shipTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public LocalDateTime getCancelTime() {
        return cancelTime;
    }

    public void setCancelTime(LocalDateTime cancelTime) {
        this.cancelTime = cancelTime;
    }

    public List<OrderItemVO> getItems() {
        return items;
    }

    public void setItems(List<OrderItemVO> items) {
        this.items = items;
    }

    public RefundVO getRefund() {
        return refund;
    }

    public void setRefund(RefundVO refund) {
        this.refund = refund;
    }
}
