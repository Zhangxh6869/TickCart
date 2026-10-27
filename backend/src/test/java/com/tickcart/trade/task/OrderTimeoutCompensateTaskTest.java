package com.tickcart.trade.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.service.OrderService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OrderTimeoutCompensateTask} 单测。
 *
 * <p>覆盖四件最容易写错的事：截止时间怎么算、扫到的单有没有逐个关、
 * 单条失败会不会连累其余、开关关掉后是否真的不碰数据库。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutCompensateTaskTest {

    private static final long TTL_MS = 900_000L;
    private static final long GRACE_MS = 60_000L;
    private static final int BATCH = 100;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderMapper orderMapper;

    /**
     * LambdaQueryWrapper 要把 {@code Order::getXxx} 解析成列名，依赖 MP 的 TableInfo；
     * 纯 Mockito 单测不走 Spring 启动流程，必须手动初始化一次，
     * 否则抛 {@code can not find lambda cache for this entity}（同 CartServiceTest 的写法）。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    private OrderTimeoutCompensateTask newTask(boolean enabled) {
        return new OrderTimeoutCompensateTask(orderService, orderMapper, TTL_MS, GRACE_MS, BATCH, enabled);
    }

    private Order orderOf(String orderNo) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        return order;
    }

    /**
     * 取 wrapper 的条件参数值。
     *
     * <p>{@code getParamNameValuePairs()} 定义在 {@code AbstractWrapper} 上，
     * 而 Mapper 方法签名收的是 {@code Wrapper}，所以要转一次。
     * 另外必须先调用 {@code getSqlSegment()}，MP 才会把条件实体化进参数表。</p>
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> paramValues(Wrapper<Order> wrapper) {
        wrapper.getSqlSegment();
        return ((AbstractWrapper<Order, ?, ?>) wrapper).getParamNameValuePairs();
    }

    @Test
    @DisplayName("截止时间 = now - 支付时限 - 宽限期，且只查待付款")
    void usesTtlPlusGraceAsCutoff() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 12, 0, 0);
        when(orderMapper.selectList(any())).thenReturn(List.of());

        newTask(true).compensateAt(now);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Order>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectList(captor.capture());
        Wrapper<Order> wrapper = captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("status", "created_at");

        LocalDateTime expectedCutoff = now.minus(Duration.ofMillis(TTL_MS + GRACE_MS));
        assertThat(paramValues(wrapper).values()).contains(expectedCutoff);
    }

    @Test
    @DisplayName("扫到多单时逐个关单")
    void closesEveryCandidate() {
        when(orderMapper.selectList(any()))
                .thenReturn(List.of(orderOf("A1"), orderOf("A2"), orderOf("A3")));

        newTask(true).compensateAt(LocalDateTime.now());

        verify(orderService).closeTimeout("A1");
        verify(orderService).closeTimeout("A2");
        verify(orderService).closeTimeout("A3");
    }

    @Test
    @DisplayName("没有候选订单时不调用关单")
    void doesNothingWhenNoCandidate() {
        when(orderMapper.selectList(any())).thenReturn(List.of());

        newTask(true).compensateAt(LocalDateTime.now());

        verify(orderService, never()).closeTimeout(any());
    }

    @Test
    @DisplayName("单条关单失败不影响其余订单，且不向外抛")
    void singleFailureDoesNotAbortOthers() {
        when(orderMapper.selectList(any())).thenReturn(List.of(orderOf("B1"), orderOf("B2")));
        doThrow(new RuntimeException("db down")).when(orderService).closeTimeout("B1");

        assertThatCode(() -> newTask(true).compensateAt(LocalDateTime.now()))
                .doesNotThrowAnyException();

        verify(orderService).closeTimeout("B2");
    }

    @Test
    @DisplayName("查询本身失败时只记日志，不向外抛")
    void queryFailureIsSwallowed() {
        when(orderMapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> newTask(true).compensateAt(LocalDateTime.now()))
                .doesNotThrowAnyException();

        verify(orderService, never()).closeTimeout(any());
    }

    @Test
    @DisplayName("开关关闭时完全不碰数据库")
    void disabledTaskTouchesNothing() {
        newTask(false).compensateAt(LocalDateTime.now());

        verifyNoInteractions(orderMapper);
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("批大小 <=0 视为无效配置，跳过扫描（避免 LIMIT 0 静默不干活）")
    void invalidBatchSizeSkips() {
        OrderTimeoutCompensateTask task =
                new OrderTimeoutCompensateTask(orderService, orderMapper, TTL_MS, GRACE_MS, 0, true);

        task.compensateAt(LocalDateTime.now());

        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("扫描上限写进 SQL：LIMIT 与配置一致")
    void appliesBatchLimit() {
        when(orderMapper.selectList(any())).thenReturn(List.of());

        newTask(true).compensateAt(LocalDateTime.now());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Order>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper, times(1)).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("LIMIT " + BATCH);
    }
}
