package com.tickcart.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 收货地址新增/编辑请求。
 *
 * <p>编辑时 {@code isDefault} 语义为「勾选才生效」：传 1 会把该地址设为默认并清除其它默认项，
 * 传 0 或省略则保持原有默认状态不变，避免编辑非默认地址时误清默认项。</p>
 */
@Schema(description = "收货地址新增/编辑请求")
public class AddressSaveRequest {

    @Schema(description = "收货人姓名", example = "张三")
    @NotBlank(message = "收货人不能为空")
    @Size(max = 50, message = "收货人姓名最长 50 字")
    private String receiver;

    @Schema(description = "收货人手机号", example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "省", example = "广东省")
    @NotBlank(message = "省份不能为空")
    @Size(max = 50, message = "省份最长 50 字")
    private String province;

    @Schema(description = "市", example = "深圳市")
    @NotBlank(message = "城市不能为空")
    @Size(max = 50, message = "城市最长 50 字")
    private String city;

    @Schema(description = "区/县", example = "南山区")
    @NotBlank(message = "区县不能为空")
    @Size(max = 50, message = "区县最长 50 字")
    private String district;

    @Schema(description = "详细地址", example = "科技园路 1 号 3 栋 501")
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址最长 255 字")
    private String detail;

    @Schema(description = "是否设为默认 0否 1是", example = "1")
    private Integer isDefault;

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

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }
}
