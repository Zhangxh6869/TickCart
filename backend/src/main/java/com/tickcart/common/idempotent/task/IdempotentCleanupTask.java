package com.tickcart.common.idempotent.task;

import com.tickcart.common.idempotent.mapper.IdempotentRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 幂等记录清理任务。
 *
 * <p>{@code idempotent_record} 每笔带幂等键的下单写一行、只增不删，长期会随下单量线性增长。
 * 这里按 {@code expire_at}（= 写入时刻 + {@code tickcart.idempotent.retention-hours}）
 * 删除过期记录。保留期内重复提交仍能命中，超出保留期的重放会被当作新请求。</p>
 *
 * <p>清理是运维动作：失败只记日志、不抛出，绝不影响任何业务请求。</p>
 */
@Component
public class IdempotentCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(IdempotentCleanupTask.class);

    private final IdempotentRecordMapper idempotentRecordMapper;

    public IdempotentCleanupTask(IdempotentRecordMapper idempotentRecordMapper) {
        this.idempotentRecordMapper = idempotentRecordMapper;
    }

    /**
     * 删除已过期的幂等记录。
     *
     * <p>触发时间见 {@code tickcart.idempotent.cleanup-cron}（默认每天 04:30，
     * 与 03:00 的搜索日志清理错开）。</p>
     */
    @Scheduled(cron = "${tickcart.idempotent.cleanup-cron:0 30 4 * * ?}")
    public void cleanExpiredRecords() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int deleted = idempotentRecordMapper.deleteExpired(now);
            log.info("idempotent_record 清理完成：删除 {} 条（早于 {} 的记录）", deleted, now);
        } catch (Exception e) {
            log.error("idempotent_record 清理失败（不影响主流程，下次定时任务会重试）", e);
        }
    }
}
