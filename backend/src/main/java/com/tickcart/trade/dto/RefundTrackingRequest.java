package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交退货物流单号（退货退款专用）。
 *
 * <p>快递公司可选，单号必填 —— 没有单号商家无法核对是哪件货，
 * 只能靠用户口头描述，售后会变成扯皮。</p>
 */
@Schema(description = "提交退货物流请求")
public class RefundTrackingRequest {

    @Schema(description = "快递公司", example = "顺丰")
    @Size(max = 32, message = "快递公司最长 32 字")
    private String company;

    @Schema(description = "物流单号", example = "SF1234567890")
    @NotBlank(message = "请填写物流单号")
    @Size(max = 64, message = "物流单号最长 64 字")
    private String trackingNo;

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }
}
