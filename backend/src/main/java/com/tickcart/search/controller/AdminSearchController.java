package com.tickcart.search.controller;

import com.tickcart.common.api.R;
import com.tickcart.search.dto.SearchStatsVO;
import com.tickcart.search.service.SearchLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台搜索分析接口（需 ROLE_ADMIN）。
 *
 * <p>路径前缀 {@code /api/admin/**}，由 {@code SecurityConfig} 统一要求管理员角色。
 * 数据来自 {@code search_log} 表，用于主动发现「用户搜不到的词」与真实热搜词。</p>
 */
@Tag(name = "搜索分析(后台)", description = "无结果词监控与热搜词统计")
@RestController
@RequestMapping("/api/admin")
public class AdminSearchController {

    private final SearchLogService searchLogService;

    public AdminSearchController(SearchLogService searchLogService) {
        this.searchLogService = searchLogService;
    }

    @Operation(summary = "搜索分析看板",
            description = "返回汇总指标 + 无结果词 TOP N + 热搜词 TOP N，可按最近天数过滤（days<=0 为全部时间）")
    @GetMapping("/search-stats")
    public R<SearchStatsVO> searchStats(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(searchLogService.stats(days, limit));
    }
}
