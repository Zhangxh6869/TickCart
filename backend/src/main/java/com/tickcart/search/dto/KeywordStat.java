package com.tickcart.search.dto;

import java.time.LocalDateTime;

/**
 * 单个关键词的搜索概览（热搜词榜单用）。
 */
public class KeywordStat {

    /** 搜索关键词 */
    private String keyword;

    /** 总搜索次数 */
    private long count;

    /** 有结果次数（result_count > 0） */
    private long hitCount;

    /** 无结果次数（result_count = 0） */
    private long missCount;

    /** 最近一次搜索时间 */
    private LocalDateTime lastSearchedAt;

    public KeywordStat() {
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public long getHitCount() {
        return hitCount;
    }

    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }

    public long getMissCount() {
        return missCount;
    }

    public void setMissCount(long missCount) {
        this.missCount = missCount;
    }

    public LocalDateTime getLastSearchedAt() {
        return lastSearchedAt;
    }

    public void setLastSearchedAt(LocalDateTime lastSearchedAt) {
        this.lastSearchedAt = lastSearchedAt;
    }

    /** 命中率（有结果次数 / 总次数），0~1，前端按百分比展示。 */
    public double getHitRate() {
        return count == 0 ? 0d : (double) hitCount / count;
    }
}
