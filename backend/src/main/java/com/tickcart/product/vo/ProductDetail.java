package com.tickcart.product.vo;

import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductImage;
import com.tickcart.product.entity.ProductSku;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品详情（含 SKU 与图集）。
 */
@Schema(description = "商品详情")
public class ProductDetail {

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "副标题")
    private String subtitle;

    @Schema(description = "搜索补充关键词（英文逗号分隔，后台编辑用）")
    private String searchKeywords;

    @Schema(description = "主图URL")
    private String mainImage;

    @Schema(description = "商品详情")
    private String detail;

    @Schema(description = "展示价格")
    private BigDecimal price;

    @Schema(description = "总库存")
    private Integer stock;

    @Schema(description = "累计销量")
    private Integer sales;

    @Schema(description = "状态 0下架 1上架")
    private Integer status;

    @Schema(description = "是否秒杀商品")
    private Integer isSeckill;

    @Schema(description = "SKU 列表")
    private List<ProductSku> skus;

    @Schema(description = "图集 URL 列表")
    private List<ProductImage> images;

    public static ProductDetail from(Product p, List<ProductSku> skus, List<ProductImage> images) {
        ProductDetail vo = new ProductDetail();
        vo.id = p.getId();
        vo.categoryId = p.getCategoryId();
        vo.name = p.getName();
        vo.subtitle = p.getSubtitle();
        vo.searchKeywords = p.getSearchKeywords();
        vo.mainImage = p.getMainImage();
        vo.detail = p.getDetail();
        vo.price = p.getPrice();
        vo.stock = p.getStock();
        vo.sales = p.getSales();
        vo.status = p.getStatus();
        vo.isSeckill = p.getIsSeckill();
        vo.skus = skus;
        vo.images = images;
        return vo;
    }

    public Long getId() {
        return id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getName() {
        return name;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getSearchKeywords() {
        return searchKeywords;
    }

    public String getMainImage() {
        return mainImage;
    }

    public String getDetail() {
        return detail;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public Integer getSales() {
        return sales;
    }

    public Integer getStatus() {
        return status;
    }

    public Integer getIsSeckill() {
        return isSeckill;
    }

    public List<ProductSku> getSkus() {
        return skus;
    }

    public List<ProductImage> getImages() {
        return images;
    }
}
