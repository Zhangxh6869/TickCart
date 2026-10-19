package com.tickcart.seckill.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tickcart.seckill.entity.SeckillActivity;
import com.tickcart.seckill.mapper.SeckillActivityMapper;
import com.tickcart.seckill.service.SeckillStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀库存预热的两个兜底触发点。
 *
 * <p>预热共有三处触发，缺一不可：</p>
 * <ol>
 *   <li><b>后台「上线」时</b> —— 见 {@code SeckillActivityService.changeEnabled}，立即预热，不让用户等</li>
 *   <li><b>应用启动时</b> —— 本类 {@link #preloadOnStartup()}，补上「服务重启期间 Redis 丢数据」</li>
 *   <li><b>定时扫描</b> —— 本类 {@link #preloadUpcoming()}，补上「上线时预热失败但没人重试」</li>
 * </ol>
 *
 * <p>重复预热是安全的：{@code SeckillStockService.preload} 用 {@code SET NX}，
 * 已有数据不会被覆盖（覆盖会把「已扣未建单」的库存还回去，造成超卖）。</p>
 */
@Component
public class SeckillPreloadTask {

    private static final Logger log = LoggerFactory.getLogger(SeckillPreloadTask.class);

    private final SeckillActivityMapper activityMapper;
    private final SeckillStockService stockService;
    /** 预热前瞻窗口：扫描「已开始或 N 秒内即将开始」的活动 */
    private final long lookaheadSeconds;

    public SeckillPreloadTask(SeckillActivityMapper activityMapper,
                              SeckillStockService stockService,
                              @Value("${tickcart.seckill.preload-lookahead-seconds:60}") long lookaheadSeconds) {
        this.activityMapper = activityMapper;
        this.stockService = stockService;
        this.lookaheadSeconds = Math.max(1, lookaheadSeconds);
    }

    /** 启动预热：服务重启后 Redis 可能已丢失库存，这里按 DB 重建（NX 保证不覆盖有效数据）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void preloadOnStartup() {
        int count = preloadCandidates();
        if (count > 0) {
            log.info("启动预热完成：处理 {} 个待开始/进行中的秒杀活动", count);
        }
    }

    /** 定时预热：每 30 秒扫一次「即将开始」的活动，兜住上线时预热失败的场景。 */
    @Scheduled(fixedDelayString = "${tickcart.seckill.preload-interval-ms:30000}")
    public void preloadUpcoming() {
        preloadCandidates();
    }

    private int preloadCandidates() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillActivity> activities;
        try {
            activities = activityMapper.selectList(new LambdaQueryWrapper<SeckillActivity>()
                    .eq(SeckillActivity::getEnabled, 1)
                    .gt(SeckillActivity::getEndTime, now)
                    .le(SeckillActivity::getStartTime, now.plusSeconds(lookaheadSeconds)));
        } catch (Exception e) {
            log.error("查询待预热秒杀活动失败（下次定时任务会重试）", e);
            return 0;
        }

        int success = 0;
        for (SeckillActivity activity : activities) {
            try {
                stockService.preload(activity);
                success++;
            } catch (Exception e) {
                // 单个活动预热失败不能影响其他活动；上线路径会因预热失败而回滚，这里只兜底重试
                log.error("预热秒杀活动失败：activityId={}", activity.getId(), e);
            }
        }
        return success;
    }
}
