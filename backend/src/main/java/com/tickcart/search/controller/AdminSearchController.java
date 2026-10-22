package com.tickcart.search.controller;

import com.tickcart.common.api.R;
import com.tickcart.search.dto.SearchStatsVO;
import com.tickcart.search.dto.SearchSynonymAddRequest;
import com.tickcart.search.dto.SearchTrendPoint;
import com.tickcart.search.service.SearchLogService;
import com.tickcart.search.service.SynonymService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台搜索分析接口（需 ROLE_ADMIN）。
 *
 * <p>路径前缀 {@code /api/admin/**}，由 {@code SecurityConfig} 统一要求管理员角色。
 * 数据来自 {@code search_log} 表，用于主动发现「用户搜不到的词」与真实热搜词。</p>
 *
 * <p>三个接口构成一条闭环：看板发现缺口（stats）→ 看走势（trend）→
 * 给缺口补同义词（search-synonyms），补完之后下次搜这个词就能命中。</p>
 */
@Tag(name = "搜索分析(后台)", description = "无结果词监控、热搜词统计与同义词补录")
@RestController
@RequestMapping("/api/admin")
public class AdminSearchController {

    private final SearchLogService searchLogService;
    private final SynonymService synonymService;

    public AdminSearchController(SearchLogService searchLogService, SynonymService synonymService) {
        this.searchLogService = searchLogService;
        this.synonymService = synonymService;
    }

    @Operation(summary = "搜索分析看板",
            description = "返回汇总指标 + 无结果词 TOP N + 热搜词 TOP N，可按最近天数过滤（days<=0 为全部时间）")
    @GetMapping("/search-stats")
    public R<SearchStatsVO> searchStats(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(searchLogService.stats(days, limit));
    }

    @Operation(summary = "搜索趋势",
            description = "按自然日返回连续数据点（缺失日期补 0），供看板迷你趋势图使用；days<=0 按现有数据首尾补齐")
    @GetMapping("/search-stats/trend")
    public R<List<SearchTrendPoint>> searchTrend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(searchLogService.trend(days));
    }

    @Operation(summary = "新增同义词",
            description = "把关键词与给定同义词归入同一组，之后搜任一词都会一起召回；已存在的词自动跳过")
    @PostMapping("/search-synonyms")
    public R<Integer> addSynonyms(@Valid @RequestBody SearchSynonymAddRequest req) {
        return R.ok(synonymService.addWords(req.getKeyword(), req.getWords()));
    }
}
