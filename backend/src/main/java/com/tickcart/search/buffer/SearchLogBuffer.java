package com.tickcart.search.buffer;

import com.tickcart.search.entity.SearchLog;
import com.tickcart.search.mapper.SearchLogMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 搜索日志写入缓冲：请求线程只入队，定时批量落库。
 *
 * <p><b>为什么</b>：原来每次搜索都在请求线程里同步 {@code insert} 一条 ——
 * ① 搜索接口的响应时间被写库拖住；② 热点词被搜爆时 {@code search_log} 成为写热点，
 * 且每条一次 DB 往返。改成批量后，100 次搜索只产生 1~2 条 INSERT。</p>
 *
 * <p><b>有界队列</b>：满了直接丢弃并计数，绝不阻塞搜索、也不让内存无限涨。
 * 搜索日志是统计素材，不是账本 —— 宁可丢，也不能影响主链路。</p>
 *
 * <p><b>代价</b>：进程被 kill -9 时最多丢失一个刷盘周期（默认 1 秒）的日志。
 * 正常停机由 {@link #shutdown()} 兜底刷完。</p>
 */
@Component
public class SearchLogBuffer {

    private static final Logger log = LoggerFactory.getLogger(SearchLogBuffer.class);

    private final SearchLogMapper searchLogMapper;
    private final int capacity;
    private final int batchSize;

    private final Queue<SearchLog> queue = new ConcurrentLinkedQueue<>();
    /** 近似计数：{@code ConcurrentLinkedQueue.size()} 是 O(n)，高频调用下不能用 */
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();

    public SearchLogBuffer(SearchLogMapper searchLogMapper,
                           @Value("${tickcart.search.log-buffer-capacity:10000}") int capacity,
                           @Value("${tickcart.search.log-flush-batch-size:200}") int batchSize) {
        this.searchLogMapper = searchLogMapper;
        this.capacity = Math.max(1, capacity);
        this.batchSize = Math.max(1, batchSize);
    }

    /** 入队；缓冲区满时丢弃并计数（每满 100 条打一次 WARN，避免刷屏）。 */
    public void offer(SearchLog entry) {
        if (entry == null) {
            return;
        }
        if (pending.get() >= capacity) {
            long total = dropped.incrementAndGet();
            if (total % 100 == 1) {
                log.warn("搜索日志缓冲区已满（容量 {}），累计丢弃 {} 条", capacity, total);
            }
            return;
        }
        queue.offer(entry);
        pending.incrementAndGet();
    }

    /**
     * 刷盘：最多取一个批次写入，剩下的留给下一个周期。
     *
     * <p>{@code synchronized} 是为了防止定时任务与停机刷盘并发操作队列。</p>
     */
    @Scheduled(fixedDelayString = "${tickcart.search.log-flush-interval-ms:1000}")
    public synchronized void flush() {
        List<SearchLog> batch = new ArrayList<>(batchSize);
        SearchLog entry;
        while (batch.size() < batchSize && (entry = queue.poll()) != null) {
            batch.add(entry);
            pending.decrementAndGet();
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            searchLogMapper.insertBatch(batch);
        } catch (Exception e) {
            // 不重投：故障时无限重试会把内存也撑爆，丢一个批次（≤1 秒）可以接受
            log.warn("批量写入搜索日志失败，丢弃 {} 条（不影响搜索）", batch.size(), e);
        }
    }

    /** 停机刷盘：把剩余日志尽量写完，避免优雅停机丢数据。 */
    @PreDestroy
    public void shutdown() {
        while (!queue.isEmpty()) {
            flush();
        }
        log.info("搜索日志缓冲区已刷盘，累计丢弃 {} 条", dropped.get());
    }
}
