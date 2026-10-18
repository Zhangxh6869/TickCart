package com.tickcart.common.cache;

/**
 * 缓存 key 常量与拼装方法。
 *
 * <p>集中在一处是为了让「读」与「失效」用同一个拼法 —— 两边各写一遍字符串，
 * 迟早会出现改了读的 key 却忘了改删的 key，表现为「后台改了前台不变」。</p>
 *
 * <p>完整 key 由 {@code CacheService} 统一加 {@code cache:} 前缀；
 * 这里只写业务部分，便于按前缀排查与清理。</p>
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** 商品详情：{@code cache:product:detail:{id}} */
    public static String productDetail(Long id) {
        return "product:detail:" + id;
    }

    /** 前台分类列表（仅启用）：{@code cache:category:list:enabled} */
    public static String categoryListEnabled() {
        return "category:list:enabled";
    }

    /** 热搜词：{@code cache:search:hot:{limit}} */
    public static String hotWords(int limit) {
        return "search:hot:" + limit;
    }
}
