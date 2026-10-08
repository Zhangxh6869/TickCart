package com.tickcart.product.controller;

import com.tickcart.common.api.PageResult;
import com.tickcart.common.api.R;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.UserContext;
import com.tickcart.product.dto.FavoriteQuery;
import com.tickcart.product.service.FavoriteService;
import com.tickcart.product.vo.ProductListItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口（需登录）。
 *
 * <p>未列入 {@code SecurityConfig} 的放行清单，因此自动落入「其余一律需登录」。</p>
 */
@Tag(name = "收藏", description = "商品收藏与收藏列表")
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Operation(summary = "收藏列表", description = "按收藏时间倒序，返回商品列表项")
    @GetMapping
    public R<PageResult<ProductListItem>> list(FavoriteQuery query) {
        return R.ok(favoriteService.page(currentUserId(), query));
    }

    @Operation(summary = "收藏商品", description = "幂等：已收藏时直接返回成功")
    @PostMapping("/{productId}")
    public R<Void> add(@PathVariable Long productId) {
        favoriteService.add(currentUserId(), productId);
        return R.ok();
    }

    @Operation(summary = "取消收藏", description = "幂等：未收藏时也返回成功")
    @DeleteMapping("/{productId}")
    public R<Void> remove(@PathVariable Long productId) {
        favoriteService.remove(currentUserId(), productId);
        return R.ok();
    }

    @Operation(summary = "是否已收藏", description = "供商品详情页回显收藏按钮状态")
    @GetMapping("/{productId}/status")
    public R<Boolean> status(@PathVariable Long productId) {
        return R.ok(favoriteService.exists(currentUserId(), productId));
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        return userId;
    }
}
