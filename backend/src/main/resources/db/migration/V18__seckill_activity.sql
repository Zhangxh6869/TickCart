-- -----------------------------------------------------------------------------
-- B1 秒杀活动表 + 订单关联活动
--
-- 设计要点：
--   1. 不存 status —— 状态由 (enabled, start_time, end_time) + 当前时间推导，
--      避免「到点没自动开始/结束」这类必须靠定时任务补救的问题
--   2. 秒杀库存独立于 product_sku.stock —— 两套账互不影响，秒杀订单不扣 SKU 库存
--   3. sold_count 由建单消费者原子累加，是「Redis 剩余库存」的对账依据
-- 详见 docs/秒杀活动管理设计.md
-- -----------------------------------------------------------------------------
CREATE TABLE `seckill_activity` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '活动ID',
    `title`          VARCHAR(64)     NOT NULL                COMMENT '活动标题',
    `product_id`     BIGINT UNSIGNED NOT NULL                COMMENT '商品ID',
    `sku_id`         BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT 'SKU ID（无规格为0）',
    `seckill_price`  DECIMAL(10, 2)  NOT NULL                COMMENT '秒杀价',
    `seckill_stock`  INT             NOT NULL                COMMENT '秒杀总库存（独立于商品库存）',
    `sold_count`     INT             NOT NULL DEFAULT 0      COMMENT '已成交数量（建单成功后原子累加）',
    `limit_per_user` INT             NOT NULL DEFAULT 1      COMMENT '每人限购数量',
    `start_time`     DATETIME        NOT NULL                COMMENT '开始时间',
    `end_time`       DATETIME        NOT NULL                COMMENT '结束时间',
    `enabled`        TINYINT         NOT NULL DEFAULT 1      COMMENT '是否上线 0下线 1上线',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_time` (`start_time`, `end_time`),
    KEY `idx_product` (`product_id`),
    KEY `idx_enabled_start` (`enabled`, `start_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '秒杀活动表';

-- -----------------------------------------------------------------------------
-- 订单关联秒杀活动
--
-- 取消/超时关单时需要回补「正确活动」的库存，仅靠订单号前缀无法定位活动，
-- 因此在订单主表上落一列。0 表示非秒杀订单，普通订单不受影响。
-- -----------------------------------------------------------------------------
ALTER TABLE `t_order`
    ADD COLUMN `activity_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '秒杀活动ID（0=非秒杀订单）' AFTER `channel`,
    ADD KEY `idx_activity` (`activity_id`);
