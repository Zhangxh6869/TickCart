package com.tickcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 账密注册请求。
 *
 * <p><b>四项必填</b>：用户名、手机号、密码、短信验证码（场景 1）。
 * 改造前手机号可选、也不校验验证码，导致注册可以完全绕开手机号真实性 ——
 * 现在手机号就是登录账号，必须先在注册这一步验证它确实属于本人。</p>
 *
 * <p><b>不再收集昵称</b>：昵称落库时固定取用户名。少一个字段，也少一处「昵称和用户名对不上」
 * 的解释成本。数据库 {@code sys_user.nickname} 字段保留（前台个人中心、后台用户管理仍在展示）。</p>
 */
@Schema(description = "账密注册请求")
public class RegisterRequest {

    @Schema(description = "用户名", example = "alice")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需在 4~20 位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;

    @Schema(description = "手机号（登录账号）", example = "13700000000")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "密码", example = "abc12345")
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需在 8~32 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S+$", message = "密码需同时包含字母和数字")
    private String password;

    @Schema(description = "短信验证码（场景 1：注册）", example = "483920")
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
