package com.tickcart.search.dto;

import java.util.List;

/**
 * 后台「搜索分析」看板数据：汇总指标 + 无结果词 TOP N + 热搜词 TOP N。
 */
public class SearchStatsVO {

    /** 区间内总搜索次数 */
    private long totalSearches;

    /** 区间内无结果搜索次数 */
    private long noResultSearches;

    /** 区间内搜索过的关键词数（去重） */
    private int distinctKeywords;

    /** 无结果词 TOP N */
    private List<KeywordCount> noResultTop;

    /** 热搜词 TOP N */
    private List<KeywordStat> hotTop;

    public SearchStatsVO() {
    }

    public long getTotalSearches() {
        return totalSearches;
    }

    public void setTotalSearches(long totalSearches) {
        this.totalSearches = totalSearches;
    }

    public long getNoResultSearches() {
        return noResultSearches;
    }

    public void setNoResultSearches(long noResultSearches) {
        this.noResultSearches = noResultSearches;
    }

    public int getDistinctKeywords() {
        return distinctKeywords;
    }

    public void setDistinctKeywords(int distinctKeywords) {
        this.distinctKeywords = distinctKeywords;
    }

    public List<KeywordCount> getNoResultTop() {
        return noResultTop;
    }

    public void setNoResultTop(List<KeywordCount> noResultTop) {
        this.noResultTop = noResultTop;
    }

    public List<KeywordStat> getHotTop() {
        return hotTop;
    }

    public void setHotTop(List<KeywordStat> hotTop) {
        this.hotTop = hotTop;
    }
}
