package com.tickcart.seckill.cache;

import java.time.LocalDateTime;

/**
 * 抢购结果凭据，存在 {@code seckill:result:{requestId}}，供前端轮询。
 *
 * <p>TTL 24 小时：比活动本身的 TTL 长 —— 用户可能第二天才回来查「我到底抢到没有」。</p>
 */
public class SeckillResult {

    /** 抢购成功，已生成订单 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 抢购失败（库存已回补） */
    public static final String STATUS_FAILED = "FAILED";

    private String requestId;
    /** 归属用户：查询时校验，避免拿到别人的订单号 */
    private Long userId;
    private String status;
    private String orderNo;
    private String message;
    private LocalDateTime updatedAt;

    public static SeckillResult success(String requestId, Long userId, String orderNo) {
        SeckillResult result = new SeckillResult();
        result.setRequestId(requestId);
        result.setUserId(userId);
        result.setStatus(STATUS_SUCCESS);
        result.setOrderNo(orderNo);
        result.setMessage("抢购成功");
        result.setUpdatedAt(LocalDateTime.now());
        return result;
    }

    public static SeckillResult failed(String requestId, Long userId, String message) {
        SeckillResult result = new SeckillResult();
        result.setRequestId(requestId);
        result.setUserId(userId);
        result.setStatus(STATUS_FAILED);
        result.setMessage(message);
        result.setUpdatedAt(LocalDateTime.now());
        return result;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
