package com.tickcart.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单（前台订单详情 / 后台审核抽屉共用）。
 */
@Schema(description = "退款单")
public class RefundVO {

    @Schema(description = "退款单号")
    private String refundNo;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "退款类型码 1未发货退款 2仅退款 3退货退款")
    private Integer type;

    @Schema(description = "退款类型文案")
    private String typeLabel;

    @Schema(description = "退款金额（快照，取自支付成功流水）")
    private BigDecimal amount;

    @Schema(description = "原因码")
    private Integer reasonCode;

    @Schema(description = "原因文案")
    private String reasonLabel;

    @Schema(description = "补充说明")
    private String reason;

    @Schema(description = "状态码 0待审核 1已退款 2已拒绝 3已撤销")
    private Integer status;

    @Schema(description = "状态文案")
    private String statusLabel;

    @Schema(description = "审核意见（拒绝时必有）")
    private String auditRemark;

    @Schema(description = "申请时间")
    private LocalDateTime createdAt;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "退款完成时间")
    private LocalDateTime refundTime;

    // ---- 退货退款（type=3）专用 ----

    @Schema(description = "退货收货地址；仅「待寄回」时下发，用户按此寄回")
    private String returnAddress;

    @Schema(description = "退货快递公司")
    private String returnCompany;

    @Schema(description = "退货物流单号")
    private String returnTrackingNo;

    @Schema(description = "用户寄出时间")
    private LocalDateTime returnTime;

    @Schema(description = "商家确认收到退货的时间")
    private LocalDateTime receiveTime;

    @Schema(description = "验收备注")
    private String receiveRemark;

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(Integer reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonLabel() {
        return reasonLabel;
    }

    public void setReasonLabel(String reasonLabel) {
        this.reasonLabel = reasonLabel;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public LocalDateTime getRefundTime() {
        return refundTime;
    }

    public void setRefundTime(LocalDateTime refundTime) {
        this.refundTime = refundTime;
    }

    public String getReturnAddress() {
        return returnAddress;
    }

    public void setReturnAddress(String returnAddress) {
        this.returnAddress = returnAddress;
    }

    public String getReturnCompany() {
        return returnCompany;
    }

    public void setReturnCompany(String returnCompany) {
        this.returnCompany = returnCompany;
    }

    public String getReturnTrackingNo() {
        return returnTrackingNo;
    }

    public void setReturnTrackingNo(String returnTrackingNo) {
        this.returnTrackingNo = returnTrackingNo;
    }

    public LocalDateTime getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }

    public LocalDateTime getReceiveTime() {
        return receiveTime;
    }

    public void setReceiveTime(LocalDateTime receiveTime) {
        this.receiveTime = receiveTime;
    }

    public String getReceiveRemark() {
        return receiveRemark;
    }

    public void setReceiveRemark(String receiveRemark) {
        this.receiveRemark = receiveRemark;
    }
}
