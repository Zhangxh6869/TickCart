package com.tickcart.search.dto;

/**
 * 搜索趋势中的单日数据点（后台「搜索分析」迷你趋势用）。
 *
 * <p>{@code day} 用字符串而非 {@code LocalDate}：{@code DATE_FORMAT} 直接产出
 * {@code yyyy-MM-dd}，避开 JDBC 对 DATE → LocalDate 的类型映射差异，前端也无需再格式化。</p>
 */
public class SearchTrendPoint {

    /** 自然日，格式 yyyy-MM-dd */
    private String day;

    /** 当日搜索总次数 */
    private long total;

    /** 当日无结果搜索次数 */
    private long miss;

    public SearchTrendPoint() {
    }

    public SearchTrendPoint(String day, long total, long miss) {
        this.day = day;
        this.total = total;
        this.miss = miss;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getMiss() {
        return miss;
    }

    public void setMiss(long miss) {
        this.miss = miss;
    }
}
