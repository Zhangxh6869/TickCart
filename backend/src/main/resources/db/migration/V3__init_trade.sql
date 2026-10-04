-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V3
-- 模块：member / cart / trade（交易链路）
-- 内容：user_address 收货地址、cart_item 购物车项、
--       t_order 订单、order_item 订单明细、payment_record 支付记录
-- 说明：购物车 MVP 用 MySQL（ADR-S1）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 收货地址表
-- is_default: 0=非默认 1=默认
-- -----------------------------------------------------------------------------
CREATE TABLE `user_address` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '地址ID',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '所属用户ID',
    `receiver`   VARCHAR(50)     NOT NULL                COMMENT '收货人姓名',
    `phone`      VARCHAR(20)     NOT NULL                COMMENT '收货人手机号',
    `province`   VARCHAR(50)     NOT NULL                COMMENT '省',
    `city`       VARCHAR(50)     NOT NULL                COMMENT '市',
    `district`   VARCHAR(50)     NOT NULL                COMMENT '区/县',
    `detail`     VARCHAR(255)    NOT NULL                COMMENT '详细地址',
    `is_default` TINYINT         NOT NULL DEFAULT 0      COMMENT '是否默认 0否 1是',
    `deleted`    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0未删 1已删',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '收货地址表';

-- -----------------------------------------------------------------------------
-- 2. 购物车项表
-- 唯一键 (user_id, product_id, sku_id) 保证同规格合并数量
-- -----------------------------------------------------------------------------
CREATE TABLE `cart_item` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '购物车项ID',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
    `product_id` BIGINT UNSIGNED NOT NULL                COMMENT '商品ID',
    `sku_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT 'SKU ID（无规格时为0）',
    `quantity`   INT             NOT NULL DEFAULT 1      COMMENT '数量',
    `checked`    TINYINT         NOT NULL DEFAULT 1      COMMENT '是否勾选 0否 1是',
    `deleted`    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0未删 1已删',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product_sku` (`user_id`, `product_id`, `sku_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '购物车项表';

-- -----------------------------------------------------------------------------
-- 3. 订单主表
-- channel: 0=普通订单 1=秒杀订单（订单号前缀区分：普通 NO，秒杀 SK）
-- status:  0=待付款 1=待发货 2=待收货 3=已完成 4=已取消 5=已超时关闭
-- 收货信息以快照形式内嵌，避免地址后续修改影响历史订单
-- -----------------------------------------------------------------------------
CREATE TABLE `t_order` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`       VARCHAR(32)     NOT NULL                COMMENT '订单号（唯一，渠道前缀）',
    `user_id`        BIGINT UNSIGNED NOT NULL                COMMENT '下单用户ID',
    `channel`        TINYINT         NOT NULL DEFAULT 0      COMMENT '渠道 0普通 1秒杀',
    `total_amount`   DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '订单总金额',
    `pay_amount`     DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '实付金额',
    `status`         TINYINT         NOT NULL DEFAULT 0      COMMENT '状态 0待付款 1待发货 2待收货 3已完成 4已取消 5已超时关闭',
    `receiver`       VARCHAR(50)     NOT NULL                COMMENT '收货人（快照）',
    `receiver_phone` VARCHAR(20)     NOT NULL                COMMENT '收货人手机号（快照）',
    `receiver_addr`  VARCHAR(400)    NOT NULL                COMMENT '收货完整地址（快照）',
    `remark`         VARCHAR(255)    DEFAULT NULL            COMMENT '买家备注',
    `pay_time`       DATETIME        DEFAULT NULL            COMMENT '支付时间',
    `ship_time`      DATETIME        DEFAULT NULL            COMMENT '发货时间',
    `finish_time`    DATETIME        DEFAULT NULL            COMMENT '完成时间',
    `cancel_time`    DATETIME        DEFAULT NULL            COMMENT '取消/关闭时间',
    `deleted`        TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0未删 1已删',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '订单主表';

-- -----------------------------------------------------------------------------
-- 4. 订单明细表（商品信息以快照保存，防止商品后续改价影响历史订单）
-- -----------------------------------------------------------------------------
CREATE TABLE `order_item` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`    BIGINT UNSIGNED NOT NULL                COMMENT '所属订单ID',
    `order_no`    VARCHAR(32)     NOT NULL                COMMENT '订单号（冗余，便于查询）',
    `product_id`  BIGINT UNSIGNED NOT NULL                COMMENT '商品ID',
    `sku_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT 'SKU ID（无规格时为0）',
    `product_name` VARCHAR(100)   NOT NULL                COMMENT '商品名称（快照）',
    `product_image` VARCHAR(255)  DEFAULT NULL            COMMENT '商品主图（快照）',
    `spec_value`  VARCHAR(100)    DEFAULT NULL            COMMENT '规格值（快照）',
    `price`       DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '成交单价（快照）',
    `quantity`    INT             NOT NULL DEFAULT 1      COMMENT '购买数量',
    `subtotal`    DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '小计 = price * quantity',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '订单明细表';

-- -----------------------------------------------------------------------------
-- 5. 支付记录表（模拟支付，预留真实支付回调形态）
-- channel: 1=微信 2=支付宝（预留）
-- status:  0=待支付 1=支付成功 2=支付失败
-- -----------------------------------------------------------------------------
CREATE TABLE `payment_record` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      BIGINT UNSIGNED NOT NULL                COMMENT '关联订单ID',
    `order_no`      VARCHAR(32)     NOT NULL                COMMENT '订单号',
    `pay_no`        VARCHAR(64)     NOT NULL                COMMENT '支付流水号（唯一）',
    `channel`       TINYINT         NOT NULL DEFAULT 0      COMMENT '支付渠道 0模拟 1微信 2支付宝',
    `amount`        DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '支付金额',
    `status`        TINYINT         NOT NULL DEFAULT 0      COMMENT '状态 0待支付 1成功 2失败',
    `callback_time` DATETIME        DEFAULT NULL            COMMENT '回调时间',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_no` (`pay_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '支付记录表';
