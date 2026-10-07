package com.tickcart.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 商品列表查询条件。
 */
@Schema(description = "商品列表查询条件")
public class ProductQuery {

    @Schema(description = "页码（从 1 开始）", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer size = 10;

    @Schema(description = "关键字（商品名称模糊匹配）")
    private String keyword;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "状态 0下架 1上架（后台可传，前台固定查上架）")
    private Integer status;

    @Schema(description = "排序字段：price / sales / createdAt", example = "createdAt")
    private String sortBy;

    @Schema(description = "排序方向：asc / desc", example = "desc")
    private String order = "desc";

    /**
     * 随机种子。传入后忽略 {@link #sortBy} / {@link #order}，改为「随机排序 + 分页」。
     *
     * <p>同一个种子下顺序稳定，因此翻页既不重复也不漏项；换一个种子即换一批随机结果。
     * 不传则按常规排序分页。</p>
     */
    @Schema(description = "随机种子；传入则按随机顺序分页（同种子顺序稳定，翻页不重复）", example = "20261007")
    private Long seed;

    public Integer getPage() {
        return page == null || page < 1 ? 1 : page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }
}
