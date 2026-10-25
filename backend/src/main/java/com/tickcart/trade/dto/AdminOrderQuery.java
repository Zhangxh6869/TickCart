package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 后台订单列表查询条件。
 *
 * <p>在 {@link OrderQuery}（状态 + 分页）基础上增加关键字、排序与下单日期范围，
 * 服务于后台的两个日常场景：</p>
 *
 * <ul>
 *   <li><b>清待发货</b>：按付款时间升序 → 等最久的那单排第一</li>
 *   <li><b>客服查单</b>：关键字可命中商品名，日期范围可圈定「昨天那批」</li>
 * </ul>
 */
@Schema(description = "后台订单列表查询条件")
public class AdminOrderQuery extends OrderQuery {

    @Schema(description = "关键字：订单号 / 收货人 / 手机号 / 商品名，不传表示不过滤", example = "NO2026")
    private String keyword;

    @Schema(description = "排序字段：createdAt（默认）/ payTime", example = "payTime")
    private String sortBy;

    @Schema(description = "排序方向：asc / desc", example = "asc")
    private String order = "desc";

    @Schema(description = "下单日期起（含），yyyy-MM-dd", example = "2026-10-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginDate;

    @Schema(description = "下单日期止（含），yyyy-MM-dd", example = "2026-10-25")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
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

    public LocalDate getBeginDate() {
        return beginDate;
    }

    public void setBeginDate(LocalDate beginDate) {
        this.beginDate = beginDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
