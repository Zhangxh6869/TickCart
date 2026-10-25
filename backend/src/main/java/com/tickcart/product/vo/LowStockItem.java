package com.tickcart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 补货待办的一行（**SKU 粒度**）。
 *
 * <p>刻意不是商品粒度：总库存 120 但某个尺码 0 件时，商品级看不出问题，
 * 用户却买不到那个尺码。所以这个 VO 不能复用 {@code ProductListItem}。</p>
 */
@Schema(description = "补货待办项（SKU 粒度）")
public class LowStockItem {

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "商品主图")
    private String mainImage;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "规格名（无规格商品为空）")
    private String specName;

    @Schema(description = "规格值（无规格商品为空）")
    private String specValue;

    @Schema(description = "SKU 图片")
    private String skuImage;

    @Schema(description = "当前库存")
    private Integer stock;

    @Schema(description = "所属分类的有效预警线")
    private Integer threshold;

    @Schema(description = "缺口 = 预警线 - 库存")
    private Integer shortage;

    /**
     * 剩余比例 = 库存 / 预警线，四舍五入到两位小数。
     *
     * <p>0 表示已断货，越接近 1 越贴近告警线。用它排序而不是缺口绝对值：
     * 生鲜阈值 30 剩 5（缺 25）和家电阈值 3 剩 0（缺 3）相比，
     * 按缺口排会把真正断货的家电排到后面。</p>
     */
    @Schema(description = "剩余比例（0 = 断货）")
    private Double remainRatio;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getMainImage() {
        return mainImage;
    }

    public void setMainImage(String mainImage) {
        this.mainImage = mainImage;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getSpecName() {
        return specName;
    }

    public void setSpecName(String specName) {
        this.specName = specName;
    }

    public String getSpecValue() {
        return specValue;
    }

    public void setSpecValue(String specValue) {
        this.specValue = specValue;
    }

    public String getSkuImage() {
        return skuImage;
    }

    public void setSkuImage(String skuImage) {
        this.skuImage = skuImage;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getThreshold() {
        return threshold;
    }

    public void setThreshold(Integer threshold) {
        this.threshold = threshold;
    }

    public Integer getShortage() {
        return shortage;
    }

    public void setShortage(Integer shortage) {
        this.shortage = shortage;
    }

    public Double getRemainRatio() {
        return remainRatio;
    }

    public void setRemainRatio(Double remainRatio) {
        this.remainRatio = remainRatio;
    }
}
