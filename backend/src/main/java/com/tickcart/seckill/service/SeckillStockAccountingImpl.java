package com.tickcart.seckill.service;

import com.tickcart.seckill.mapper.SeckillActivityMapper;
import com.tickcart.trade.service.SeckillStockAccounting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 秒杀库存记账实现（trade 定义的扩展点）。
 *
 * <p>Redis 计数与 DB 的 {@code sold_count} 是两套口径：前者决定「还能不能卖」，
 * 后者用于后台展示与对账。两者必须一起变，否则会出现「库存回来了、进度条没动」。</p>
 */
@Component
public class SeckillStockAccountingImpl implements SeckillStockAccounting {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockAccountingImpl.class);

    private final SeckillStockService stockService;
    private final SeckillActivityMapper activityMapper;

    public SeckillStockAccountingImpl(SeckillStockService stockService,
                                      SeckillActivityMapper activityMapper) {
        this.stockService = stockService;
        this.activityMapper = activityMapper;
    }

    /**
     * 累加已售数。
     *
     * <p>影响 0 行说明 {@code sold_count + n > seckill_stock} —— Redis 与 DB 已经不一致
     * （典型原因是 Redis 重建时用了偏低的已售数）。此时订单已经建好，<b>不回滚</b>：
     * 让用户白抢比多卖一单糟糕得多，这里只打 ERROR 作为告警。</p>
     */
    @Override
    public void increaseSold(Long activityId, int quantity) {
        if (activityId == null || quantity <= 0) {
            return;
        }
        int affected = activityMapper.increaseSoldCount(activityId, quantity);
        if (affected == 0) {
            log.error("秒杀已售数累加失败（超出活动库存，Redis 与 DB 可能不一致）：activityId={}, quantity={}",
                    activityId, quantity);
        }
    }

    @Override
    public void restore(Long activityId, Long userId, int quantity) {
        if (activityId == null || quantity <= 0) {
            return;
        }
        // Redis 侧：活动已结束、key 已过期时会自动跳过（不凭空创建库存 key）
        stockService.restore(activityId, userId, quantity);
        // DB 侧：已售数照常减，否则统计会一直偏高
        activityMapper.decreaseSoldCount(activityId, quantity);
        log.info("秒杀库存已回补：activityId={}, userId={}, quantity={}", activityId, userId, quantity);
    }
}
