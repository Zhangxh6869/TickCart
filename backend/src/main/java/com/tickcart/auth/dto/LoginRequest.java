package com.tickcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 账密登录请求。
 *
 * <p>注意这里只做「格式与长度」校验，<b>不做密码强度校验</b>：登录侧一旦要求复杂度，
 * 历史账号与演示账号（demo/123456）就登不进来。强度要求只加在注册侧。</p>
 *
 * <p>长度上限同时是防御：BCrypt 只取前 72 字节，超长输入纯属浪费 CPU；
 * 用户名上限与库字段（VARCHAR(50)）对齐，避免超长串打到数据库层才报错。</p>
 */
@Schema(description = "账密登录请求")
public class LoginRequest {

    @Schema(description = "用户名", example = "demo")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 1, max = 50, message = "用户名长度需在 1~50 位")
    @Pattern(regexp = "^[A-Za-z0-9_@.-]+$", message = "用户名只能包含字母、数字、下划线、@、点和横线")
    private String username;

    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 64, message = "密码长度需在 1~64 位")
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
