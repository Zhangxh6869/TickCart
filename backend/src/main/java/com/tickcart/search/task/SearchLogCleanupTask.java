package com.tickcart.search.task;

import com.tickcart.search.mapper.SearchLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * search_log 保留策略：定时删除过老的搜索日志。
 *
 * <p>{@code search_log} 每搜一次写一行、只增不删，长期会随搜索量线性增长
 * （占磁盘、拖慢统计查询）。这里按 {@code tickcart.search.log-retention-days}
 * 保留最近 N 天，早于「今天 - N 天」的记录在每天固定时间清理掉。</p>
 *
 * <p>清理是运维动作：失败只记日志、不抛出，绝不影响任何业务请求。</p>
 */
@Component
public class SearchLogCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(SearchLogCleanupTask.class);

    private final SearchLogMapper searchLogMapper;
    private final long retentionDays;

    public SearchLogCleanupTask(SearchLogMapper searchLogMapper,
                                @Value("${tickcart.search.log-retention-days:90}") long retentionDays) {
        this.searchLogMapper = searchLogMapper;
        this.retentionDays = retentionDays;
    }

    /**
     * 删除早于保留窗口的搜索日志。
     *
     * <p>触发时间见 {@code tickcart.search.log-cleanup-cron}（默认每天 03:00）。</p>
     */
    @Scheduled(cron = "${tickcart.search.log-cleanup-cron:0 0 3 * * ?}")
    public void cleanExpiredLogs() {
        if (retentionDays <= 0) {
            log.info("search_log 保留天数配置为 {}（<=0 表示不清理），跳过本次清理", retentionDays);
            return;
        }
        // 以「自然日」为界：保留今天在内的最近 N 天，更早的整日记录一律清掉
        LocalDateTime cutoff = LocalDate.now().minusDays(retentionDays).atStartOfDay();
        try {
            int deleted = searchLogMapper.deleteBefore(cutoff);
            log.info("search_log 清理完成：删除 {} 条（保留最近 {} 天，早于 {} 的记录）",
                    deleted, retentionDays, cutoff);
        } catch (Exception e) {
            log.error("search_log 清理失败（不影响主流程，下次定时任务会重试）", e);
        }
    }
}
