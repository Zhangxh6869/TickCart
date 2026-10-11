package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 立即购买请求（商品详情页直达下单，不经过购物车）。
 *
 * <p>与 {@link OrderCreateRequest} 的区别：结算下单的商品来自服务端购物车，
 * 这里由客户端明确指定要买的商品与规格，因此商品信息必须随请求带上。</p>
 */
@Schema(description = "立即购买请求")
public class OrderBuyNowRequest {

    @Schema(description = "收货地址ID", example = "1")
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    @Schema(description = "商品ID", example = "1")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "SKU ID，无规格商品传 0 或省略", example = "0")
    private Long skuId;

    @Schema(description = "购买数量", example = "1")
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量必须大于 0")
    @Max(value = 99, message = "单次最多购买 99 件")
    private Integer quantity;

    @Schema(description = "买家备注", example = "尽快发货")
    @Size(max = 255, message = "备注最长 255 字")
    private String remark;

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
