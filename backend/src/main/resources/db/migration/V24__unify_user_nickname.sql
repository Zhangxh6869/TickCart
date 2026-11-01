-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V24
-- 内容：统一昵称与用户名（nickname = username）
--
-- 背景：注册流程自 b06eaed 起不再收集昵称，落库固定取用户名（AuthService#createUser）。
--       但存量账号仍带着中文展示名，与新账号的语义分叉：
--         V4 种子      admin / 系统管理员、demo / 演示用户
--         早期注册     zzxh / 张筱函、uploader67232 / 上传测试
--       于是前台「个人信息」页会出现两行不同的值，用户分不清哪个才是自己的名字。
--
-- 本脚本：把存量数据一次性对齐，使全库 nickname 恒等于 username。
--
-- 注意：
--   1. 覆盖不可逆 —— 执行前的中文展示名已备份至
--      .workbuddy-ai/backup/sys_user_20261101_before_unify.sql
--   2. 刻意不改 V4__seed_data.sql —— Flyway 开启了 validate-on-migrate，
--      改动已应用脚本会导致校验和不匹配、应用启动失败。
--      新库执行顺序为 V4 插入中文名 → 本脚本覆盖为用户名，最终结果一致。
--   3. 仅改数据、不动表结构（nickname 仍允许 NULL，结构变更需另行确认）。
-- =============================================================================

UPDATE `sys_user`
SET `nickname` = `username`
WHERE `nickname` IS NULL
   OR `nickname` <> `username`;
