package com.tickcart.auth.service;

import com.tickcart.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SmsService} 单元测试。
 *
 * <p>重点守住一条：验证码必须是随机的。曾经 Mock 模式固定返回配置里的明文，
 * 任何人看配置就能登录/重置任意账号 —— 那是安全漏洞，不是联调便利。</p>
 */
@DisplayName("短信服务")
class SmsServiceTest {

    @Test
    @DisplayName("验证码为 6 位纯数字，首位非 0")
    void generateCodeIsSixDigits() {
        SmsService service = new SmsService();

        for (int i = 0; i < 50; i++) {
            assertThat(service.generateCode()).matches("[1-9]\\d{5}");
        }
    }

    @Test
    @DisplayName("连续生成的验证码各不相同（不是固定码）")
    void generateCodeIsRandom() {
        SmsService service = new SmsService();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            codes.add(service.generateCode());
        }
        Set<String> distinct = new HashSet<>(codes);

        // 阈值取 15 而不是 20：真随机下 20 次几乎不可能撞到 5 次，
        // 但固定码必然是 1 —— 既不会误报，也拦得住回归。
        assertThat(distinct).hasSizeGreaterThan(15);
    }

    @Test
    @DisplayName("Mock 模式默认开启（本地不接网关也能联调）")
    void mockEnabledByDefault() {
        assertThat(new SmsService().isMockEnabled()).isTrue();
    }

    @Test
    @DisplayName("关闭 Mock 且未接真实网关时发送失败，而不是静默吞掉")
    void sendFailsWhenMockDisabled() {
        SmsService service = new SmsService();
        service.setMockEnabled(false);

        assertThatThrownBy(() -> service.send("13700000000", "123456"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("短信网关未接入");
    }
}
