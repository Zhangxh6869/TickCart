package com.tickcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 账密登录请求。
 *
 * <p><b>用户名与注册口径完全一致</b>（4~20 位字母数字下划线）：注册侧只允许这个范围，
 * 登录侧放宽到 1~50 位没有意义 —— 那些输入注定查不到账号，却仍要走一次查库 + BCrypt，
 * 白白消耗资源。前端也复用同一套规则，避免「注册时说不能填 @、登录时却接受」的困惑。</p>
 *
 * <p><b>密码只对齐长度上限，不校验强度</b>：注册要求 ≥8 位且字母+数字，但登录侧照搬会把
 * 历史账号挡在门外（演示账号 demo/123456 是 6 位纯数字）。登录是「验证已有密码」，
 * 规则由注册时决定，这里的作用只是防御超长输入。</p>
 */
@Schema(description = "账密登录请求")
public class LoginRequest {

    @Schema(description = "用户名", example = "demo")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需在 4~20 位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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
