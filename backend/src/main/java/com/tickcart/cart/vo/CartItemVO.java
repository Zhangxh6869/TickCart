package com.tickcart.cart.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 购物车列表项（购物车记录 + 商品/SKU 实时信息）。
 *
 * <p>价格与库存均取实时值而非快照：购物车是「未成交」状态，需反映商品最新情况；
 * 真正下单时才写入快照（见 {@code order_item}）。</p>
 */
@Schema(description = "购物车列表项")
public class CartItemVO {

    @Schema(description = "购物车项ID")
    private Long id;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "SKU ID，无规格时为 0")
    private Long skuId;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "是否勾选 0否 1是")
    private Integer checked;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "商品图片（有 SKU 图时取 SKU 图）")
    private String productImage;

    @Schema(description = "规格值")
    private String specValue;

    @Schema(description = "实时单价（有 SKU 取 SKU 价）")
    private BigDecimal price;

    @Schema(description = "实时库存")
    private Integer stock;

    @Schema(description = "小计 = price * quantity")
    private BigDecimal subtotal;

    @Schema(description = "是否有效（商品下架/规格删除/库存不足时为 false）")
    private boolean valid;

    @Schema(description = "失效原因，有效时为 null")
    private String invalidReason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getChecked() {
        return checked;
    }

    public void setChecked(Integer checked) {
        this.checked = checked;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductImage() {
        return productImage;
    }

    public void setProductImage(String productImage) {
        this.productImage = productImage;
    }

    public String getSpecValue() {
        return specValue;
    }

    public void setSpecValue(String specValue) {
        this.specValue = specValue;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getInvalidReason() {
        return invalidReason;
    }

    public void setInvalidReason(String invalidReason) {
        this.invalidReason = invalidReason;
    }
}
