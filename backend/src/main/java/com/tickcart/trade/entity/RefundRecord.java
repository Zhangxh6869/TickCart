package com.tickcart.trade.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体，对应 {@code refund_record}。
 *
 * <p><b>按「退款单」建模，不按订单</b>：同一订单可以有多条记录（被拒绝后再次申请、
 * 将来部分退款分多次退），但**同一时刻只能有一条进行中**（{@link #activeKey}）。</p>
 *
 * <p>金额是快照：取「支付成功」那条流水的金额，不随后续订单改动而变。
 * 不能直接用 {@code order.pay_amount} —— 同一订单可能有多条支付流水，
 * 用户支付失败重试时 id 最大的那条未必是成功的那条。</p>
 */
@TableName("refund_record")
public class RefundRecord {

    /** 退款类型：未发货退款（P0 唯一支持的类型） */
    public static final int TYPE_UNSHIPPED = 1;

    /** 退款类型：仅退款（已发货未收货，P1） */
    public static final int TYPE_REFUND_ONLY = 2;

    /** 退款类型：退货退款（P2） */
    public static final int TYPE_RETURN_GOODS = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退款单号，唯一；前缀 RF */
    private String refundNo;

    private Long orderId;

    private String orderNo;

    private Long userId;

    /** 退款类型 1未发货退款 2仅退款 3退货退款 */
    private Integer type;

    /** 退款金额（快照） */
    private BigDecimal amount;

    /** 原因码 1不想要了 2信息填错 3商品问题 4其他 */
    private Integer reasonCode;

    /** 补充说明 */
    private String reason;

    /** 状态 0待审核 1已退款 2已拒绝 3已撤销 */
    private Integer status;

    /** 申请前的订单状态：拒绝/撤销时的回退目标 */
    private Integer originStatus;

    /**
     * 进行中标记。
     *
     * <p>进行中 = {@code orderNo}（占用 {@code uk_active_order} 唯一键，阻止第二笔申请）；
     * 终态时置 {@code null}（MySQL 唯一索引允许多个 NULL，故历史记录可无限多）。</p>
     */
    private String activeKey;

    /** 审核意见（拒绝时必填） */
    private String auditRemark;

    private LocalDateTime auditTime;

    private LocalDateTime refundTime;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

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

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
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

    public Integer getOriginStatus() {
        return originStatus;
    }

    public void setOriginStatus(Integer originStatus) {
        this.originStatus = originStatus;
    }

    public String getActiveKey() {
        return activeKey;
    }

    public void setActiveKey(String activeKey) {
        this.activeKey = activeKey;
    }

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
