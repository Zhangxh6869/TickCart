package com.tickcart.search.service;

import com.tickcart.search.dto.KeywordCount;
import com.tickcart.search.dto.KeywordStat;
import com.tickcart.search.dto.SearchStatsVO;
import com.tickcart.search.entity.SearchLog;
import com.tickcart.search.mapper.SearchLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 搜索日志服务。
 *
 * <p>职责：① 在每次前台搜索后 best-effort 记录一行（写入失败绝不阻塞搜索主流程）；
 * ② 为后台「搜索分析」看板提供聚合统计（无结果词 TOP N / 热搜词 TOP N）。</p>
 */
@Service
public class SearchLogService {

    private static final Logger log = LoggerFactory.getLogger(SearchLogService.class);

    /** keyword 列长度上限，超长截断，避免单条日志撑爆或越界。 */
    private static final int KEYWORD_MAX = 64;

    private final SearchLogMapper searchLogMapper;

    public SearchLogService(SearchLogMapper searchLogMapper) {
        this.searchLogMapper = searchLogMapper;
    }

    /**
     * 记录一次搜索（best-effort，失败不影响主流程）。
     *
     * @param keyword     关键词（会 trim，空白则忽略）
     * @param resultCount 本次命中商品数
     * @param userId      登录用户ID，未登录传 null
     */
    public void record(String keyword, int resultCount, Long userId) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String kw = keyword.trim();
        if (kw.length() > KEYWORD_MAX) {
            kw = kw.substring(0, KEYWORD_MAX);
        }
        try {
            SearchLog entity = new SearchLog();
            entity.setKeyword(kw);
            entity.setResultCount(resultCount);
            entity.setUserId(userId);
            // created_at 由 MyMetaObjectHandler 在插入时自动填充
            searchLogMapper.insert(entity);
        } catch (Exception e) {
            // 搜索日志仅用于统计，写入失败绝不能影响搜索结果的返回
            log.warn("记录搜索日志失败 keyword={} resultCount={}", kw, resultCount, e);
        }
    }

    /**
     * 搜索分析看板数据：汇总指标 + 无结果词 TOP N + 热搜词 TOP N。
     *
     * @param days  时间范围（天），<=0 表示全部时间
     * @param limit 榜单条数上限
     */
    public SearchStatsVO stats(int days, int limit) {
        LocalDateTime since = toSince(days);
        SearchStatsVO vo = new SearchStatsVO();
        vo.setTotalSearches(searchLogMapper.totalSearches(since));
        vo.setNoResultSearches(searchLogMapper.noResultSearches(since));
        vo.setDistinctKeywords(searchLogMapper.distinctKeywords(since));
        vo.setNoResultTop(searchLogMapper.noResultTopN(limit, since));
        vo.setHotTop(searchLogMapper.hotTopN(limit, since));
        return vo;
    }

    private LocalDateTime toSince(int days) {
        if (days <= 0) {
            return null; // 全部时间
        }
        return LocalDateTime.now().minusDays(days);
    }
}
