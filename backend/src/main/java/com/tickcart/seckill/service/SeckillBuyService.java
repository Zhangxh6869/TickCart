package com.tickcart.seckill.service;

import com.tickcart.common.exception.BizException;
import com.tickcart.common.exception.RateLimitedException;
import com.tickcart.member.service.AddressService;
import com.tickcart.seckill.cache.SeckillActivitySnapshot;
import com.tickcart.seckill.enums.SeckillActivityStatus;
import com.tickcart.seckill.mq.SeckillMessageProducer;
import com.tickcart.seckill.mq.SeckillOrderMessage;
import com.tickcart.seckill.vo.SeckillBuyResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 秒杀抢购编排（同步段）。
 *
 * <p>整条链路<b>不查数据库</b>：活动状态与限购数来自预热快照，库存来自 Redis，
 * 建单交给 MQ 消费者（B3）。这样抢购接口的耗时与「下单」这件事完全解耦 ——
 * 500 并发抢购也只是一串 Redis 命令 + 一条消息投递。</p>
 *
 * <p>失败语义分三类，前端要能区分：</p>
 * <ul>
 *   <li>503 未就绪 —— 预热还没完成，稍后重试即可</li>
 *   <li>409 售罄/超限购/未开始 —— 用户侧问题，重试无意义</li>
 *   <li>429 请求过于频繁 —— 连点或脚本，等 3 秒</li>
 * </ul>
 */
@Service
public class SeckillBuyService {

    private static final Logger log = LoggerFactory.getLogger(SeckillBuyService.class);

    private final SeckillStockService stockService;
    private final SeckillMessageProducer messageProducer;
    private final AddressService addressService;

    public SeckillBuyService(SeckillStockService stockService,
                             SeckillMessageProducer messageProducer,
                             AddressService addressService) {
        this.stockService = stockService;
        this.messageProducer = messageProducer;
        this.addressService = addressService;
    }

    /**
     * 抢购：校验 → 防重 → 原子扣减 → 投递建单消息。
     *
     * @param quantity 抢购数量，null 按 1 处理
     * @return 受理结果（含 {@code requestId}，供前端轮询）
     */
    public SeckillBuyResultVO buy(Long userId, Long activityId, Integer quantity) {
        int qty = quantity == null ? 1 : quantity;

        SeckillActivitySnapshot snapshot = stockService.snapshot(activityId);
        if (snapshot == null) {
            // 未预热：可能是刚上线还没轮到预热任务，也可能 Redis 丢数据
            throw BizException.serviceUnavailable("活动尚未就绪，请稍后重试");
        }

        SeckillActivityStatus status = SeckillActivityStatus.of(
                snapshot.getEnabled(), snapshot.getStartTime(), snapshot.getEndTime(), LocalDateTime.now());
        if (status == SeckillActivityStatus.PENDING) {
            throw BizException.conflict("活动尚未开始");
        }
        if (status != SeckillActivityStatus.ONGOING) {
            throw BizException.conflict("活动已结束");
        }

        int limit = snapshot.getLimitPerUser() == null ? 1 : snapshot.getLimitPerUser();
        if (qty > limit) {
            // 提前挡：省一次 Lua 执行，也让提示更直接
            throw BizException.conflict("每人限购 " + limit + " 件");
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        if (!stockService.tryLockRequest(activityId, userId, requestId)) {
            throw new RateLimitedException(3);
        }

        // 有收货地址才能下单：抢购是异步建单，等消费者发现没地址就太晚了 —— 库存白扣一次再退回，
        // 用户还以为抢到了。放在请求锁之后，同一用户 3 秒内最多查一次，不会把 DB 打穿。
        if (addressService.getDefault(userId) == null) {
            stockService.unlockRequest(activityId, userId);
            throw BizException.conflict("请先添加收货地址");
        }

        long remaining;
        try {
            remaining = stockService.tryDeduct(activityId, userId, qty, limit);
        } catch (RuntimeException e) {
            // Redis 异常：fail-close（不知道库存就不卖），但要放行重试
            stockService.unlockRequest(activityId, userId);
            throw e;
        }
        if (remaining < 0) {
            stockService.unlockRequest(activityId, userId);
            throw mapDeductFailure(remaining, limit);
        }

        if (!messageProducer.sendOrder(buildMessage(requestId, snapshot, userId, qty))) {
            // 扣了库存却没进队列 = 用户白抢，必须立刻还回去
            stockService.restore(activityId, userId, qty);
            stockService.unlockRequest(activityId, userId);
            throw BizException.serviceUnavailable("下单繁忙，请稍后重试");
        }

        log.info("秒杀抢购受理：activityId={}, userId={}, qty={}, remaining={}, requestId={}",
                activityId, userId, qty, remaining, requestId);
        return new SeckillBuyResultVO(requestId, remaining);
    }

    /** Lua 返回码 → 业务异常。 */
    private BizException mapDeductFailure(long code, int limit) {
        if (code == SeckillStockService.RESULT_NOT_PRELOADED) {
            return BizException.serviceUnavailable("活动尚未就绪，请稍后重试");
        }
        if (code == SeckillStockService.RESULT_SOLD_OUT) {
            return BizException.conflict("已抢光");
        }
        if (code == SeckillStockService.RESULT_OVER_LIMIT) {
            return BizException.conflict("每人限购 " + limit + " 件");
        }
        return BizException.conflict("抢购失败，请稍后重试");
    }

    private SeckillOrderMessage buildMessage(String requestId, SeckillActivitySnapshot snapshot,
                                             Long userId, int qty) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setRequestId(requestId);
        message.setActivityId(snapshot.getActivityId());
        message.setUserId(userId);
        message.setProductId(snapshot.getProductId());
        message.setSkuId(snapshot.getSkuId());
        message.setQuantity(qty);
        // 价格用快照，消费者不再回查活动
        message.setSeckillPrice(snapshot.getSeckillPrice());
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
}
