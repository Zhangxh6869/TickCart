package com.tickcart.product.controller;

import com.tickcart.common.api.PageResult;
import com.tickcart.common.api.R;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.UserContext;
import com.tickcart.product.dto.ViewHistoryQuery;
import com.tickcart.product.service.ViewHistoryService;
import com.tickcart.product.vo.ViewHistoryItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 浏览记录接口（需登录）。
 *
 * <p>★ 刻意**不**加入 {@code SecurityConfig} 的放行清单：浏览记录是私有数据，
 * 必须落入「其余一律需登录」。给 {@code /api/view-history/**} 加 {@code permitAll}
 * 会让任何人都能读写别人的浏览记录（userId 只从登录态取，但接口本身就不该匿名可达）。</p>
 */
@Tag(name = "浏览记录", description = "商品浏览记录与浏览记录列表")
@RestController
@RequestMapping("/api/view-history")
public class ViewHistoryController {

    private final ViewHistoryService viewHistoryService;

    public ViewHistoryController(ViewHistoryService viewHistoryService) {
        this.viewHistoryService = viewHistoryService;
    }

    @Operation(summary = "浏览记录列表", description = "按最近浏览时间倒序，返回商品列表项（多带 viewTime）")
    @GetMapping
    public R<PageResult<ViewHistoryItem>> list(ViewHistoryQuery query) {
        return R.ok(viewHistoryService.page(currentUserId(), query));
    }

    @Operation(summary = "记录一次浏览", description = "幂等：同一商品重复上报只刷新浏览时间")
    @PostMapping("/{productId}")
    public R<Void> record(@PathVariable Long productId) {
        viewHistoryService.record(currentUserId(), productId);
        return R.ok();
    }

    @Operation(summary = "删除单条浏览记录", description = "幂等：没浏览过也返回成功")
    @DeleteMapping("/{productId}")
    public R<Void> remove(@PathVariable Long productId) {
        viewHistoryService.remove(currentUserId(), productId);
        return R.ok();
    }

    @Operation(summary = "清空浏览记录", description = "只清空当前用户的记录，幂等")
    @DeleteMapping
    public R<Void> clear() {
        viewHistoryService.clear(currentUserId());
        return R.ok();
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        return userId;
    }
}
