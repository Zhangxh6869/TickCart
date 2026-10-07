-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V7
-- 内容：修复演示商品的图片指向
-- 背景：
--   1. V4 种子数据把商品 1、2 的图集指向 /img/demo/*.png，但仓库里并不存在该目录
--      （apps/mall 没有 public/），导致详情页图集全是碎图；
--   2. 商品 1、2、6、7 的主图指向 /api/files/**（后台上传目录），该目录在 .gitignore 中，
--      全新克隆后同样会 404。
-- 做法：统一改用已随仓库提交的 imgs/ 图片（经 /img/** 静态映射访问）。
--       这些文件与原先上传的图片**字节完全一致**（md5 相同），界面观感不变，
--       但换来了「克隆即能跑」的可复现性。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 主图改用仓库内图片
-- -----------------------------------------------------------------------------
UPDATE `product` SET `main_image` = '/img/iphone_17__fb1277oq3eaa_large_2x.jpg'  WHERE `id` = 1;
UPDATE `product` SET `main_image` = '/img/iphone_18_pro__dj48ysc0yagm_large_2x.jpg' WHERE `id` = 2;
UPDATE `product` SET `main_image` = '/img/iphone_air__b5qmgl05ojyq_large_2x.jpg'   WHERE `id` = 6;
UPDATE `product` SET `main_image` = '/img/iphone_duo__hefmlegzwbmi_large_2x.jpg'   WHERE `id` = 7;

-- -----------------------------------------------------------------------------
-- 2. 清掉指向不存在的 /img/demo/ 的图集记录
-- -----------------------------------------------------------------------------
DELETE FROM `product_image` WHERE `url` LIKE '/img/demo/%';

-- -----------------------------------------------------------------------------
-- 3. 为 iPhone 系列商品补图集
--    sort 0 与主图保持一致，否则点击商品后图集首图与列表缩略图会对不上
-- -----------------------------------------------------------------------------
INSERT INTO `product_image` (`product_id`, `url`, `sort`) VALUES
(1, '/img/iphone_17__fb1277oq3eaa_large_2x.jpg',     0),
(1, '/img/iphone_air__b5qmgl05ojyq_large_2x.jpg',    1),
(1, '/img/iphone_duo__hefmlegzwbmi_large_2x.jpg',    2),
(2, '/img/iphone_18_pro__dj48ysc0yagm_large_2x.jpg', 0),
(2, '/img/iphone_duo__hefmlegzwbmi_large_2x.jpg',    1),
(2, '/img/iphone_17__fb1277oq3eaa_large_2x.jpg',     2),
(6, '/img/iphone_air__b5qmgl05ojyq_large_2x.jpg',    0),
(6, '/img/iphone_17__fb1277oq3eaa_large_2x.jpg',     1),
(6, '/img/iphone_18_pro__dj48ysc0yagm_large_2x.jpg', 2),
(7, '/img/iphone_duo__hefmlegzwbmi_large_2x.jpg',    0),
(7, '/img/iphone_air__b5qmgl05ojyq_large_2x.jpg',    1),
(7, '/img/iphone_18_pro__dj48ysc0yagm_large_2x.jpg', 2);
