package com.tickcart.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台用户详情。
 *
 * <p>与列表行的差别：这里是<b>明文手机号</b>。脱敏只在列表做，
 * 因为列表是随手截图的位置，详情是主动点开排查的位置。</p>
 */
@Schema(description = "后台用户详情")
public class AdminUserDetailVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "手机号（明文）", example = "13800008888")
    private String phone;

    @Schema(description = "头像地址")
    private String avatar;

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

    @Schema(description = "退款次数（含进行中）")
    private long refundCount;

    @Schema(description = "最近下单时间；从未下单为 null")
    private LocalDateTime lastOrderAt;

    @Schema(description = "注册时间")
    private LocalDateTime createdAt;

    @Schema(description = "最近 5 笔订单，新的在前")
    private List<AdminUserOrderVO> recentOrders;

    @Schema(description = "收货地址列表")
    private List<AdminUserAddressVO> addresses;

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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
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

    public long getRefundCount() {
        return refundCount;
    }

    public void setRefundCount(long refundCount) {
        this.refundCount = refundCount;
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

    public List<AdminUserOrderVO> getRecentOrders() {
        return recentOrders;
    }

    public void setRecentOrders(List<AdminUserOrderVO> recentOrders) {
        this.recentOrders = recentOrders;
    }

    public List<AdminUserAddressVO> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<AdminUserAddressVO> addresses) {
        this.addresses = addresses;
    }
}
