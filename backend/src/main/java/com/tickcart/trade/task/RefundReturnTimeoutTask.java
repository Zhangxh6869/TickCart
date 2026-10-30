package com.tickcart.trade.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tickcart.trade.entity.RefundRecord;
import com.tickcart.trade.enums.RefundStatus;
import com.tickcart.trade.mapper.RefundRecordMapper;
import com.tickcart.trade.service.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退货超时自动关闭：用户同意退货后一直不寄货，到期关闭本次售后。
 *
 * <p><b>不关会怎样</b>：「待寄回」不是终态，{@code refund_record.active_key} 一直占着
 * 订单号，订单停在「退款中」—— 既不能发货也不能退款，用户还没法再申请一次。
 * 在此之前唯一的出口是商家手动「关闭退货」（{@code reject}），也就是把
 * <b>一个本该自动发生的动作压在人工身上</b>；商家不处理，单子就永久卡住。</p>
 *
 * <p><b>为什么只扫「待寄回」，不扫「待收货」</b>：这两段的「该不该关」判据不同。</p>
 * <ul>
 *   <li><b>待寄回</b>：货还在用户手上，超时 = 用户放弃了。判据是纯粹的时间，
 *       本地就能定，所以自动化。</li>
 *   <li><b>待收货</b>：用户已经填了单号，货在<b>路上</b>。到底「物流慢」还是「单号是假的」，
 *       只有查物流轨迹才知道 —— 而本项目不做物流对接（设计文档 §14.8 限制 4）。
 *       强行按时间关，会把真在路上的货判死：货退回来了、钱却已经不给退了。
 *       这一段继续留给商家按实际情况处理。</li>
 * </ul>
 *
 * <p><b>幂等与并发</b>：本任务不做任何状态判断，全部交给
 * {@link RefundService#closeReturnTimeout} —— 它是 {@code @Transactional} + 条件 UPDATE
 * （{@code WHERE status = 待寄回}）。用户恰好在这一刻提交物流单号、或商家恰好手动关闭，
 * 只有一个能改成功，另一个安全跳过。因此重复扫描、多实例并发都不会重复回退订单。</p>
 *
 * <p><b>为什么不加分布式锁</b>：与 {@code OrderTimeoutCompensateTask} 同理 ——
 * 幂等已由条件 UPDATE 保证，多实例各扫一遍的代价只是几次多余的 SELECT；
 * 反过来引入 Redis 互斥，会让这个兜底动作多一个可能失效的依赖。</p>
 *
 * <p><b>时间从哪算</b>：{@code audit_time}（商家点「同意退货」的时刻），不是 {@code created_at}
 * （用户提交申请的时刻）。用户申请后商家可能压了几天才审核，那几天不该算在用户头上。
 * 存量数据若 {@code audit_time} 为空（正常流程不会），退回用 {@code updated_at} 兜底 ——
 * 否则这类行永远满足不了时间条件，等于换个方式继续卡住。</p>
 */
@Component
public class RefundReturnTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(RefundReturnTimeoutTask.class);

    private final RefundService refundService;
    private final RefundRecordMapper refundRecordMapper;

    /** 超时时长（小时）：同意退货后多久没寄出就自动关闭 */
    private final long timeoutHours;
    /** 单次扫描上限：避免一次捞出过多退款单长时间占用连接 */
    private final int batchSize;
    /** 开关：排障时可临时关掉，避免自动关闭干扰观察 */
    private final boolean enabled;

    public RefundReturnTimeoutTask(RefundService refundService,
                                   RefundRecordMapper refundRecordMapper,
                                   @Value("${tickcart.refund.return-timeout-hours:168}") long timeoutHours,
                                   @Value("${tickcart.refund.return-timeout-batch-size:100}") int batchSize,
                                   @Value("${tickcart.refund.return-timeout-enabled:true}") boolean enabled) {
        this.refundService = refundService;
        this.refundRecordMapper = refundRecordMapper;
        this.timeoutHours = timeoutHours;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    /**
     * 定时扫描并关闭超时未寄回的退货单。
     *
     * <p>触发间隔见 {@code tickcart.refund.return-timeout-scan-interval-ms}（默认 10 分钟）。
     * 真正的逻辑在 {@link #closeExpiredAt(LocalDateTime)}，本方法只提供「现在」。</p>
     */
    @Scheduled(fixedDelayString = "${tickcart.refund.return-timeout-scan-interval-ms:600000}")
    public void closeExpired() {
        closeExpiredAt(LocalDateTime.now());
    }

    /**
     * 以给定时间为「现在」执行一次扫描，便于单测注入固定时钟。
     *
     * <p>失败一律不外抛：这是兜底动作，不能因为一条坏数据让整个任务停摆；
     * 没关掉的单会在下一轮扫描里被重新捞到。</p>
     *
     * @param now 当前时间
     */
    void closeExpiredAt(LocalDateTime now) {
        if (!enabled) {
            log.debug("退货超时自动关闭已关闭（tickcart.refund.return-timeout-enabled=false），跳过");
            return;
        }
        if (batchSize <= 0) {
            log.warn("退货超时自动关闭的批大小配置为 {}（<=0 视为无效），跳过本次扫描", batchSize);
            return;
        }
        if (timeoutHours <= 0) {
            log.warn("退货超时时长配置为 {} 小时（<=0 视为无效），跳过本次扫描", timeoutHours);
            return;
        }

        LocalDateTime cutoff = now.minus(Duration.ofHours(timeoutHours));
        List<RefundRecord> candidates;
        try {
            candidates = refundRecordMapper.selectList(new LambdaQueryWrapper<RefundRecord>()
                    .select(RefundRecord::getRefundNo)
                    .eq(RefundRecord::getStatus, RefundStatus.WAIT_RETURN.getCode())
                    // audit_time 为空时退回用 updated_at：AND 优先级高于 OR，
                    // 生成的是 (audit_time < ? OR (audit_time IS NULL AND updated_at < ?))
                    .and(w -> w.lt(RefundRecord::getAuditTime, cutoff)
                            .or()
                            .isNull(RefundRecord::getAuditTime)
                            .lt(RefundRecord::getUpdatedAt, cutoff))
                    .orderByAsc(RefundRecord::getId)
                    .last("LIMIT " + batchSize));
        } catch (Exception e) {
            log.error("退货超时自动关闭：查询待关闭退款单失败（不影响主流程，下次任务会重试）", e);
            return;
        }
        if (candidates.isEmpty()) {
            return;
        }

        int closed = 0;
        int failed = 0;
        for (RefundRecord record : candidates) {
            try {
                // 幂等：用户刚寄出、刚撤销、商家刚手动关闭，这里都会安全返回 false
                if (refundService.closeReturnTimeout(record.getRefundNo())) {
                    closed++;
                }
            } catch (Exception e) {
                failed++;
                log.error("退货超时自动关闭：关闭失败，留给下一轮重试 refundNo={}",
                        record.getRefundNo(), e);
            }
        }
        log.info("退货超时自动关闭完成：扫描到 {} 单（同意时间早于 {}），已关闭 {} 单，失败 {} 单",
                candidates.size(), cutoff, closed, failed);
    }
}
