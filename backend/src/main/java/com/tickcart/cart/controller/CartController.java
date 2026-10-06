package com.tickcart.cart.controller;

import com.tickcart.cart.dto.CartAddRequest;
import com.tickcart.cart.service.CartService;
import com.tickcart.cart.vo.CartSummary;
import com.tickcart.common.api.R;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车接口（需登录）。
 */
@Tag(name = "购物车", description = "购物车增删改查、勾选与合计")
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "购物车列表", description = "含失效项标记与勾选合计")
    @GetMapping
    public R<CartSummary> list() {
        return R.ok(cartService.list(currentUserId()));
    }

    @Operation(summary = "购物车项数", description = "tabbar 角标用")
    @GetMapping("/count")
    public R<Integer> count() {
        return R.ok(cartService.count(currentUserId()));
    }

    @Operation(summary = "加入购物车", description = "同商品同规格自动合并数量")
    @PostMapping
    public R<Void> add(@Valid @RequestBody CartAddRequest req) {
        cartService.add(currentUserId(), req);
        return R.ok();
    }

    @Operation(summary = "修改数量", description = "传 0 表示删除该项")
    @PutMapping("/{id}")
    public R<Void> updateQuantity(@PathVariable Long id, @RequestParam Integer quantity) {
        cartService.updateQuantity(currentUserId(), id, quantity);
        return R.ok();
    }

    @Operation(summary = "勾选/取消勾选单项")
    @PutMapping("/{id}/checked")
    public R<Void> updateChecked(@PathVariable Long id, @RequestParam Integer checked) {
        cartService.updateChecked(currentUserId(), id, checked);
        return R.ok();
    }

    @Operation(summary = "全选/取消全选")
    @PutMapping("/checked")
    public R<Void> checkAll(@RequestParam Integer checked) {
        cartService.checkAll(currentUserId(), checked);
        return R.ok();
    }

    @Operation(summary = "删除购物车项")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        cartService.delete(currentUserId(), id);
        return R.ok();
    }

    @Operation(summary = "删除已勾选项", description = "下单成功后清理")
    @DeleteMapping("/checked")
    public R<Void> deleteChecked() {
        cartService.deleteChecked(currentUserId());
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
