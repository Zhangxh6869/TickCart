package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 发货时填写的物流信息。
 *
 * <p><b>两项都可以不填</b>：请求体整体可省略（见 {@code AdminOrderController#ship}），
 * 此时等价于旧的「一键发货」。原因是批量发货（{@code batchShip}）刻意不采集单号 ——
 * 几十单共用一个单号没有意义，硬要填只会逼管理员乱填。</p>
 *
 * <p>但「只填单号不填快递公司」会被拒绝（校验在 {@code OrderService#ship} 里做）：
 * 单号脱离承运商就没有任何查询价值，属于填了一半的状态。</p>
 *
 * <p>★ 字段名用 {@code expressCompany} 而不是 {@code company}：退货单那边叫
 * {@code company}，但订单上还有「收货人」「买家备注」等字段，{@code company} 在这个
 * 上下文里指向不明；宁可牺牲一点跨模块的字面一致，也别让读代码的人猜。</p>
 */
@Schema(description = "发货请求（物流信息）")
public class ShipRequest {

    @Schema(description = "快递公司", example = "顺丰速运")
    @Size(max = 32, message = "快递公司最长 32 字")
    private String expressCompany;

    @Schema(description = "运单号", example = "SF1234567890")
    @Size(max = 64, message = "运单号最长 64 字")
    private String trackingNo;

    public String getExpressCompany() {
        return expressCompany;
    }

    public void setExpressCompany(String expressCompany) {
        this.expressCompany = expressCompany;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }
}
