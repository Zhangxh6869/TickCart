package com.tickcart.common.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedisHealthGuard} 单测：三态转换与边界。
 *
 * <p>时钟是可控的 —— 熔断器的全部逻辑都在「时间 + 连续失败次数」上，
 * 用真实时钟只能靠 sleep 等 30 秒，既慢又不稳。</p>
 */
@DisplayName("Redis 熔断器")
class RedisHealthGuardTest {

    private static final int THRESHOLD = 5;
    private static final long OPEN_MS = 30_000L;
    private static final long LEASE_MS = 5_000L;

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private RedisHealthGuard guard;

    @BeforeEach
    void setUp() {
        guard = new RedisHealthGuard(true, THRESHOLD, OPEN_MS, LEASE_MS, now::get);
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            guard.onFailure();
        }
    }

    @Test
    @DisplayName("关闭态：一律放行")
    void closedAllowsEverything() {
        assertThat(guard.allow()).isTrue();
        assertThat(guard.isOpen()).isFalse();
    }

    @Test
    @DisplayName("失败未达阈值：仍放行（避免一次抖动就熔断）")
    void belowThresholdStillAllows() {
        failTimes(THRESHOLD - 1);

        assertThat(guard.isOpen()).isFalse();
        assertThat(guard.allow()).isTrue();
    }

    @Test
    @DisplayName("连续失败达到阈值：打开，后续请求一律拒绝")
    void reachingThresholdOpens() {
        failTimes(THRESHOLD);

        assertThat(guard.isOpen()).isTrue();
        assertThat(guard.allow()).isFalse();
    }

    @Test
    @DisplayName("中途成功会清零计数：失败 4 次 + 成功 + 失败 4 次，不应熔断")
    void successResetsCounter() {
        failTimes(THRESHOLD - 1);
        guard.onSuccess();
        failTimes(THRESHOLD - 1);

        assertThat(guard.isOpen()).isFalse();
        assertThat(guard.allow()).isTrue();
    }

    @Test
    @DisplayName("到期后半开：只放一个探针，其余继续拒绝")
    void halfOpenAllowsSingleProbe() {
        failTimes(THRESHOLD);
        now.addAndGet(OPEN_MS);

        assertThat(guard.allow()).as("第一个请求当探针").isTrue();
        assertThat(guard.allow()).as("探针在飞，其余拒绝").isFalse();
        assertThat(guard.allow()).isFalse();
    }

    @Test
    @DisplayName("探针成功：熔断关闭，恢复全放行")
    void probeSuccessCloses() {
        failTimes(THRESHOLD);
        now.addAndGet(OPEN_MS);
        assertThat(guard.allow()).isTrue();

        guard.onSuccess();

        assertThat(guard.isOpen()).isFalse();
        assertThat(guard.allow()).isTrue();
        assertThat(guard.allow()).isTrue();
    }

    @Test
    @DisplayName("探针失败：重新打开一个完整窗口，而不是回到关闭")
    void probeFailureReopens() {
        failTimes(THRESHOLD);
        now.addAndGet(OPEN_MS);
        assertThat(guard.allow()).isTrue();

        guard.onFailure();

        assertThat(guard.isOpen()).isTrue();
        assertThat(guard.allow()).isFalse();
        // 再等满一个窗口才允许下一次试探
        now.addAndGet(OPEN_MS);
        assertThat(guard.allow()).isTrue();
    }

    @Test
    @DisplayName("探针失联：租约到期后允许换一个探针（能自愈，不会永久卡死）")
    void probeLeaseExpiresAndRetries() {
        failTimes(THRESHOLD);
        now.addAndGet(OPEN_MS);
        assertThat(guard.allow()).as("探针 A").isTrue();
        assertThat(guard.allow()).as("探针 A 未回报").isFalse();

        // 探针 A 既不成功也不失败（请求在回报之前就终止了）
        now.addAndGet(LEASE_MS);

        assertThat(guard.allow()).as("租约到期后换探针 B").isTrue();
    }

    @Test
    @DisplayName("开关关闭：永不熔断，全部放行")
    void disabledNeverTrips() {
        RedisHealthGuard off = new RedisHealthGuard(false, THRESHOLD, OPEN_MS, LEASE_MS, now::get);

        for (int i = 0; i < THRESHOLD * 3; i++) {
            off.onFailure();
        }

        assertThat(off.isOpen()).isFalse();
        assertThat(off.allow()).isTrue();
    }
}
