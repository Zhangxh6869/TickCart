package com.tickcart.seckill.cache;

import com.tickcart.seckill.entity.SeckillActivity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动快照，预热时写入 {@code seckill:activity:{id}}。
 *
 * <p><b>为什么要快照</b>：抢购接口每请求都要校验「活动是否进行中」「限购多少」「秒杀价多少」。
 * 若每次都查 DB，秒杀流量会直接把 {@code seckill_activity} 打穿；预热时把这份数据写进 Redis，
 * 抢购链路<b>全程不碰数据库</b>。</p>
 *
 * <p>字段刻意只保留抢购必需的最小集：改标题、改时间不影响已预热快照，
 * 反正活动开始后这些字段本来就不允许改。</p>
 */
public class SeckillActivitySnapshot {

    private Long activityId;
    private Long productId;
    private Long skuId;
    /** 秒杀价快照：下单金额以此为准，不再回查活动 */
    private BigDecimal seckillPrice;
    private Integer limitPerUser;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer enabled;

    public static SeckillActivitySnapshot from(SeckillActivity activity) {
        SeckillActivitySnapshot snapshot = new SeckillActivitySnapshot();
        snapshot.setActivityId(activity.getId());
        snapshot.setProductId(activity.getProductId());
        snapshot.setSkuId(activity.getSkuId());
        snapshot.setSeckillPrice(activity.getSeckillPrice());
        snapshot.setLimitPerUser(activity.getLimitPerUser());
        snapshot.setStartTime(activity.getStartTime());
        snapshot.setEndTime(activity.getEndTime());
        snapshot.setEnabled(activity.getEnabled());
        return snapshot;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
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

    public BigDecimal getSeckillPrice() {
        return seckillPrice;
    }

    public void setSeckillPrice(BigDecimal seckillPrice) {
        this.seckillPrice = seckillPrice;
    }

    public Integer getLimitPerUser() {
        return limitPerUser;
    }

    public void setLimitPerUser(Integer limitPerUser) {
        this.limitPerUser = limitPerUser;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
