package com.tickcart.search.controller;

import com.tickcart.common.api.R;
import com.tickcart.search.service.SearchSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台搜索建议接口（公开，无需登录）。
 *
 * <p>与后台的 {@code /api/admin/search-stats} 不同，这里只暴露「给用户看」的
 * 热门搜索词与相关搜索词，不返回任何统计明细。</p>
 */
@Tag(name = "搜索建议(前台)", description = "热门搜索、相关搜索")
@RestController
@RequestMapping("/api/search")
public class SearchController {

    /** 单次返回的词数上限，防止调用方传超大 limit 拖垮查询。 */
    private static final int MAX_LIMIT = 20;

    private final SearchSuggestionService suggestionService;

    public SearchController(SearchSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @Operation(summary = "热门搜索词", description = "真实搜索词频 TOP N；无搜索数据时兜底热销商品名")
    @GetMapping("/hot")
    public R<List<String>> hot(@RequestParam(defaultValue = "8") int limit) {
        return R.ok(suggestionService.hotWords(clamp(limit)));
    }

    @Operation(summary = "相关搜索词", description = "与 keyword 相关的搜索词（真实日志优先，命中商品高频词兜底）")
    @GetMapping("/related")
    public R<List<String>> related(@RequestParam(required = false) String keyword,
                                   @RequestParam(defaultValue = "8") int limit) {
        return R.ok(suggestionService.relatedWords(keyword, clamp(limit)));
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
