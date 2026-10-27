package com.tickcart.admin.controller;

import com.tickcart.admin.vo.DashboardVO;
import com.tickcart.common.api.R;
import com.tickcart.product.service.ProductService;
import com.tickcart.seckill.dto.SeckillActivityQuery;
import com.tickcart.seckill.enums.SeckillActivityStatus;
import com.tickcart.seckill.service.SeckillActivityService;
import com.tickcart.seckill.vo.SeckillActivityVO;
import com.tickcart.trade.service.DashboardTrendService;
import com.tickcart.trade.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /** 战报最多展示几场（进行中优先，其次即将开始、已结束） */
    private static final int REPORT_ACTIVITY_LIMIT = 4;
    /** 多取一些再过滤「已下线」的草稿，保证过滤后仍有足够行数 */
    private static final int REPORT_ACTIVITY_SCAN = 8;
    /** 库存告急最多展示几条 SKU */
    private static final int LOW_STOCK_LIMIT = 5;

    private final OrderService orderService;
    private final SeckillActivityService seckillActivityService;
    private final ProductService productService;
    private final DashboardTrendService dashboardTrendService;

    public DashboardController(OrderService orderService,
                               SeckillActivityService seckillActivityService,
                               ProductService productService,
                               DashboardTrendService dashboardTrendService) {
        this.orderService = orderService;
        this.seckillActivityService = seckillActivityService;
        this.productService = productService;
        this.dashboardTrendService = dashboardTrendService;
    }

    @Operation(summary = "运营看板聚合数据", description = "一次返回订单概览、秒杀战报、库存告急与近 N 天趋势")
    @GetMapping
    public R<DashboardVO> dashboard(@RequestParam(defaultValue = "7") int days) {
        // days<=0 视为未传，用默认 7；过大钳到 90，防一次拉太多天
        int bounded = days <= 0 ? 7 : Math.min(days, 90);

        DashboardVO vo = new DashboardVO();
        vo.setOrder(orderService.adminOverview());
        vo.setSeckill(seckillActivityService.overview());
        vo.setSeckillActivities(recentActivities());
        vo.setLowStock(productService.lowStock(LOW_STOCK_LIMIT));
        vo.setOrderTrend(dashboardTrendService.dailyOrderTrend(bounded));
        vo.setAmountTrend(dashboardTrendService.dailyAmountTrend(bounded));
        return R.ok(vo);
    }

    /**
     * 战报明细：走活动列表的默认排序（进行中 → 即将开始 → 已结束 → 已下线），
     * 这样「当前在场」永远排前面，历史场次沉底，不会因为数据里没有在场活动就整块空白。
     *
     * <p>「已下线」是草稿被撤下的状态，不属于战报（用户侧已不可见），这里过滤掉。</p>
     */
    private List<SeckillActivityVO> recentActivities() {
        SeckillActivityQuery query = new SeckillActivityQuery();
        query.setPage(1);
        query.setSize(REPORT_ACTIVITY_SCAN);
        return seckillActivityService.page(query).getRecords().stream()
                .filter(vo -> vo.getStatus() == null || vo.getStatus() != SeckillActivityStatus.DISABLED.getCode())
                .limit(REPORT_ACTIVITY_LIMIT)
                .toList();
    }
}
