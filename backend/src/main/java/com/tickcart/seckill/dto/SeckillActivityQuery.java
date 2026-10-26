package com.tickcart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 后台秒杀活动查询条件。
 *
 * <p>{@code status} 是推导出来的状态，不能直接落到 SQL 的某一列上 ——
 * Service 会把它翻译成 {@code enabled} + 时间区间的组合条件。</p>
 */
@Schema(description = "秒杀活动查询条件")
public class SeckillActivityQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    private long page = 1;

    @Schema(description = "每页条数", example = "20")
    private long size = 20;

    @Schema(description = "是否上线：0下线 1上线；不传表示全部", example = "1")
    private Integer enabled;

    @Schema(description = "状态：0已下线 1即将开始 2进行中 3已结束；不传表示全部", example = "2")
    private Integer status;

    /**
     * 只看「在场」的活动（进行中 + 即将开始）。
     *
     * <p>页面的默认视图 —— 已结束的活动不能删也改不了价，会一直堆在库里；
     * 按 id 或按时间排都会挤满第一屏，运营打开页面想看的只是「现在有什么场」。</p>
     */
    @Schema(description = "只看进行中+即将开始（与 status 互斥，优先 status）", example = "true")
    private Boolean activeOnly;

    @Schema(description = "标题或商品名模糊搜索")
    private String keyword;

    @Schema(description = "排序字段：startTime / endTime / id；不传按「进行中→即将开始→已结束→已下线」沉底", example = "startTime")
    private String sortBy;

    @Schema(description = "排序方向：asc / desc", example = "desc")
    private String order;

    @Schema(description = "开始日期（含），按活动开始时间筛选", example = "2026-10-19")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginDate;

    @Schema(description = "结束日期（含），按活动结束时间筛选", example = "2026-10-26")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Boolean getActiveOnly() {
        return activeOnly;
    }

    public void setActiveOnly(Boolean activeOnly) {
        this.activeOnly = activeOnly;
    }

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
