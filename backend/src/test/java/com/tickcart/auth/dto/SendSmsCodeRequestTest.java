package com.tickcart.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SendSmsCodeRequest} 的校验约束测试。
 *
 * <p>★ 回归用例：{@code scene} 是 {@code Integer}，曾误标 {@code @NotBlank}
 * （该注解只适用于 CharSequence），Hibernate Validator 找不到校验器会抛
 * {@code UnexpectedTypeException}（HV000030）→ 接口对**所有**场景都返回 500，
 * 注册与验证码登录的「获取验证码」按钮全部不可用。这里锁住它别再退回去。</p>
 */
@DisplayName("发送验证码请求")
class SendSmsCodeRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private SendSmsCodeRequest req(String phone, Integer scene) {
        SendSmsCodeRequest request = new SendSmsCodeRequest();
        request.setPhone(phone);
        request.setScene(scene);
        return request;
    }

    @Test
    @DisplayName("三种场景都能通过校验（不能抛 UnexpectedTypeException）")
    void everyScenePassesValidation() {
        assertThat(validator.validate(req("13900000000", 1))).isEmpty();
        assertThat(validator.validate(req("13900000000", 2))).isEmpty();
        assertThat(validator.validate(req("13900000000", 3))).isEmpty();
    }

    @Test
    @DisplayName("场景为空被拒")
    void nullSceneRejected() {
        assertThat(validator.validate(req("13900000000", null)))
                .anySatisfy(v -> assertThat(v.getMessage()).isEqualTo("场景不能为空"));
    }

    @Test
    @DisplayName("手机号格式不合法被拒")
    void invalidPhoneRejected() {
        assertThat(validator.validate(req("12345", 1)))
                .anySatisfy(v -> assertThat(v.getMessage()).isEqualTo("手机号格式不正确"));
    }
}
