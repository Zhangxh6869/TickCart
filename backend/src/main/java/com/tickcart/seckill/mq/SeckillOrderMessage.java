package com.tickcart.seckill.mq;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀建单消息。
 *
 * <p><b>带 {@code seckillPrice} 快照</b>：消费者建单时直接用它，不再回查活动 ——
 * 即使活动在排队期间被修改（设计上已禁止），也不会影响已抢到名额的用户。</p>
 *
 * <p>{@code requestId} 同时是「抢购结果」的凭据：消费者建单成功后写
 * {@code seckill:result:{requestId}}，前端据此轮询（B4）。</p>
 */
public class SeckillOrderMessage {

    private String requestId;
    private Long activityId;
    private Long userId;
    private Long productId;
    private Long skuId;
    /** 收货地址ID（抢购时由用户选定，建单时按此取快照） */
    private Long addressId;
    private Integer quantity;
    private BigDecimal seckillPrice;
    private LocalDateTime createdAt;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSeckillPrice() {
        return seckillPrice;
    }

    public void setSeckillPrice(BigDecimal seckillPrice) {
        this.seckillPrice = seckillPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
