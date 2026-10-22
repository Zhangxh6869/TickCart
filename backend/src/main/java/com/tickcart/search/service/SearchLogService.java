package com.tickcart.search.service;

import com.tickcart.search.dto.KeywordCount;
import com.tickcart.search.dto.KeywordStat;
import com.tickcart.search.dto.SearchStatsVO;
import com.tickcart.search.dto.SearchTrendPoint;
import com.tickcart.search.buffer.SearchLogBuffer;
import com.tickcart.search.entity.SearchLog;
import com.tickcart.search.mapper.SearchLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索日志服务。
 *
 * <p>职责：① 在每次前台搜索后 best-effort 记录一行（写入失败绝不阻塞搜索主流程）；
 * ② 为后台「搜索分析」看板提供聚合统计（无结果词 TOP N / 热搜词 TOP N）。</p>
 *
 * <p>写入走 {@link SearchLogBuffer} 的内存队列 + 定时批量落库：请求线程只做一次入队，
 * 不承担 DB 往返，也不会因为搜索日志写不进去而拖慢搜索接口。</p>
 */
@Service
public class SearchLogService {

    private static final Logger log = LoggerFactory.getLogger(SearchLogService.class);

    /** keyword 列长度上限，超长截断，避免单条日志撑爆或越界。 */
    private static final int KEYWORD_MAX = 64;

    private final SearchLogMapper searchLogMapper;
    private final SearchLogBuffer searchLogBuffer;

    public SearchLogService(SearchLogMapper searchLogMapper, SearchLogBuffer searchLogBuffer) {
        this.searchLogMapper = searchLogMapper;
        this.searchLogBuffer = searchLogBuffer;
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
            // 批量插入走自定义 SQL，不经过 MyMetaObjectHandler，时间戳必须在这里填
            entity.setCreatedAt(LocalDateTime.now());
            searchLogBuffer.offer(entity);
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

    /**
     * 搜索趋势：按自然日返回连续的数据点，缺失日期补 0。
     *
     * <p>口径与 {@link #stats} 略有差异，这是刻意的：stats 用「滚动 N×24 小时」，
     * 而趋势必须落在自然日边界上，否则首日只有半天数据、折线首点会莫名塌陷。
     * 两者差异不到一个自然日，都落在「最近 N 天」的语义内。</p>
     *
     * @param days 时间范围（天），<=0 表示按现有数据的首尾日补齐
     */
    public List<SearchTrendPoint> trend(int days) {
        LocalDate start;
        LocalDate end;
        if (days > 0) {
            end = LocalDate.now();
            start = end.minusDays(days - 1L);
        } else {
            // 全部时间：先取数据边界，空表直接返回空列表（不臆造日期）
            List<SearchTrendPoint> raw = searchLogMapper.trendByDay(null);
            if (raw.isEmpty()) {
                return List.of();
            }
            return fillMissingDays(raw, LocalDate.parse(raw.get(0).getDay()),
                    LocalDate.parse(raw.get(raw.size() - 1).getDay()));
        }
        return fillMissingDays(searchLogMapper.trendByDay(start.atStartOfDay()), start, end);
    }

    /**
     * 把「只有有日志的日期」补齐成连续日期序列，缺失日给 0。
     *
     * <p>补 0 而不是跳过，是因为前端要直接按点画折线：跳过缺失日会让折线把
     * 两个不相邻的日期连成一条斜线，视觉上像「一直有人在搜」。</p>
     */
    private List<SearchTrendPoint> fillMissingDays(List<SearchTrendPoint> raw,
                                                   LocalDate start, LocalDate end) {
        Map<String, SearchTrendPoint> byDay = new HashMap<>(raw.size() * 2);
        for (SearchTrendPoint p : raw) {
            byDay.put(p.getDay(), p);
        }
        List<SearchTrendPoint> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.toString();
            result.add(byDay.getOrDefault(key, new SearchTrendPoint(key, 0, 0)));
        }
        return result;
    }
}
