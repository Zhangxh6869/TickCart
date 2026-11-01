package com.tickcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 手机验证码注册请求。
 *
 * <p>改造后与 {@link RegisterRequest} 完全等价：四项必填、同样校验场景 1 的验证码、
 * 昵称同样取用户名。保留两个入口只为兼容既有调用方（E2E、压测脚本），
 * 新代码请统一使用 {@code /api/auth/register}。</p>
 *
 * <p><b>用户名与密码由「可选」改为「必填」</b>：原设计允许只凭手机号建号，此时用户名取手机号、
 * 密码写随机 BCrypt 串（该账号只能短信登录）。既然手机号已成为唯一登录账号，
 * 「有没有密码」不再是注册形态的分水岭 —— 注册就是一次完整开户。</p>
 */
@Schema(description = "手机验证码注册请求")
public class SmsRegisterRequest {

    @Schema(description = "用户名", example = "bob")
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

    @Schema(description = "验证码", example = "123456")
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
