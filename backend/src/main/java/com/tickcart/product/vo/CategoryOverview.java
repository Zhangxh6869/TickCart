package com.tickcart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分类总览行：分类本身 + 它在商品侧的用量。
 *
 * <p>列表页需要「这个分类有多少商品、其中多少告急」才能判断两件事：
 * 能不能删、要不要补货。单独一个 overview 接口而不是给 {@code Category} 实体加字段 ——
 * 那个实体同时被前台分类列表缓存复用，塞统计字段进去会污染缓存。</p>
 */
@Schema(description = "分类总览（含商品用量）")
public class CategoryOverview {

    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "父分类ID，0 为顶级")
    private Long parentId;

    @Schema(description = "排序值（越小越靠前）")
    private Integer sort;

    @Schema(description = "状态 0禁用 1启用")
    private Integer status;

    @Schema(description = "库存预警线；null 表示继承")
    private Integer stockWarnThreshold;

    @Schema(description = "该分类下商品数（不含已删除）")
    private int productCount;

    @Schema(description = "其中库存告急的商品数")
    private int lowStockCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getStockWarnThreshold() {
        return stockWarnThreshold;
    }

    public void setStockWarnThreshold(Integer stockWarnThreshold) {
        this.stockWarnThreshold = stockWarnThreshold;
    }

    public int getProductCount() {
        return productCount;
    }

    public void setProductCount(int productCount) {
        this.productCount = productCount;
    }

    public int getLowStockCount() {
        return lowStockCount;
    }

    public void setLowStockCount(int lowStockCount) {
        this.lowStockCount = lowStockCount;
    }
}
