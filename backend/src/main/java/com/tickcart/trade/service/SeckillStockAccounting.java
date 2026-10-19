package com.tickcart.trade.service;

/**
 * 秒杀库存记账扩展点。
 *
 * <p>秒杀订单的库存记在 Redis 计数与活动已售数上，与普通订单的 {@code product_sku.stock}
 * 是两套账。建单成功要累加已售数，取消/超时关闭要回补 —— 但 trade 模块不该反过来依赖
 * seckill 模块，所以在 trade 侧定义这个接口，由 seckill 模块实现（依赖方向仍是 seckill → trade）。</p>
 */
public interface SeckillStockAccounting {

    /**
     * 建单成功后累加活动已售数。
     *
     * <p>实现方必须在「已售数超出活动库存」时只告警、不抛异常 ——
     * 订单已经建好、用户已经抢到，不能因为统计口径不一致就把订单回滚。</p>
     *
     * @param activityId 活动 ID
     * @param quantity   数量
     */
    void increaseSold(Long activityId, int quantity);

    /**
     * 回补一笔秒杀订单占用的库存（取消 / 超时关闭）。
     *
     * @param activityId 活动 ID
     * @param userId     下单用户（用于回退其已购数量，限购才算得准）
     * @param quantity   数量
     */
    void restore(Long activityId, Long userId, int quantity);
}
