package com.tickcart.seckill.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 抢购结果（供前端轮询）。
 */
@Schema(description = "秒杀抢购结果")
public class SeckillResultVO {

    @Schema(description = "结果凭据")
    private String requestId;

    @Schema(description = "状态：SUCCESS 成功 / FAILED 失败")
    private String status;

    @Schema(description = "订单号（成功时有值）")
    private String orderNo;

    @Schema(description = "提示文案")
    private String message;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
