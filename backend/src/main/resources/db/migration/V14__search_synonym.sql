-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V14
-- 内容：搜索同义词表 + 种子
--
-- 目的
--   解决「用户搜的词与商品里的词对不上」——典型：搜「苹果手机」搜不到 iPhone 系列
--   （商品名是 iPhone17 / iPhone18 Pro …）。实测「苹果手机 / 笔记本电脑 / 无线耳机 /
--   唇膏」在引入本表前都是 0 结果，而它们的同义词能搜到。
--
-- 结构：同义词「组」模型
--   同一 group_id 下的词互为同义词（天然对称、天然 1:N），每个词只存一行。
--   一个词只归属一个组（uk_word）；查询时先按关键词找到组，再取全组词作为「展开词」。
--   为什么不用「词对(A→B)」模型：同义词是对称关系，词对模型要双向存两行、
--   n 个词两两同义要 n×(n-1) 行，新增一个词还要补 n 行。
--
-- 匹配口径
--   关键词与 word 整词相等（utf8mb4_general_ci → 大小写不敏感）才展开；
--   关键词不在表里时，搜索行为与引入本表前**完全一致**。
-- =============================================================================

CREATE TABLE `search_synonym` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_id`   INT UNSIGNED    NOT NULL                COMMENT '同义词组：同组内的词互为同义词',
    `word`       VARCHAR(64)     NOT NULL                COMMENT '词条',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`),
    KEY `idx_group` (`group_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT '搜索同义词表';

-- 种子：同组内互为同义词。
-- ★ 刻意不放「苹果」这种有歧义的词：库里既有 iPhone（品牌），也有「红富士苹果」（水果），
--   把「苹果」并进手机组会让搜水果的人搜出手机。
INSERT INTO `search_synonym` (`group_id`, `word`) VALUES
(1, '苹果手机'), (1, 'iPhone'),
(2, '笔记本电脑'), (2, '笔记本'), (2, '电脑'),
(3, '无线耳机'), (3, '蓝牙耳机'), (3, '耳机'),
(4, '唇膏'), (4, '口红');
