package com.tickcart.search.buffer;

import com.tickcart.search.entity.SearchLog;
import com.tickcart.search.mapper.SearchLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SearchLogBuffer} 单元测试。
 *
 * <p>覆盖：批量落库、队列有界丢弃、写失败不抛、空队列不发 SQL。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("搜索日志缓冲")
class SearchLogBufferTest {

    private static final int CAPACITY = 3;
    private static final int BATCH_SIZE = 2;

    @Mock
    private SearchLogMapper searchLogMapper;

    private SearchLogBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new SearchLogBuffer(searchLogMapper, CAPACITY, BATCH_SIZE);
    }

    @Test
    @DisplayName("按批次落库：满一批写一批，剩余留到下次")
    void flushInBatches() {
        buffer.offer(log("a"));
        buffer.offer(log("b"));
        buffer.offer(log("c"));

        buffer.flush();
        buffer.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SearchLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(searchLogMapper, times(2)).insertBatch(captor.capture());
        assertThat(captor.getAllValues().get(0)).hasSize(BATCH_SIZE);
        assertThat(captor.getAllValues().get(1)).hasSize(1);
    }

    @Test
    @DisplayName("队列有界：超出容量直接丢弃，不阻塞、不涨内存")
    void dropsWhenFull() {
        buffer.offer(log("a"));
        buffer.offer(log("b"));
        buffer.offer(log("c"));
        buffer.offer(log("d"));   // 超出容量 3，应被丢弃

        buffer.flush();
        buffer.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SearchLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(searchLogMapper, times(2)).insertBatch(captor.capture());
        int total = captor.getAllValues().stream().mapToInt(List::size).sum();
        assertThat(total).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("批量写入失败：只吞掉异常，不影响调用方")
    void insertFailureSwallowed() {
        when(searchLogMapper.insertBatch(anyList()))
                .thenThrow(new RuntimeException("db down"));
        buffer.offer(log("a"));

        assertThatCode(() -> buffer.flush()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("队列为空：不产生任何 SQL")
    void emptyQueueSkipsInsert() {
        buffer.flush();

        verify(searchLogMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("停机刷盘：把剩余日志写完")
    void shutdownFlushesRemaining() {
        buffer.offer(log("a"));
        buffer.offer(log("b"));

        buffer.shutdown();

        verify(searchLogMapper).insertBatch(anyList());
    }

    private SearchLog log(String keyword) {
        SearchLog entry = new SearchLog();
        entry.setKeyword(keyword);
        entry.setResultCount(1);
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }
}
