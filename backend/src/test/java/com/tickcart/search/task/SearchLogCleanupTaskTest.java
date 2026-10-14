package com.tickcart.search.task;

import com.tickcart.search.mapper.SearchLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SearchLogCleanupTask} 单测：保留窗口计算、<=0 不清理、失败不抛出。
 */
@ExtendWith(MockitoExtension.class)
class SearchLogCleanupTaskTest {

    @Mock
    private SearchLogMapper searchLogMapper;

    @Test
    @DisplayName("按保留天数删除：cutoff = 今天 - N 天的 0 点")
    void deletesBeforeCutoff() {
        SearchLogCleanupTask task = new SearchLogCleanupTask(searchLogMapper, 90);
        when(searchLogMapper.deleteBefore(any())).thenReturn(5);

        task.cleanExpiredLogs();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(searchLogMapper).deleteBefore(captor.capture());
        assertThat(captor.getValue()).isEqualTo(LocalDate.now().minusDays(90).atStartOfDay());
    }

    @Test
    @DisplayName("保留天数 <=0 时跳过清理（不调用删除）")
    void skipsWhenRetentionNotPositive() {
        SearchLogCleanupTask task = new SearchLogCleanupTask(searchLogMapper, 0);

        task.cleanExpiredLogs();

        verify(searchLogMapper, never()).deleteBefore(any());
    }

    @Test
    @DisplayName("删除抛异常时不向外抛（不影响主流程）")
    void swallowsMapperError() {
        SearchLogCleanupTask task = new SearchLogCleanupTask(searchLogMapper, 30);
        when(searchLogMapper.deleteBefore(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(task::cleanExpiredLogs).doesNotThrowAnyException();
    }
}
