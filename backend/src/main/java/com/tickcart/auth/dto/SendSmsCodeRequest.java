package com.tickcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 发送短信验证码请求。
 */
@Schema(description = "发送短信验证码请求")
public class SendSmsCodeRequest {

    @Schema(description = "手机号", example = "13700000000")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    // ★ 必须是 @NotNull 不是 @NotBlank —— @NotBlank 只适用于 CharSequence，
    //   标在 Integer 上会让 Hibernate Validator 抛 UnexpectedTypeException（HV000030），
    //   接口直接 500（踩过：本接口曾对所有场景都返回 500，注册/登录的「获取验证码」全不可用）。
    //   取值范围（1~3）由 AuthService.sendSmsCode 校验。
    @Schema(description = "场景 1注册 2登录 3重置密码", example = "1")
    @NotNull(message = "场景不能为空")
    private Integer scene;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getScene() {
        return scene;
    }

    public void setScene(Integer scene) {
        this.scene = scene;
    }
}
