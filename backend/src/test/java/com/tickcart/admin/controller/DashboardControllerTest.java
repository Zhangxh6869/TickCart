package com.tickcart.admin.controller;

import com.tickcart.admin.vo.DashboardVO;
import com.tickcart.common.api.R;
import com.tickcart.product.service.CategoryService;
import com.tickcart.product.vo.CategoryOverview;
import com.tickcart.seckill.service.SeckillActivityService;
import com.tickcart.seckill.vo.SeckillOverviewVO;
import com.tickcart.trade.service.DashboardTrendService;
import com.tickcart.trade.service.OrderService;
import com.tickcart.trade.vo.DailyPoint;
import com.tickcart.trade.vo.OrderOverviewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DashboardController} 单测。
 *
 * <p>职责是纯编排：各域数据原样透传，趋势天数做 1~90 钳制。</p>
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private SeckillActivityService seckillActivityService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private DashboardTrendService dashboardTrendService;

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(orderService, seckillActivityService,
                categoryService, dashboardTrendService);
    }

    @Test
    @DisplayName("聚合：各域 overview 原样透传，趋势按请求天数")
    void aggregatesAllDomains() {
        OrderOverviewVO order = new OrderOverviewVO();
        SeckillOverviewVO seckill = new SeckillOverviewVO();
        List<CategoryOverview> category = List.of(new CategoryOverview());
        List<DailyPoint> orderTrend = List.of(new DailyPoint("2026-10-20", 1, java.math.BigDecimal.ZERO));
        List<DailyPoint> amountTrend = List.of(new DailyPoint("2026-10-20", 1, java.math.BigDecimal.ONE));

        when(orderService.adminOverview()).thenReturn(order);
        when(seckillActivityService.overview()).thenReturn(seckill);
        when(categoryService.overview()).thenReturn(category);
        when(dashboardTrendService.dailyOrderTrend(7)).thenReturn(orderTrend);
        when(dashboardTrendService.dailyAmountTrend(7)).thenReturn(amountTrend);

        R<DashboardVO> result = controller.dashboard(7);

        assertThat(result.getCode()).isZero(); // 成功
        DashboardVO vo = result.getData();
        assertThat(vo.getOrder()).isSameAs(order);
        assertThat(vo.getSeckill()).isSameAs(seckill);
        assertThat(vo.getCategory()).isSameAs(category);
        assertThat(vo.getOrderTrend()).isSameAs(orderTrend);
        assertThat(vo.getAmountTrend()).isSameAs(amountTrend);
    }

    @Test
    @DisplayName("默认天数：不传时按 7 天")
    void defaultsToSevenDays() {
        when(orderService.adminOverview()).thenReturn(new OrderOverviewVO());
        when(seckillActivityService.overview()).thenReturn(new SeckillOverviewVO());
        when(categoryService.overview()).thenReturn(List.of());
        when(dashboardTrendService.dailyOrderTrend(7)).thenReturn(List.of());
        when(dashboardTrendService.dailyAmountTrend(7)).thenReturn(List.of());

        controller.dashboard(0);

        verify(dashboardTrendService).dailyOrderTrend(7);
        verify(dashboardTrendService).dailyAmountTrend(7);
    }

    @Test
    @DisplayName("天数钳制：过大钳到 90")
    void clampsUpperBoundDaysTo90() {
        when(orderService.adminOverview()).thenReturn(new OrderOverviewVO());
        when(seckillActivityService.overview()).thenReturn(new SeckillOverviewVO());
        when(categoryService.overview()).thenReturn(List.of());
        when(dashboardTrendService.dailyOrderTrend(90)).thenReturn(List.of());
        when(dashboardTrendService.dailyAmountTrend(90)).thenReturn(List.of());

        controller.dashboard(999);
        verify(dashboardTrendService).dailyOrderTrend(90);
        verify(dashboardTrendService).dailyAmountTrend(90);
    }
}
