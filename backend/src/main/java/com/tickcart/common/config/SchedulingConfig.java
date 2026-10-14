package com.tickcart.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 定时任务（{@code @Scheduled}）。
 *
 * <p>目前用于 search_log 的保留清理（{@code SearchLogCleanupTask}）。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
