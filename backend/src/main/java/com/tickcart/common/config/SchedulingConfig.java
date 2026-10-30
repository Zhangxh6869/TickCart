package com.tickcart.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 定时任务（{@code @Scheduled}）。
 *
 * <p>目前有四个任务：搜索日志清理（{@code SearchLogCleanupTask}）、幂等记录清理
 * （{@code IdempotentCleanupTask}）、待付款超时关单兜底（{@code OrderTimeoutCompensateTask}）、
 * 退货超时自动关闭（{@code RefundReturnTimeoutTask}）。</p>
 *
 * <p>默认调度器只有 1 个线程，四个任务会互相排队。目前都是「查一批 + 逐条处理」的短任务
 * （毫秒到秒级），排队没有实际影响；真出现长任务再配 {@code TaskScheduler} 线程池。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
