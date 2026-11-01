package com.tickcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 账密登录请求。
 *
 * <p><b>账号统一为手机号</b>：登录不接受用户名。用户名只是注册时起的展示名与唯一标识，
 * 一旦允许它同时作为登录凭据，就会出现「一个账号两套入口」—— 改手机号或改用户名时，
 * 旧凭据是否还有效会变成难以回答的问题，登录失败计数也要按两套 key 各记一份。</p>
 *
 * <p>验证码登录见 {@link SmsLoginRequest}：两种方式共用同一个手机号，
 * 区别只在第二项是密码还是验证码。</p>
 *
 * <p><b>密码只对齐长度上限，不校验强度</b>：注册要求 ≥8 位且字母+数字，但登录侧照搬会把
 * 历史账号挡在门外（演示账号 13900000000 的密码是 6 位纯数字）。登录是「验证已有密码」，
 * 规则由注册时决定，这里的作用只是防御超长输入。</p>
 */
@Schema(description = "账密登录请求")
public class LoginRequest {

    @Schema(description = "手机号（登录账号）", example = "13900000000")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 32, message = "密码长度需在 1~32 位")
    @Pattern(regexp = "^\\S+$", message = "密码不能包含空白字符")
    private String password;

    @Schema(description = "图形验证码标识（仅需安全验证时回传）")
    @Size(max = 64, message = "验证码标识不合法")
    private String captchaId;

    @Schema(description = "图形验证码内容（仅需安全验证时回传）")
    @Size(max = 8, message = "验证码内容不合法")
    private String captchaCode;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public void setCaptchaCode(String captchaCode) {
        this.captchaCode = captchaCode;
    }
}
