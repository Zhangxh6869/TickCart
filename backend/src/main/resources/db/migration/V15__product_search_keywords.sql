-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V15
-- 内容：商品「搜索关键词」字段 + 种子
--
-- 目的
--   解决「用户搜品类词搜不到商品」——搜索此前只匹配 `name` + `subtitle` 的**字面子串**，
--   于是：
--     · 搜「裤子」= 0 件（库里只有「高腰直筒牛仔裤」，没有哪个商品名含「裤子」二字）；
--     · 搜「运动」只出 8 件（都是名字/副标题里带「运动」的），而「运动」分类下的
--       「专业比赛用篮球」「轻量跳绳」「可调节哑铃」等一件都不出 —— 分类不参与匹配；
--     · 搜「手机」出不了 iPhone17（商品名里没有「手机」二字）；
--     · 搜「服饰」= 0 件（分类名同样不参与匹配）。
--
--   本表字段存放「该商品还应该被哪些词搜到」，搜索时与 name / subtitle 一起 OR 匹配。
--   它是**人工维护的召回词**，不是商品属性：只写用户会搜、而商品名里没有的品类词/别名。
--
-- 与 V14 同义词表的分工（两者互补，别混用）
--   · `search_synonym`：词与词的**等价**关系，全库通用（口红↔唇膏），与具体商品无关；
--   · `search_keywords`：**商品**维度的召回词，能表达「这个商品属于哪些品类」。
--     上下位关系（运动 → 跳绳/篮球）走这里，**不能**走同义词表：同义词是双向的，
--     把「运动」和「跳绳」并成一组会让搜「篮球」也搜出「瑜伽垫」。
--
-- 匹配口径
--   LIKE '%展开词%'，与 name / subtitle 完全一致的转义规则（见 ProductService#escapeLike）。
--   值为 NULL 的商品搜索行为与引入本字段前**完全一致**。
--
-- 种子设计原则
--   ① 分类级批量：每个分类下的商品补上该分类的**口语名**（「运动」「鞋」「衣服」…），
--      让「搜品类词 → 出该品类全部商品」成立；
--   ② 商品级精准：只给「名字里没有、但用户会这么搜」的商品补别名（iPhone→手机、
--      牛仔裤→裤子、拉杆箱→行李箱…）；
--   ③ ★宁可少补也不要错补：分类级词必须是该分类**全部**商品都成立的词。
--      反例：「酒水」分类含「天然气泡矿泉水」，加「酒」会让搜「酒」搜出矿泉水；
--      「食品生鲜」含西兰花/胡萝卜，加「零食」「水果」会让搜「水果」搜出蔬菜
--      （蔬菜/水果各自单独补到对应商品）。
--   ④ ★注意匹配是 **LIKE '%词%' 子串匹配**，所以一个 token 里不能含「更短的常见搜索词」，
--      否则那个短词会误命中。踩过的坑：给「酒水」分类写 token「酒水」，结果搜「酒」
--      命中全部分类商品（因为 '酒水' LIKE '%酒%'）。同理不能写「酒类」。故只留「饮料/饮品」。
-- =============================================================================

ALTER TABLE `product`
    ADD COLUMN `search_keywords` VARCHAR(255) NULL DEFAULT NULL
        COMMENT '搜索补充关键词，英文逗号分隔（品类词/别名，如「手机,智能手机,苹果手机」）'
        AFTER `subtitle`;

-- -----------------------------------------------------------------------------
-- 1. 分类级：补该分类的口语名（分类 id → 词，见 V12 的分类定义）
-- -----------------------------------------------------------------------------
UPDATE `product` SET `search_keywords` = '数码,电子产品'            WHERE `category_id` = 1;  -- 手机数码
UPDATE `product` SET `search_keywords` = '鞋,鞋子'                  WHERE `category_id` = 5;  -- 鞋类
UPDATE `product` SET `search_keywords` = '衣服,服装,服饰'            WHERE `category_id` = 6;  -- 服饰
UPDATE `product` SET `search_keywords` = '护肤,彩妆,化妆品,美妆'      WHERE `category_id` = 7;  -- 护肤彩妆
UPDATE `product` SET `search_keywords` = '洗护,个护,清洁用品'         WHERE `category_id` = 8;  -- 个人洗护
UPDATE `product` SET `search_keywords` = '箱包,包包,背包,行李'        WHERE `category_id` = 9;  -- 箱包
UPDATE `product` SET `search_keywords` = '家居,百货,日用品,生活用品'   WHERE `category_id` = 10; -- 家居百货
UPDATE `product` SET `search_keywords` = '配饰,饰品,配件'            WHERE `category_id` = 11; -- 配饰
UPDATE `product` SET `search_keywords` = '运动,健身,体育'            WHERE `category_id` = 12; -- 运动
UPDATE `product` SET `search_keywords` = '手表,腕表'                WHERE `category_id` = 13; -- 手表
-- ★ 只写「饮料/饮品」，不写「酒水/酒类」：token 里含「酒」会让搜「酒」命中矿泉水
UPDATE `product` SET `search_keywords` = '饮料,饮品'                WHERE `category_id` = 14; -- 酒水
UPDATE `product` SET `search_keywords` = '医药,健康,医疗'            WHERE `category_id` = 15; -- 医药健康
UPDATE `product` SET `search_keywords` = '家电,电器,小家电'           WHERE `category_id` = 16; -- 家电
UPDATE `product` SET `search_keywords` = '模玩,盲盒,玩具,手办'        WHERE `category_id` = 17; -- 模玩盲盒
UPDATE `product` SET `search_keywords` = '宠物,宠物用品'             WHERE `category_id` = 18; -- 宠物
UPDATE `product` SET `search_keywords` = '母婴,婴儿,宝宝'            WHERE `category_id` = 19; -- 母婴
UPDATE `product` SET `search_keywords` = '汽车,车载,车品'            WHERE `category_id` = 20; -- 车类
UPDATE `product` SET `search_keywords` = '乐器,音乐'                WHERE `category_id` = 21; -- 乐器
UPDATE `product` SET `search_keywords` = '图书,书籍,书'              WHERE `category_id` = 22; -- 图书
UPDATE `product` SET `search_keywords` = '食品,生鲜,农产品'           WHERE `category_id` = 23; -- 食品生鲜

-- -----------------------------------------------------------------------------
-- 2. 商品级：补名字里没有、但用户会搜的别名
--    CONCAT_WS 会跳过 NULL，所以与上面的分类级词自动合并，不会互相覆盖。
-- -----------------------------------------------------------------------------

-- 手机：iPhone 系列商品名不含「手机」二字（种子库的 TickPhone 同理）
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '手机', '智能手机')
    WHERE `name` LIKE 'iPhone%' OR `name` LIKE 'TickPhone%';
-- 「苹果手机」只给真的 iPhone；TickPhone 是虚构品牌，给了会让搜水果的人搜出手机
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '苹果手机')
    WHERE `name` LIKE 'iPhone%';

-- 笔记本：种子库商品名是 TickBook Air 14
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '电脑', '笔记本')
    WHERE `name` LIKE 'TickBook%';

-- 裤子：库里只有牛仔裤，商品名不含「裤子」
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '裤子', '长裤')
    WHERE `name` LIKE '%牛仔裤%';

UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '裙子')
    WHERE `name` LIKE '%连衣裙%';

-- 行李箱：商品名是「20 寸登机拉杆箱」
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '行李箱')
    WHERE `name` LIKE '%拉杆箱%';

-- 首饰：项链/耳环/戒指的通用上位词（墨镜、腰带、围巾不算首饰，所以只补这三类）
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '项链', '首饰')
    WHERE `name` LIKE '%锁骨链%';
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '首饰')
    WHERE `name` LIKE '%耳环%' OR `name` LIKE '%戒指%';

-- 风扇：商品名是「桌面空气循环扇」
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '风扇')
    WHERE `name` LIKE '%循环扇%';

-- 尿不湿：商品名是「婴儿纸尿裤」
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '尿不湿')
    WHERE `name` LIKE '%纸尿裤%';

-- 蔬菜 / 水果：商品名里都没有这两个词（「红富士苹果」不含「水果」）
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '蔬菜')
    WHERE `name` LIKE '%西兰花%' OR `name` LIKE '%胡萝卜%';
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '水果')
    WHERE `name` LIKE '%苹果%' OR `name` LIKE '%榴莲%'
       OR `name` LIKE '%蓝莓%' OR `name` LIKE '%牛油果%';

-- 水杯 / 床品：日常口语词，商品名分别是「双层保温玻璃杯」「全棉四件套」
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '水杯')
    WHERE `name` LIKE '%保温玻璃杯%';
UPDATE `product` SET `search_keywords` = CONCAT_WS(',', `search_keywords`, '床单', '被套', '床品')
    WHERE `name` LIKE '%四件套%';
