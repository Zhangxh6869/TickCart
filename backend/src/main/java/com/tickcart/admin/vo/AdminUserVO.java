package com.tickcart.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台用户列表行。
 *
 * <p>手机号在此层已脱敏（{@code 138****8888}）—— 列表是截图外流的高危位置，
 * 需要明文请进详情接口。</p>
 *
 * <p>{@code orderCount} 与 {@code paidOrderCount} 放在一起看才有意义：
 * 「下单 20 笔、付款 1 笔」通常不是正常用户，而是占库存或刷单。</p>
 */
@Schema(description = "后台用户列表行")
public class AdminUserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "手机号（已脱敏）", example = "138****8888")
    private String phone;

    @Schema(description = "性别 0未知 1男 2女")
    private Integer gender;

    @Schema(description = "角色 0普通用户 1管理员")
    private Integer role;

    @Schema(description = "状态 0正常 1禁用")
    private Integer status;

    @Schema(description = "下单总数（含已取消、已超时）")
    private long orderCount;

    @Schema(description = "已付款订单数（待发货/待收货/已完成）")
    private long paidOrderCount;

    @Schema(description = "累计消费金额（已付款且未退款）")
    private BigDecimal paidAmount;

    @Schema(description = "最近下单时间；从未下单为 null")
    private LocalDateTime lastOrderAt;

    @Schema(description = "注册时间")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(long orderCount) {
        this.orderCount = orderCount;
    }

    public long getPaidOrderCount() {
        return paidOrderCount;
    }

    public void setPaidOrderCount(long paidOrderCount) {
        this.paidOrderCount = paidOrderCount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public LocalDateTime getLastOrderAt() {
        return lastOrderAt;
    }

    public void setLastOrderAt(LocalDateTime lastOrderAt) {
        this.lastOrderAt = lastOrderAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
