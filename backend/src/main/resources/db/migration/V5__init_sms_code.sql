-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V5
-- 模块：auth（认证与用户）
-- 内容：sys_sms_code 手机短信验证码表
-- 说明：开发环境走 Mock 短信（固定验证码），表结构按真实短信形态设计，
--       后续接入真实短信网关时无需改表。
-- 约定：主键 BIGINT UNSIGNED；时间 datetime；状态 tinyint + 注释枚举
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 手机短信验证码表
-- scene: 1=注册 2=登录 3=重置密码
-- used:  0=未使用 1=已使用
-- 校验口径：取该 (phone, scene) 下最新一条未使用且未过期的记录比对
-- -----------------------------------------------------------------------------
CREATE TABLE `sys_sms_code` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `phone`      VARCHAR(20)     NOT NULL                COMMENT '手机号',
    `code`       VARCHAR(10)     NOT NULL                COMMENT '验证码',
    `scene`      TINYINT         NOT NULL DEFAULT 1      COMMENT '场景 1注册 2登录 3重置密码',
    `used`       TINYINT         NOT NULL DEFAULT 0      COMMENT '是否已使用 0未使用 1已使用',
    `expires_at` DATETIME        NOT NULL                COMMENT '过期时间',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_phone_scene` (`phone`, `scene`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '手机短信验证码表';
