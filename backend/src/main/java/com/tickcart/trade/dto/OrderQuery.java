package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 订单列表查询条件。
 */
@Schema(description = "订单列表查询条件")
public class OrderQuery {

    @Schema(description = "订单状态筛选，不传表示全部", example = "0")
    private Integer status;

    /**
     * 多状态筛选，优先级高于 {@link #status}。
     *
     * <p>用于「退款/售后」这类跨状态的页签 —— 它要同时覆盖「退款中(6)」与「已退款(7)」，
     * 单值 {@code status} 表达不了。前端传 {@code statuses=6,7}，Spring 自动按逗号拆分。</p>
     */
    @Schema(description = "订单状态多值筛选（逗号分隔），优先于 status", example = "6,7")
    private List<Integer> statuses;

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

    public List<Integer> getStatuses() {
        return statuses;
    }

    public void setStatuses(List<Integer> statuses) {
        this.statuses = statuses;
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
