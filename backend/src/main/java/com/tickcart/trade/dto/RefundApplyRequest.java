package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 申请退款请求。
 *
 * <p>只收「原因」与「说明」两样东西 —— <b>金额不由前端提交</b>：
 * 退多少由后端按「支付成功那条流水」算（见 {@code RefundService.resolveRefundAmount}），
 * 否则客户端可以随便填一个金额把钱退走。</p>
 */
@Schema(description = "申请退款请求")
public class RefundApplyRequest {

    @Schema(description = "退款原因码 1不想要了 2地址或信息填错 3商品有问题 4其他", example = "1")
    @NotNull(message = "请选择退款原因")
    @Min(value = 1, message = "退款原因不合法")
    @Max(value = 4, message = "退款原因不合法")
    private Integer reasonCode;

    @Schema(description = "补充说明", example = "拍错了规格")
    @Size(max = 200, message = "补充说明最长 200 字")
    private String reason;

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
}
