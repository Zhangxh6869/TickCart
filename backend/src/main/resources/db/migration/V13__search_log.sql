-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V13
-- 内容：搜索日志表，用于「无结果搜索词」监控与真实热搜词统计
--
-- 背景
--   在这张表之前，搜索对开发者是个黑盒：谁搜了什么、哪些词搜不到，全都看不到。
--   （搜索页的热搜词一度拿「分类名」当关键词，8/8 点是「没有找到相关商品」，
--    而这种问题只能靠用户口头反馈才发现 —— 有了本表就能主动看到。）
--
-- 记全量而不是只记「无结果」的词
--   只记命中 0 的词虽然也能查无结果，但会丢掉搜索量信息；
--   记全量后同一份数据既能给「无结果词 TOP N」，也能给「真实热搜词 TOP N」，
--   后者可以替掉前端现在「取热销商品名首段」的权宜做法。
--
-- 是否记录用户
--   user_id 允许为 NULL：搜索是公开的，未登录也能搜。
--   只用于统计，不做个体行为分析。
--
-- 增长控制
--   每次搜索一行，量级与搜索 PV 同阶。演示项目不做清理任务，
--   真上线需要按 created_at 归档/清理（或改成按天聚合的统计表）。
-- =============================================================================

CREATE TABLE `search_log` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `keyword`      VARCHAR(64)     NOT NULL                COMMENT '搜索关键词（已 trim）',
    `result_count` INT             NOT NULL DEFAULT 0      COMMENT '本次搜索命中商品数',
    `user_id`      BIGINT UNSIGNED DEFAULT NULL            COMMENT '用户ID（未登录为 NULL）',
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
    PRIMARY KEY (`id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_keyword` (`keyword`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '搜索日志表';
