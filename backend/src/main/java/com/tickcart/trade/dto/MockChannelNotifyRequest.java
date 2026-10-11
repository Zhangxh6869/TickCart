package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 模拟渠道提交支付结果请求。
 */
@Schema(description = "模拟渠道支付结果提交")
public class MockChannelNotifyRequest {

    @Schema(description = "渠道结果 success 成功 / fail 失败 / cancel 用户取消", example = "success")
    @NotBlank(message = "请提供支付结果")
    @Pattern(regexp = "success|fail|cancel", message = "支付结果只能是 success / fail / cancel")
    private String result;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
