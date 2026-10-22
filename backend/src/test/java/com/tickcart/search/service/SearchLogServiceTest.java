package com.tickcart.search.service;

import com.tickcart.search.buffer.SearchLogBuffer;
import com.tickcart.search.dto.SearchTrendPoint;
import com.tickcart.search.mapper.SearchLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SearchLogService#trend(int)} 单测：日期补齐、范围口径、空数据。
 *
 * <p>补齐是前端能直接画折线的前提 —— 没有搜索的那天也必须有一个 0 值点，
 * 否则折线会把两个不相邻的日期连起来，看起来像「一直有人搜」。</p>
 */
@ExtendWith(MockitoExtension.class)
class SearchLogServiceTest {

    @Mock
    private SearchLogMapper searchLogMapper;

    @Mock
    private SearchLogBuffer searchLogBuffer;

    private SearchLogService newService() {
        return new SearchLogService(searchLogMapper, searchLogBuffer);
    }

    @Test
    @DisplayName("trend(days>0)：返回连续 N 个自然日，缺失日期补 0，按日期升序")
    void trendFillsMissingDays() {
        LocalDate today = LocalDate.now();
        when(searchLogMapper.trendByDay(any())).thenReturn(List.of(
                new SearchTrendPoint(today.minusDays(2).toString(), 100, 20),
                new SearchTrendPoint(today.toString(), 50, 5)));

        List<SearchTrendPoint> points = newService().trend(7);

        assertThat(points).hasSize(7);
        assertThat(points.get(0).getDay()).isEqualTo(today.minusDays(6).toString());
        assertThat(points.get(6).getDay()).isEqualTo(today.toString());

        // 有数据的两天保持原值
        SearchTrendPoint day2 = points.get(4);
        assertThat(day2.getDay()).isEqualTo(today.minusDays(2).toString());
        assertThat(day2.getTotal()).isEqualTo(100);
        assertThat(day2.getMiss()).isEqualTo(20);

        SearchTrendPoint last = points.get(6);
        assertThat(last.getTotal()).isEqualTo(50);
        assertThat(last.getMiss()).isEqualTo(5);

        // 其余天补 0（不是 null，也不是被跳过）
        assertThat(points.get(0).getTotal()).isZero();
        assertThat(points.get(0).getMiss()).isZero();
    }

    @Test
    @DisplayName("trend(days>0)：since 取自然日 0 点，保证区间是完整 N 天")
    void trendUsesNaturalDayStart() {
        when(searchLogMapper.trendByDay(any())).thenReturn(List.of());

        newService().trend(7);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(searchLogMapper).trendByDay(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(LocalDate.now().minusDays(6).atStartOfDay());
    }

    @Test
    @DisplayName("trend(days<=0)：按数据首尾日补齐，无数据时返回空列表")
    void trendAllTimeUsesDataRange() {
        LocalDate today = LocalDate.now();
        when(searchLogMapper.trendByDay(null)).thenReturn(List.of(
                new SearchTrendPoint(today.minusDays(2).toString(), 10, 1),
                new SearchTrendPoint(today.toString(), 30, 3)));

        List<SearchTrendPoint> points = newService().trend(0);

        assertThat(points).hasSize(3);
        assertThat(points.get(1).getTotal()).isZero();
        assertThat(points.get(2).getTotal()).isEqualTo(30);
    }

    @Test
    @DisplayName("trend(days<=0)：无任何日志时返回空列表，不臆造日期")
    void trendAllTimeEmpty() {
        when(searchLogMapper.trendByDay(null)).thenReturn(List.of());

        assertThat(newService().trend(0)).isEmpty();
    }
}
