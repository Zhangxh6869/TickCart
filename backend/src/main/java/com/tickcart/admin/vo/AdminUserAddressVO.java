package com.tickcart.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户详情里的收货地址（只读）。
 *
 * <p>后台刻意不提供地址编辑：地址归属用户，客服代改会污染下单快照的来源，
 * 真要改让用户自己在商城端改。</p>
 */
@Schema(description = "用户收货地址（只读）")
public class AdminUserAddressVO {

    @Schema(description = "地址ID")
    private Long id;

    @Schema(description = "收货人")
    private String receiver;

    @Schema(description = "收货人手机号（已脱敏）")
    private String phone;

    @Schema(description = "省市区 + 详细地址")
    private String fullAddress;

    @Schema(description = "是否默认 0否 1是")
    private Integer isDefault;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getFullAddress() {
        return fullAddress;
    }

    public void setFullAddress(String fullAddress) {
        this.fullAddress = fullAddress;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }
}
