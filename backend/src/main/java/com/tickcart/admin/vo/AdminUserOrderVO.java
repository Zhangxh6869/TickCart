package com.tickcart.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户详情里的「最近订单」摘要。
 *
 * <p>客服排查时几乎都要看「这个人最近买了什么、卡在哪个状态」，
 * 所以详情接口直接带上最近几单，避免再跳一次订单页按用户 ID 搜。</p>
 */
@Schema(description = "用户最近订单摘要")
public class AdminUserOrderVO {

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "订单状态 0待付款 1待发货 2待收货 3已完成 4已取消 5已超时关闭 6退款中 7已退款")
    private Integer status;

    @Schema(description = "订单状态文案")
    private String statusLabel;

    @Schema(description = "实付金额")
    private BigDecimal payAmount;

    @Schema(description = "渠道 0普通 1秒杀")
    private Integer channel;

    @Schema(description = "下单时间")
    private LocalDateTime createdAt;

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

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
