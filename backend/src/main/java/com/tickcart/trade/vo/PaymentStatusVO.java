package com.tickcart.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 支付状态查询结果（供收银台轮询）。
 *
 * <p>收银台只关心两件事：**订单是否已支付**、**最新一笔流水处于什么状态**。
 * 因此这里同时带出订单状态与最新支付流水的状态，避免前端为了判断结果再拉一次订单详情。</p>
 *
 * <p>判读方式：{@code orderStatus=1} 即支付成功；{@code orderStatus=0} 且
 * {@code payStatus=0} 表示仍在支付中；{@code payStatus=2/3} 表示失败/用户取消，
 * 订单仍是待付款，可以重新发起支付。</p>
 */
@Schema(description = "支付状态查询结果")
public class PaymentStatusVO {

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "订单状态码 0待付款 1待发货 2待收货 3已完成 4已取消 5已超时关闭")
    private Integer orderStatus;

    @Schema(description = "订单状态文案")
    private String orderStatusLabel;

    @Schema(description = "最新一笔支付流水号；尚未发起过支付时为 null")
    private String payNo;

    @Schema(description = "支付状态 0待支付 1成功 2失败 3已关闭；尚未发起过支付时为 null")
    private Integer payStatus;

    @Schema(description = "支付状态文案")
    private String payStatusLabel;

    @Schema(description = "支付渠道 0模拟 1微信 2支付宝")
    private Integer channel;

    @Schema(description = "支付完成/关闭时间")
    private LocalDateTime payTime;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getOrderStatusLabel() {
        return orderStatusLabel;
    }

    public void setOrderStatusLabel(String orderStatusLabel) {
        this.orderStatusLabel = orderStatusLabel;
    }

    public String getPayNo() {
        return payNo;
    }

    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    public Integer getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(Integer payStatus) {
        this.payStatus = payStatus;
    }

    public String getPayStatusLabel() {
        return payStatusLabel;
    }

    public void setPayStatusLabel(String payStatusLabel) {
        this.payStatusLabel = payStatusLabel;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public LocalDateTime getPayTime() {
        return payTime;
    }

    public void setPayTime(LocalDateTime payTime) {
        this.payTime = payTime;
    }
}
