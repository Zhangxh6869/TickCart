package com.tickcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 手机验证码注册请求。
 */
@Schema(description = "手机验证码注册请求")
public class SmsRegisterRequest {

    @Schema(description = "手机号", example = "13700000000")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "验证码", example = "123456")
    @NotBlank(message = "验证码不能为空")
    private String code;

    @Schema(description = "用户名（可选，不填默认用手机号）", example = "bob")
    @Pattern(regexp = "^$|^[a-zA-Z0-9_]{4,20}$", message = "用户名需为 4~20 位字母数字下划线")
    private String username;

    @Schema(description = "密码（可选）", example = "abc12345")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位")
    private String password;

    @Schema(description = "昵称（可选）", example = "鲍勃")
    @Size(max = 50, message = "昵称最长 50 字")
    private String nickname;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
