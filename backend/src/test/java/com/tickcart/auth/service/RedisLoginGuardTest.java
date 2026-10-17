package com.tickcart.auth.service;

import com.tickcart.common.config.LoginGuardProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶梯锁定规则单元测试。
 *
 * <p>只测纯计算部分（{@code lockSecondsForRound}）—— Redis 读写属于集成行为，
 * 放在接口实测里验证，用单测覆盖它只会变成「测 Mockito」。</p>
 */
@DisplayName("登录锁定阶梯")
class RedisLoginGuardTest {

    private final LoginGuardProperties props = new LoginGuardProperties();

    @Test
    @DisplayName("首轮按基础时长锁定")
    void firstRoundUsesBaseDuration() {
        assertThat(RedisLoginGuard.lockSecondsForRound(1, props)).isEqualTo(15 * 60L);
    }

    @Test
    @DisplayName("第二轮升级为 1 小时")
    void secondRoundEscalates() {
        assertThat(RedisLoginGuard.lockSecondsForRound(2, props)).isEqualTo(60 * 60L);
    }

    @Test
    @DisplayName("第三轮及以后封顶 24 小时")
    void thirdRoundHitsCeiling() {
        assertThat(RedisLoginGuard.lockSecondsForRound(3, props)).isEqualTo(1440 * 60L);
        assertThat(RedisLoginGuard.lockSecondsForRound(9, props)).isEqualTo(1440 * 60L);
    }

    @Test
    @DisplayName("阈值可配置：改配置即改锁定时长")
    void durationFollowsConfiguration() {
        props.setLockMinutes(1);
        props.setEscalateLockMinutes(5);
        props.setMaxLockMinutes(30);

        assertThat(RedisLoginGuard.lockSecondsForRound(1, props)).isEqualTo(60L);
        assertThat(RedisLoginGuard.lockSecondsForRound(2, props)).isEqualTo(300L);
        assertThat(RedisLoginGuard.lockSecondsForRound(3, props)).isEqualTo(1800L);
    }
}
