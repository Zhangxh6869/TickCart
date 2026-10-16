package com.tickcart.trade.controller;

import com.tickcart.common.api.PageResult;
import com.tickcart.common.api.R;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.UserContext;
import com.tickcart.trade.dto.OrderBuyNowRequest;
import com.tickcart.trade.dto.OrderCreateRequest;
import com.tickcart.trade.dto.OrderQuery;
import com.tickcart.trade.service.OrderService;
import com.tickcart.trade.vo.OrderCountVO;
import com.tickcart.trade.vo.OrderDetailVO;
import com.tickcart.trade.vo.OrderListItemVO;
import com.tickcart.trade.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（需登录）。
 */
@Tag(name = "订单", description = "下单、订单列表与详情、取消与确认收货")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "创建订单", description = "结算购物车中已勾选且有效的商品")
    @PostMapping
    public R<OrderVO> create(@Valid @RequestBody OrderCreateRequest req) {
        return R.ok(orderService.create(currentUserId(), req));
    }

    @Operation(summary = "立即购买", description = "商品详情页直达下单，不经过购物车")
    @PostMapping("/buy-now")
    public R<OrderVO> buyNow(@Valid @RequestBody OrderBuyNowRequest req) {
        return R.ok(orderService.createBuyNow(currentUserId(), req));
    }

    @Operation(summary = "订单列表", description = "可按状态筛选")
    @GetMapping
    public R<PageResult<OrderListItemVO>> list(OrderQuery query) {
        return R.ok(orderService.page(currentUserId(), query));
    }

    /**
     * 各状态订单数量，供「我的」页订单宫格展示角标。
     *
     * <p>路径是字面量 {@code /count}，与下面的 {@code /{orderNo}} 同层级 ——
     * Spring 的路径匹配中字面量优先于路径变量，因此本方法不会被
     * 「把 count 当成订单号」的详情接口抢走。</p>
     */
    @Operation(summary = "各状态订单数量", description = "供「我的」页订单宫格展示角标")
    @GetMapping("/count")
    public R<OrderCountVO> count() {
        return R.ok(orderService.countByStatus(currentUserId()));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public R<OrderDetailVO> detail(@PathVariable String orderNo) {
        return R.ok(orderService.detail(currentUserId(), orderNo));
    }

    @Operation(summary = "取消订单", description = "仅待付款可取消，取消后回补库存")
    @PutMapping("/{orderNo}/cancel")
    public R<Void> cancel(@PathVariable String orderNo) {
        orderService.cancel(currentUserId(), orderNo);
        return R.ok();
    }

    @Operation(summary = "确认收货", description = "仅待收货可确认")
    @PutMapping("/{orderNo}/confirm")
    public R<Void> confirm(@PathVariable String orderNo) {
        orderService.confirm(currentUserId(), orderNo);
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
