package com.tickcart.admin.controller;

import com.tickcart.admin.vo.DashboardVO;
import com.tickcart.common.api.R;
import com.tickcart.product.service.CategoryService;
import com.tickcart.seckill.service.SeckillActivityService;
import com.tickcart.trade.service.DashboardTrendService;
import com.tickcart.trade.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台运营看板聚合接口。
 *
 * <p>纯编排层：各域数据归各域 service，这里只负责一次拉齐返回。
 * 权限由 {@code SecurityConfig} 对 {@code /api/admin/**} 统一要求 ROLE_ADMIN。</p>
 */
@Tag(name = "后台运营看板")
@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    private final OrderService orderService;
    private final SeckillActivityService seckillActivityService;
    private final CategoryService categoryService;
    private final DashboardTrendService dashboardTrendService;

    public DashboardController(OrderService orderService,
                               SeckillActivityService seckillActivityService,
                               CategoryService categoryService,
                               DashboardTrendService dashboardTrendService) {
        this.orderService = orderService;
        this.seckillActivityService = seckillActivityService;
        this.categoryService = categoryService;
        this.dashboardTrendService = dashboardTrendService;
    }

    @Operation(summary = "运营看板聚合数据", description = "一次返回订单概览、秒杀战报、分类库存与近 N 天趋势")
    @GetMapping
    public R<DashboardVO> dashboard(@RequestParam(defaultValue = "7") int days) {
        // days<=0 视为未传，用默认 7；过大钳到 90，防一次拉太多天
        int bounded = days <= 0 ? 7 : Math.min(days, 90);
        DashboardVO vo = new DashboardVO();
        vo.setOrder(orderService.adminOverview());
        vo.setSeckill(seckillActivityService.overview());
        vo.setCategory(categoryService.overview());
        vo.setOrderTrend(dashboardTrendService.dailyOrderTrend(bounded));
        vo.setAmountTrend(dashboardTrendService.dailyAmountTrend(bounded));
        return R.ok(vo);
    }
}
