package com.tickcart.product.vo;

import com.tickcart.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品列表项（精简字段，用于列表页）。
 */
@Schema(description = "商品列表项")
public class ProductListItem {

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "副标题")
    private String subtitle;

    @Schema(description = "主图URL")
    private String mainImage;

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

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    public static ProductListItem from(Product p) {
        ProductListItem vo = new ProductListItem();
        vo.id = p.getId();
        vo.categoryId = p.getCategoryId();
        vo.name = p.getName();
        vo.subtitle = p.getSubtitle();
        vo.mainImage = p.getMainImage();
        vo.price = p.getPrice();
        vo.stock = p.getStock();
        vo.sales = p.getSales();
        vo.status = p.getStatus();
        vo.isSeckill = p.getIsSeckill();
        vo.createdAt = p.getCreatedAt();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getMainImage() {
        return mainImage;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
