package com.tickcart.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 后台用户列表查询条件。
 *
 * <p>服务于两个日常场景：</p>
 * <ul>
 *   <li><b>客服找人</b>：用户只报得出手机号或昵称，关键字对三者做模糊匹配</li>
 *   <li><b>风控排查</b>：按注册日期圈定「这周新注册的一批」再逐个看</li>
 * </ul>
 */
@Schema(description = "后台用户列表查询条件")
public class AdminUserQuery {

    @Schema(description = "页码（从 1 开始）", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "20")
    private Integer size = 20;

    /**
     * 关键字：用户名 / 昵称 / 手机号模糊匹配；纯数字时额外按用户 ID 精确匹配。
     *
     * <p>用户来电常常只报一个数字 ID，没有 ID 命中会变成「查无此人」。</p>
     */
    @Schema(description = "关键字：用户名 / 昵称 / 手机号 / 用户ID", example = "1380000")
    private String keyword;

    @Schema(description = "状态 0正常 1禁用，不传表示全部")
    private Integer status;

    @Schema(description = "注册日期起（含），yyyy-MM-dd", example = "2026-10-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginDate;

    @Schema(description = "注册日期止（含），yyyy-MM-dd", example = "2026-10-31")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Schema(description = "排序字段：createdAt（默认）/ id", example = "createdAt")
    private String sortBy;

    @Schema(description = "排序方向：asc / desc", example = "desc")
    private String order = "desc";

    public Integer getPage() {
        return page == null || page < 1 ? 1 : page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        if (size == null || size < 1) {
            return 20;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
}
