package com.tickcart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 秒杀抢购请求。
 */
@Schema(description = "秒杀抢购请求")
public class SeckillBuyRequest {

    @Schema(description = "抢购数量", example = "1")
    @Min(value = 1, message = "抢购数量必须大于 0")
    @Max(value = 99, message = "抢购数量过大")
    private Integer quantity = 1;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
