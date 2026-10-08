package com.tickcart.trade.controller;

import com.tickcart.common.api.PageResult;
import com.tickcart.common.api.R;
import com.tickcart.trade.dto.AdminOrderQuery;
import com.tickcart.trade.service.OrderService;
import com.tickcart.trade.vo.AdminOrderListItemVO;
import com.tickcart.trade.vo.OrderDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台订单管理接口（需 ROLE_ADMIN）。
 *
 * <p>路径前缀 {@code /api/admin/**}，由 {@code SecurityConfig} 统一要求管理员角色。
 * 后台可查看全部用户的订单（不做归属过滤），并执行发货。</p>
 */
@Tag(name = "订单(后台)", description = "订单查询与发货")
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "订单列表", description = "全部用户的订单，可按状态与关键字（订单号/收货人/手机号）筛选")
    @GetMapping
    public R<PageResult<AdminOrderListItemVO>> list(AdminOrderQuery query) {
        return R.ok(orderService.adminPage(query));
    }

    @Operation(summary = "订单详情", description = "含收货快照与商品明细")
    @GetMapping("/{orderNo}")
    public R<OrderDetailVO> detail(@PathVariable String orderNo) {
        return R.ok(orderService.adminDetail(orderNo));
    }

    @Operation(summary = "发货", description = "仅待发货订单可发货，发货后订单进入待收货")
    @PutMapping("/{orderNo}/ship")
    public R<Void> ship(@PathVariable String orderNo) {
        orderService.ship(orderNo);
        return R.ok();
    }
}
