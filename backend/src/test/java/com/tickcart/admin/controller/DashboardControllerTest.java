package com.tickcart.admin.controller;

import com.tickcart.admin.vo.DashboardVO;
import com.tickcart.common.api.PageResult;
import com.tickcart.common.api.R;
import com.tickcart.product.service.ProductService;
import com.tickcart.product.vo.LowStockReport;
import com.tickcart.seckill.dto.SeckillActivityQuery;
import com.tickcart.seckill.service.SeckillActivityService;
import com.tickcart.seckill.vo.SeckillActivityVO;
import com.tickcart.seckill.vo.SeckillOverviewVO;
import com.tickcart.trade.service.DashboardTrendService;
import com.tickcart.trade.service.OrderService;
import com.tickcart.trade.vo.DailyPoint;
import com.tickcart.trade.vo.OrderOverviewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DashboardController} 单测。
 *
 * <p>职责是纯编排：各域数据原样透传，趋势天数做 1~90 钳制，战报与库存走明细接口。</p>
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private SeckillActivityService seckillActivityService;
    @Mock
    private ProductService productService;
    @Mock
    private DashboardTrendService dashboardTrendService;

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(orderService, seckillActivityService,
                productService, dashboardTrendService);
    }

    /** 打桩各域最小返回值，避免每个用例重复。 */
    private void stubCommon(int days) {
        when(orderService.adminOverview()).thenReturn(new OrderOverviewVO());
        when(seckillActivityService.overview()).thenReturn(new SeckillOverviewVO());
        when(seckillActivityService.page(any(SeckillActivityQuery.class)))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 8, 0));
        when(productService.lowStock(5)).thenReturn(new LowStockReport());
        when(dashboardTrendService.dailyOrderTrend(days)).thenReturn(List.of());
        when(dashboardTrendService.dailyAmountTrend(days)).thenReturn(List.of());
    }

    @Test
    @DisplayName("聚合：各域数据原样透传，趋势按请求天数")
    void aggregatesAllDomains() {
        OrderOverviewVO order = new OrderOverviewVO();
        SeckillOverviewVO seckill = new SeckillOverviewVO();
        List<SeckillActivityVO> activities = List.of(new SeckillActivityVO());
        LowStockReport lowStock = new LowStockReport();
        List<DailyPoint> orderTrend = List.of(new DailyPoint("2026-10-20", 1, BigDecimal.ZERO));
        List<DailyPoint> amountTrend = List.of(new DailyPoint("2026-10-20", 1, BigDecimal.ONE));

        when(orderService.adminOverview()).thenReturn(order);
        when(seckillActivityService.overview()).thenReturn(seckill);
        when(seckillActivityService.page(any(SeckillActivityQuery.class)))
                .thenReturn(new PageResult<>(activities, 1, 1, 8, 1));
        when(productService.lowStock(5)).thenReturn(lowStock);
        when(dashboardTrendService.dailyOrderTrend(7)).thenReturn(orderTrend);
        when(dashboardTrendService.dailyAmountTrend(7)).thenReturn(amountTrend);

        R<DashboardVO> result = controller.dashboard(7);

        assertThat(result.getCode()).isZero();
        DashboardVO vo = result.getData();
        assertThat(vo.getOrder()).isSameAs(order);
        assertThat(vo.getSeckill()).isSameAs(seckill);
        // 战报会过滤「已下线」并截断，返回的是新列表，按内容比较
        assertThat(vo.getSeckillActivities()).containsExactlyElementsOf(activities);
        assertThat(vo.getLowStock()).isSameAs(lowStock);
        assertThat(vo.getOrderTrend()).isSameAs(orderTrend);
        assertThat(vo.getAmountTrend()).isSameAs(amountTrend);
    }

    @Test
    @DisplayName("战报明细：按默认排序扫描前 8 场（不传 status/activeOnly，让已结束也能沉底展示）")
    void requestsRecentActivitiesWithDefaultSort() {
        stubCommon(7);

        controller.dashboard(7);

        ArgumentCaptor<SeckillActivityQuery> captor = ArgumentCaptor.forClass(SeckillActivityQuery.class);
        verify(seckillActivityService).page(captor.capture());
        SeckillActivityQuery q = captor.getValue();
        assertThat(q.getPage()).isEqualTo(1);
        assertThat(q.getSize()).isEqualTo(8);
        // 不指定 status / activeOnly / sortBy：交给 service 默认「在场优先、已结束沉底」排序
        assertThat(q.getStatus()).isNull();
        assertThat(q.getActiveOnly()).isNull();
        assertThat(q.getSortBy()).isNull();
    }

    @Test
    @DisplayName("战报明细：过滤「已下线」草稿，且最多保留 4 场")
    void filtersDisabledAndCapsAtFour() {
        when(orderService.adminOverview()).thenReturn(new OrderOverviewVO());
        when(seckillActivityService.overview()).thenReturn(new SeckillOverviewVO());
        when(productService.lowStock(5)).thenReturn(new LowStockReport());
        when(dashboardTrendService.dailyOrderTrend(7)).thenReturn(List.of());
        when(dashboardTrendService.dailyAmountTrend(7)).thenReturn(List.of());

        List<SeckillActivityVO> scanned = List.of(
                activity(2, "进行中"),
                activity(0, "已下线-应被过滤"),
                activity(1, "即将开始"),
                activity(3, "已结束1"),
                activity(3, "已结束2"),
                activity(3, "已结束3"));
        when(seckillActivityService.page(any(SeckillActivityQuery.class)))
                .thenReturn(new PageResult<>(scanned, scanned.size(), 1, 8, 1));

        DashboardVO vo = controller.dashboard(7).getData();

        assertThat(vo.getSeckillActivities())
                .extracting(SeckillActivityVO::getStatusLabel)
                .containsExactly("进行中", "即将开始", "已结束1", "已结束2");
    }

    private SeckillActivityVO activity(int status, String label) {
        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setStatus(status);
        vo.setStatusLabel(label);
        return vo;
    }


    @Test
    @DisplayName("库存告急：固定取 5 条 SKU 级明细")
    void requestsLowStockWithLimit() {
        stubCommon(7);

        controller.dashboard(7);

        verify(productService).lowStock(5);
    }

    @Test
    @DisplayName("默认天数：不传（0）时按 7 天")
    void defaultsToSevenDays() {
        stubCommon(7);

        controller.dashboard(0);

        verify(dashboardTrendService).dailyOrderTrend(7);
        verify(dashboardTrendService).dailyAmountTrend(7);
    }

    @Test
    @DisplayName("天数钳制：过大钳到 90")
    void clampsUpperBoundDaysTo90() {
        stubCommon(90);

        controller.dashboard(999);

        verify(dashboardTrendService).dailyOrderTrend(90);
        verify(dashboardTrendService).dailyAmountTrend(90);
    }
}
