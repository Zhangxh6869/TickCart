-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V2
-- 模块：product（商品域）
-- 内容：product_category 分类、product 商品、product_sku 规格、
--       product_image 商品图集、user_favorite 收藏
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 商品分类表（树形结构）
-- status: 0=禁用 1=启用
-- -----------------------------------------------------------------------------
CREATE TABLE `product_category` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `parent_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '父分类ID，0为顶级',
    `name`       VARCHAR(50)     NOT NULL                COMMENT '分类名称',
    `icon`       VARCHAR(255)    DEFAULT NULL            COMMENT '分类图标URL',
    `level`      TINYINT         NOT NULL DEFAULT 1      COMMENT '层级 1一级 2二级 ...',
    `sort`       INT             NOT NULL DEFAULT 0      COMMENT '排序值（越小越靠前）',
    `status`     TINYINT         NOT NULL DEFAULT 1      COMMENT '状态 0禁用 1启用',
    `deleted`    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0未删 1已删',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status_sort` (`status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '商品分类表';

-- -----------------------------------------------------------------------------
-- 2. 商品表
-- status:   0=下架 1=上架
-- 库存/销量：以商品的 SKU 汇总展示；stock 为总可售库存
-- 秒杀：is_seckill 标记该商品是否参与秒杀（详情表在秒杀阶段建）
-- -----------------------------------------------------------------------------
CREATE TABLE `product` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `category_id` BIGINT UNSIGNED NOT NULL                COMMENT '所属分类ID',
    `name`        VARCHAR(100)    NOT NULL                COMMENT '商品名称',
    `subtitle`    VARCHAR(255)    DEFAULT NULL            COMMENT '副标题/卖点',
    `main_image`  VARCHAR(255)    DEFAULT NULL            COMMENT '主图URL',
    `detail`      TEXT            DEFAULT NULL            COMMENT '商品详情（富文本/长文本）',
    `price`       DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '展示价格（最低SKU价）',
    `stock`       INT             NOT NULL DEFAULT 0      COMMENT '总库存',
    `sales`       INT             NOT NULL DEFAULT 0      COMMENT '累计销量',
    `status`      TINYINT         NOT NULL DEFAULT 0      COMMENT '状态 0下架 1上架',
    `is_seckill`  TINYINT         NOT NULL DEFAULT 0      COMMENT '是否秒杀商品 0否 1是',
    `deleted`     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0未删 1已删',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_is_seckill` (`is_seckill`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '商品表';

-- -----------------------------------------------------------------------------
-- 3. 商品 SKU 表（规格与独立库存）
-- -----------------------------------------------------------------------------
CREATE TABLE `product_sku` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    `product_id` BIGINT UNSIGNED NOT NULL                COMMENT '所属商品ID',
    `spec_name`  VARCHAR(50)     DEFAULT NULL            COMMENT '规格名（如 颜色/内存）',
    `spec_value` VARCHAR(100)    DEFAULT NULL            COMMENT '规格值（如 黑色/8+128G）',
    `sku_image`  VARCHAR(255)    DEFAULT NULL            COMMENT 'SKU 图片URL',
    `price`      DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT 'SKU 价格',
    `stock`      INT             NOT NULL DEFAULT 0      COMMENT 'SKU 库存',
    `deleted`    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0未删 1已删',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '商品SKU表';

-- -----------------------------------------------------------------------------
-- 4. 商品图集表
-- -----------------------------------------------------------------------------
CREATE TABLE `product_image` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id` BIGINT UNSIGNED NOT NULL                COMMENT '所属商品ID',
    `url`        VARCHAR(255)    NOT NULL                COMMENT '图片URL',
    `sort`       INT             NOT NULL DEFAULT 0      COMMENT '排序值（越小越靠前）',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '商品图集表';

-- -----------------------------------------------------------------------------
-- 5. 用户收藏表
-- 唯一键 (user_id, product_id) 防重复收藏
-- -----------------------------------------------------------------------------
CREATE TABLE `user_favorite` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
    `product_id` BIGINT UNSIGNED NOT NULL                COMMENT '商品ID',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '用户收藏表';
