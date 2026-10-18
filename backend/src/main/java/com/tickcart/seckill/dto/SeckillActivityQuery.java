package com.tickcart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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

    @Schema(description = "每页条数", example = "10")
    private long size = 10;

    @Schema(description = "是否上线：0下线 1上线；不传表示全部", example = "1")
    private Integer enabled;

    @Schema(description = "状态：0已下线 1即将开始 2进行中 3已结束；不传表示全部", example = "2")
    private Integer status;

    @Schema(description = "标题模糊搜索")
    private String keyword;

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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
