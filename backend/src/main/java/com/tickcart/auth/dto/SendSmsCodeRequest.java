package com.tickcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

    @Schema(description = "场景 1注册 2登录 3重置密码", example = "1")
    @NotBlank(message = "场景不能为空")
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
