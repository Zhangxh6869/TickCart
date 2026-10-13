package com.tickcart.search.dto;

import java.time.LocalDateTime;

/**
 * 单个关键词的搜索次数（无结果词榜单用）。
 */
public class KeywordCount {

    /** 搜索关键词 */
    private String keyword;

    /** 搜索次数（这里恒为「无结果」次数） */
    private long count;

    /** 最近一次搜索时间 */
    private LocalDateTime lastSearchedAt;

    public KeywordCount() {
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

    public LocalDateTime getLastSearchedAt() {
        return lastSearchedAt;
    }

    public void setLastSearchedAt(LocalDateTime lastSearchedAt) {
        this.lastSearchedAt = lastSearchedAt;
    }
}
