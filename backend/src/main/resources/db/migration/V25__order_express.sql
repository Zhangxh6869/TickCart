-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V25
-- 内容：订单增加发货物流信息（express_company / tracking_no）
--
-- 背景：此前发货只有一个「一键」动作 —— PUT /api/admin/orders/{no}/ship 不接收请求体，
--       OrderService.ship() 只写 ship_time。后果是数据从来没被采集过：
--         用户端「待收货」只看到发货时间，不知道谁发的、单号多少、货到哪了；
--         后台批量发货后想补单号，只能改库；
--         售后/客服无法回答「我的货到哪了」。
--       对比：退货退款有 return_company / return_tracking_no，发货反而没有，属不对称。
--
-- 设计：
--   1. 两列均可空 —— ① 历史订单无从回填；② 批量发货（batchShip）刻意不采集单号
--      （几十单共用一个单号没有意义），设 NOT NULL 会让存量数据与批量流程同时失败。
--   2. 只加列、不改既有列、无回填脚本，对存量数据零影响。
--   3. 不加索引：唯一查询入口是「按订单号查详情」，已被 uk_order_no 覆盖。
-- =============================================================================

ALTER TABLE `t_order`
    ADD COLUMN `express_company` VARCHAR(32) DEFAULT NULL COMMENT '快递公司（发货时填写，可空）' AFTER `ship_time`,
    ADD COLUMN `tracking_no`     VARCHAR(64) DEFAULT NULL COMMENT '运单号（发货时填写，可空）' AFTER `express_company`;
