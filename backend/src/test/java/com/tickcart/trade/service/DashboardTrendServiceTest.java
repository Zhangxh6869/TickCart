package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.vo.DailyPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DashboardTrendService} 单测。
 *
 * <p>核心不变量：返回的列表永远是「从 days 天前到今天」的连续日期（缺失补 0），
 * 前端可直接画图，不用自己补空天。</p>
 */
@ExtendWith(MockitoExtension.class)
class DashboardTrendServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private DashboardTrendService trendService;

    /** 今天在 10/20，近 7 天 => [10/14..10/20]。固定这个时间点便于断言连续日期。 */
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
    }

    private Map<String, Object> row(String day, long cnt) {
        Map<String, Object> m = new HashMap<>();
        m.put("day", Date.valueOf(day));
        m.put("cnt", cnt);
        return m;
    }

    private Map<String, Object> amountRow(String day, long cnt, String amt) {
        Map<String, Object> m = new HashMap<>();
        m.put("day", Date.valueOf(day));
        m.put("cnt", cnt);
        m.put("amt", new BigDecimal(amt));
        return m;
    }

    @Test
    @DisplayName("订单趋势：返回连续 days 天，缺失天补 0")
    void orderTrendFillsMissingDaysWithZero() {
        when(orderMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(
                row(today.minusDays(2L).toString(), 5),
                row(today.minusDays(4L).toString(), 3)));

        List<DailyPoint> points = trendService.dailyOrderTrend(7);

        assertThat(points).hasSize(7);
        // 日期连续且升序
        for (int i = 0; i < 7; i++) {
            assertThat(points.get(i).getDay())
                    .isEqualTo(today.minusDays(6L).plusDays(i).toString());
        }
        // 有数据的天填上，无数据的天补 0
        assertThat(points.get(2).getCount()).isEqualTo(3);   // 10 天前(=minus4) ? 见下
        assertThat(points.get(4).getCount()).isEqualTo(5);   // 10 天前(=minus2)
        assertThat(points.get(0).getCount()).isZero();        // 无数据
    }

    @Test
    @DisplayName("订单趋势：结果集列名大小写不一致也能取到值")
    void orderTrendIsCaseInsensitiveOnColumns() {
        Map<String, Object> m = new HashMap<>();
        m.put("DAY", Date.valueOf(today.toString()));
        m.put("CNT", 4L);
        when(orderMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(m));

        List<DailyPoint> points = trendService.dailyOrderTrend(1);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).getCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("订单趋势：查询按 created_at 取近 N 天起点并分组按日")
    void orderTrendBuildsGroupByQuery() {
        when(orderMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of());

        trendService.dailyOrderTrend(7);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<Order>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).selectMaps(captor.capture());
        QueryWrapper<Order> wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("created_at").contains("GROUP BY DATE(created_at)");
        // 起点 = today - 6 天的 00:00
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(today.minusDays(6L).atStartOfDay());
    }

    @Test
    @DisplayName("成交额趋势：金额与数量正确，空天金额补 0")
    void amountTrendAggregatesAndFillsZero() {
        when(orderMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(
                amountRow(today.minusDays(1L).toString(), 2, "180.50"),
                amountRow(today.toString(), 1, "99.00")));

        List<DailyPoint> points = trendService.dailyAmountTrend(3);

        assertThat(points).hasSize(3);
        assertThat(points.get(0).getAmount()).isEqualByComparingTo("0");     // 最早一天无数据
        assertThat(points.get(1).getAmount()).isEqualByComparingTo("180.50"); // 昨天
        assertThat(points.get(1).getCount()).isEqualTo(2);
        assertThat(points.get(2).getAmount()).isEqualByComparingTo("99.00"); // 今天
        assertThat(points.get(2).getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("成交额趋势：查询按 pay_time 分组")
    void amountTrendGroupsByPayTime() {
        when(orderMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of());

        trendService.dailyAmountTrend(30);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<Order>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).selectMaps(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("pay_time");
    }

    @Test
    @DisplayName("days=1 时只返回今天一个点")
    void singleDayReturnsTodayOnly() {
        when(orderMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of());

        List<DailyPoint> points = trendService.dailyOrderTrend(1);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).getDay()).isEqualTo(today.toString());
    }
}
