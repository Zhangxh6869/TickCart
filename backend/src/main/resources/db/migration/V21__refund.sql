-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V21
-- 内容：退款单表（P0：未发货退款）
--
-- 背景
--   已支付订单在 V3 的状态机里是单行道（待发货 → 待收货 → 已完成），
--   用户付了钱反悔没有任何出口。本表承载退款申请与审核结果。
--
-- 设计要点
--   1. 按「退款单」建模，不按订单：一单可能多次售后（本次拒绝、下次再申请），
--      每次一条记录。将来做部分退款只需加 refund_item 子表，本表不用改。
--   2. ★ active_key 是「同一订单同时只能有一个进行中退款单」的实现手段：
--        进行中 = order_no（占唯一键）
--        终态   = NULL（MySQL 唯一索引允许多个 NULL，故历史记录可无限多）
--      比「先 select 再 insert」可靠 —— 后者并发下会插出两条。
--   3. origin_status 记「申请前的订单状态」，拒绝/撤销时回退到它。
--      不写死「回退到待发货」，是为了 P1（待收货发起）/ P2（已完成发起）不用改代码。
--   4. 本表不含 refund_item 子表，也不含「退哪几行」的字段 —— P0 只支持整单退款。
--      这个限制写在 Service 层校验里，不是表结构里，所以将来放开不用迁移数据。
-- =============================================================================

CREATE TABLE `refund_record` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `refund_no`     VARCHAR(32)     NOT NULL                COMMENT '退款单号（唯一，前缀 RF）',
    `order_id`      BIGINT UNSIGNED NOT NULL                COMMENT '关联订单ID',
    `order_no`      VARCHAR(32)     NOT NULL                COMMENT '订单号（冗余，便于查询）',
    `user_id`       BIGINT UNSIGNED NOT NULL                COMMENT '申请用户ID（越权校验用）',
    `type`          TINYINT         NOT NULL DEFAULT 1      COMMENT '1未发货退款 2仅退款(已发货) 3退货退款',
    `amount`        DECIMAL(10,2)   NOT NULL                COMMENT '退款金额（快照，取支付成功流水）',
    `reason_code`   TINYINT         NOT NULL                COMMENT '原因码 1不想要了 2信息填错 3商品问题 4其他',
    `reason`        VARCHAR(255)    DEFAULT NULL            COMMENT '补充说明',
    `status`        TINYINT         NOT NULL DEFAULT 0      COMMENT '0待审核 1已退款 2已拒绝 3已撤销',
    `origin_status` TINYINT         NOT NULL                COMMENT '申请前的订单状态（拒绝/撤销时的回退目标）',
    `active_key`    VARCHAR(32)     DEFAULT NULL            COMMENT '进行中标记：进行中=order_no，终态=NULL',
    `audit_remark`  VARCHAR(255)    DEFAULT NULL            COMMENT '审核意见（拒绝时必填）',
    `audit_time`    DATETIME        DEFAULT NULL            COMMENT '审核时间',
    `refund_time`   DATETIME        DEFAULT NULL            COMMENT '退款完成时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0未删 1已删',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    UNIQUE KEY `uk_active_order` (`active_key`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '退款单';
