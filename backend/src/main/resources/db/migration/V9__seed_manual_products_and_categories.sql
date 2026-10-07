-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V9
-- 内容：把开发者在后台手工建的两项「有内容的数据」固化为种子数据
--       1. 补建演示商品 6 / 7（iPhone Air、iPhone Duo）—— 有真实主图，会出现在首页
--       2. 把开发者已删除的 3 个分类（家用电器 / 服饰鞋包 / 食品生鲜）同步置为逻辑删除
--
-- 背景
--   这些改动是开发者在后台管理界面做的，只落在本机数据库里，代码仓库中没有记录。
--   于是「从零建库」跑完 V1~V8 后会与开发库不一致：分类栏多出 3 个已被删掉的分类，
--   又少了商品 6/7（空库 58 个商品 / 6 个分类，开发库 60 个 / 21 个）。
--   本迁移把这两项对齐。
--
--   其余 17 个分类（护肤彩妆…相机）**有意不固化**：它们没有任何商品，
--   点进去就是「暂无商品」，放进种子只是噪音。将来若要，另起迁移补。
--
-- 与 V8 的先后关系（重要）
--   V7 按「商品 1/2/6/7 都存在」的假设给 6/7 插过图集，而全新库里 6/7 尚不存在，
--   V8 因此把这 6 行当孤儿删掉了。所以本迁移在补建商品 6/7 之后**必须重新补图集**。
--
-- 幂等性
--   · 商品用 INSERT IGNORE：已存在则整行跳过，线上库的既有行不会被改动。
--   · 图集表只有 PRIMARY(id) 与 idx_product_id（非唯一），INSERT IGNORE 去不了重，
--     故改用 NOT EXISTS 守卫：该商品只要已有任何图集行就整批跳过。
--   · 分类删除带 deleted = 0 条件。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 补建商品 6 / 7
--    数据取自开发者在后台所建（product.price 与 stock 原样保留）。
--    后台保存时产生的两条空壳 SKU（spec_name / spec_value 均为空串，商品 7 的
--    SKU 价格还是 4.00、与商品价 10000 明显不符）不具任何规格语义，故**不固化**。
--    商品无 SKU 时前台按 product.stock 走，加购与下单均正常（见 CartService）。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `product`
    (`id`, `category_id`, `name`, `subtitle`, `main_image`, `detail`,
     `price`, `stock`, `sales`, `status`, `is_seckill`)
VALUES
    (6, 1, 'iPhone Air', '', '/img/iphone_air__b5qmgl05ojyq_large_2x.jpg', '', 8000.00,  4, 0, 1, 0),
    (7, 1, 'iPhone Duo', '', '/img/iphone_duo__hefmlegzwbmi_large_2x.jpg', '', 10000.00, 15, 0, 1, 0);

-- -----------------------------------------------------------------------------
-- 2. 补图集（sort 0 与主图一致，否则列表缩略图与详情首图会对不上）
-- -----------------------------------------------------------------------------
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
              SELECT 6 AS `product_id`, '/img/iphone_air__b5qmgl05ojyq_large_2x.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 6,               '/img/iphone_17__fb1277oq3eaa_large_2x.jpg',         1
    UNION ALL SELECT 6,               '/img/iphone_18_pro__dj48ysc0yagm_large_2x.jpg',     2
    UNION ALL SELECT 7,               '/img/iphone_duo__hefmlegzwbmi_large_2x.jpg',        0
    UNION ALL SELECT 7,               '/img/iphone_air__b5qmgl05ojyq_large_2x.jpg',        1
    UNION ALL SELECT 7,               '/img/iphone_18_pro__dj48ysc0yagm_large_2x.jpg',     2
) AS `src`
WHERE NOT EXISTS (
    SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`
);

-- -----------------------------------------------------------------------------
-- 3. 开发者已删除的 3 个分类，同步置为逻辑删除
-- -----------------------------------------------------------------------------
UPDATE `product_category` SET `deleted` = 1 WHERE `id` IN (2, 3, 4) AND `deleted` = 0;
