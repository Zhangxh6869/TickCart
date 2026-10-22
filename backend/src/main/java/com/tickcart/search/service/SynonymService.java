package com.tickcart.search.service;

import com.tickcart.common.exception.BizException;
import com.tickcart.search.entity.SearchSynonym;
import com.tickcart.search.mapper.SearchSynonymMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 单个词条长度上限，与 {@code search_synonym.word} 列宽一致。 */
    private static final int WORD_MAX = 64;

    private static final Logger log = LoggerFactory.getLogger(SynonymService.class);

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

    /**
     * 后台「+ 同义词」：把 {@code keyword} 与 {@code words} 归入同一个同义词组。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>keyword 已在某个组 → 新词追加到该组，不重复插入 keyword；</li>
     *   <li>keyword 不在表中 → 新建组（max + 1），keyword 与同义词一起入库；</li>
     *   <li>表里已存在的词一律跳过，<b>不做跨组迁移</b> —— 迁移会悄悄改变已有搜索的
     *       召回范围，属于运营不可预期的副作用。</li>
     * </ul>
     *
     * @return 实际新增的词条数（去重与跳过之后）
     */
    @Transactional
    public int addWords(String keyword, List<String> words) {
        String kw = normalize(keyword);
        if (kw.isEmpty()) {
            throw BizException.badRequest("关键词不能为空");
        }
        // LinkedHashSet：去重且保持运营填写的顺序；同时剔除与 keyword 相同的词
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (words != null) {
            for (String w : words) {
                String t = normalize(w);
                if (!t.isEmpty() && !t.equals(kw)) {
                    targets.add(t);
                }
            }
        }
        if (targets.isEmpty()) {
            throw BizException.badRequest("至少填写一个与关键词不同的同义词");
        }

        Integer groupId = searchSynonymMapper.findGroupId(kw);
        int added = 0;
        if (groupId == null) {
            groupId = searchSynonymMapper.maxGroupId() + 1;
            if (searchSynonymMapper.countByWord(kw) == 0 && insert(kw, groupId)) {
                added++;
            }
        }
        for (String word : targets) {
            if (searchSynonymMapper.countByWord(word) > 0) {
                continue; // 已存在（可能在其他组），跳过而非迁移
            }
            if (insert(word, groupId)) {
                added++;
            }
        }
        return added;
    }

    /**
     * 插入一个词条。
     *
     * <p>{@code search_synonym.word} 上有唯一索引，预检之后仍可能被并发请求抢先插入，
     * 这里吞掉 {@link DuplicateKeyException} 视为「已存在」—— 同义词是幂等的，
     * 重复添加不该让运营看到 500。</p>
     */
    private boolean insert(String word, int groupId) {
        SearchSynonym entity = new SearchSynonym();
        entity.setWord(word);
        entity.setGroupId(groupId);
        try {
            searchSynonymMapper.insert(entity);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("同义词已存在，跳过 word={}", word);
            return false;
        }
    }

    private String normalize(String word) {
        if (word == null) {
            return "";
        }
        String w = word.trim();
        return w.length() > WORD_MAX ? w.substring(0, WORD_MAX) : w;
    }
}
