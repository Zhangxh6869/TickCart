package com.tickcart.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台订单列表项。
 *
 * <p>与前台 {@link OrderListItemVO} 的区别：后台需要看到「是谁下的单」，
 * 因此额外带出买家 ID 与收货快照；前台列表则只展示自己的订单，无需这些字段。</p>
 */
@Schema(description = "后台订单列表项")
public class AdminOrderListItemVO {

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "买家用户ID")
    private Long userId;

    @Schema(description = "收货人（快照）")
    private String receiver;

    @Schema(description = "收货人手机号（快照）")
    private String receiverPhone;

    @Schema(description = "收货地址（快照）。列表直接展示摘要，客服不用点开详情就能确认地址")
    private String receiverAddr;

    @Schema(description = "订单状态码 0待付款 1待发货 2待收货 3已完成 4已取消 5已超时关闭")
    private Integer status;

    @Schema(description = "订单状态文案")
    private String statusLabel;

    @Schema(description = "订单总金额")
    private BigDecimal totalAmount;

    @Schema(description = "实付金额")
    private BigDecimal payAmount;

    @Schema(description = "商品总件数")
    private Integer totalQuantity;

    @Schema(description = "下单时间")
    private LocalDateTime createdAt;

    @Schema(description = "支付时间；未支付时为 null。后台用它算「已付款等待时长」")
    private LocalDateTime payTime;

    @Schema(description = "发货时间；未发货时为 null")
    private LocalDateTime shipTime;

    @Schema(description = "取消时间；未取消时为 null")
    private LocalDateTime cancelTime;

    @Schema(description = "是否秒杀订单（订单来源渠道，与支付渠道无关）")
    private Boolean seckill;

    @Schema(description = "最新一笔支付流水号；用户尚未发起过支付时为 null")
    private String payNo;

    @Schema(description = "支付渠道 0模拟 1微信 2支付宝；无支付流水时为 null")
    private Integer channel;

    @Schema(description = "商品明细")
    private List<OrderItemVO> items;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPayNo() {
        return payNo;
    }

    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public List<OrderItemVO> getItems() {
        return items;
    }

    public void setItems(List<OrderItemVO> items) {
        this.items = items;
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

    public LocalDateTime getCancelTime() {
        return cancelTime;
    }

    public void setCancelTime(LocalDateTime cancelTime) {
        this.cancelTime = cancelTime;
    }

    public Boolean getSeckill() {
        return seckill;
    }

    public void setSeckill(Boolean seckill) {
        this.seckill = seckill;
    }
}
