package com.tickcart.search.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tickcart.common.cache.CacheKeys;
import com.tickcart.common.cache.CacheProperties;
import com.tickcart.common.cache.CacheService;
import com.tickcart.product.entity.Product;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.search.mapper.SearchLogMapper;
import com.tickcart.search.util.SearchTermExtractor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 搜索建议服务：给前台提供「热门搜索」与「相关搜索」。
 *
 * <p>数据以真实搜索日志（{@code search_log}）为准；日志不足以支撑时，用命中商品的
 * 高频词兜底（见 {@link SearchTermExtractor}），保证任何情况下都有可用内容。</p>
 */
@Service
public class SearchSuggestionService {

    /** 抽兜底词时取多少条命中商品（按销量降序），太少会词穷、太多会引入长尾噪音。 */
    private static final int TERM_POOL = 60;

    /** 兜底取热销商品名时多取一些，去重后仍够 limit 个。 */
    private static final int HEAD_POOL_FACTOR = 3;

    private final SearchLogMapper searchLogMapper;
    private final ProductMapper productMapper;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    public SearchSuggestionService(SearchLogMapper searchLogMapper,
                                   ProductMapper productMapper,
                                   CacheService cacheService,
                                   CacheProperties cacheProperties) {
        this.searchLogMapper = searchLogMapper;
        this.productMapper = productMapper;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    /**
     * 全局热门搜索词：真实搜索词频 TOP N；搜索日志为空时兜底「热销商品名首段」。
     */
    public List<String> hotWords(int limit) {
        // 热搜依赖 search_log 词频，每次都要 GROUP BY 全表聚合，是全站最贵的只读查询之一。
        // 用短 TTL 自然过期而不是主动失效：搜索日志每搜一次就写一行，主动失效等于永不命中。
        return cacheService.getList(CacheKeys.hotWords(limit),
                cacheProperties.getHotWordsTtlSeconds(),
                String.class,
                () -> loadHotWords(limit));
    }

    /** 直查 DB 计算热搜词（缓存未命中时调用）。 */
    private List<String> loadHotWords(int limit) {
        List<String> hot = searchLogMapper.hotKeywords(limit);
        if (!hot.isEmpty()) {
            return hot;
        }
        return hotProductNameHeads(limit);
    }

    /**
     * 与 {@code keyword} 相关的搜索词。
     *
     * <p>两步：① 先从真实搜索日志里取与关键词有包含关系的搜索词；
     * ② 不足时用命中商品的高频词补齐。关键词本身不会出现在结果里。</p>
     */
    public List<String> relatedWords(String keyword, int limit) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(kw.toLowerCase());

        // ① 真实搜索日志里与关键词相关的词
        for (String w : searchLogMapper.relatedKeywords(kw, escapeLike(kw), limit)) {
            addUnique(result, seen, w);
        }

        // ② 不够 → 命中商品的高频词兜底
        if (result.size() < limit) {
            List<Product> matched = matchProducts(kw);
            for (String w : SearchTermExtractor.extract(matched, kw, limit * 2)) {
                addUnique(result, seen, w);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    /** 取与关键词匹配的在售商品（与前台搜索口径一致：名称或副标题包含），按销量降序。 */
    private List<Product> matchProducts(String kw) {
        String pattern = "%" + escapeLike(kw) + "%";
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        wrapper.apply("(name LIKE {0} ESCAPE '!' OR subtitle LIKE {1} ESCAPE '!')", pattern, pattern)
                .eq("status", 1)
                .orderByDesc("sales")
                .last("LIMIT " + TERM_POOL);
        return productMapper.selectList(wrapper);
    }

    /** 兜底热搜：热销商品名的首段（去掉规格/颜色后缀）。 */
    private List<String> hotProductNameHeads(int limit) {
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1).orderByDesc("sales").last("LIMIT " + (limit * HEAD_POOL_FACTOR));
        List<String> heads = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Product p : productMapper.selectList(wrapper)) {
            String name = p.getName() == null ? "" : p.getName().trim();
            String head = name.isEmpty() ? "" : name.split("\\s+")[0];
            if (!head.isEmpty() && seen.add(head.toLowerCase())) {
                heads.add(head);
                if (heads.size() >= limit) {
                    break;
                }
            }
        }
        return heads;
    }

    private static void addUnique(List<String> list, Set<String> seen, String word) {
        if (word == null) {
            return;
        }
        String w = word.trim();
        if (!w.isEmpty() && seen.add(w.toLowerCase())) {
            list.add(w);
        }
    }

    /** 转义 LIKE 通配符，转义符用 {@code !}（与 ProductService 保持一致）。 */
    private static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
