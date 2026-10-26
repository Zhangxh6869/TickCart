package com.tickcart.seckill.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动概览（后台页头 KPI 与状态 chips 的数据源）。
 *
 * <p><b>刻意不提供「今日」类指标</b>：演示库的活动可能停在几天前，
 * 任何以「今天」为窗口的计数都会是 0，让人误以为页面坏了。这里只给
 * 与时间窗口无关的存量口径。</p>
 */
@Schema(description = "秒杀活动概览")
public class SeckillOverviewVO {

    @Schema(description = "活动总数")
    private long total;

    @Schema(description = "进行中")
    private long ongoing;

    @Schema(description = "即将开始")
    private long pending;

    @Schema(description = "已结束")
    private long ended;

    @Schema(description = "已下线")
    private long disabled;

    /**
     * 已结束但仍为上线状态的数量。
     *
     * <p>它不影响任何线上行为（前台按 {@code end_time > now} 过滤），
     * 只表示后台数据没收尾 —— 页面用一条告警条提示，不自动改 enabled。</p>
     */
    private long endedButEnabled;

    @Schema(description = "秒杀成交额（仅已支付订单）")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Schema(description = "秒杀成交单数（仅已支付订单）")
    private long paidOrderCount;

    @Schema(description = "已售件数（活动 sold_count 之和，含待付款）")
    private long soldQuantity;

    @Schema(description = "最近一场即将开始的开始时间；没有则为 null")
    private LocalDateTime nextStartTime;

    @Schema(description = "进行中的活动里最早结束的时间；没有则为 null")
    private LocalDateTime ongoingEndTime;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getOngoing() {
        return ongoing;
    }

    public void setOngoing(long ongoing) {
        this.ongoing = ongoing;
    }

    public long getPending() {
        return pending;
    }

    public void setPending(long pending) {
        this.pending = pending;
    }

    public long getEnded() {
        return ended;
    }

    public void setEnded(long ended) {
        this.ended = ended;
    }

    public long getDisabled() {
        return disabled;
    }

    public void setDisabled(long disabled) {
        this.disabled = disabled;
    }

    public long getEndedButEnabled() {
        return endedButEnabled;
    }

    public void setEndedButEnabled(long endedButEnabled) {
        this.endedButEnabled = endedButEnabled;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public long getPaidOrderCount() {
        return paidOrderCount;
    }

    public void setPaidOrderCount(long paidOrderCount) {
        this.paidOrderCount = paidOrderCount;
    }

    public long getSoldQuantity() {
        return soldQuantity;
    }

    public void setSoldQuantity(long soldQuantity) {
        this.soldQuantity = soldQuantity;
    }

    public LocalDateTime getNextStartTime() {
        return nextStartTime;
    }

    public void setNextStartTime(LocalDateTime nextStartTime) {
        this.nextStartTime = nextStartTime;
    }

    public LocalDateTime getOngoingEndTime() {
        return ongoingEndTime;
    }

    public void setOngoingEndTime(LocalDateTime ongoingEndTime) {
        this.ongoingEndTime = ongoingEndTime;
    }
}
