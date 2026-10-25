package com.tickcart.trade.controller;

import com.tickcart.common.api.PageResult;
import com.tickcart.common.api.R;
import com.tickcart.trade.dto.AdminOrderQuery;
import com.tickcart.trade.service.OrderService;
import com.tickcart.trade.vo.AdminOrderDetailVO;
import com.tickcart.trade.vo.AdminOrderListItemVO;
import com.tickcart.trade.vo.BatchShipResultVO;
import com.tickcart.trade.vo.OrderOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台订单管理接口（需 ROLE_ADMIN）。
 *
 * <p>路径前缀 {@code /api/admin/**}，由 {@code SecurityConfig} 统一要求管理员角色。
 * 后台可查看全部用户的订单（不做归属过滤），并执行发货 / 批量发货 / 关闭订单。</p>
 */
@Tag(name = "订单(后台)", description = "订单查询、发货与关闭")
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "订单概览",
            description = "各状态计数 + 待发货最早付款时间 + 成交额，供页头 KPI 与状态筛选一次取全")
    @GetMapping("/overview")
    public R<OrderOverviewVO> overview() {
        return R.ok(orderService.adminOverview());
    }

    @Operation(summary = "订单列表",
            description = "全部用户的订单，可按状态、关键字（订单号/收货人/手机号/商品名）、下单日期筛选，"
                    + "支持 sortBy=createdAt|payTime 排序")
    @GetMapping
    public R<PageResult<AdminOrderListItemVO>> list(AdminOrderQuery query) {
        return R.ok(orderService.adminPage(query));
    }

    @Operation(summary = "订单详情", description = "含收货快照、商品明细与支付语义（状态/渠道/流水号）")
    @GetMapping("/{orderNo}")
    public R<AdminOrderDetailVO> detail(@PathVariable String orderNo) {
        return R.ok(orderService.adminDetail(orderNo));
    }

    @Operation(summary = "发货", description = "仅待发货订单可发货，发货后订单进入待收货")
    @PutMapping("/{orderNo}/ship")
    public R<Void> ship(@PathVariable String orderNo) {
        orderService.ship(orderNo);
        return R.ok();
    }

    @Operation(summary = "批量发货",
            description = "允许部分成功：返回成功清单与失败清单（含原因），失败项由管理员单独处理")
    @PutMapping("/batch/ship")
    public R<BatchShipResultVO> batchShip(@RequestBody List<String> orderNos) {
        return R.ok(orderService.batchShip(orderNos));
    }

    @Operation(summary = "关闭订单", description = "仅待付款订单可关闭，关闭时回补库存（等同用户取消）")
    @PutMapping("/{orderNo}/close")
    public R<Void> close(@PathVariable String orderNo) {
        orderService.adminClose(orderNo);
        return R.ok();
    }
}
