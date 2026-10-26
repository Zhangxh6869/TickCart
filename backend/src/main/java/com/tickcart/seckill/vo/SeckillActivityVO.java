package com.tickcart.seckill.vo;

import com.tickcart.seckill.entity.SeckillActivity;
import com.tickcart.seckill.enums.SeckillActivityStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动视图（后台与前台共用）。
 *
 * <p>带商品快照（名称/主图/原价）是为了让前台列表与后台表格都不必再查一次商品 ——
 * 商品信息由调用方批量查回后填入，避免 N+1。</p>
 */
@Schema(description = "秒杀活动")
public class SeckillActivityVO {

    @Schema(description = "活动ID")
    private Long id;

    @Schema(description = "活动标题")
    private String title;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品名称（快照，来自 product 表）")
    private String productName;

    @Schema(description = "商品主图（快照）")
    private String productImage;

    @Schema(description = "SKU ID，0 表示无规格")
    private Long skuId;

    @Schema(description = "规格值")
    private String specValue;

    @Schema(description = "商品原价")
    private BigDecimal originalPrice;

    @Schema(description = "秒杀价")
    private BigDecimal seckillPrice;

    @Schema(description = "秒杀总库存")
    private Integer seckillStock;

    @Schema(description = "已成交数量")
    private Integer soldCount;

    @Schema(description = "剩余库存（DB 口径；B2 之后前台展示以 Redis 为准）")
    private Integer remainingStock;

    @Schema(description = "秒杀成交额（仅已支付订单）")
    private BigDecimal paidAmount;

    @Schema(description = "秒杀成交单数（仅已支付订单）")
    private Integer paidOrderCount;

    /**
     * Redis 库存 key 是否存在（是否已预热）。
     *
     * <p>只对「即将开始 / 进行中」有意义：已结束的活动按设计不建库存 key，
     * 前端不要把这种情况标成异常。</p>
     */
    @Schema(description = "是否已预热到 Redis")
    private boolean preloaded;

    @Schema(description = "每人限购")
    private Integer limitPerUser;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "是否上线 0下线 1上线")
    private Integer enabled;

    @Schema(description = "状态码 0已下线 1即将开始 2进行中 3已结束")
    private Integer status;

    @Schema(description = "状态文案")
    private String statusLabel;

    /**
     * 由实体与推导出的状态组装；商品字段由调用方填。
     *
     * @param now 判定时刻，由调用方统一传入（同一页内所有活动用同一个时刻，避免边界不一致）
     */
    public static SeckillActivityVO from(SeckillActivity activity, LocalDateTime now) {
        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setId(activity.getId());
        vo.setTitle(activity.getTitle());
        vo.setProductId(activity.getProductId());
        vo.setSkuId(activity.getSkuId());
        vo.setSeckillPrice(activity.getSeckillPrice());
        vo.setSeckillStock(activity.getSeckillStock());
        vo.setSoldCount(activity.getSoldCount());
        vo.setRemainingStock(remaining(activity));
        vo.setLimitPerUser(activity.getLimitPerUser());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setEnabled(activity.getEnabled());

        SeckillActivityStatus status = SeckillActivityStatus.of(activity, now);
        vo.setStatus(status.getCode());
        vo.setStatusLabel(status.getLabel());
        return vo;
    }

    /** 剩余库存：不低于 0（对账异常时也不能把负数展示给用户）。 */
    private static int remaining(SeckillActivity activity) {
        int stock = activity.getSeckillStock() == null ? 0 : activity.getSeckillStock();
        int sold = activity.getSoldCount() == null ? 0 : activity.getSoldCount();
        return Math.max(stock - sold, 0);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductImage() {
        return productImage;
    }

    public void setProductImage(String productImage) {
        this.productImage = productImage;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getSpecValue() {
        return specValue;
    }

    public void setSpecValue(String specValue) {
        this.specValue = specValue;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public BigDecimal getSeckillPrice() {
        return seckillPrice;
    }

    public void setSeckillPrice(BigDecimal seckillPrice) {
        this.seckillPrice = seckillPrice;
    }

    public Integer getSeckillStock() {
        return seckillStock;
    }

    public void setSeckillStock(Integer seckillStock) {
        this.seckillStock = seckillStock;
    }

    public Integer getSoldCount() {
        return soldCount;
    }

    public void setSoldCount(Integer soldCount) {
        this.soldCount = soldCount;
    }

    public Integer getRemainingStock() {
        return remainingStock;
    }

    public void setRemainingStock(Integer remainingStock) {
        this.remainingStock = remainingStock;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public Integer getPaidOrderCount() {
        return paidOrderCount;
    }

    public void setPaidOrderCount(Integer paidOrderCount) {
        this.paidOrderCount = paidOrderCount;
    }

    public boolean isPreloaded() {
        return preloaded;
    }

    public void setPreloaded(boolean preloaded) {
        this.preloaded = preloaded;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }
}
