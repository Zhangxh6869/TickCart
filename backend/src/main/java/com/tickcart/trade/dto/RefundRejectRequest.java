package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台拒绝退款请求。
 *
 * <p>拒绝意见必填：用户看到「已拒绝」却不知道为什么，只能再提一次申请，
 * 售后工单会来回滚。</p>
 */
@Schema(description = "拒绝退款请求")
public class RefundRejectRequest {

    @Schema(description = "拒绝原因（必填，会展示给用户）", example = "商品已发出，请收到后申请退货退款")
    @NotBlank(message = "请填写拒绝原因")
    @Size(max = 200, message = "拒绝原因最长 200 字")
    private String auditRemark;

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }
}
