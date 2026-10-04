-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V1
-- 模块：auth（用户与认证）
-- 内容：sys_user 用户表、sys_refresh_token 刷新令牌表
-- 约定：主键 BIGINT UNSIGNED；时间 datetime；状态 tinyint + 注释枚举
-- 引擎/字符集：InnoDB / utf8mb4
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 用户表
-- role:   0=普通用户  1=管理员
-- gender: 0=未知      1=男      2=女
-- status: 0=正常      1=禁用
-- -----------------------------------------------------------------------------
CREATE TABLE `sys_user` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`   VARCHAR(50)     NOT NULL                COMMENT '登录用户名（唯一）',
    `password`   VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 加密后的密码',
    `phone`      VARCHAR(20)     DEFAULT NULL            COMMENT '手机号（唯一，可空）',
    `nickname`   VARCHAR(50)     DEFAULT NULL            COMMENT '昵称',
    `avatar`     VARCHAR(255)    DEFAULT NULL            COMMENT '头像URL',
    `gender`     TINYINT         NOT NULL DEFAULT 0      COMMENT '性别 0未知 1男 2女',
    `role`       TINYINT         NOT NULL DEFAULT 0      COMMENT '角色 0普通用户 1管理员',
    `status`     TINYINT         NOT NULL DEFAULT 0      COMMENT '状态 0正常 1禁用',
    `deleted`    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0未删 1已删',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_role` (`role`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '用户表';

-- -----------------------------------------------------------------------------
-- 2. 刷新令牌表（access + refresh 双令牌方案）
-- revoked: 0=有效 1=已吊销
-- -----------------------------------------------------------------------------
CREATE TABLE `sys_refresh_token` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '所属用户ID',
    `token`      VARCHAR(255)    NOT NULL                COMMENT '刷新令牌值（唯一）',
    `expires_at` DATETIME        NOT NULL                COMMENT '过期时间',
    `revoked`    TINYINT         NOT NULL DEFAULT 0      COMMENT '是否吊销 0有效 1已吊销',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token` (`token`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '刷新令牌表';
