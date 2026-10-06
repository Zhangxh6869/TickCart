package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 发起支付请求。
 */
@Schema(description = "发起支付请求")
public class PaymentRequest {

    @Schema(description = "支付渠道 1微信 2支付宝", example = "1")
    @NotNull(message = "请选择支付方式")
    @Min(value = 1, message = "请选择支付方式")
    @Max(value = 2, message = "请选择支付方式")
    private Integer channel;

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }
}
