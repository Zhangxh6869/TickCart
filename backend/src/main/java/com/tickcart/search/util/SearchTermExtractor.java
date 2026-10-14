package com.tickcart.search.util;

import com.tickcart.product.entity.Product;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从命中商品的文本里抽取候选搜索词（用于「相关搜索」的兜底来源）。
 *
 * <p>商品名形如「5G 旗舰智能手机 12+256G 曜夜黑」、副标题形如「骁龙旗舰芯片，超清三摄」，
 * 用「空白 + 常见中英文标点」切分即可得到较干净的短语。切分后只保留**含中文**、
 * 且长度 ≥2 的词，以滤掉「12+256G」「67W」这类规格串。</p>
 *
 * <p>排序：出现次数降序；次数相同时保持插入顺序（商品名先于副标题，因此品类词
 * 会排在卖点词前面）。</p>
 */
public final class SearchTermExtractor {

    /** 切分符：空白与常见中英文标点。 */
    private static final String SEPARATORS = "[\\s，,、。；;：:！!？?（）()·|/]+";

    private SearchTermExtractor() {
    }

    /**
     * 抽取候选词。
     *
     * @param products 命中商品（一般按销量降序取一批）
     * @param query    当前关键词（会从结果里排除，避免把用户刚搜的词又推荐回去）
     * @param limit    返回上限
     */
    public static List<String> extract(List<Product> products, String query, int limit) {
        if (products == null || products.isEmpty() || limit <= 0) {
            return List.of();
        }
        String q = query == null ? "" : query.trim();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Product p : products) {
            collect(p.getName(), counts, q);
            collect(p.getSubtitle(), counts, q);
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(limit)
                .toList();
    }

    private static void collect(String text, Map<String, Integer> counts, String query) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String raw : text.split(SEPARATORS)) {
            String term = raw.trim();
            if (isMeaningful(term, query)) {
                counts.merge(term, 1, Integer::sum);
            }
        }
    }

    private static boolean isMeaningful(String term, String query) {
        if (term.length() < 2) {
            return false;
        }
        if (term.equalsIgnoreCase(query)) {
            return false; // 关键词本身不作为「相关词」再推给用户
        }
        return containsCjk(term);
    }

    private static boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
