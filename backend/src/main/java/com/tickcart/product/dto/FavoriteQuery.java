package com.tickcart.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 收藏列表分页查询条件。
 */
@Schema(description = "收藏列表分页查询条件")
public class FavoriteQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer size = 10;

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
        return size > 50 ? 50 : size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
