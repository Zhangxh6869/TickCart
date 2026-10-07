-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V10
-- 内容：修正「空壳 SKU」的价格 —— 规格名与规格值均为空、或价格 <= 0 的 SKU，
--       价格一律回写为所属商品的展示价。
--
-- 背景
--   后台「新建商品」表单默认预置一行空 SKU（specName / specValue / price 全空，
--   见 apps/admin/src/views/ProductManageView.vue:68）。开发者建商品时往往只填库存，
--   于是这行以 price = 0 落库；商品 7 更是在价格格里顺手填了 4。
--
--   而商城详情页加载后会自动选中「第一个有库存的 SKU」，并以其价格作为整件商品的
--   成交价（ProductDetailView.vue:59 / :41，后端 CartService.buildItemVo:264 同样取
--   SKU 价），于是：
--       商品 6 iPhone Air  标价 8000  → 详情页显示并按 ¥0.00 下单（SKU id=44）
--       商品 7 iPhone Duo  标价 10000 → 详情页显示并按 ¥4.00 下单（SKU id=46）
--   注意 product.stock 是「SKU 库存之和」，显示正常（4 / 15），所以肉眼看不出来。
--
-- 说明
--   · 只改数据，不动表结构。
--   · 幂等：末尾带 price <> p.price，重复执行不会产生额外影响。
--   · 只改价格、不删行：删行会让后台编辑该商品时回填出空 SKU 列表，
--     保存时 ProductService.update 会把 product.stock 重算为 0（见 L202-208），
--     等于把「价格错」换成「库存没了」。行保留则 stock 不受影响。
--   · 与代码侧同规则：ProductService.resolveSkuPrice 已让新建/编辑商品时
--     不再落库 0 价 SKU，本迁移负责把历史脏数据对齐。
-- =============================================================================

UPDATE `product_sku` `s`
JOIN `product` `p` ON `p`.`id` = `s`.`product_id`
SET `s`.`price` = `p`.`price`
WHERE `s`.`deleted` = 0
  AND `s`.`price` <> `p`.`price`
  AND (
        -- 空壳 SKU：没有规格身份，代表商品默认规格，本就不该有独立价格
        (COALESCE(TRIM(`s`.`spec_name`), '') = '' AND COALESCE(TRIM(`s`.`spec_value`), '') = '')
        -- 有规格但价格没填：价格没填不等于免费
        OR `s`.`price` <= 0
      );
