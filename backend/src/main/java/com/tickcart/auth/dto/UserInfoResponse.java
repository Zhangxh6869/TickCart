package com.tickcart.auth.dto;

import com.tickcart.auth.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户信息响应（对外，不含密码等敏感字段）。
 */
@Schema(description = "用户信息")
public class UserInfoResponse {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "手机号（脱敏）")
    private String phone;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "性别 0未知 1男 2女")
    private Integer gender;

    @Schema(description = "角色 0普通用户 1管理员")
    private Integer role;

    public static UserInfoResponse from(User user) {
        UserInfoResponse dto = new UserInfoResponse();
        dto.id = user.getId();
        dto.username = user.getUsername();
        dto.phone = maskPhone(user.getPhone());
        dto.nickname = user.getNickname();
        dto.avatar = user.getAvatar();
        dto.gender = user.getGender();
        dto.role = user.getRole();
        return dto;
    }

    /** 手机号脱敏：138****0000。 */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPhone() {
        return phone;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public Integer getGender() {
        return gender;
    }

    public Integer getRole() {
        return role;
    }
}
