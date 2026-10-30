package com.tickcart.trade.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.trade.entity.RefundRecord;
import com.tickcart.trade.enums.RefundStatus;
import com.tickcart.trade.mapper.RefundRecordMapper;
import com.tickcart.trade.service.RefundService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RefundReturnTimeoutTask} 单测。
 *
 * <p>覆盖五件最容易写错的事：截止时间怎么算、只扫「待寄回」、扫到的单有没有逐个关、
 * 单条失败会不会连累其余、开关关掉后是否真的不碰数据库。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("退货超时自动关闭")
class RefundReturnTimeoutTaskTest {

    private static final long TIMEOUT_HOURS = 168L;
    private static final int BATCH = 100;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0, 0);

    @Mock
    private RefundService refundService;

    @Mock
    private RefundRecordMapper refundRecordMapper;

    /**
     * LambdaQueryWrapper 要把 {@code RefundRecord::getXxx} 解析成列名，依赖 MP 的 TableInfo；
     * 纯 Mockito 单测不走 Spring 启动流程，必须手动初始化一次，
     * 否则抛 {@code can not find lambda cache for this entity}。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), RefundRecord.class);
    }

    private RefundReturnTimeoutTask newTask(boolean enabled) {
        return new RefundReturnTimeoutTask(refundService, refundRecordMapper, TIMEOUT_HOURS, BATCH, enabled);
    }

    private RefundRecord refundOf(String refundNo) {
        RefundRecord record = new RefundRecord();
        record.setRefundNo(refundNo);
        return record;
    }

    /** 取 wrapper 的条件参数值；必须先调 getSqlSegment()，MP 才会把条件实体化进参数表。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> paramValues(Wrapper<RefundRecord> wrapper) {
        wrapper.getSqlSegment();
        return ((AbstractWrapper<RefundRecord, ?, ?>) wrapper).getParamNameValuePairs();
    }

    @Test
    @DisplayName("截止时间 = now - 超时时长，且只查「待寄回」")
    void usesTimeoutAsCutoffAndOnlyScansWaitReturn() {
        when(refundRecordMapper.selectList(any())).thenReturn(List.of());

        newTask(true).closeExpiredAt(NOW);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<RefundRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(refundRecordMapper).selectList(captor.capture());
        Wrapper<RefundRecord> wrapper = captor.getValue();

        LocalDateTime expectedCutoff = NOW.minus(Duration.ofHours(TIMEOUT_HOURS));
        Map<String, Object> params = paramValues(wrapper);
        assertThat(params.values()).contains(RefundStatus.WAIT_RETURN.getCode(), expectedCutoff);

        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("status");
        // ★ 时间锚点是 audit_time（同意退货的时刻），不是 created_at（用户提交申请的时刻）
        assertThat(sql).contains("audit_time");
        assertThat(sql).doesNotContain("created_at");
        // 防御分支：audit_time 为空时退回 updated_at，否则这类行永远关不掉
        assertThat(sql).contains("updated_at");
        assertThat(sql).contains("LIMIT " + BATCH);
    }

    @Test
    @DisplayName("扫到的退款单逐个关闭")
    void closesEachCandidate() {
        when(refundRecordMapper.selectList(any()))
                .thenReturn(List.of(refundOf("RF001"), refundOf("RF002")));
        when(refundService.closeReturnTimeout(any())).thenReturn(true);

        newTask(true).closeExpiredAt(NOW);

        verify(refundService).closeReturnTimeout("RF001");
        verify(refundService).closeReturnTimeout("RF002");
    }

    @Test
    @DisplayName("单条失败不连累其余，也不外抛")
    void oneFailureDoesNotStopTheRest() {
        when(refundRecordMapper.selectList(any()))
                .thenReturn(List.of(refundOf("RF001"), refundOf("RF002")));
        when(refundService.closeReturnTimeout("RF001")).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> newTask(true).closeExpiredAt(NOW)).doesNotThrowAnyException();

        verify(refundService).closeReturnTimeout("RF002");
    }

    @Test
    @DisplayName("查询失败只记日志，不抛异常（下一轮会重试）")
    void queryFailureIsSwallowed() {
        when(refundRecordMapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> newTask(true).closeExpiredAt(NOW)).doesNotThrowAnyException();

        verify(refundService, never()).closeReturnTimeout(any());
    }

    @Test
    @DisplayName("开关关掉后不碰数据库")
    void disabledTaskTouchesNothing() {
        newTask(false).closeExpiredAt(NOW);

        verifyNoInteractions(refundRecordMapper, refundService);
    }

    @Test
    @DisplayName("批大小配置非法时跳过本次扫描")
    void invalidBatchSizeSkipsScan() {
        RefundReturnTimeoutTask task =
                new RefundReturnTimeoutTask(refundService, refundRecordMapper, TIMEOUT_HOURS, 0, true);

        task.closeExpiredAt(NOW);

        verifyNoInteractions(refundRecordMapper, refundService);
    }

    @Test
    @DisplayName("超时时长配置非法时跳过本次扫描（不能把 0 当成「立刻全关」）")
    void invalidTimeoutSkipsScan() {
        RefundReturnTimeoutTask task =
                new RefundReturnTimeoutTask(refundService, refundRecordMapper, 0, BATCH, true);

        task.closeExpiredAt(NOW);

        verifyNoInteractions(refundRecordMapper, refundService);
    }

    @Test
    @DisplayName("没有超时单时不做任何关闭动作")
    void nothingToClose() {
        when(refundRecordMapper.selectList(any())).thenReturn(List.of());

        newTask(true).closeExpiredAt(NOW);

        verify(refundService, never()).closeReturnTimeout(any());
    }
}
