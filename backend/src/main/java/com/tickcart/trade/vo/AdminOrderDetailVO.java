package com.tickcart.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 后台订单详情：在前台共用的 {@link OrderDetailVO} 之上补「支付语义」。
 *
 * <p>为什么单独一个 VO 而不是给 {@link OrderDetailVO} 加字段：前台订单详情是用户看自己的单，
 * 不需要支付流水的内部编号；后台要核对「这单到底有没有收到钱」。两者的字段诉求不同，
 * 混在一个类里会让前台接口白白多返回一组内部字段。</p>
 *
 * <p><b>★ 支付口径</b>：{@code payStatus} 是判断「有没有收到钱」的唯一依据，
 * 不要只看 {@code payNo} 是否为空 —— 支付失败/用户取消/超时关闭都会留下流水，
 * 已取消的订单也可能挂着一条「待支付」流水。</p>
 */
@Schema(description = "后台订单详情")
public class AdminOrderDetailVO extends OrderDetailVO {

    @Schema(description = "支付状态 0待支付 1支付成功 2支付失败 3已关闭；从未发起支付时为 null")
    private Integer payStatus;

    @Schema(description = "支付状态文案；从未发起支付时为 null")
    private String payStatusLabel;

    @Schema(description = "支付渠道 0模拟 1微信 2支付宝；从未发起支付时为 null")
    private Integer payChannel;

    @Schema(description = "支付流水号；从未发起支付时为 null")
    private String payNo;

    @Schema(description = "买家用户ID（列表行已下发，这里补上便于详情独立展示）")
    private Long userId;

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

    public Integer getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(Integer payChannel) {
        this.payChannel = payChannel;
    }

    public String getPayNo() {
        return payNo;
    }

    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
