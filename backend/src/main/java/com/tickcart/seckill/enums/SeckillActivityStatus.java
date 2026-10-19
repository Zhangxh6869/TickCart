package com.tickcart.seckill.enums;

import com.tickcart.seckill.entity.SeckillActivity;

import java.time.LocalDateTime;

/**
 * 秒杀活动状态。
 *
 * <p><b>状态不落库，按时间推导</b>：如果存一个 {@code status} 字段，就必须有定时任务在
 * {@code start_time} / {@code end_time} 到点时改它；任务漏跑、服务重启、时钟偏差都会导致
 * 「活动该开始了却还是未开始」—— 这是秒杀项目最经典的线上事故。时间推导永远准，
 * 不需要任何补偿机制。</p>
 *
 * <p>判定顺序固定：<b>先看 enabled，再看时间</b>。手动下线优先于时间。</p>
 */
public enum SeckillActivityStatus {

    /** 后台手动下线 */
    DISABLED(0, "已下线"),
    /** 已上线，但未到开始时间 */
    PENDING(1, "即将开始"),
    /** 正在进行 */
    ONGOING(2, "进行中"),
    /** 已过结束时间 */
    ENDED(3, "已结束");

    private final int code;
    private final String label;

    SeckillActivityStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 推导活动在给定时刻的状态。
     *
     * @param activity 活动（需含 enabled / startTime / endTime）
     * @param now      判定时刻
     */
    public static SeckillActivityStatus of(SeckillActivity activity, LocalDateTime now) {
        return of(activity.getEnabled(), activity.getStartTime(), activity.getEndTime(), now);
    }

    /**
     * 由字段直接推导，供 Redis 活动快照使用（快照里没有实体对象）。
     *
     * <p>两个入口共用同一段判断，避免「实体一套、快照一套」导致状态口径不一致。</p>
     */
    public static SeckillActivityStatus of(Integer enabled, LocalDateTime startTime,
                                           LocalDateTime endTime, LocalDateTime now) {
        if (!Integer.valueOf(1).equals(enabled)) {
            return DISABLED;
        }
        if (now.isBefore(startTime)) {
            return PENDING;
        }
        if (now.isBefore(endTime)) {
            return ONGOING;
        }
        return ENDED;
    }

    /** 是否已开始过（进行中或已结束）—— 用于「进行中禁止改价改库存」的判断。 */
    public boolean isStarted() {
        return this == ONGOING || this == ENDED;
    }
}
