package com.tickcart.seckill.enums;

import com.tickcart.seckill.entity.SeckillActivity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SeckillActivityStatus} 状态推导测试。
 *
 * <p>这是 B1 里唯一「纯函数」的核心逻辑，边界必须逐个钉死 ——
 * 状态推导错一天，前台就会把进行中的活动显示成已结束。</p>
 */
@DisplayName("秒杀活动状态推导")
class SeckillActivityStatusTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Test
    @DisplayName("已下线优先于时间：即使正在时间窗内也是 DISABLED")
    void disabledWins() {
        SeckillActivity activity = activity(0);

        assertThat(SeckillActivityStatus.of(activity, START.plusMinutes(30)))
                .isEqualTo(SeckillActivityStatus.DISABLED);
    }

    @Test
    @DisplayName("开始前是即将开始")
    void pendingBeforeStart() {
        assertThat(SeckillActivityStatus.of(activity(1), START.minusSeconds(1)))
                .isEqualTo(SeckillActivityStatus.PENDING);
    }

    @Test
    @DisplayName("到开始时刻立即进行中（边界取等号）")
    void ongoingAtStart() {
        assertThat(SeckillActivityStatus.of(activity(1), START))
                .isEqualTo(SeckillActivityStatus.ONGOING);
    }

    @Test
    @DisplayName("结束前一刻仍是进行中")
    void ongoingBeforeEnd() {
        assertThat(SeckillActivityStatus.of(activity(1), END.minusSeconds(1)))
                .isEqualTo(SeckillActivityStatus.ONGOING);
    }

    @Test
    @DisplayName("到结束时刻立即已结束（边界取等号）")
    void endedAtEnd() {
        assertThat(SeckillActivityStatus.of(activity(1), END))
                .isEqualTo(SeckillActivityStatus.ENDED);
    }

    @Test
    @DisplayName("isStarted 用于判断「已开始过」，PENDING 不算")
    void isStarted() {
        assertThat(SeckillActivityStatus.PENDING.isStarted()).isFalse();
        assertThat(SeckillActivityStatus.ONGOING.isStarted()).isTrue();
        assertThat(SeckillActivityStatus.ENDED.isStarted()).isTrue();
        assertThat(SeckillActivityStatus.DISABLED.isStarted()).isFalse();
    }

    private SeckillActivity activity(int enabled) {
        SeckillActivity activity = new SeckillActivity();
        activity.setEnabled(enabled);
        activity.setStartTime(START);
        activity.setEndTime(END);
        return activity;
    }
}
