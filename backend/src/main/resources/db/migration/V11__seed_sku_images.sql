-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V11
-- 内容：为「手机数码」(category_id = 1) 的 39 个 SKU 补上 SKU 图片 (product_sku.sku_image)。
--
-- 背景
--   商品图集与 SKU 图片的编辑入口是后补的（后台「新增/编辑商品」弹窗），
--   历史种子数据里 product_sku.sku_image 全为空（共 179 个 SKU 无一有图）。
--   详情页在选中某个 SKU 时会把该 SKU 的图片顶到轮播首位，款式面板也用它当缩略图，
--   购物车 / 订单行同样优先取 SKU 图（CartService.buildItemVo / OrderService.buildOrderItem），
--   所以这批图缺失时，用户切规格看不到任何视觉反馈。
--
-- 图片
--   落在仓库 imgs/sku/（随 git 提交），经 /img/** 静态映射对外提供，见 imgs/SOURCES.md。
--   配色型 SKU（颜色 / 表带）一律取「同色」产品图；版本型 SKU（内存 / 存储）无视觉差异，
--   取同型号另一张官方图，便于切换规格时看到变化。
--
-- 说明
--   · 只改数据，不动表结构。
--   · 幂等：按 (product_id, spec_name, spec_value) 定位，重复执行结果一致。
--   · 刻意用 UPDATE 而非走后台 PUT /api/admin/products/{id}：后者会逻辑删除旧 SKU 再插入新行，
--     SKU id 全部改变，会让 cart_item.uk_user_product_sku 里已有的 15 条购物车记录指向已删 SKU。
--   · 与代码侧无耦合：前端 gallery 里 SKU 图与图集去重（ProductDetailView.vue），
--     所以这里给的值即使与主图相同也不会重复出现。
-- =============================================================================

UPDATE `product_sku` SET `sku_image` = '/img/sku/p6-black.jpg' WHERE `product_id` = 6 AND `deleted` = 0;   -- #44 iPhone Air · iPhone Air
UPDATE `product_sku` SET `sku_image` = '/img/sku/p1-white.jpg' WHERE `product_id` = 1 AND `spec_name` = '颜色' AND `spec_value` = '白色 256G' AND `deleted` = 0;   -- #40 iPhone17 · iPhone17 颜色=白色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p2-silver.jpg' WHERE `product_id` = 2 AND `spec_name` = '内存' AND `spec_value` = '16G+1T' AND `deleted` = 0;   -- #43 iPhone18 Pro · iPhone18Pro 内存=16G+1T
UPDATE `product_sku` SET `sku_image` = '/img/sku/p2-black.jpg' WHERE `product_id` = 2 AND `spec_name` = '内存' AND `spec_value` = '16G+512G' AND `deleted` = 0;   -- #42 iPhone18 Pro · iPhone18Pro 内存=16G+512G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p1-black.jpg' WHERE `product_id` = 1 AND `spec_name` = '颜色' AND `spec_value` = '黑色 256G' AND `deleted` = 0;   -- #39 iPhone17 · iPhone17 颜色=黑色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p101-b.jpg' WHERE `product_id` = 101 AND `spec_name` = '版本' AND `spec_value` = '16+512G' AND `deleted` = 0;   -- #48 5G 旗舰智能手机 12+256G 曜夜黑 · 101 曜夜黑 · 版本=16+512G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p7-hero.jpg' WHERE `product_id` = 7 AND `deleted` = 0;   -- #46 iPhone Duo · iPhone Duo
UPDATE `product_sku` SET `sku_image` = '/img/sku/p1-natural-titanium.jpg' WHERE `product_id` = 1 AND `spec_name` = '颜色' AND `spec_value` = '原色钛 512G' AND `deleted` = 0;   -- #41 iPhone17 · iPhone17 颜色=原色钛
UPDATE `product_sku` SET `sku_image` = '/img/sku/p104-a.jpg' WHERE `product_id` = 104 AND `spec_name` = '版本' AND `spec_value` = '16G+512G' AND `deleted` = 0;   -- #53 轻薄办公笔记本 14 英寸 16G+512G · 104 办公笔记本 · 版本=16G+512G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p102-a.jpg' WHERE `product_id` = 102 AND `spec_name` = '版本' AND `spec_value` = '8+128G' AND `deleted` = 0;   -- #49 5G 轻薄智能手机 8+128G 星空灰 · 102 星空灰 · 版本=8+128G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p105-a.jpg' WHERE `product_id` = 105 AND `spec_name` = '版本' AND `spec_value` = '32G+1T' AND `deleted` = 0;   -- #55 高性能游戏笔记本 15.6 英寸 32G+1T · 105 游戏笔记本 · 版本=32G+1T
UPDATE `product_sku` SET `sku_image` = '/img/sku/p105-b.jpg' WHERE `product_id` = 105 AND `spec_name` = '版本' AND `spec_value` = '32G+2T' AND `deleted` = 0;   -- #56 高性能游戏笔记本 15.6 英寸 32G+1T · 105 游戏笔记本 · 版本=32G+2T
UPDATE `product_sku` SET `sku_image` = '/img/sku/p106-a.jpg' WHERE `product_id` = 106 AND `spec_name` = '版本' AND `spec_value` = '16G+512G' AND `deleted` = 0;   -- #57 一体机电脑 24 英寸 16G+512G · 106 一体机 · 版本=16G+512G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p106-b.jpg' WHERE `product_id` = 106 AND `spec_name` = '版本' AND `spec_value` = '24G+1T' AND `deleted` = 0;   -- #58 一体机电脑 24 英寸 16G+512G · 106 一体机 · 版本=24G+1T
UPDATE `product_sku` SET `sku_image` = '/img/sku/p107-a.jpg' WHERE `product_id` = 107 AND `spec_name` = '版本' AND `spec_value` = '8+256G' AND `deleted` = 0;   -- #59 平板电脑 11 英寸 8+256G · 107 平板 11 寸 · 版本=8+256G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p107-b.jpg' WHERE `product_id` = 107 AND `spec_name` = '版本' AND `spec_value` = '12+512G' AND `deleted` = 0;   -- #60 平板电脑 11 英寸 8+256G · 107 平板 11 寸 · 版本=12+512G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p108-a.jpg' WHERE `product_id` = 108 AND `spec_name` = '版本' AND `spec_value` = '6+128G' AND `deleted` = 0;   -- #61 平板电脑 10.9 英寸 6+128G · 108 平板 10.9 寸 · 版本=6+128G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p108-b.jpg' WHERE `product_id` = 108 AND `spec_name` = '版本' AND `spec_value` = '8+256G' AND `deleted` = 0;   -- #62 平板电脑 10.9 英寸 6+128G · 108 平板 10.9 寸 · 版本=8+256G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p104-b.jpg' WHERE `product_id` = 104 AND `spec_name` = '版本' AND `spec_value` = '32G+1T' AND `deleted` = 0;   -- #54 轻薄办公笔记本 14 英寸 16G+512G · 104 办公笔记本 · 版本=32G+1T
UPDATE `product_sku` SET `sku_image` = '/img/sku/p101-a.jpg' WHERE `product_id` = 101 AND `spec_name` = '版本' AND `spec_value` = '12+256G' AND `deleted` = 0;   -- #47 5G 旗舰智能手机 12+256G 曜夜黑 · 101 曜夜黑 · 版本=12+256G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p102-b.jpg' WHERE `product_id` = 102 AND `spec_name` = '版本' AND `spec_value` = '8+256G' AND `deleted` = 0;   -- #50 5G 轻薄智能手机 8+128G 星空灰 · 102 星空灰 · 版本=8+256G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p103-red.jpg' WHERE `product_id` = 103 AND `spec_name` = '版本' AND `spec_value` = '8+256G' AND `deleted` = 0;   -- #51 全面屏智能手机 8+256G 赤焰红 · 103 赤焰红 · 版本=8+256G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p103-red.jpg' WHERE `product_id` = 103 AND `spec_name` = '版本' AND `spec_value` = '12+256G' AND `deleted` = 0;   -- #52 全面屏智能手机 8+256G 赤焰红 · 103 赤焰红 · 版本=12+256G
UPDATE `product_sku` SET `sku_image` = '/img/sku/p109-b.jpg' WHERE `product_id` = 109 AND `spec_name` = '表带' AND `spec_value` = '尼龙灰' AND `deleted` = 0;   -- #64 智能运动手表 血氧心率监测 · 109 智能手表 表带=尼龙灰
UPDATE `product_sku` SET `sku_image` = '/img/sku/p110-black.jpg' WHERE `product_id` = 110 AND `spec_name` = '颜色' AND `spec_value` = '曜石黑' AND `deleted` = 0;   -- #65 头戴式无线降噪耳机 · 110 头戴耳机 颜色=曜石黑
UPDATE `product_sku` SET `sku_image` = '/img/sku/p111-white.jpg' WHERE `product_id` = 111 AND `spec_name` = '颜色' AND `spec_value` = '白色' AND `deleted` = 0;   -- #67 真无线主动降噪耳机 · 111 降噪耳机 颜色=白色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p112-pink.jpg' WHERE `product_id` = 112 AND `spec_name` = '颜色' AND `spec_value` = '粉色' AND `deleted` = 0;   -- #70 真无线蓝牙耳机 长续航 · 112 蓝牙耳机 颜色=粉色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p113-blue.jpg' WHERE `product_id` = 113 AND `spec_name` = '颜色' AND `spec_value` = '蓝色' AND `deleted` = 0;   -- #72 便携式蓝牙音箱 防水低音炮 · 113 蓝牙音箱 颜色=蓝色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p109-a.jpg' WHERE `product_id` = 109 AND `spec_name` = '表带' AND `spec_value` = '硅胶黑' AND `deleted` = 0;   -- #63 智能运动手表 血氧心率监测 · 109 智能手表 表带=硅胶黑
UPDATE `product_sku` SET `sku_image` = '/img/sku/p110-white.jpg' WHERE `product_id` = 110 AND `spec_name` = '颜色' AND `spec_value` = '月光白' AND `deleted` = 0;   -- #66 头戴式无线降噪耳机 · 110 头戴耳机 颜色=月光白
UPDATE `product_sku` SET `sku_image` = '/img/sku/p111-black.jpg' WHERE `product_id` = 111 AND `spec_name` = '颜色' AND `spec_value` = '黑色' AND `deleted` = 0;   -- #68 真无线主动降噪耳机 · 111 降噪耳机 颜色=黑色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p112-white.jpg' WHERE `product_id` = 112 AND `spec_name` = '颜色' AND `spec_value` = '白色' AND `deleted` = 0;   -- #69 真无线蓝牙耳机 长续航 · 112 蓝牙耳机 颜色=白色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p114-silver.jpg' WHERE `product_id` = 114 AND `spec_name` = '颜色' AND `spec_value` = '银色' AND `deleted` = 0;   -- #74 无线蓝牙键盘 超薄静音 · 114 键盘 颜色=银色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p115-black.jpg' WHERE `product_id` = 115 AND `spec_name` = '颜色' AND `spec_value` = '黑色' AND `deleted` = 0;   -- #76 无线静音鼠标 人体工学 · 115 鼠标 颜色=黑色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p113-black.jpg' WHERE `product_id` = 113 AND `spec_name` = '颜色' AND `spec_value` = '黑色' AND `deleted` = 0;   -- #71 便携式蓝牙音箱 防水低音炮 · 113 蓝牙音箱 颜色=黑色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p114-gray.jpg' WHERE `product_id` = 114 AND `spec_name` = '颜色' AND `spec_value` = '深空灰' AND `deleted` = 0;   -- #73 无线蓝牙键盘 超薄静音 · 114 键盘 颜色=深空灰
UPDATE `product_sku` SET `sku_image` = '/img/sku/p115-white.jpg' WHERE `product_id` = 115 AND `spec_name` = '颜色' AND `spec_value` = '白色' AND `deleted` = 0;   -- #75 无线静音鼠标 人体工学 · 115 鼠标 颜色=白色
UPDATE `product_sku` SET `sku_image` = '/img/sku/p116-body.jpg' WHERE `product_id` = 116 AND `spec_name` = '套装' AND `spec_value` = '单机身' AND `deleted` = 0;   -- #77 微单相机 2420 万像素 套机 · 116 相机 套装=单机身
UPDATE `product_sku` SET `sku_image` = '/img/sku/p116-kit.jpg' WHERE `product_id` = 116 AND `spec_name` = '套装' AND `spec_value` = '含 15-45 镜头' AND `deleted` = 0;   -- #78 微单相机 2420 万像素 套机 · 116 相机 套装=含 15-45 镜头
