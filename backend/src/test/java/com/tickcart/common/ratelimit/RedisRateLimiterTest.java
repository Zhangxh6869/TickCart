package com.tickcart.common.ratelimit;

import com.tickcart.common.redis.RedisHealthGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisRateLimiter} 单元测试。
 *
 * <p>Lua 脚本本身需要真 Redis，这里只验证「脚本返回值 → 等待毫秒」的换算与 fail-open 降级，
 * 脚本语义由运行时验证覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis 令牌桶")
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    private RedisRateLimiter limiter;
    private RedisHealthGuard healthGuard;

    @BeforeEach
    void setUp() {
        // 熔断器保持关闭态，让用例聚焦限流换算；熔断行为单独有用例
        healthGuard = new RedisHealthGuard(true, 5, 30_000L, 5_000L);
        limiter = new RedisRateLimiter(redis, healthGuard);
    }

    @Test
    @DisplayName("脚本返回 0：放行")
    @SuppressWarnings("unchecked")
    void allowed() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(0L);

        assertThat(limiter.tryAcquire("search:ip:1.2.3.4", 10, 1)).isZero();
    }

    @Test
    @DisplayName("脚本返回等待毫秒：原样透出，供上层换算成秒")
    @SuppressWarnings("unchecked")
    void throttled() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(1500L);

        assertThat(limiter.tryAcquire("search:ip:1.2.3.4", 10, 1)).isEqualTo(1500L);
    }

    @Test
    @DisplayName("Redis 故障：fail-open 放行，不向上抛异常")
    @SuppressWarnings("unchecked")
    void redisFailureFailsOpen() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(limiter.tryAcquire("search:ip:1.2.3.4", 10, 1)).isZero();
    }

    @Test
    @DisplayName("非法阈值（0 或负数）按 1 处理，避免除零")
    @SuppressWarnings("unchecked")
    void illegalPermitsNormalized() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(0L);

        // 不抛异常即达标：容量/窗口被夹到 1，速率不会除零
        assertThat(limiter.tryAcquire("k", 0, 0)).isZero();
    }

    @Test
    @DisplayName("熔断期间：直接放行，且完全不调用 Redis（省掉整段超时等待）")
    @SuppressWarnings("unchecked")
    void circuitOpenSkipsRedis() {
        for (int i = 0; i < 5; i++) {
            healthGuard.onFailure();
        }

        assertThat(limiter.tryAcquire("search:ip:1.2.3.4", 10, 1)).isZero();

        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any(), any(), any());
    }
}
