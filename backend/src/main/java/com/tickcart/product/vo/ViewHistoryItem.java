package com.tickcart.product.vo;

import com.tickcart.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 浏览记录列表项 = 商品列表项 + 该商品最近一次浏览时间。
 *
 * <p>字段与 {@link ProductListItem} 逐一对齐（不是嵌套 {@code product} 对象），
 * 这样前端可以直接把它交给 {@code ProductCard} 渲染，无需为浏览记录页单独写一套卡片。
 * 商品侧字段统一从 {@link ProductListItem#from(Product)} 取值，保证「商品字段口径」只有一份。</p>
 */
@Schema(description = "浏览记录列表项")
public class ViewHistoryItem {

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

    @Schema(description = "商品创建时间")
    private LocalDateTime createdAt;

    /** 最近一次浏览时间：前端按它做「今天 / 昨天 / 日期」分组与倒序展示 */
    @Schema(description = "最近一次浏览时间")
    private LocalDateTime viewTime;

    /**
     * 由商品与浏览时间组装。
     *
     * <p>商品字段走 {@link ProductListItem#from(Product)} 再回读，而不是在这里重写一遍映射：
     * 将来商品列表项加字段时，浏览记录会自动跟上。</p>
     */
    public static ViewHistoryItem of(Product product, LocalDateTime viewTime) {
        ProductListItem base = ProductListItem.from(product);
        ViewHistoryItem vo = new ViewHistoryItem();
        vo.id = base.getId();
        vo.categoryId = base.getCategoryId();
        vo.name = base.getName();
        vo.subtitle = base.getSubtitle();
        vo.mainImage = base.getMainImage();
        vo.price = base.getPrice();
        vo.stock = base.getStock();
        vo.sales = base.getSales();
        vo.status = base.getStatus();
        vo.isSeckill = base.getIsSeckill();
        vo.createdAt = base.getCreatedAt();
        vo.viewTime = viewTime;
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

    public LocalDateTime getViewTime() {
        return viewTime;
    }
}
