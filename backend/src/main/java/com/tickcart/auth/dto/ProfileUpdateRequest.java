package com.tickcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 自助修改资料请求：仅允许修改用户名与性别。
 *
 * <p>手机号是登录账号、role/status 是安全字段，均不在此暴露；
 * 即便请求体里塞了这些字段，{@code AuthService} 也只读取本类的两个属性。</p>
 */
@Schema(description = "自助修改资料")
public class ProfileUpdateRequest {

    @Schema(description = "用户名", example = "alice")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需在 4~20 位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;

    @Schema(description = "性别 0未知 1男 2女")
    @NotNull(message = "性别不能为空")
    @Min(0)
    @Max(2)
    private Integer gender;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }
}
