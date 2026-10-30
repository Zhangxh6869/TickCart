-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V22
-- 内容：退货退款（P2）所需的退货物流与入库验收字段
--
-- 背景
--   P0/P1 的退款是「钱退回去，流程就结束」；退货退款中间夹着一段物理流程：
--     商家同意退货 → 用户寄回 → 商家收到并验收 → 才算退完
--   这段时间里钱没退、货没回、订单不能进终态，所以必须能回答「货现在在哪」。
--
-- 为什么是加列而不是新建退货表
--   退货信息与退款单是 1:1，且永远一起读。拆表只会在每次查退款单时多一次 join，
--   没有任何收益。（将来若出现「一次退货对应多个退款单」才需要拆。）
--
-- 全部列可空：P0/P1 的退款单不涉及退货，这些列对它们是 NULL。
-- =============================================================================

ALTER TABLE `refund_record`
    ADD COLUMN `return_company`     VARCHAR(32)  DEFAULT NULL COMMENT '退货快递公司'
        AFTER `audit_time`,
    ADD COLUMN `return_tracking_no` VARCHAR(64)  DEFAULT NULL COMMENT '退货物流单号（用户填写）'
        AFTER `return_company`,
    ADD COLUMN `return_time`        DATETIME     DEFAULT NULL COMMENT '用户寄出时间'
        AFTER `return_tracking_no`,
    ADD COLUMN `receive_time`       DATETIME     DEFAULT NULL COMMENT '商家确认收到退货的时间'
        AFTER `return_time`,
    ADD COLUMN `receive_remark`     VARCHAR(255) DEFAULT NULL COMMENT '验收备注（如：包装破损，同意退款）'
        AFTER `receive_time`;
