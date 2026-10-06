package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 订单列表查询条件。
 */
@Schema(description = "订单列表查询条件")
public class OrderQuery {

    @Schema(description = "订单状态筛选，不传表示全部", example = "0")
    private Integer status;

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer size = 10;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

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
