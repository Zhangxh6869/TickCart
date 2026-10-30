package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 商家确认收到退货。
 *
 * <p>验收备注可选：多数情况货没问题，强制填会让人写「无」。
 * 但「包装破损仍同意退款」这类情况应该留痕，所以给一个可选字段。</p>
 */
@Schema(description = "确认收到退货请求")
public class RefundReceiveRequest {

    @Schema(description = "验收备注（可选）", example = "外包装破损，商品完好")
    @Size(max = 200, message = "验收备注最长 200 字")
    private String receiveRemark;

    public String getReceiveRemark() {
        return receiveRemark;
    }

    public void setReceiveRemark(String receiveRemark) {
        this.receiveRemark = receiveRemark;
    }
}
