package com.tickcart.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品新增/编辑请求（含 SKU、图集）。
 */
@Schema(description = "商品新增/编辑请求")
public class ProductSaveRequest {

    @Schema(description = "商品ID（编辑时必填）")
    private Long id;

    @Schema(description = "所属分类ID", example = "1")
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @Schema(description = "商品名称", example = "TickPhone 15 Pro")
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "商品名称最长 100 字")
    private String name;

    @Schema(description = "副标题/卖点")
    @Size(max = 255, message = "副标题最长 255 字")
    private String subtitle;

    @Schema(description = "主图URL")
    private String mainImage;

    @Schema(description = "商品详情")
    private String detail;

    @Schema(description = "展示价格", example = "6999.00")
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", message = "价格不能为负")
    private BigDecimal price;

    @Schema(description = "状态 0下架 1上架", example = "1")
    private Integer status;

    @Schema(description = "是否秒杀商品 0否 1是", example = "0")
    private Integer isSeckill;

    @Schema(description = "SKU 列表")
    @Valid
    private List<SkuItem> skus;

    @Schema(description = "图集 URL 列表")
    private List<String> images;

    /** 商品 SKU 明细。 */
    @Schema(description = "SKU 明细")
    public static class SkuItem {

        @Schema(description = "SKU ID（编辑时携带）")
        private Long id;

        @Schema(description = "规格名", example = "颜色")
        private String specName;

        @Schema(description = "规格值", example = "黑色 256G")
        private String specValue;

        @Schema(description = "SKU 图片URL")
        private String skuImage;

        @Schema(description = "SKU 价格", example = "6999.00")
        @NotNull(message = "SKU 价格不能为空")
        @DecimalMin(value = "0.00", message = "SKU 价格不能为负")
        private BigDecimal price;

        @Schema(description = "SKU 库存", example = "100")
        @NotNull(message = "SKU 库存不能为空")
        @Min(value = 0, message = "SKU 库存不能为负")
        private Integer stock;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getMainImage() {
        return mainImage;
    }

    public void setMainImage(String mainImage) {
        this.mainImage = mainImage;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getIsSeckill() {
        return isSeckill;
    }

    public void setIsSeckill(Integer isSeckill) {
        this.isSeckill = isSeckill;
    }

    public List<SkuItem> getSkus() {
        return skus;
    }

    public void setSkus(List<SkuItem> skus) {
        this.skus = skus;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }
}
