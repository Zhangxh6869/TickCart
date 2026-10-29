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
}
