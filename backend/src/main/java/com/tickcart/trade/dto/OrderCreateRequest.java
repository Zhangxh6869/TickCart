package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建订单请求（结算购物车中已勾选的商品）。
 */
@Schema(description = "创建订单请求")
public class OrderCreateRequest {

    @Schema(description = "收货地址ID", example = "1")
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    @Schema(description = "买家备注", example = "尽快发货")
    @Size(max = 255, message = "备注最长 255 字")
    private String remark;

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
