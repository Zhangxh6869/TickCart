package com.tickcart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 新建/编辑秒杀活动请求。
 */
@Schema(description = "秒杀活动保存请求")
public class SeckillActivitySaveRequest {

    @Schema(description = "活动标题", example = "双十一秒杀 · 精选牛油果")
    @NotBlank(message = "活动标题不能为空")
    @Size(max = 64, message = "活动标题最长 64 字")
    private String title;

    @Schema(description = "商品ID", example = "302")
    @NotNull(message = "请选择商品")
    private Long productId;

    @Schema(description = "SKU ID，无规格商品传 0 或省略", example = "490")
    private Long skuId;

    @Schema(description = "秒杀价", example = "9.90")
    @NotNull(message = "秒杀价不能为空")
    @DecimalMin(value = "0.01", message = "秒杀价必须大于 0")
    private BigDecimal seckillPrice;

    @Schema(description = "秒杀库存（独立于商品库存）", example = "100")
    @NotNull(message = "秒杀库存不能为空")
    @Min(value = 1, message = "秒杀库存必须大于 0")
    @Max(value = 1000000, message = "秒杀库存过大")
    private Integer seckillStock;

    @Schema(description = "每人限购数量", example = "1")
    @NotNull(message = "限购数量不能为空")
    @Min(value = 1, message = "限购数量必须大于 0")
    @Max(value = 99, message = "限购数量过大")
    private Integer limitPerUser;

    @Schema(description = "开始时间", example = "2026-10-20T10:00:00")
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @Schema(description = "结束时间", example = "2026-10-20T12:00:00")
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public BigDecimal getSeckillPrice() {
        return seckillPrice;
    }

    public void setSeckillPrice(BigDecimal seckillPrice) {
        this.seckillPrice = seckillPrice;
    }

    public Integer getSeckillStock() {
        return seckillStock;
    }

    public void setSeckillStock(Integer seckillStock) {
        this.seckillStock = seckillStock;
    }

    public Integer getLimitPerUser() {
        return limitPerUser;
    }

    public void setLimitPerUser(Integer limitPerUser) {
        this.limitPerUser = limitPerUser;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
