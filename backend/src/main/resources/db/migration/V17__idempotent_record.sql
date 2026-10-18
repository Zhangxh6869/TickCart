-- -----------------------------------------------------------------------------
-- 幂等记录表（A1 下单幂等）
--
-- 作用：同一 (scene, user_id, idem_key) 只允许存在一行。
--   · 插入成功 = 抢到本次请求的执行权
--   · 插入撞唯一键 = 重复请求，回查 biz_no 直接回放首次结果
--
-- 与业务同事务写入：业务失败整体回滚，记录一并消失，用户可正常重试。
-- 因此「行存在」即等价于「该次请求已完成」，不需要 status 字段
-- （详见 docs/下单幂等与结算抢占设计.md 第 4 节）。
-- -----------------------------------------------------------------------------
CREATE TABLE `idempotent_record` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scene`      VARCHAR(32)     NOT NULL                COMMENT '业务场景：order.create / order.buyNow / seckill.buy',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '用户ID（幂等键按用户隔离，防越权回放他人订单）',
    `idem_key`   VARCHAR(64)     NOT NULL                COMMENT '幂等键（客户端生成的 UUID）',
    `biz_no`     VARCHAR(64)     DEFAULT NULL            COMMENT '业务单号；非空即代表该次请求已成功完成',
    `expire_at`  DATETIME        NOT NULL                COMMENT '过期时间（定时任务按此清理）',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scene_user_key` (`scene`, `user_id`, `idem_key`),
    KEY `idx_expire_at` (`expire_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '幂等记录表';
