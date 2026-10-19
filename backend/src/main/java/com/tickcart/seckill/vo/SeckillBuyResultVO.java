package com.tickcart.seckill.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 抢购受理结果。
 *
 * <p>抢购接口是<b>异步受理</b>：返回 {@code requestId} 表示「已占到名额、建单排队中」，
 * 真正的订单由消费者创建，前端拿 {@code requestId} 轮询结果（B4）。</p>
 */
@Schema(description = "秒杀抢购受理结果")
public class SeckillBuyResultVO {

    @Schema(description = "结果凭据，用于轮询建单结果")
    private String requestId;

    @Schema(description = "扣减后的剩余库存")
    private Long remainingStock;

    @Schema(description = "状态：QUEUED 排队中")
    private String status = "QUEUED";

    public SeckillBuyResultVO() {
    }

    public SeckillBuyResultVO(String requestId, Long remainingStock) {
        this.requestId = requestId;
        this.remainingStock = remainingStock;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getRemainingStock() {
        return remainingStock;
    }

    public void setRemainingStock(Long remainingStock) {
        this.remainingStock = remainingStock;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
