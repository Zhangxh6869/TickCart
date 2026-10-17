package com.tickcart.auth.service;

import com.tickcart.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 密码策略单元测试。
 *
 * <p>长度与字符组合由 Bean Validation 负责，这里只覆盖本类的两条规则：
 * 弱口令字典、密码不得包含用户名。</p>
 */
@DisplayName("密码策略")
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("强密码通过")
    void acceptsStrongPassword() {
        assertThatCode(() -> policy.validatePassword("Kx7#mQ2v", "alice")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("命中弱口令字典则拒绝")
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> policy.validatePassword("123456", "alice"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过于简单");
    }

    @Test
    @DisplayName("弱口令不区分大小写")
    void rejectsWeakPasswordIgnoringCase() {
        assertThatThrownBy(() -> policy.validatePassword("PassWord123", "alice"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过于简单");
    }

    @Test
    @DisplayName("密码包含用户名则拒绝")
    void rejectsPasswordContainingUsername() {
        assertThatThrownBy(() -> policy.validatePassword("alice2024", "alice"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能包含用户名");
    }

    @Test
    @DisplayName("空密码不归本类管（交给 @NotBlank）")
    void skipsBlankPassword() {
        assertThatCode(() -> policy.validatePassword("", "alice")).doesNotThrowAnyException();
    }
}
