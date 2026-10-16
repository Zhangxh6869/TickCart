-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V16
-- 内容：用户浏览记录表 `user_view_history`
--
-- 目的
--   支撑「我的」页新增的「浏览记录」入口：记录用户看过的商品，按最近浏览时间倒序展示，
--   支持删除单条与清空全部。
--
-- 设计要点（刻意与 `user_favorite` 对齐，两套接口共用同一套实现思路）
--   1. **去重**：唯一键 `uk_user_product`，同一用户同一商品只保留一条。
--      重复浏览走 `INSERT ... ON DUPLICATE KEY UPDATE view_time = NOW()`，只刷新时间、不产生新行。
--      因此单用户记录数**有界**（最多 = 在售商品总数，当前 162），无需定时清理任务。
--   2. **无 deleted 列**：删除记录走物理删除。若用逻辑删除，用户删掉记录后再浏览同一商品
--      会撞 `uk_user_product`（唯一键不含 deleted）—— 这个坑在购物车换规格那里踩过。
--   3. `view_time` 是**业务时间**（列表排序依据），与 `updated_at` 分开：
--      后者会被任何 UPDATE 刷新，语义上不该拿来当「浏览时间」。
--   4. `created_at` 保留「首次浏览时间」，为将来做「第一次看到是在什么时候」留口子。
--
-- 索引
--   `uk_user_product` 同时承担「按用户去重」与「按 (user_id, product_id) 点查」两个职责；
--   `idx_user_time` 覆盖唯一的热查询：某用户按最近浏览时间倒序分页。
--   `idx_product_id` 与 user_favorite 对齐，留给未来的「商品被多少人看过」统计。
-- =============================================================================

CREATE TABLE `user_view_history` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT                  COMMENT '主键',
    `user_id`    BIGINT UNSIGNED NOT NULL                                 COMMENT '用户ID',
    `product_id` BIGINT UNSIGNED NOT NULL                                 COMMENT '商品ID',
    `view_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '最近一次浏览时间',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '首次浏览时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    KEY `idx_user_time` (`user_id`, `view_time`),
    KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '用户浏览记录表';
