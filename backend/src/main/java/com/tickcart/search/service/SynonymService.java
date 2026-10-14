package com.tickcart.search.service;

import com.tickcart.search.mapper.SearchSynonymMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 同义词服务：把搜索关键词展开成一组「同义词」，供商品搜索一并召回。
 *
 * <p>例：搜「苹果手机」→ 展开成 {@code [苹果手机, iPhone]}，从而命中 iPhone 系列。</p>
 */
@Service
public class SynonymService {

    /** 单次展开的词数上限，避免超大组把 WHERE 撑得过长。 */
    private static final int MAX_TERMS = 8;

    private final SearchSynonymMapper searchSynonymMapper;

    public SynonymService(SearchSynonymMapper searchSynonymMapper) {
        this.searchSynonymMapper = searchSynonymMapper;
    }

    /**
     * 展开关键词。
     *
     * @return 含原关键词在内的展开词（去重、原词在前）；关键词为空返回空列表；
     *         表里没有该词时只返回原关键词本身（此时搜索行为与引入同义词前一致）。
     */
    public List<String> expand(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return List.of();
        }
        List<String> group = searchSynonymMapper.wordsInSameGroup(kw);
        if (group.isEmpty()) {
            return List.of(kw);
        }
        // LinkedHashSet 去重且保序：原词排最前，其余同义词按表内 id 顺序
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.add(kw);
        terms.addAll(group);

        List<String> result = new ArrayList<>(terms);
        return result.size() > MAX_TERMS ? result.subList(0, MAX_TERMS) : result;
    }
}
