-- =============================================================================
-- TickCart 秒杀商城 · 数据库迁移 V23
-- 内容：补齐商品详情的图文内容、图集与款式图
--
-- 背景
--   详情页的「商品详情」区此前只有一行字：商品 1/2/6/7 的 detail 为空（显示「暂无详情内容」），
--   201~302 是一句「本商品为演示数据…」。同时 201~302 在 product_image 里一行都没有，
--   图集只有主图一张；product_sku.sku_image 有 356 行为 NULL，款式卡片上没有款式图。
--
--   本迁移按「分类模板 + 商品自身卖点/规格」为每个商品组装图文详情（HTML），
--   并补图集与款式图。详情 HTML 由 scripts/gen-product-detail-migration.py 生成，
--   内容只用到库里已有的字段；分类级通用说明（使用建议、售后条款）为模板文案。
--
-- 幂等性
--   · detail 只在「空」或仍是占位文案时覆盖 —— 后台手工改过的详情不会被冲掉。
--   · 图集按「该商品已有任何图集行就整批跳过」守卫（沿用 V9 的写法）。
--   · sku_image 只在 NULL/空串时写入。
--
-- 款式图口径
--   尺码类规格（鞋服的「尺码」「均码」）**不配款式图**：同一件衣服的 M 码与 L 码
--   外观完全一致，逐码配图会让人误以为「换个尺码换个样子」。详情页对这类规格
--   回退显示商品主图，见 ProductDetailView.vue 的 skuThumb()。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 图文详情（HTML）
-- -----------------------------------------------------------------------------
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>旗舰影像</li><li>全新上市</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/iphone_17__fb1277oq3eaa_large_2x.jpg" alt="iPhone17"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>黑色 256G / 白色 256G / 原色钛 512G</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 1 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/iphone_18_pro__dj48ysc0yagm_large_2x.jpg" alt="iPhone18 Pro"><section class="pd-block"><h3>规格参数</h3><table><tr><th>内存</th><td>16G+512G / 16G+1T</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 2 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/iphone_air__b5qmgl05ojyq_large_2x.jpg" alt="iPhone Air"><section class="pd-block"><h3>规格参数</h3><table><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 6 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/iphone_duo__hefmlegzwbmi_large_2x.jpg" alt="iPhone Duo"><section class="pd-block"><h3>规格参数</h3><table><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 7 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>骁龙旗舰芯片</li><li>超清三摄</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-01.jpg" alt="5G 旗舰智能手机 12+256G 曜夜黑"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>12+256G / 16+512G</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 101 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>轻至 168g</li><li>长续航</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-02.jpg" alt="5G 轻薄智能手机 8+128G 星空灰"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>8+128G / 8+256G</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 102 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>120Hz 高刷</li><li>快充 67W</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-03.jpg" alt="全面屏智能手机 8+256G 赤焰红"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>8+256G / 12+256G</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 103 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>2.8K 护眼屏</li><li>全金属机身</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-04.jpg" alt="轻薄办公笔记本 14 英寸 16G+512G"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>16G+512G / 32G+1T</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 104 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>独显直连</li><li>165Hz 电竞屏</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-05.jpg" alt="高性能游戏笔记本 15.6 英寸 32G+1T"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>32G+1T / 32G+2T</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 105 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>4.5K 视网膜屏</li><li>简约桌面</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-06.jpg" alt="一体机电脑 24 英寸 16G+512G"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>16G+512G / 24G+1T</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 106 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>120Hz 全面屏</li><li>支持手写笔</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-07.jpg" alt="平板电脑 11 英寸 8+256G"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>8+256G / 12+512G</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 107 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>轻薄便携</li><li>影音办公两用</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-08.jpg" alt="平板电脑 10.9 英寸 6+128G"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>6+128G / 8+256G</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 108 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>14 天续航</li><li>100+ 运动模式</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-09.jpg" alt="智能运动手表 血氧心率监测"><section class="pd-block"><h3>规格参数</h3><table><tr><th>表带</th><td>硅胶黑 / 尼龙灰</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 109 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>主动降噪</li><li>40 小时续航</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-10.jpg" alt="头戴式无线降噪耳机"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>曜石黑 / 月光白</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 110 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>通透模式</li><li>无线充电盒</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-11.jpg" alt="真无线主动降噪耳机"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>白色 / 黑色</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 111 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>半入耳舒适佩戴</li><li>低延迟</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-12.jpg" alt="真无线蓝牙耳机 长续航"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>白色 / 粉色</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 112 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>IPX7 防水</li><li>12 小时播放</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-13.jpg" alt="便携式蓝牙音箱 防水低音炮"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>黑色 / 蓝色</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 113 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>多设备切换</li><li>长续航</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-14.jpg" alt="无线蓝牙键盘 超薄静音"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>深空灰 / 银色</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 114 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>静音微动</li><li>双模连接</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-15.jpg" alt="无线静音鼠标 人体工学"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>白色 / 黑色</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 115 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>五轴防抖</li><li>4K 视频</li><li>出厂预装正版系统，开箱即用</li><li>全国联保一年，官方售后网点可查</li></ul></section><img src="/img/phones/phone-16.jpg" alt="微单相机 2420 万像素 套机"><section class="pd-block"><h3>规格参数</h3><table><tr><th>套装</th><td>单机身 / 含 15-45 镜头</td></tr><tr><th>包装清单</th><td>主机 × 1、数据线 × 1、说明书 × 1</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 116 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>经典高帮</li><li>百搭耐磨</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-01.jpg" alt="复古高帮板鞋 白色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 117 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>回弹中底</li><li>透气网面</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-02.jpg" alt="轻量缓震跑鞋 撞色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 118 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>一脚蹬设计</li><li>商务休闲</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-03.jpg" alt="头层牛皮乐福鞋 深蓝"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 119 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>手工缝线</li><li>真皮内里</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-04.jpg" alt="英伦风布洛克皮鞋 棕色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 120 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>全白百搭</li><li>四季可穿</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-05.jpg" alt="经典低帮小白鞋"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 121 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>全掌气垫</li><li>轻弹舒适</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-06.jpg" alt="气垫缓震运动鞋 橙黄"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 122 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>厚底防滑</li><li>秋冬必备</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-07.jpg" alt="复古工装马丁靴 棕色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 123 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>硫化工艺</li><li>街头风</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-08.jpg" alt="经典帆布滑板鞋 酒红"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 124 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>系带设计</li><li>沉稳有型</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-09.jpg" alt="商务正装皮鞋 棕色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 125 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>轻量碳板</li><li>专业竞速</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-10.jpg" alt="竞速跑步鞋 全掌回弹"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 126 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>7cm 细跟</li><li>优雅气质</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-11.jpg" alt="时尚尖头高跟鞋 印花"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>35 / 36 / 37 / 38</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 127 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>绒面质感</li><li>日常百搭</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-12.jpg" alt="休闲板鞋 卡其棕"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 128 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>5cm 厚底</li><li>显高显瘦</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-13.jpg" alt="厚底增高运动鞋 粉白"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>35 / 36 / 37 / 38</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 129 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>实战缓震</li><li>包裹支撑</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-14.jpg" alt="高帮篮球鞋 黑红"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>40 / 41 / 42 / 43</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 130 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>复古轮廓</li><li>潮流配色</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-15.jpg" alt="街头潮流运动鞋"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 131 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>复古老爹风</li><li>厚底舒适</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-16.jpg" alt="拼色老爹鞋"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>35 / 36 / 37 / 38</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 132 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>飞织鞋面</li><li>轻盈透气</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-17.jpg" alt="针织透气运动鞋 灰色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 133 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>头层牛皮</li><li>简约百搭</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-18.jpg" alt="简约真皮小白鞋"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>39 / 40 / 41 / 42</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 134 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>撞色厚底</li><li>少女感十足</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-19.jpg" alt="马卡龙厚底休闲鞋"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>35 / 36 / 37 / 38</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 135 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>抗扭转支撑</li><li>耐磨大底</li><li>鞋楦按亚洲脚型开发，久走不夹脚</li><li>鞋底耐磨橡胶配方，湿滑地面也稳</li></ul></section><img src="/img/shoes/shoe-20.jpg" alt="篮球实战鞋 黑白红"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>40 / 41 / 42 / 43</td></tr><tr><th>鞋面材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>日常通勤 / 休闲出行</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，不要靠近热源烘烤。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 136 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>260g 精梳棉</li><li>不透不闷</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-01.jpg" alt="纯棉圆领短袖T恤 白色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 137 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>原创印花</li><li>宽松版型</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-02.jpg" alt="印花短袖T恤 米白"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 138 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>基础百搭</li><li>四季可穿</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-03.jpg" alt="简约纯色短袖T恤 黑色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 139 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>弹力面料</li><li>修身显瘦</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-04.jpg" alt="修身圆领T恤 藕粉"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 140 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>抓绒内里</li><li>秋冬保暖</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-05.jpg" alt="纯棉圆领卫衣 白色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 141 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>慵懒风外搭</li><li>柔软亲肤</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-06.jpg" alt="针织流苏披肩 米白"><section class="pd-block"><h3>规格参数</h3><table><tr><th>均码</th><td>均码</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 142 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>做旧水洗</li><li>复古百搭</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-07.jpg" alt="水洗牛仔外套"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 143 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>PU 皮料</li><li>帅气机车风</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-08.jpg" alt="机车皮衣外套 黑色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 144 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>做旧皮面</li><li>硬朗有型</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-09.jpg" alt="复古皮夹克 棕色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 145 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>合身剪裁</li><li>通勤可穿</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-10.jpg" alt="修身皮衣外套 咖色"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 146 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>多口袋设计</li><li>宽松廓形</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-11.jpg" alt="宽松工装夹克 焦糖"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 147 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>经典翻领</li><li>垂顺挺括</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-12.jpg" alt="中长款风衣外套 军绿"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 148 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>羊毛混纺</li><li>保暖显气质</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-13.jpg" alt="长款毛呢大衣 雾蓝"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 149 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>垂坠感面料</li><li>显瘦显高</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-14.jpg" alt="优雅长款连衣裙 正红"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>S / M / L</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 150 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>显腿长</li><li>微弹舒适</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-15.jpg" alt="高腰直筒牛仔裤"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>26 / 27 / 28 / 29</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 151 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>做旧破洞</li><li>街头风</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-16.jpg" alt="破洞修身牛仔裤"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>26 / 27 / 28 / 29</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 152 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>水洗做旧</li><li>单穿外搭均可</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-17.jpg" alt="复古牛仔衬衫"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 153 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>抗皱免烫</li><li>通勤必备</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-18.jpg" alt="商务免烫衬衫 三件装"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 154 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>柔顺面料</li><li>日常百搭</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-19.jpg" alt="纯棉基础衬衫 豆绿"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 155 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>柔软亲肤</li><li>简约百搭</li><li>版型经过多次试身调整，上身不显臃肿</li><li>面料亲肤，可机洗不易变形</li></ul></section><img src="/img/clothing/clothing-20.jpg" alt="秋季针织衫 多色可选"><section class="pd-block"><h3>规格参数</h3><table><tr><th>尺码</th><td>M / L / XL</td></tr><tr><th>面料成分</th><td>按款式而定，详见商品标题</td></tr><tr><th>洗涤方式</th><td>机洗冷水，反面洗涤</td></tr></table></section><section class="pd-block"><h3>使用与保养</h3><p>深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，避免暴晒。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 156 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>绵密泡沫</li><li>敏感肌适用</li><li>配方温和，日常护理可长期使用</li><li>质地细腻好推开，上脸不搓泥</li></ul></section><img src="/img/skincare/201-1.jpg" alt="氨基酸温和洁面乳"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>150ml / 250ml</td></tr><tr><th>适用肤质</th><td>各种肤质</td></tr><tr><th>保质期</th><td>未开封 3 年，开封后建议 12 个月内用完</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/skincare/201-2.jpg" alt="氨基酸温和洁面乳"><section class="pd-block"><h3>使用与保养</h3><p>取适量于掌心，均匀涂抹于清洁后的肌肤并轻拍至吸收；首次使用建议先在耳后小面积试用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 201 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>提亮肤色</li><li>淡化痘印</li><li>配方温和，日常护理可长期使用</li><li>质地细腻好推开，上脸不搓泥</li></ul></section><img src="/img/skincare/202-1.jpg" alt="烟酰胺美白精华液"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>30ml / 50ml</td></tr><tr><th>适用肤质</th><td>各种肤质</td></tr><tr><th>保质期</th><td>未开封 3 年，开封后建议 12 个月内用完</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/skincare/202-2.jpg" alt="烟酰胺美白精华液"><section class="pd-block"><h3>使用与保养</h3><p>取适量于掌心，均匀涂抹于清洁后的肌肤并轻拍至吸收；首次使用建议先在耳后小面积试用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 202 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>长效锁水</li><li>清爽不黏腻</li><li>配方温和，日常护理可长期使用</li><li>质地细腻好推开，上脸不搓泥</li></ul></section><img src="/img/skincare/203-1.jpg" alt="玻尿酸补水保湿面霜"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>50g / 100g</td></tr><tr><th>适用肤质</th><td>各种肤质</td></tr><tr><th>保质期</th><td>未开封 3 年，开封后建议 12 个月内用完</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/skincare/203-2.jpg" alt="玻尿酸补水保湿面霜"><section class="pd-block"><h3>使用与保养</h3><p>取适量于掌心，均匀涂抹于清洁后的肌肤并轻拍至吸收；首次使用建议先在耳后小面积试用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 203 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>淡化细纹</li><li>紧致眼周</li><li>配方温和，日常护理可长期使用</li><li>质地细腻好推开，上脸不搓泥</li></ul></section><img src="/img/skincare/204-1.jpg" alt="视黄醇紧致抗皱眼霜"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>15g / 30g</td></tr><tr><th>适用肤质</th><td>各种肤质</td></tr><tr><th>保质期</th><td>未开封 3 年，开封后建议 12 个月内用完</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/skincare/204-2.jpg" alt="视黄醇紧致抗皱眼霜"><section class="pd-block"><h3>使用与保养</h3><p>取适量于掌心，均匀涂抹于清洁后的肌肤并轻拍至吸收；首次使用建议先在耳后小面积试用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 204 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>显白不拔干</li><li>持久显色</li><li>配方温和，日常护理可长期使用</li><li>质地细腻好推开，上脸不搓泥</li></ul></section><img src="/img/skincare/205-1.jpg" alt="丝绒哑光口红"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>正红色 / 豆沙色</td></tr><tr><th>适用肤质</th><td>各种肤质</td></tr><tr><th>保质期</th><td>未开封 3 年，开封后建议 12 个月内用完</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/skincare/205-2.jpg" alt="丝绒哑光口红"><section class="pd-block"><h3>使用与保养</h3><p>取适量于掌心，均匀涂抹于清洁后的肌肤并轻拍至吸收；首次使用建议先在耳后小面积试用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 205 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>轻薄遮瑕</li><li>12 小时不脱妆</li><li>配方温和，日常护理可长期使用</li><li>质地细腻好推开，上脸不搓泥</li></ul></section><img src="/img/skincare/206-1.jpg" alt="控油持妆粉底液"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>自然色 / 象牙白</td></tr><tr><th>适用肤质</th><td>各种肤质</td></tr><tr><th>保质期</th><td>未开封 3 年，开封后建议 12 个月内用完</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/skincare/206-2.jpg" alt="控油持妆粉底液"><section class="pd-block"><h3>使用与保养</h3><p>取适量于掌心，均匀涂抹于清洁后的肌肤并轻拍至吸收；首次使用建议先在耳后小面积试用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 206 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>蓬松去屑</li><li>温和清洁</li><li>泡沫细腻易冲洗，洗后不假滑</li><li>淡香配方，留香自然不冲鼻</li></ul></section><img src="/img/bath/207-1.jpg" alt="氨基酸控油洗发水"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>500ml / 750ml</td></tr><tr><th>适用人群</th><td>成人通用</td></tr><tr><th>保质期</th><td>未开封 3 年</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/bath/207-2.jpg" alt="氨基酸控油洗发水"><section class="pd-block"><h3>使用与保养</h3><p>取适量加水揉搓起泡后使用，再用清水充分冲净；避免接触眼睛，不慎入眼请立即用清水冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 207 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>抚平毛躁</li><li>柔顺易梳</li><li>泡沫细腻易冲洗，洗后不假滑</li><li>淡香配方，留香自然不冲鼻</li></ul></section><img src="/img/bath/208-1.jpg" alt="修护柔顺护发素"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>500ml / 750ml</td></tr><tr><th>适用人群</th><td>成人通用</td></tr><tr><th>保质期</th><td>未开封 3 年</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/bath/208-2.jpg" alt="修护柔顺护发素"><section class="pd-block"><h3>使用与保养</h3><p>取适量加水揉搓起泡后使用，再用清水充分冲净；避免接触眼睛，不慎入眼请立即用清水冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 208 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>保湿滋润</li><li>留香持久</li><li>泡沫细腻易冲洗，洗后不假滑</li><li>淡香配方，留香自然不冲鼻</li></ul></section><img src="/img/bath/209-1.jpg" alt="氨基酸沐浴露"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>750ml / 1L</td></tr><tr><th>适用人群</th><td>成人通用</td></tr><tr><th>保质期</th><td>未开封 3 年</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/bath/209-2.jpg" alt="氨基酸沐浴露"><section class="pd-block"><h3>使用与保养</h3><p>取适量加水揉搓起泡后使用，再用清水充分冲净；避免接触眼睛，不慎入眼请立即用清水冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 209 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>温和去角质</li><li>嫩滑肌肤</li><li>泡沫细腻易冲洗，洗后不假滑</li><li>淡香配方，留香自然不冲鼻</li></ul></section><img src="/img/bath/210-1.jpg" alt="海盐净肤磨砂膏"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>250g / 400g</td></tr><tr><th>适用人群</th><td>成人通用</td></tr><tr><th>保质期</th><td>未开封 3 年</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/bath/210-2.jpg" alt="海盐净肤磨砂膏"><section class="pd-block"><h3>使用与保养</h3><p>取适量加水揉搓起泡后使用，再用清水充分冲净；避免接触眼睛，不慎入眼请立即用清水冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 210 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>滋润不黏腻</li><li>留香 8 小时</li><li>泡沫细腻易冲洗，洗后不假滑</li><li>淡香配方，留香自然不冲鼻</li></ul></section><img src="/img/bath/211-1.jpg" alt="清新持久香氛身体乳"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>300ml / 500ml</td></tr><tr><th>适用人群</th><td>成人通用</td></tr><tr><th>保质期</th><td>未开封 3 年</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/bath/211-2.jpg" alt="清新持久香氛身体乳"><section class="pd-block"><h3>使用与保养</h3><p>取适量加水揉搓起泡后使用，再用清水充分冲净；避免接触眼睛，不慎入眼请立即用清水冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 211 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>清新口气</li><li>呵护牙龈</li><li>泡沫细腻易冲洗，洗后不假滑</li><li>淡香配方，留香自然不冲鼻</li></ul></section><img src="/img/bath/212-1.jpg" alt="深层洁净牙膏"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>120g 单支 / 120g 两支装</td></tr><tr><th>适用人群</th><td>成人通用</td></tr><tr><th>保质期</th><td>未开封 3 年</td></tr><tr><th>储存方式</th><td>阴凉干燥处保存，避免阳光直射</td></tr></table></section><img src="/img/bath/212-2.jpg" alt="深层洁净牙膏"><section class="pd-block"><h3>使用与保养</h3><p>取适量加水揉搓起泡后使用，再用清水充分冲净；避免接触眼睛，不慎入眼请立即用清水冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 212 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>大容量</li><li>可装 14 寸笔记本</li><li>内部分区合理，日常通勤与短途出行都能装</li><li>五金件与走线加固，长期使用不易开线</li></ul></section><img src="/img/bags/213-1.jpg" alt="头层牛皮通勤托特包"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>黑色 / 焦糖棕</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>通勤 / 出差 / 旅行</td></tr></table></section><img src="/img/bags/213-2.jpg" alt="头层牛皮通勤托特包"><section class="pd-block"><h3>使用与保养</h3><p>避免长时间受潮与暴晒；皮具沾水后及时用干布吸干，定期使用专用护理剂保养可延长寿命。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 213 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>防泼水</li><li>多隔层收纳</li><li>内部分区合理，日常通勤与短途出行都能装</li><li>五金件与走线加固，长期使用不易开线</li></ul></section><img src="/img/bags/214-1.jpg" alt="轻便尼龙双肩背包"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>黑色 / 雾霾蓝</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>通勤 / 出差 / 旅行</td></tr></table></section><img src="/img/bags/214-2.jpg" alt="轻便尼龙双肩背包"><section class="pd-block"><h3>使用与保养</h3><p>避免长时间受潮与暴晒；皮具沾水后及时用干布吸干，定期使用专用护理剂保养可延长寿命。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 214 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>百搭经典</li><li>单肩斜挎两用</li><li>内部分区合理，日常通勤与短途出行都能装</li><li>五金件与走线加固，长期使用不易开线</li></ul></section><img src="/img/bags/215-1.jpg" alt="复古真皮斜挎小方包"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>棕色 / 黑色</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>通勤 / 出差 / 旅行</td></tr></table></section><img src="/img/bags/215-2.jpg" alt="复古真皮斜挎小方包"><section class="pd-block"><h3>使用与保养</h3><p>避免长时间受潮与暴晒；皮具沾水后及时用干布吸干，定期使用专用护理剂保养可延长寿命。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 215 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>万向静音轮</li><li>TSA 密码锁</li><li>内部分区合理，日常通勤与短途出行都能装</li><li>五金件与走线加固，长期使用不易开线</li></ul></section><img src="/img/bags/216-1.jpg" alt="20 寸登机拉杆箱"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>银色 / 深空灰</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>通勤 / 出差 / 旅行</td></tr></table></section><img src="/img/bags/216-2.jpg" alt="20 寸登机拉杆箱"><section class="pd-block"><h3>使用与保养</h3><p>避免长时间受潮与暴晒；皮具沾水后及时用干布吸干，定期使用专用护理剂保养可延长寿命。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 216 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>简约耐用</li><li>日常通勤</li><li>内部分区合理，日常通勤与短途出行都能装</li><li>五金件与走线加固，长期使用不易开线</li></ul></section><img src="/img/bags/217-1.jpg" alt="大容量帆布单肩包"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>米白 / 军绿</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>通勤 / 出差 / 旅行</td></tr></table></section><img src="/img/bags/217-2.jpg" alt="大容量帆布单肩包"><section class="pd-block"><h3>使用与保养</h3><p>避免长时间受潮与暴晒；皮具沾水后及时用干布吸干，定期使用专用护理剂保养可延长寿命。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 217 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>多卡位</li><li>轻薄便携</li><li>内部分区合理，日常通勤与短途出行都能装</li><li>五金件与走线加固，长期使用不易开线</li></ul></section><img src="/img/bags/218-1.jpg" alt="简约真皮长款钱包"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>黑色 / 深棕</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>通勤 / 出差 / 旅行</td></tr></table></section><img src="/img/bags/218-2.jpg" alt="简约真皮长款钱包"><section class="pd-block"><h3>使用与保养</h3><p>避免长时间受潮与暴晒；皮具沾水后及时用干布吸干，定期使用专用护理剂保养可延长寿命。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 218 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>家用碗盘</li><li>微波炉可用</li><li>尺寸经过实测，日常使用不占地方</li><li>材质安全，接触食物/皮肤的部分均符合国标</li></ul></section><img src="/img/home/219-1.jpg" alt="北欧简约陶瓷餐具套装"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>4 人份 / 6 人份</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>家庭日常使用</td></tr></table></section><img src="/img/home/219-2.jpg" alt="北欧简约陶瓷餐具套装"><section class="pd-block"><h3>使用与保养</h3><p>首次使用前请清洗干净；避免骤冷骤热，以免材质开裂；带有涂层或木质的器具请勿使用钢丝球刷洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 219 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>少油不粘</li><li>燃气电磁炉通用</li><li>尺寸经过实测，日常使用不占地方</li><li>材质安全，接触食物/皮肤的部分均符合国标</li></ul></section><img src="/img/home/220-1.jpg" alt="不粘锅炒锅"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>30cm / 32cm</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>家庭日常使用</td></tr></table></section><img src="/img/home/220-2.jpg" alt="不粘锅炒锅"><section class="pd-block"><h3>使用与保养</h3><p>首次使用前请清洗干净；避免骤冷骤热，以免材质开裂；带有涂层或木质的器具请勿使用钢丝球刷洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 220 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>隔热防烫</li><li>办公家用</li><li>尺寸经过实测，日常使用不占地方</li><li>材质安全，接触食物/皮肤的部分均符合国标</li></ul></section><img src="/img/home/221-1.jpg" alt="双层保温玻璃杯"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>400ml / 500ml</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>家庭日常使用</td></tr></table></section><img src="/img/home/221-2.jpg" alt="双层保温玻璃杯"><section class="pd-block"><h3>使用与保养</h3><p>首次使用前请清洗干净；避免骤冷骤热，以免材质开裂；带有涂层或木质的器具请勿使用钢丝球刷洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 221 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>护颈回弹</li><li>透气防螨</li><li>尺寸经过实测，日常使用不占地方</li><li>材质安全，接触食物/皮肤的部分均符合国标</li></ul></section><img src="/img/home/222-1.jpg" alt="天然乳胶枕头"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>单只装 / 一对装</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>家庭日常使用</td></tr></table></section><img src="/img/home/222-2.jpg" alt="天然乳胶枕头"><section class="pd-block"><h3>使用与保养</h3><p>首次使用前请清洗干净；避免骤冷骤热，以免材质开裂；带有涂层或木质的器具请勿使用钢丝球刷洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 222 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>亲肤透气</li><li>裸睡舒适</li><li>尺寸经过实测，日常使用不占地方</li><li>材质安全，接触食物/皮肤的部分均符合国标</li></ul></section><img src="/img/home/223-1.jpg" alt="全棉四件套"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>1.5m 床 / 1.8m 床</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>家庭日常使用</td></tr></table></section><img src="/img/home/223-2.jpg" alt="全棉四件套"><section class="pd-block"><h3>使用与保养</h3><p>首次使用前请清洗干净；避免骤冷骤热，以免材质开裂；带有涂层或木质的器具请勿使用钢丝球刷洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 223 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>可折叠</li><li>大容量储物</li><li>尺寸经过实测，日常使用不占地方</li><li>材质安全，接触食物/皮肤的部分均符合国标</li></ul></section><img src="/img/home/224-1.jpg" alt="多功能收纳整理箱"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>45L / 66L</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>适用场景</th><td>家庭日常使用</td></tr></table></section><img src="/img/home/224-2.jpg" alt="多功能收纳整理箱"><section class="pd-block"><h3>使用与保养</h3><p>首次使用前请清洗干净；避免骤冷骤热，以免材质开裂；带有涂层或木质的器具请勿使用钢丝球刷洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 224 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>百搭日常</li><li>不易过敏</li><li>细节打磨到位，日常佩戴不勾衣物</li><li>附赠收纳盒，送礼自用都合适</li></ul></section><img src="/img/accessories/225-1.jpg" alt="925 银简约锁骨链"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>银色 / 金色</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>保养</th><td>避免接触香水与汗液</td></tr></table></section><img src="/img/accessories/225-2.jpg" alt="925 银简约锁骨链"><section class="pd-block"><h3>使用与保养</h3><p>佩戴后建议用软布擦拭表面再收纳；避免与硬物碰撞，长期不戴时请密封保存以防氧化。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 225 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>优雅气质</li><li>通勤必备</li><li>细节打磨到位，日常佩戴不勾衣物</li><li>附赠收纳盒，送礼自用都合适</li></ul></section><img src="/img/accessories/226-1.jpg" alt="天然淡水珍珠耳环"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>白色 / 淡粉</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>保养</th><td>避免接触香水与汗液</td></tr></table></section><img src="/img/accessories/226-2.jpg" alt="天然淡水珍珠耳环"><section class="pd-block"><h3>使用与保养</h3><p>佩戴后建议用软布擦拭表面再收纳；避免与硬物碰撞，长期不戴时请密封保存以防氧化。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 226 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>防紫外线</li><li>显脸小</li><li>细节打磨到位，日常佩戴不勾衣物</li><li>附赠收纳盒，送礼自用都合适</li></ul></section><img src="/img/accessories/227-1.jpg" alt="复古方形偏光墨镜"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>黑框 / 茶框</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>保养</th><td>避免接触香水与汗液</td></tr></table></section><img src="/img/accessories/227-2.jpg" alt="复古方形偏光墨镜"><section class="pd-block"><h3>使用与保养</h3><p>佩戴后建议用软布擦拭表面再收纳；避免与硬物碰撞，长期不戴时请密封保存以防氧化。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 227 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>商务休闲</li><li>百搭款</li><li>细节打磨到位，日常佩戴不勾衣物</li><li>附赠收纳盒，送礼自用都合适</li></ul></section><img src="/img/accessories/228-1.jpg" alt="头层牛皮针扣腰带"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>黑色 / 棕色</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>保养</th><td>避免接触香水与汗液</td></tr></table></section><img src="/img/accessories/228-2.jpg" alt="头层牛皮针扣腰带"><section class="pd-block"><h3>使用与保养</h3><p>佩戴后建议用软布擦拭表面再收纳；避免与硬物碰撞，长期不戴时请密封保存以防氧化。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 228 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>柔软亲肤</li><li>秋冬必备</li><li>细节打磨到位，日常佩戴不勾衣物</li><li>附赠收纳盒，送礼自用都合适</li></ul></section><img src="/img/accessories/229-1.jpg" alt="羊毛针织保暖围巾"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>米白 / 酒红</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>保养</th><td>避免接触香水与汗液</td></tr></table></section><img src="/img/accessories/229-2.jpg" alt="羊毛针织保暖围巾"><section class="pd-block"><h3>使用与保养</h3><p>佩戴后建议用软布擦拭表面再收纳；避免与硬物碰撞，长期不戴时请密封保存以防氧化。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 229 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>不易变形</li><li>可调节</li><li>细节打磨到位，日常佩戴不勾衣物</li><li>附赠收纳盒，送礼自用都合适</li></ul></section><img src="/img/accessories/230-1.jpg" alt="纯钛简约情侣戒指"><section class="pd-block"><h3>规格参数</h3><table><tr><th>颜色</th><td>银色 / 枪灰</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>保养</th><td>避免接触香水与汗液</td></tr></table></section><img src="/img/accessories/230-2.jpg" alt="纯钛简约情侣戒指"><section class="pd-block"><h3>使用与保养</h3><p>佩戴后建议用软布擦拭表面再收纳；避免与硬物碰撞，长期不戴时请密封保存以防氧化。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 230 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>家用健身</li><li>防滑握把</li><li>家用场景友好，收纳不占地</li><li>握感与防滑做过实测，出汗也不打滑</li></ul></section><img src="/img/sports/231-1.jpg" alt="可调节哑铃"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>单只 10kg / 一对 20kg</td></tr><tr><th>适用场景</th><td>居家健身 / 户外运动</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr></table></section><img src="/img/sports/231-2.jpg" alt="可调节哑铃"><section class="pd-block"><h3>使用与保养</h3><p>运动前请充分热身；器械类请定期检查连接件是否松动，出现破损立即停止使用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 231 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>回弹护膝</li><li>便于收纳</li><li>家用场景友好，收纳不占地</li><li>握感与防滑做过实测，出汗也不打滑</li></ul></section><img src="/img/sports/232-1.jpg" alt="加厚防滑瑜伽垫"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>6mm / 8mm</td></tr><tr><th>适用场景</th><td>居家健身 / 户外运动</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr></table></section><img src="/img/sports/232-2.jpg" alt="加厚防滑瑜伽垫"><section class="pd-block"><h3>使用与保养</h3><p>运动前请充分热身；器械类请定期检查连接件是否松动，出现破损立即停止使用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 232 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>防滑耐磨</li><li>室内外通用</li><li>家用场景友好，收纳不占地</li><li>握感与防滑做过实测，出汗也不打滑</li></ul></section><img src="/img/sports/233-1.jpg" alt="专业比赛用篮球"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>6 号 / 7 号</td></tr><tr><th>适用场景</th><td>居家健身 / 户外运动</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr></table></section><img src="/img/sports/233-2.jpg" alt="专业比赛用篮球"><section class="pd-block"><h3>使用与保养</h3><p>运动前请充分热身；器械类请定期检查连接件是否松动，出现破损立即停止使用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 233 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>五档阻力</li><li>塑形训练</li><li>家用场景友好，收纳不占地</li><li>握感与防滑做过实测，出汗也不打滑</li></ul></section><img src="/img/sports/234-1.jpg" alt="弹力阻力带套装"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>基础款 3 档 / 进阶款 5 档</td></tr><tr><th>适用场景</th><td>居家健身 / 户外运动</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr></table></section><img src="/img/sports/234-2.jpg" alt="弹力阻力带套装"><section class="pd-block"><h3>使用与保养</h3><p>运动前请充分热身；器械类请定期检查连接件是否松动，出现破损立即停止使用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 234 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>轴承顺滑</li><li>无绳可选</li><li>家用场景友好，收纳不占地</li><li>握感与防滑做过实测，出汗也不打滑</li></ul></section><img src="/img/sports/235-1.jpg" alt="轻量跳绳"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>有绳款 / 无绳款</td></tr><tr><th>适用场景</th><td>居家健身 / 户外运动</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr></table></section><img src="/img/sports/235-2.jpg" alt="轻量跳绳"><section class="pd-block"><h3>使用与保养</h3><p>运动前请充分热身；器械类请定期检查连接件是否松动，出现破损立即停止使用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 235 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>透气排汗</li><li>轻薄舒适</li><li>家用场景友好，收纳不占地</li><li>握感与防滑做过实测，出汗也不打滑</li></ul></section><img src="/img/sports/236-1.jpg" alt="运动速干 T 恤"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>M 码 / L 码</td></tr><tr><th>适用场景</th><td>居家健身 / 户外运动</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr></table></section><img src="/img/sports/236-2.jpg" alt="运动速干 T 恤"><section class="pd-block"><h3>使用与保养</h3><p>运动前请充分热身；器械类请定期检查连接件是否松动，出现破损立即停止使用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 236 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>30 米防水</li><li>日历显示</li><li>走时稳定，日常佩戴误差小</li><li>表带可自行更换，通勤运动换着戴</li></ul></section><img src="/img/watches/237-1.jpg" alt="商务石英腕表"><section class="pd-block"><h3>规格参数</h3><table><tr><th>表带</th><td>精钢表带 / 真皮表带</td></tr><tr><th>防水等级</th><td>生活防水，避免游泳与热水淋浴</td></tr><tr><th>包装清单</th><td>手表 × 1、说明书 × 1</td></tr></table></section><img src="/img/watches/237-2.jpg" alt="商务石英腕表"><section class="pd-block"><h3>使用与保养</h3><p>避免在热水澡、桑拿等高温高湿环境佩戴；石英表建议 1~2 年更换一次电池，长期不戴请取出电池。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 237 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>极简表盘</li><li>通勤百搭</li><li>走时稳定，日常佩戴误差小</li><li>表带可自行更换，通勤运动换着戴</li></ul></section><img src="/img/watches/238-1.jpg" alt="简约超薄石英表"><section class="pd-block"><h3>规格参数</h3><table><tr><th>表带</th><td>黑色皮带 / 棕色皮带</td></tr><tr><th>防水等级</th><td>生活防水，避免游泳与热水淋浴</td></tr><tr><th>包装清单</th><td>手表 × 1、说明书 × 1</td></tr></table></section><img src="/img/watches/238-2.jpg" alt="简约超薄石英表"><section class="pd-block"><h3>使用与保养</h3><p>避免在热水澡、桑拿等高温高湿环境佩戴；石英表建议 1~2 年更换一次电池，长期不戴请取出电池。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 238 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>计时夜光</li><li>50 米防水</li><li>走时稳定，日常佩戴误差小</li><li>表带可自行更换，通勤运动换着戴</li></ul></section><img src="/img/watches/239-1.jpg" alt="户外运动电子表"><section class="pd-block"><h3>规格参数</h3><table><tr><th>表带</th><td>黑色 / 军绿</td></tr><tr><th>防水等级</th><td>生活防水，避免游泳与热水淋浴</td></tr><tr><th>包装清单</th><td>手表 × 1、说明书 × 1</td></tr></table></section><img src="/img/watches/239-2.jpg" alt="户外运动电子表"><section class="pd-block"><h3>使用与保养</h3><p>避免在热水澡、桑拿等高温高湿环境佩戴；石英表建议 1~2 年更换一次电池，长期不戴请取出电池。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 239 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>透底机芯</li><li>真皮表带</li><li>走时稳定，日常佩戴误差小</li><li>表带可自行更换，通勤运动换着戴</li></ul></section><img src="/img/watches/240-1.jpg" alt="复古机械自动腕表"><section class="pd-block"><h3>规格参数</h3><table><tr><th>表带</th><td>银色 / 玫瑰金</td></tr><tr><th>防水等级</th><td>生活防水，避免游泳与热水淋浴</td></tr><tr><th>包装清单</th><td>手表 × 1、说明书 × 1</td></tr></table></section><img src="/img/watches/240-2.jpg" alt="复古机械自动腕表"><section class="pd-block"><h3>使用与保养</h3><p>避免在热水澡、桑拿等高温高湿环境佩戴；石英表建议 1~2 年更换一次电池，长期不戴请取出电池。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 240 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>精致小巧</li><li>日常佩戴</li><li>走时稳定，日常佩戴误差小</li><li>表带可自行更换，通勤运动换着戴</li></ul></section><img src="/img/watches/241-1.jpg" alt="女士镶钻石英表"><section class="pd-block"><h3>规格参数</h3><table><tr><th>表带</th><td>银色 / 金色</td></tr><tr><th>防水等级</th><td>生活防水，避免游泳与热水淋浴</td></tr><tr><th>包装清单</th><td>手表 × 1、说明书 × 1</td></tr></table></section><img src="/img/watches/241-2.jpg" alt="女士镶钻石英表"><section class="pd-block"><h3>使用与保养</h3><p>避免在热水澡、桑拿等高温高湿环境佩戴；石英表建议 1~2 年更换一次电池，长期不戴请取出电池。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 241 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>睡眠监测</li><li>14 天续航</li><li>走时稳定，日常佩戴误差小</li><li>表带可自行更换，通勤运动换着戴</li></ul></section><img src="/img/watches/242-1.jpg" alt="智能手环"><section class="pd-block"><h3>规格参数</h3><table><tr><th>表带</th><td>曜石黑 / 樱花粉</td></tr><tr><th>防水等级</th><td>生活防水，避免游泳与热水淋浴</td></tr><tr><th>包装清单</th><td>手表 × 1、说明书 × 1</td></tr></table></section><img src="/img/watches/242-2.jpg" alt="智能手环"><section class="pd-block"><h3>使用与保养</h3><p>避免在热水澡、桑拿等高温高湿环境佩戴；石英表建议 1~2 年更换一次电池，长期不戴请取出电池。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 242 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>单宁柔顺</li><li>果香浓郁</li><li>原瓶进口/原厂直供，全程控温运输</li><li>礼盒装可选，节日送礼体面</li></ul></section><img src="/img/drinks/243-1.jpg" alt="干红葡萄酒"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>750ml 单瓶 / 750ml 双瓶</td></tr><tr><th>储存条件</th><td>阴凉避光，卧放或竖放均可</td></tr><tr><th>温馨提示</th><td>未成年人禁止饮酒，请适量饮用</td></tr></table></section><img src="/img/drinks/243-2.jpg" alt="干红葡萄酒"><section class="pd-block"><h3>使用与保养</h3><p>开瓶后建议尽快饮用；红葡萄酒醒酒 20~30 分钟风味更佳，未饮完可塞回软木塞冷藏保存 2~3 天。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 243 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>麦香醇厚</li><li>泡沫细腻</li><li>原瓶进口/原厂直供，全程控温运输</li><li>礼盒装可选，节日送礼体面</li></ul></section><img src="/img/drinks/244-1.jpg" alt="精酿小麦啤酒"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>330ml×6 / 330ml×12</td></tr><tr><th>储存条件</th><td>阴凉避光，卧放或竖放均可</td></tr><tr><th>温馨提示</th><td>未成年人禁止饮酒，请适量饮用</td></tr></table></section><img src="/img/drinks/244-2.jpg" alt="精酿小麦啤酒"><section class="pd-block"><h3>使用与保养</h3><p>开瓶后建议尽快饮用；红葡萄酒醒酒 20~30 分钟风味更佳，未饮完可塞回软木塞冷藏保存 2~3 天。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 244 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>橡木桶陈酿</li><li>醇厚顺滑</li><li>原瓶进口/原厂直供，全程控温运输</li><li>礼盒装可选，节日送礼体面</li></ul></section><img src="/img/drinks/245-1.jpg" alt="单一麦芽威士忌"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>700ml / 1000ml</td></tr><tr><th>储存条件</th><td>阴凉避光，卧放或竖放均可</td></tr><tr><th>温馨提示</th><td>未成年人禁止饮酒，请适量饮用</td></tr></table></section><img src="/img/drinks/245-2.jpg" alt="单一麦芽威士忌"><section class="pd-block"><h3>使用与保养</h3><p>开瓶后建议尽快饮用；红葡萄酒醒酒 20~30 分钟风味更佳，未饮完可塞回软木塞冷藏保存 2~3 天。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 245 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>入口绵柔</li><li>回味悠长</li><li>原瓶进口/原厂直供，全程控温运输</li><li>礼盒装可选，节日送礼体面</li></ul></section><img src="/img/drinks/246-1.jpg" alt="清香型白酒"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>500ml / 500ml 双瓶</td></tr><tr><th>储存条件</th><td>阴凉避光，卧放或竖放均可</td></tr><tr><th>温馨提示</th><td>未成年人禁止饮酒，请适量饮用</td></tr></table></section><img src="/img/drinks/246-2.jpg" alt="清香型白酒"><section class="pd-block"><h3>使用与保养</h3><p>开瓶后建议尽快饮用；红葡萄酒醒酒 20~30 分钟风味更佳，未饮完可塞回软木塞冷藏保存 2~3 天。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 246 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>弱碱性</li><li>清爽解腻</li><li>原瓶进口/原厂直供，全程控温运输</li><li>礼盒装可选，节日送礼体面</li></ul></section><img src="/img/drinks/247-1.jpg" alt="天然气泡矿泉水"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>500ml×12 / 500ml×24</td></tr><tr><th>储存条件</th><td>阴凉避光，卧放或竖放均可</td></tr><tr><th>温馨提示</th><td>未成年人禁止饮酒，请适量饮用</td></tr></table></section><img src="/img/drinks/247-2.jpg" alt="天然气泡矿泉水"><section class="pd-block"><h3>使用与保养</h3><p>开瓶后建议尽快饮用；红葡萄酒醒酒 20~30 分钟风味更佳，未饮完可塞回软木塞冷藏保存 2~3 天。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 247 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>0 酒精</li><li>清甜气泡</li><li>原瓶进口/原厂直供，全程控温运输</li><li>礼盒装可选，节日送礼体面</li></ul></section><img src="/img/drinks/248-1.jpg" alt="无醇起泡葡萄汁"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>750ml 单瓶 / 750ml 双瓶</td></tr><tr><th>储存条件</th><td>阴凉避光，卧放或竖放均可</td></tr><tr><th>温馨提示</th><td>未成年人禁止饮酒，请适量饮用</td></tr></table></section><img src="/img/drinks/248-2.jpg" alt="无醇起泡葡萄汁"><section class="pd-block"><h3>使用与保养</h3><p>开瓶后建议尽快饮用；红葡萄酒醒酒 20~30 分钟风味更佳，未饮完可塞回软木塞冷藏保存 2~3 天。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 248 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>三层防护</li><li>透气不闷</li><li>家庭常备，日常护理与应急都能用上</li><li>独立包装/分格收纳，取用干净卫生</li></ul></section><img src="/img/health/249-1.jpg" alt="医用外科口罩"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>50 只装 / 100 只装</td></tr><tr><th>适用人群</th><td>成人及 3 岁以上儿童（具体见包装说明）</td></tr><tr><th>储存方式</th><td>阴凉干燥处，儿童接触不到的地方</td></tr></table></section><img src="/img/health/249-2.jpg" alt="医用外科口罩"><section class="pd-block"><h3>使用与保养</h3><p>请按包装或说明书标注的用法用量使用；本品不能替代药品与专业诊疗，症状持续或加重请及时就医。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 249 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>快速测温</li><li>蜂鸣提示</li><li>家庭常备，日常护理与应急都能用上</li><li>独立包装/分格收纳，取用干净卫生</li></ul></section><img src="/img/health/250-1.jpg" alt="电子体温计"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>腋下款 / 额温款</td></tr><tr><th>适用人群</th><td>成人及 3 岁以上儿童（具体见包装说明）</td></tr><tr><th>储存方式</th><td>阴凉干燥处，儿童接触不到的地方</td></tr></table></section><img src="/img/health/250-2.jpg" alt="电子体温计"><section class="pd-block"><h3>使用与保养</h3><p>请按包装或说明书标注的用法用量使用；本品不能替代药品与专业诊疗，症状持续或加重请及时就医。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 250 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>分层收纳</li><li>常备药品齐全</li><li>家庭常备，日常护理与应急都能用上</li><li>独立包装/分格收纳，取用干净卫生</li></ul></section><img src="/img/health/251-1.jpg" alt="家庭应急医药箱"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>基础款 / 加强款</td></tr><tr><th>适用人群</th><td>成人及 3 岁以上儿童（具体见包装说明）</td></tr><tr><th>储存方式</th><td>阴凉干燥处，儿童接触不到的地方</td></tr></table></section><img src="/img/health/251-2.jpg" alt="家庭应急医药箱"><section class="pd-block"><h3>使用与保养</h3><p>请按包装或说明书标注的用法用量使用；本品不能替代药品与专业诊疗，症状持续或加重请及时就医。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 251 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>补充维 C</li><li>清新口味</li><li>家庭常备，日常护理与应急都能用上</li><li>独立包装/分格收纳，取用干净卫生</li></ul></section><img src="/img/health/252-1.jpg" alt="维生素 C 泡腾片"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>20 片 / 40 片</td></tr><tr><th>适用人群</th><td>成人及 3 岁以上儿童（具体见包装说明）</td></tr><tr><th>储存方式</th><td>阴凉干燥处，儿童接触不到的地方</td></tr></table></section><img src="/img/health/252-2.jpg" alt="维生素 C 泡腾片"><section class="pd-block"><h3>使用与保养</h3><p>请按包装或说明书标注的用法用量使用；本品不能替代药品与专业诊疗，症状持续或加重请及时就医。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 252 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>弹性舒适</li><li>运动护具</li><li>家庭常备，日常护理与应急都能用上</li><li>独立包装/分格收纳，取用干净卫生</li></ul></section><img src="/img/health/253-1.jpg" alt="医用弹力绷带"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>2 卷装 / 4 卷装</td></tr><tr><th>适用人群</th><td>成人及 3 岁以上儿童（具体见包装说明）</td></tr><tr><th>储存方式</th><td>阴凉干燥处，儿童接触不到的地方</td></tr></table></section><img src="/img/health/253-2.jpg" alt="医用弹力绷带"><section class="pd-block"><h3>使用与保养</h3><p>请按包装或说明书标注的用法用量使用；本品不能替代药品与专业诊疗，症状持续或加重请及时就医。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 253 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>热敷脉冲</li><li>缓解酸痛</li><li>家庭常备，日常护理与应急都能用上</li><li>独立包装/分格收纳，取用干净卫生</li></ul></section><img src="/img/health/254-1.jpg" alt="颈椎按摩仪"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>充电款 / 充电款 + 车载</td></tr><tr><th>适用人群</th><td>成人及 3 岁以上儿童（具体见包装说明）</td></tr><tr><th>储存方式</th><td>阴凉干燥处，儿童接触不到的地方</td></tr></table></section><img src="/img/health/254-2.jpg" alt="颈椎按摩仪"><section class="pd-block"><h3>使用与保养</h3><p>请按包装或说明书标注的用法用量使用；本品不能替代药品与专业诊疗，症状持续或加重请及时就医。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 254 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>豆浆米糊</li><li>一键清洗</li><li>一键操作，老人小孩也能上手</li><li>机身可拆洗，日常清洁不费劲</li></ul></section><img src="/img/appliances/255-1.jpg" alt="破壁料理机"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>基础款 / 静音款</td></tr><tr><th>额定电压</th><td>220V / 50Hz</td></tr><tr><th>保修</th><td>整机保修 1 年，主要部件 2 年</td></tr></table></section><img src="/img/appliances/255-2.jpg" alt="破壁料理机"><section class="pd-block"><h3>使用与保养</h3><p>使用前请确认电压匹配并可靠接地；清洁前务必断电，不要将机身浸入水中冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 255 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>快速烧水</li><li>自动断电</li><li>一键操作，老人小孩也能上手</li><li>机身可拆洗，日常清洁不费劲</li></ul></section><img src="/img/appliances/256-1.jpg" alt="电热水壶"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>1.5L / 1.7L</td></tr><tr><th>额定电压</th><td>220V / 50Hz</td></tr><tr><th>保修</th><td>整机保修 1 年，主要部件 2 年</td></tr></table></section><img src="/img/appliances/256-2.jpg" alt="电热水壶"><section class="pd-block"><h3>使用与保养</h3><p>使用前请确认电压匹配并可靠接地；清洁前务必断电，不要将机身浸入水中冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 256 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>静音柔风</li><li>可摇头</li><li>一键操作，老人小孩也能上手</li><li>机身可拆洗，日常清洁不费劲</li></ul></section><img src="/img/appliances/257-1.jpg" alt="桌面空气循环扇"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>标准款 / 遥控款</td></tr><tr><th>额定电压</th><td>220V / 50Hz</td></tr><tr><th>保修</th><td>整机保修 1 年，主要部件 2 年</td></tr></table></section><img src="/img/appliances/257-2.jpg" alt="桌面空气循环扇"><section class="pd-block"><h3>使用与保养</h3><p>使用前请确认电压匹配并可靠接地；清洁前务必断电，不要将机身浸入水中冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 257 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>煮面火锅</li><li>宿舍适用</li><li>一键操作，老人小孩也能上手</li><li>机身可拆洗，日常清洁不费劲</li></ul></section><img src="/img/appliances/258-1.jpg" alt="多功能电煮锅"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>1.5L / 2.5L</td></tr><tr><th>额定电压</th><td>220V / 50Hz</td></tr><tr><th>保修</th><td>整机保修 1 年，主要部件 2 年</td></tr></table></section><img src="/img/appliances/258-2.jpg" alt="多功能电煮锅"><section class="pd-block"><h3>使用与保养</h3><p>使用前请确认电压匹配并可靠接地；清洁前务必断电，不要将机身浸入水中冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 258 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>强力吸尘</li><li>拍打杀菌</li><li>一键操作，老人小孩也能上手</li><li>机身可拆洗，日常清洁不费劲</li></ul></section><img src="/img/appliances/259-1.jpg" alt="家用除螨仪"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>有线款 / 无线款</td></tr><tr><th>额定电压</th><td>220V / 50Hz</td></tr><tr><th>保修</th><td>整机保修 1 年，主要部件 2 年</td></tr></table></section><img src="/img/appliances/259-2.jpg" alt="家用除螨仪"><section class="pd-block"><h3>使用与保养</h3><p>使用前请确认电压匹配并可靠接地；清洁前务必断电，不要将机身浸入水中冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 259 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>大雾量</li><li>缺水断电</li><li>一键操作，老人小孩也能上手</li><li>机身可拆洗，日常清洁不费劲</li></ul></section><img src="/img/appliances/260-1.jpg" alt="迷你加湿器"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>300ml / 500ml</td></tr><tr><th>额定电压</th><td>220V / 50Hz</td></tr><tr><th>保修</th><td>整机保修 1 年，主要部件 2 年</td></tr></table></section><img src="/img/appliances/260-2.jpg" alt="迷你加湿器"><section class="pd-block"><h3>使用与保养</h3><p>使用前请确认电压匹配并可靠接地；清洁前务必断电，不要将机身浸入水中冲洗。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 260 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>精细涂装</li><li>多关节可动</li><li>细节涂装到位，摆桌面很有氛围</li><li>关节/结构可动，把玩与展示两不误</li></ul></section><img src="/img/toys/261-1.jpg" alt="关节可动机甲手办"><section class="pd-block"><h3>规格参数</h3><table><tr><th>款式</th><td>红莲款 / 苍蓝款</td></tr><tr><th>适用年龄</th><td>15 岁以上</td></tr><tr><th>材质</th><td>PVC / ABS</td></tr></table></section><img src="/img/toys/261-2.jpg" alt="关节可动机甲手办"><section class="pd-block"><h3>使用与保养</h3><p>避免暴晒与高温，防止材质变形褪色；可动部件请勿大力掰动，细小零件请远离婴幼儿。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 261 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>惊喜随机</li><li>收藏摆件</li><li>细节涂装到位，摆桌面很有氛围</li><li>关节/结构可动，把玩与展示两不误</li></ul></section><img src="/img/toys/262-1.jpg" alt="盲盒手办"><section class="pd-block"><h3>规格参数</h3><table><tr><th>款式</th><td>随机单盒 / 整盒 6 只</td></tr><tr><th>适用年龄</th><td>15 岁以上</td></tr><tr><th>材质</th><td>PVC / ABS</td></tr></table></section><img src="/img/toys/262-2.jpg" alt="盲盒手办"><section class="pd-block"><h3>使用与保养</h3><p>避免暴晒与高温，防止材质变形褪色；可动部件请勿大力掰动，细小零件请远离婴幼儿。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 262 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>益智拼搭</li><li>可展示</li><li>细节涂装到位，摆桌面很有氛围</li><li>关节/结构可动，把玩与展示两不误</li></ul></section><img src="/img/toys/263-1.jpg" alt="拼装积木模型"><section class="pd-block"><h3>规格参数</h3><table><tr><th>款式</th><td>800 片 / 1200 片</td></tr><tr><th>适用年龄</th><td>15 岁以上</td></tr><tr><th>材质</th><td>PVC / ABS</td></tr></table></section><img src="/img/toys/263-2.jpg" alt="拼装积木模型"><section class="pd-block"><h3>使用与保养</h3><p>避免暴晒与高温，防止材质变形褪色；可动部件请勿大力掰动，细小零件请远离婴幼儿。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 263 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>柔软亲肤</li><li>抱枕陪睡</li><li>细节涂装到位，摆桌面很有氛围</li><li>关节/结构可动，把玩与展示两不误</li></ul></section><img src="/img/toys/264-1.jpg" alt="卡通毛绒玩偶"><section class="pd-block"><h3>规格参数</h3><table><tr><th>款式</th><td>25cm / 30cm</td></tr><tr><th>适用年龄</th><td>15 岁以上</td></tr><tr><th>材质</th><td>PVC / ABS</td></tr></table></section><img src="/img/toys/264-2.jpg" alt="卡通毛绒玩偶"><section class="pd-block"><h3>使用与保养</h3><p>避免暴晒与高温，防止材质变形褪色；可动部件请勿大力掰动，细小零件请远离婴幼儿。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 264 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>开门可动</li><li>精致收藏</li><li>细节涂装到位，摆桌面很有氛围</li><li>关节/结构可动，把玩与展示两不误</li></ul></section><img src="/img/toys/265-1.jpg" alt="合金仿真车模"><section class="pd-block"><h3>规格参数</h3><table><tr><th>款式</th><td>1:32 / 1:24</td></tr><tr><th>适用年龄</th><td>15 岁以上</td></tr><tr><th>材质</th><td>PVC / ABS</td></tr></table></section><img src="/img/toys/265-2.jpg" alt="合金仿真车模"><section class="pd-block"><h3>使用与保养</h3><p>避免暴晒与高温，防止材质变形褪色；可动部件请勿大力掰动，细小零件请远离婴幼儿。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 265 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>场景组合</li><li>办公装饰</li><li>细节涂装到位，摆桌面很有氛围</li><li>关节/结构可动，把玩与展示两不误</li></ul></section><img src="/img/toys/266-1.jpg" alt="桌面摆件手办套装"><section class="pd-block"><h3>规格参数</h3><table><tr><th>款式</th><td>3 件套 / 5 件套</td></tr><tr><th>适用年龄</th><td>15 岁以上</td></tr><tr><th>材质</th><td>PVC / ABS</td></tr></table></section><img src="/img/toys/266-2.jpg" alt="桌面摆件手办套装"><section class="pd-block"><h3>使用与保养</h3><p>避免暴晒与高温，防止材质变形褪色；可动部件请勿大力掰动，细小零件请远离婴幼儿。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 266 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>防爆冲</li><li>夜间安全</li><li>材质安全无异味，宠物接受度高</li><li>结构简单好清洗，日常打理省心</li></ul></section><img src="/img/pets/267-1.jpg" alt="宠物牵引绳"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>S 号 / L 号</td></tr><tr><th>适用宠物</th><td>猫 / 犬通用（具体见商品标题）</td></tr><tr><th>清洁方式</th><td>可水洗，晾干后使用</td></tr></table></section><img src="/img/pets/267-2.jpg" alt="宠物牵引绳"><section class="pd-block"><h3>使用与保养</h3><p>首次使用请先清洗晾干；观察宠物适应情况，如出现啃咬吞咽风险请及时收纳。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 267 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>定时定量</li><li>防卡粮</li><li>材质安全无异味，宠物接受度高</li><li>结构简单好清洗，日常打理省心</li></ul></section><img src="/img/pets/268-1.jpg" alt="宠物自动喂食器"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>3L / 5L</td></tr><tr><th>适用宠物</th><td>猫 / 犬通用（具体见商品标题）</td></tr><tr><th>清洁方式</th><td>可水洗，晾干后使用</td></tr></table></section><img src="/img/pets/268-2.jpg" alt="宠物自动喂食器"><section class="pd-block"><h3>使用与保养</h3><p>首次使用请先清洗晾干；观察宠物适应情况，如出现啃咬吞咽风险请及时收纳。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 268 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>结团快</li><li>低粉尘</li><li>材质安全无异味，宠物接受度高</li><li>结构简单好清洗，日常打理省心</li></ul></section><img src="/img/pets/269-1.jpg" alt="豆腐猫砂"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>10L 单袋 / 10L 三袋</td></tr><tr><th>适用宠物</th><td>猫 / 犬通用（具体见商品标题）</td></tr><tr><th>清洁方式</th><td>可水洗，晾干后使用</td></tr></table></section><img src="/img/pets/269-2.jpg" alt="豆腐猫砂"><section class="pd-block"><h3>使用与保养</h3><p>首次使用请先清洗晾干；观察宠物适应情况，如出现啃咬吞咽风险请及时收纳。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 269 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>通风透气</li><li>出行必备</li><li>材质安全无异味，宠物接受度高</li><li>结构简单好清洗，日常打理省心</li></ul></section><img src="/img/pets/270-1.jpg" alt="宠物航空箱"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>小号 / 中号</td></tr><tr><th>适用宠物</th><td>猫 / 犬通用（具体见商品标题）</td></tr><tr><th>清洁方式</th><td>可水洗，晾干后使用</td></tr></table></section><img src="/img/pets/270-2.jpg" alt="宠物航空箱"><section class="pd-block"><h3>使用与保养</h3><p>首次使用请先清洗晾干；观察宠物适应情况，如出现啃咬吞咽风险请及时收纳。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 270 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>耐咬去口臭</li><li>清洁牙齿</li><li>材质安全无异味，宠物接受度高</li><li>结构简单好清洗，日常打理省心</li></ul></section><img src="/img/pets/271-1.jpg" alt="狗狗磨牙洁齿棒"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>小包装 / 大包装</td></tr><tr><th>适用宠物</th><td>猫 / 犬通用（具体见商品标题）</td></tr><tr><th>清洁方式</th><td>可水洗，晾干后使用</td></tr></table></section><img src="/img/pets/271-2.jpg" alt="狗狗磨牙洁齿棒"><section class="pd-block"><h3>使用与保养</h3><p>首次使用请先清洗晾干；观察宠物适应情况，如出现啃咬吞咽风险请及时收纳。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 271 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>一键退毛</li><li>按摩舒适</li><li>材质安全无异味，宠物接受度高</li><li>结构简单好清洗，日常打理省心</li></ul></section><img src="/img/pets/272-1.jpg" alt="宠物梳毛刷"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>标准款 / 加长款</td></tr><tr><th>适用宠物</th><td>猫 / 犬通用（具体见商品标题）</td></tr><tr><th>清洁方式</th><td>可水洗，晾干后使用</td></tr></table></section><img src="/img/pets/272-2.jpg" alt="宠物梳毛刷"><section class="pd-block"><h3>使用与保养</h3><p>首次使用请先清洗晾干；观察宠物适应情况，如出现啃咬吞咽风险请及时收纳。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 272 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>防胀气</li><li>宽口径</li><li>母婴级材质，通过相关安全检测</li><li>结构圆润无锐角，宝宝使用更放心</li></ul></section><img src="/img/baby/273-1.jpg" alt="婴儿玻璃奶瓶"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>160ml / 240ml</td></tr><tr><th>适用年龄</th><td>详见商品标题</td></tr><tr><th>执行标准</th><td>符合国家母婴用品相关标准</td></tr></table></section><img src="/img/baby/273-2.jpg" alt="婴儿玻璃奶瓶"><section class="pd-block"><h3>使用与保养</h3><p>使用前请清洗并消毒；请勿让宝宝单独使用，成人需在旁看护；出现破损、变形请立即停用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 273 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>无骨缝制</li><li>A 类标准</li><li>母婴级材质，通过相关安全检测</li><li>结构圆润无锐角，宝宝使用更放心</li></ul></section><img src="/img/baby/274-1.jpg" alt="新生儿纯棉连体衣"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>59cm / 66cm</td></tr><tr><th>适用年龄</th><td>详见商品标题</td></tr><tr><th>执行标准</th><td>符合国家母婴用品相关标准</td></tr></table></section><img src="/img/baby/274-2.jpg" alt="新生儿纯棉连体衣"><section class="pd-block"><h3>使用与保养</h3><p>使用前请清洗并消毒；请勿让宝宝单独使用，成人需在旁看护；出现破损、变形请立即停用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 274 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>超薄透气</li><li>干爽不红臀</li><li>母婴级材质，通过相关安全检测</li><li>结构圆润无锐角，宝宝使用更放心</li></ul></section><img src="/img/baby/275-1.jpg" alt="婴儿纸尿裤"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>M 码 46 片 / L 码 40 片</td></tr><tr><th>适用年龄</th><td>详见商品标题</td></tr><tr><th>执行标准</th><td>符合国家母婴用品相关标准</td></tr></table></section><img src="/img/baby/275-2.jpg" alt="婴儿纸尿裤"><section class="pd-block"><h3>使用与保养</h3><p>使用前请清洗并消毒；请勿让宝宝单独使用，成人需在旁看护；出现破损、变形请立即停用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 275 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>手动研磨</li><li>便于携带</li><li>母婴级材质，通过相关安全检测</li><li>结构圆润无锐角，宝宝使用更放心</li></ul></section><img src="/img/baby/276-1.jpg" alt="宝宝辅食研磨碗套装"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>基础套装 / 升级套装</td></tr><tr><th>适用年龄</th><td>详见商品标题</td></tr><tr><th>执行标准</th><td>符合国家母婴用品相关标准</td></tr></table></section><img src="/img/baby/276-2.jpg" alt="宝宝辅食研磨碗套装"><section class="pd-block"><h3>使用与保养</h3><p>使用前请清洗并消毒；请勿让宝宝单独使用，成人需在旁看护；出现破损、变形请立即停用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 276 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>精准控温</li><li>一键冲奶</li><li>母婴级材质，通过相关安全检测</li><li>结构圆润无锐角，宝宝使用更放心</li></ul></section><img src="/img/baby/277-1.jpg" alt="婴儿恒温调奶器"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>标准款 / 智能款</td></tr><tr><th>适用年龄</th><td>详见商品标题</td></tr><tr><th>执行标准</th><td>符合国家母婴用品相关标准</td></tr></table></section><img src="/img/baby/277-2.jpg" alt="婴儿恒温调奶器"><section class="pd-block"><h3>使用与保养</h3><p>使用前请清洗并消毒；请勿让宝宝单独使用，成人需在旁看护；出现破损、变形请立即停用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 277 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>五点式固定</li><li>侧撞防护</li><li>母婴级材质，通过相关安全检测</li><li>结构圆润无锐角，宝宝使用更放心</li></ul></section><img src="/img/baby/278-1.jpg" alt="儿童安全座椅"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>0-4 岁 / 9 个月-12 岁</td></tr><tr><th>适用年龄</th><td>详见商品标题</td></tr><tr><th>执行标准</th><td>符合国家母婴用品相关标准</td></tr></table></section><img src="/img/baby/278-2.jpg" alt="儿童安全座椅"><section class="pd-block"><h3>使用与保养</h3><p>使用前请清洗并消毒；请勿让宝宝单独使用，成人需在旁看护；出现破损、变形请立即停用。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 278 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>稳固防抖</li><li>单手取放</li><li>安装简单，多数车型通用</li><li>用料耐高温，夏季暴晒不变形</li></ul></section><img src="/img/cars/279-1.jpg" alt="车载手机支架"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>出风口款 / 仪表台款</td></tr><tr><th>适用车型</th><td>轿车 / SUV 通用（具体见商品标题）</td></tr><tr><th>安装方式</th><td>免工具安装</td></tr></table></section><img src="/img/cars/279-2.jpg" alt="车载手机支架"><section class="pd-block"><h3>使用与保养</h3><p>安装前请清洁接触面并保持干燥；涉及电路的产品请确认接口匹配，不确定时建议到店安装。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 279 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>防滑耐磨</li><li>易清洁</li><li>安装简单，多数车型通用</li><li>用料耐高温，夏季暴晒不变形</li></ul></section><img src="/img/cars/280-1.jpg" alt="汽车全包围脚垫"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>前排单片 / 全车套装</td></tr><tr><th>适用车型</th><td>轿车 / SUV 通用（具体见商品标题）</td></tr><tr><th>安装方式</th><td>免工具安装</td></tr></table></section><img src="/img/cars/280-2.jpg" alt="汽车全包围脚垫"><section class="pd-block"><h3>使用与保养</h3><p>安装前请清洁接触面并保持干燥；涉及电路的产品请确认接口匹配，不确定时建议到店安装。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 280 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>除甲醛异味</li><li>静音运行</li><li>安装简单，多数车型通用</li><li>用料耐高温，夏季暴晒不变形</li></ul></section><img src="/img/cars/281-1.jpg" alt="车载空气净化器"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>标准款 / 香氛款</td></tr><tr><th>适用车型</th><td>轿车 / SUV 通用（具体见商品标题）</td></tr><tr><th>安装方式</th><td>免工具安装</td></tr></table></section><img src="/img/cars/281-2.jpg" alt="车载空气净化器"><section class="pd-block"><h3>使用与保养</h3><p>安装前请清洁接触面并保持干燥；涉及电路的产品请确认接口匹配，不确定时建议到店安装。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 281 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>高清夜视</li><li>循环录制</li><li>安装简单，多数车型通用</li><li>用料耐高温，夏季暴晒不变形</li></ul></section><img src="/img/cars/282-1.jpg" alt="汽车行车记录仪"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>单镜头 / 双镜头</td></tr><tr><th>适用车型</th><td>轿车 / SUV 通用（具体见商品标题）</td></tr><tr><th>安装方式</th><td>免工具安装</td></tr></table></section><img src="/img/cars/282-2.jpg" alt="汽车行车记录仪"><section class="pd-block"><h3>使用与保养</h3><p>安装前请清洁接触面并保持干燥；涉及电路的产品请确认接口匹配，不确定时建议到店安装。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 282 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>大吸力</li><li>便携充电</li><li>安装简单，多数车型通用</li><li>用料耐高温，夏季暴晒不变形</li></ul></section><img src="/img/cars/283-1.jpg" alt="车载吸尘器"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>基础款 / 强力款</td></tr><tr><th>适用车型</th><td>轿车 / SUV 通用（具体见商品标题）</td></tr><tr><th>安装方式</th><td>免工具安装</td></tr></table></section><img src="/img/cars/283-2.jpg" alt="车载吸尘器"><section class="pd-block"><h3>使用与保养</h3><p>安装前请清洁接触面并保持干燥；涉及电路的产品请确认接口匹配，不确定时建议到店安装。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 283 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>防晒隔热</li><li>可折叠</li><li>安装简单，多数车型通用</li><li>用料耐高温，夏季暴晒不变形</li></ul></section><img src="/img/cars/284-1.jpg" alt="汽车遮阳挡"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>前挡款 / 前挡 + 侧挡</td></tr><tr><th>适用车型</th><td>轿车 / SUV 通用（具体见商品标题）</td></tr><tr><th>安装方式</th><td>免工具安装</td></tr></table></section><img src="/img/cars/284-2.jpg" alt="汽车遮阳挡"><section class="pd-block"><h3>使用与保养</h3><p>安装前请清洁接触面并保持干燥；涉及电路的产品请确认接口匹配，不确定时建议到店安装。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 284 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>云杉面板</li><li>音色通透</li><li>出厂已调音，开箱即可上手</li><li>附赠基础教程与配件，零基础也能学</li></ul></section><img src="/img/instruments/285-1.jpg" alt="民谣吉他"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>40 寸 / 41 寸</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>包装清单</th><td>乐器 × 1、配件若干（见商品标题）</td></tr></table></section><img src="/img/instruments/285-2.jpg" alt="民谣吉他"><section class="pd-block"><h3>使用与保养</h3><p>每次弹奏后用软布擦拭琴身与琴弦；避免阳光直射与温湿剧变，长期不用请松弦并放入琴包。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 285 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>多种音色</li><li>带力度感应</li><li>出厂已调音，开箱即可上手</li><li>附赠基础教程与配件，零基础也能学</li></ul></section><img src="/img/instruments/286-1.jpg" alt="便携式电子琴"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>61 键 / 88 键</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>包装清单</th><td>乐器 × 1、配件若干（见商品标题）</td></tr></table></section><img src="/img/instruments/286-2.jpg" alt="便携式电子琴"><section class="pd-block"><h3>使用与保养</h3><p>每次弹奏后用软布擦拭琴身与琴弦；避免阳光直射与温湿剧变，长期不用请松弦并放入琴包。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 286 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>小巧易学</li><li>附带教程</li><li>出厂已调音，开箱即可上手</li><li>附赠基础教程与配件，零基础也能学</li></ul></section><img src="/img/instruments/287-1.jpg" alt="尤克里里"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>23 寸 / 26 寸</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>包装清单</th><td>乐器 × 1、配件若干（见商品标题）</td></tr></table></section><img src="/img/instruments/287-2.jpg" alt="尤克里里"><section class="pd-block"><h3>使用与保养</h3><p>每次弹奏后用软布擦拭琴身与琴弦；避免阳光直射与温湿剧变，长期不用请松弦并放入琴包。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 287 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>手工蒙皮</li><li>音色饱满</li><li>出厂已调音，开箱即可上手</li><li>附赠基础教程与配件，零基础也能学</li></ul></section><img src="/img/instruments/288-1.jpg" alt="非洲鼓"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>10 寸 / 12 寸</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>包装清单</th><td>乐器 × 1、配件若干（见商品标题）</td></tr></table></section><img src="/img/instruments/288-2.jpg" alt="非洲鼓"><section class="pd-block"><h3>使用与保养</h3><p>每次弹奏后用软布擦拭琴身与琴弦；避免阳光直射与温湿剧变，长期不用请松弦并放入琴包。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 288 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>空灵音色</li><li>易于上手</li><li>出厂已调音，开箱即可上手</li><li>附赠基础教程与配件，零基础也能学</li></ul></section><img src="/img/instruments/289-1.jpg" alt="卡林巴拇指琴"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>17 音 / 21 音</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>包装清单</th><td>乐器 × 1、配件若干（见商品标题）</td></tr></table></section><img src="/img/instruments/289-2.jpg" alt="卡林巴拇指琴"><section class="pd-block"><h3>使用与保养</h3><p>每次弹奏后用软布擦拭琴身与琴弦；避免阳光直射与温湿剧变，长期不用请松弦并放入琴包。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 289 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>多节奏型</li><li>带调音功能</li><li>出厂已调音，开箱即可上手</li><li>附赠基础教程与配件，零基础也能学</li></ul></section><img src="/img/instruments/290-1.jpg" alt="专业节拍器"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>基础款 / 多功能款</td></tr><tr><th>材质</th><td>按款式而定，详见商品标题</td></tr><tr><th>包装清单</th><td>乐器 × 1、配件若干（见商品标题）</td></tr></table></section><img src="/img/instruments/290-2.jpg" alt="专业节拍器"><section class="pd-block"><h3>使用与保养</h3><p>每次弹奏后用软布擦拭琴身与琴弦；避免阳光直射与温湿剧变，长期不用请松弦并放入琴包。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 290 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>零基础自学</li><li>配套源码</li><li>正版授权，印刷清晰不伤眼</li><li>纸张厚实不透印，翻阅手感好</li></ul></section><img src="/img/books/291-1.jpg" alt="计算机编程入门指南"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>平装 / 精装</td></tr><tr><th>装帧</th><td>平装 / 精装（见商品标题）</td></tr><tr><th>开本</th><td>16 开</td></tr></table></section><img src="/img/books/291-2.jpg" alt="计算机编程入门指南"><section class="pd-block"><h3>使用与保养</h3><p>建议在光线充足处阅读，每 40 分钟休息一次；存放时避免受潮与重压。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 291 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>精装全 4 册</li><li>注释详尽</li><li>正版授权，印刷清晰不伤眼</li><li>纸张厚实不透印，翻阅手感好</li></ul></section><img src="/img/books/292-1.jpg" alt="中国古典文学名著套装"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>平装 / 精装</td></tr><tr><th>装帧</th><td>平装 / 精装（见商品标题）</td></tr><tr><th>开本</th><td>16 开</td></tr></table></section><img src="/img/books/292-2.jpg" alt="中国古典文学名著套装"><section class="pd-block"><h3>使用与保养</h3><p>建议在光线充足处阅读，每 40 分钟休息一次；存放时避免受潮与重压。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 292 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>全彩插图</li><li>亲子共读</li><li>正版授权，印刷清晰不伤眼</li><li>纸张厚实不透印，翻阅手感好</li></ul></section><img src="/img/books/293-1.jpg" alt="儿童科普绘本套装"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>20 册 / 30 册</td></tr><tr><th>装帧</th><td>平装 / 精装（见商品标题）</td></tr><tr><th>开本</th><td>16 开</td></tr></table></section><img src="/img/books/293-2.jpg" alt="儿童科普绘本套装"><section class="pd-block"><h3>使用与保养</h3><p>建议在光线充足处阅读，每 40 分钟休息一次；存放时避免受潮与重压。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 293 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>案例详实</li><li>方法可落地</li><li>正版授权，印刷清晰不伤眼</li><li>纸张厚实不透印，翻阅手感好</li></ul></section><img src="/img/books/294-1.jpg" alt="商业管理与思维进阶"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>平装 / 精装</td></tr><tr><th>装帧</th><td>平装 / 精装（见商品标题）</td></tr><tr><th>开本</th><td>16 开</td></tr></table></section><img src="/img/books/294-2.jpg" alt="商业管理与思维进阶"><section class="pd-block"><h3>使用与保养</h3><p>建议在光线充足处阅读，每 40 分钟休息一次；存放时避免受潮与重压。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 294 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>词根词缀</li><li>高效记忆</li><li>正版授权，印刷清晰不伤眼</li><li>纸张厚实不透印，翻阅手感好</li></ul></section><img src="/img/books/295-1.jpg" alt="英语单词速记手册"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>口袋版 / 大开本</td></tr><tr><th>装帧</th><td>平装 / 精装（见商品标题）</td></tr><tr><th>开本</th><td>16 开</td></tr></table></section><img src="/img/books/295-2.jpg" alt="英语单词速记手册"><section class="pd-block"><h3>使用与保养</h3><p>建议在光线充足处阅读，每 40 分钟休息一次；存放时避免受潮与重压。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 295 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>分步图解</li><li>零基础入门</li><li>正版授权，印刷清晰不伤眼</li><li>纸张厚实不透印，翻阅手感好</li></ul></section><img src="/img/books/296-1.jpg" alt="手绘简笔画教程"><section class="pd-block"><h3>规格参数</h3><table><tr><th>版本</th><td>平装 / 精装</td></tr><tr><th>装帧</th><td>平装 / 精装（见商品标题）</td></tr><tr><th>开本</th><td>16 开</td></tr></table></section><img src="/img/books/296-2.jpg" alt="手绘简笔画教程"><section class="pd-block"><h3>使用与保养</h3><p>建议在光线充足处阅读，每 40 分钟休息一次；存放时避免受潮与重压。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 296 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>当日采摘</li><li>清脆爽口</li><li>产地直发，全程冷链锁鲜</li><li>下单后 48 小时内发出，坏果包赔</li></ul></section><img src="/img/fresh/297-1.jpg" alt="新鲜有机西兰花"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>500g / 1kg</td></tr><tr><th>储存方式</th><td>冷藏保存，建议 3 天内食用</td></tr><tr><th>保质期</th><td>以包装标注为准</td></tr></table></section><img src="/img/fresh/297-2.jpg" alt="新鲜有机西兰花"><section class="pd-block"><h3>使用与保养</h3><p>收到后请及时开箱检查并冷藏；生食类请清洗干净，如发现变质请拍照留存并联系售后。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 297 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>脆甜多汁</li><li>产地直发</li><li>产地直发，全程冷链锁鲜</li><li>下单后 48 小时内发出，坏果包赔</li></ul></section><img src="/img/fresh/298-1.jpg" alt="红富士苹果"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>5 斤装 / 10 斤装</td></tr><tr><th>储存方式</th><td>冷藏保存，建议 3 天内食用</td></tr><tr><th>保质期</th><td>以包装标注为准</td></tr></table></section><img src="/img/fresh/298-2.jpg" alt="红富士苹果"><section class="pd-block"><h3>使用与保养</h3><p>收到后请及时开箱检查并冷藏；生食类请清洗干净，如发现变质请拍照留存并联系售后。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 298 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>果肉饱满</li><li>香糯细腻</li><li>产地直发，全程冷链锁鲜</li><li>下单后 48 小时内发出，坏果包赔</li></ul></section><img src="/img/fresh/299-1.jpg" alt="金枕头榴莲"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>带壳整只 / 纯果肉装</td></tr><tr><th>储存方式</th><td>冷藏保存，建议 3 天内食用</td></tr><tr><th>保质期</th><td>以包装标注为准</td></tr></table></section><img src="/img/fresh/299-2.jpg" alt="金枕头榴莲"><section class="pd-block"><h3>使用与保养</h3><p>收到后请及时开箱检查并冷藏；生食类请清洗干净，如发现变质请拍照留存并联系售后。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 299 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>大果饱满</li><li>酸甜可口</li><li>产地直发，全程冷链锁鲜</li><li>下单后 48 小时内发出，坏果包赔</li></ul></section><img src="/img/fresh/300-1.jpg" alt="高原新鲜蓝莓"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>125g×4 盒 / 125g×8 盒</td></tr><tr><th>储存方式</th><td>冷藏保存，建议 3 天内食用</td></tr><tr><th>保质期</th><td>以包装标注为准</td></tr></table></section><img src="/img/fresh/300-2.jpg" alt="高原新鲜蓝莓"><section class="pd-block"><h3>使用与保养</h3><p>收到后请及时开箱检查并冷藏；生食类请清洗干净，如发现变质请拍照留存并联系售后。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 300 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>脆嫩清甜</li><li>农家种植</li><li>产地直发，全程冷链锁鲜</li><li>下单后 48 小时内发出，坏果包赔</li></ul></section><img src="/img/fresh/301-1.jpg" alt="现挖新鲜胡萝卜"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>2 斤装 / 5 斤装</td></tr><tr><th>储存方式</th><td>冷藏保存，建议 3 天内食用</td></tr><tr><th>保质期</th><td>以包装标注为准</td></tr></table></section><img src="/img/fresh/301-2.jpg" alt="现挖新鲜胡萝卜"><section class="pd-block"><h3>使用与保养</h3><p>收到后请及时开箱检查并冷藏；生食类请清洗干净，如发现变质请拍照留存并联系售后。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 301 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');
UPDATE `product` SET `detail` = '<section class="pd-block"><h3>产品亮点</h3><ul><li>熟度适中</li><li>口感绵密</li><li>产地直发，全程冷链锁鲜</li><li>下单后 48 小时内发出，坏果包赔</li></ul></section><img src="/img/fresh/302-1.jpg" alt="精选牛油果"><section class="pd-block"><h3>规格参数</h3><table><tr><th>规格</th><td>4 个装 / 8 个装</td></tr><tr><th>储存方式</th><td>冷藏保存，建议 3 天内食用</td></tr><tr><th>保质期</th><td>以包装标注为准</td></tr></table></section><img src="/img/fresh/302-2.jpg" alt="精选牛油果"><section class="pd-block"><h3>使用与保养</h3><p>收到后请及时开箱检查并冷藏；生食类请清洗干净，如发现变质请拍照留存并联系售后。</p></section><section class="pd-block"><h3>售后保障</h3><p>7 天无理由退换 · 正品保障 · 全场免运费。签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。</p></section>'
WHERE `id` = 302 AND (`detail` IS NULL OR `detail` = '' OR `detail` LIKE '%本商品为演示数据%');

-- -----------------------------------------------------------------------------
-- 2. 图集：102 个商品各补 2 张（201~302 此前没有任何图集行）
-- -----------------------------------------------------------------------------
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 201 AS `product_id`, '/img/skincare/skincare-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 201 AS `product_id`, '/img/skincare/201-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 201 AS `product_id`, '/img/skincare/201-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 202 AS `product_id`, '/img/skincare/skincare-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 202 AS `product_id`, '/img/skincare/202-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 202 AS `product_id`, '/img/skincare/202-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 203 AS `product_id`, '/img/skincare/skincare-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 203 AS `product_id`, '/img/skincare/203-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 203 AS `product_id`, '/img/skincare/203-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 204 AS `product_id`, '/img/skincare/skincare-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 204 AS `product_id`, '/img/skincare/204-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 204 AS `product_id`, '/img/skincare/204-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 205 AS `product_id`, '/img/skincare/skincare-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 205 AS `product_id`, '/img/skincare/205-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 205 AS `product_id`, '/img/skincare/205-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 206 AS `product_id`, '/img/skincare/skincare-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 206 AS `product_id`, '/img/skincare/206-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 206 AS `product_id`, '/img/skincare/206-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 207 AS `product_id`, '/img/bath/bath-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 207 AS `product_id`, '/img/bath/207-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 207 AS `product_id`, '/img/bath/207-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 208 AS `product_id`, '/img/bath/bath-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 208 AS `product_id`, '/img/bath/208-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 208 AS `product_id`, '/img/bath/208-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 209 AS `product_id`, '/img/bath/bath-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 209 AS `product_id`, '/img/bath/209-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 209 AS `product_id`, '/img/bath/209-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 210 AS `product_id`, '/img/bath/bath-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 210 AS `product_id`, '/img/bath/210-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 210 AS `product_id`, '/img/bath/210-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 211 AS `product_id`, '/img/bath/bath-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 211 AS `product_id`, '/img/bath/211-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 211 AS `product_id`, '/img/bath/211-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 212 AS `product_id`, '/img/bath/bath-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 212 AS `product_id`, '/img/bath/212-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 212 AS `product_id`, '/img/bath/212-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 213 AS `product_id`, '/img/bags/bags-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 213 AS `product_id`, '/img/bags/213-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 213 AS `product_id`, '/img/bags/213-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 214 AS `product_id`, '/img/bags/bags-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 214 AS `product_id`, '/img/bags/214-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 214 AS `product_id`, '/img/bags/214-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 215 AS `product_id`, '/img/bags/bags-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 215 AS `product_id`, '/img/bags/215-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 215 AS `product_id`, '/img/bags/215-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 216 AS `product_id`, '/img/bags/bags-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 216 AS `product_id`, '/img/bags/216-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 216 AS `product_id`, '/img/bags/216-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 217 AS `product_id`, '/img/bags/bags-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 217 AS `product_id`, '/img/bags/217-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 217 AS `product_id`, '/img/bags/217-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 218 AS `product_id`, '/img/bags/bags-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 218 AS `product_id`, '/img/bags/218-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 218 AS `product_id`, '/img/bags/218-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 219 AS `product_id`, '/img/home/home-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 219 AS `product_id`, '/img/home/219-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 219 AS `product_id`, '/img/home/219-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 220 AS `product_id`, '/img/home/home-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 220 AS `product_id`, '/img/home/220-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 220 AS `product_id`, '/img/home/220-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 221 AS `product_id`, '/img/home/home-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 221 AS `product_id`, '/img/home/221-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 221 AS `product_id`, '/img/home/221-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 222 AS `product_id`, '/img/home/home-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 222 AS `product_id`, '/img/home/222-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 222 AS `product_id`, '/img/home/222-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 223 AS `product_id`, '/img/home/home-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 223 AS `product_id`, '/img/home/223-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 223 AS `product_id`, '/img/home/223-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 224 AS `product_id`, '/img/home/home-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 224 AS `product_id`, '/img/home/224-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 224 AS `product_id`, '/img/home/224-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 225 AS `product_id`, '/img/accessories/accessories-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 225 AS `product_id`, '/img/accessories/225-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 225 AS `product_id`, '/img/accessories/225-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 226 AS `product_id`, '/img/accessories/accessories-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 226 AS `product_id`, '/img/accessories/226-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 226 AS `product_id`, '/img/accessories/226-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 227 AS `product_id`, '/img/accessories/accessories-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 227 AS `product_id`, '/img/accessories/227-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 227 AS `product_id`, '/img/accessories/227-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 228 AS `product_id`, '/img/accessories/accessories-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 228 AS `product_id`, '/img/accessories/228-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 228 AS `product_id`, '/img/accessories/228-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 229 AS `product_id`, '/img/accessories/accessories-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 229 AS `product_id`, '/img/accessories/229-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 229 AS `product_id`, '/img/accessories/229-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 230 AS `product_id`, '/img/accessories/accessories-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 230 AS `product_id`, '/img/accessories/230-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 230 AS `product_id`, '/img/accessories/230-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 231 AS `product_id`, '/img/sports/sports-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 231 AS `product_id`, '/img/sports/231-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 231 AS `product_id`, '/img/sports/231-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 232 AS `product_id`, '/img/sports/sports-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 232 AS `product_id`, '/img/sports/232-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 232 AS `product_id`, '/img/sports/232-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 233 AS `product_id`, '/img/sports/sports-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 233 AS `product_id`, '/img/sports/233-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 233 AS `product_id`, '/img/sports/233-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 234 AS `product_id`, '/img/sports/sports-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 234 AS `product_id`, '/img/sports/234-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 234 AS `product_id`, '/img/sports/234-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 235 AS `product_id`, '/img/sports/sports-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 235 AS `product_id`, '/img/sports/235-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 235 AS `product_id`, '/img/sports/235-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 236 AS `product_id`, '/img/sports/sports-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 236 AS `product_id`, '/img/sports/236-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 236 AS `product_id`, '/img/sports/236-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 237 AS `product_id`, '/img/watches/watches-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 237 AS `product_id`, '/img/watches/237-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 237 AS `product_id`, '/img/watches/237-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 238 AS `product_id`, '/img/watches/watches-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 238 AS `product_id`, '/img/watches/238-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 238 AS `product_id`, '/img/watches/238-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 239 AS `product_id`, '/img/watches/watches-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 239 AS `product_id`, '/img/watches/239-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 239 AS `product_id`, '/img/watches/239-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 240 AS `product_id`, '/img/watches/watches-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 240 AS `product_id`, '/img/watches/240-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 240 AS `product_id`, '/img/watches/240-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 241 AS `product_id`, '/img/watches/watches-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 241 AS `product_id`, '/img/watches/241-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 241 AS `product_id`, '/img/watches/241-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 242 AS `product_id`, '/img/watches/watches-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 242 AS `product_id`, '/img/watches/242-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 242 AS `product_id`, '/img/watches/242-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 243 AS `product_id`, '/img/drinks/drinks-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 243 AS `product_id`, '/img/drinks/243-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 243 AS `product_id`, '/img/drinks/243-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 244 AS `product_id`, '/img/drinks/drinks-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 244 AS `product_id`, '/img/drinks/244-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 244 AS `product_id`, '/img/drinks/244-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 245 AS `product_id`, '/img/drinks/drinks-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 245 AS `product_id`, '/img/drinks/245-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 245 AS `product_id`, '/img/drinks/245-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 246 AS `product_id`, '/img/drinks/drinks-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 246 AS `product_id`, '/img/drinks/246-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 246 AS `product_id`, '/img/drinks/246-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 247 AS `product_id`, '/img/drinks/drinks-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 247 AS `product_id`, '/img/drinks/247-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 247 AS `product_id`, '/img/drinks/247-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 248 AS `product_id`, '/img/drinks/drinks-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 248 AS `product_id`, '/img/drinks/248-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 248 AS `product_id`, '/img/drinks/248-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 249 AS `product_id`, '/img/health/health-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 249 AS `product_id`, '/img/health/249-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 249 AS `product_id`, '/img/health/249-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 250 AS `product_id`, '/img/health/health-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 250 AS `product_id`, '/img/health/250-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 250 AS `product_id`, '/img/health/250-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 251 AS `product_id`, '/img/health/health-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 251 AS `product_id`, '/img/health/251-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 251 AS `product_id`, '/img/health/251-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 252 AS `product_id`, '/img/health/health-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 252 AS `product_id`, '/img/health/252-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 252 AS `product_id`, '/img/health/252-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 253 AS `product_id`, '/img/health/health-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 253 AS `product_id`, '/img/health/253-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 253 AS `product_id`, '/img/health/253-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 254 AS `product_id`, '/img/health/health-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 254 AS `product_id`, '/img/health/254-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 254 AS `product_id`, '/img/health/254-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 255 AS `product_id`, '/img/appliances/appliances-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 255 AS `product_id`, '/img/appliances/255-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 255 AS `product_id`, '/img/appliances/255-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 256 AS `product_id`, '/img/appliances/appliances-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 256 AS `product_id`, '/img/appliances/256-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 256 AS `product_id`, '/img/appliances/256-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 257 AS `product_id`, '/img/appliances/appliances-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 257 AS `product_id`, '/img/appliances/257-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 257 AS `product_id`, '/img/appliances/257-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 258 AS `product_id`, '/img/appliances/appliances-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 258 AS `product_id`, '/img/appliances/258-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 258 AS `product_id`, '/img/appliances/258-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 259 AS `product_id`, '/img/appliances/appliances-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 259 AS `product_id`, '/img/appliances/259-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 259 AS `product_id`, '/img/appliances/259-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 260 AS `product_id`, '/img/appliances/appliances-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 260 AS `product_id`, '/img/appliances/260-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 260 AS `product_id`, '/img/appliances/260-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 261 AS `product_id`, '/img/toys/toys-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 261 AS `product_id`, '/img/toys/261-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 261 AS `product_id`, '/img/toys/261-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 262 AS `product_id`, '/img/toys/toys-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 262 AS `product_id`, '/img/toys/262-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 262 AS `product_id`, '/img/toys/262-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 263 AS `product_id`, '/img/toys/toys-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 263 AS `product_id`, '/img/toys/263-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 263 AS `product_id`, '/img/toys/263-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 264 AS `product_id`, '/img/toys/toys-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 264 AS `product_id`, '/img/toys/264-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 264 AS `product_id`, '/img/toys/264-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 265 AS `product_id`, '/img/toys/toys-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 265 AS `product_id`, '/img/toys/265-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 265 AS `product_id`, '/img/toys/265-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 266 AS `product_id`, '/img/toys/toys-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 266 AS `product_id`, '/img/toys/266-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 266 AS `product_id`, '/img/toys/266-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 267 AS `product_id`, '/img/pets/pets-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 267 AS `product_id`, '/img/pets/267-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 267 AS `product_id`, '/img/pets/267-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 268 AS `product_id`, '/img/pets/pets-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 268 AS `product_id`, '/img/pets/268-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 268 AS `product_id`, '/img/pets/268-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 269 AS `product_id`, '/img/pets/pets-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 269 AS `product_id`, '/img/pets/269-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 269 AS `product_id`, '/img/pets/269-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 270 AS `product_id`, '/img/pets/pets-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 270 AS `product_id`, '/img/pets/270-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 270 AS `product_id`, '/img/pets/270-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 271 AS `product_id`, '/img/pets/pets-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 271 AS `product_id`, '/img/pets/271-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 271 AS `product_id`, '/img/pets/271-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 272 AS `product_id`, '/img/pets/pets-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 272 AS `product_id`, '/img/pets/272-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 272 AS `product_id`, '/img/pets/272-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 273 AS `product_id`, '/img/baby/baby-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 273 AS `product_id`, '/img/baby/273-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 273 AS `product_id`, '/img/baby/273-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 274 AS `product_id`, '/img/baby/baby-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 274 AS `product_id`, '/img/baby/274-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 274 AS `product_id`, '/img/baby/274-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 275 AS `product_id`, '/img/baby/baby-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 275 AS `product_id`, '/img/baby/275-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 275 AS `product_id`, '/img/baby/275-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 276 AS `product_id`, '/img/baby/baby-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 276 AS `product_id`, '/img/baby/276-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 276 AS `product_id`, '/img/baby/276-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 277 AS `product_id`, '/img/baby/baby-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 277 AS `product_id`, '/img/baby/277-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 277 AS `product_id`, '/img/baby/277-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 278 AS `product_id`, '/img/baby/baby-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 278 AS `product_id`, '/img/baby/278-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 278 AS `product_id`, '/img/baby/278-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 279 AS `product_id`, '/img/cars/cars-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 279 AS `product_id`, '/img/cars/279-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 279 AS `product_id`, '/img/cars/279-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 280 AS `product_id`, '/img/cars/cars-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 280 AS `product_id`, '/img/cars/280-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 280 AS `product_id`, '/img/cars/280-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 281 AS `product_id`, '/img/cars/cars-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 281 AS `product_id`, '/img/cars/281-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 281 AS `product_id`, '/img/cars/281-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 282 AS `product_id`, '/img/cars/cars-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 282 AS `product_id`, '/img/cars/282-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 282 AS `product_id`, '/img/cars/282-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 283 AS `product_id`, '/img/cars/cars-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 283 AS `product_id`, '/img/cars/283-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 283 AS `product_id`, '/img/cars/283-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 284 AS `product_id`, '/img/cars/cars-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 284 AS `product_id`, '/img/cars/284-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 284 AS `product_id`, '/img/cars/284-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 285 AS `product_id`, '/img/instruments/instruments-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 285 AS `product_id`, '/img/instruments/285-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 285 AS `product_id`, '/img/instruments/285-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 286 AS `product_id`, '/img/instruments/instruments-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 286 AS `product_id`, '/img/instruments/286-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 286 AS `product_id`, '/img/instruments/286-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 287 AS `product_id`, '/img/instruments/instruments-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 287 AS `product_id`, '/img/instruments/287-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 287 AS `product_id`, '/img/instruments/287-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 288 AS `product_id`, '/img/instruments/instruments-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 288 AS `product_id`, '/img/instruments/288-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 288 AS `product_id`, '/img/instruments/288-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 289 AS `product_id`, '/img/instruments/instruments-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 289 AS `product_id`, '/img/instruments/289-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 289 AS `product_id`, '/img/instruments/289-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 290 AS `product_id`, '/img/instruments/instruments-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 290 AS `product_id`, '/img/instruments/290-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 290 AS `product_id`, '/img/instruments/290-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 291 AS `product_id`, '/img/books/books-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 291 AS `product_id`, '/img/books/291-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 291 AS `product_id`, '/img/books/291-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 292 AS `product_id`, '/img/books/books-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 292 AS `product_id`, '/img/books/292-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 292 AS `product_id`, '/img/books/292-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 293 AS `product_id`, '/img/books/books-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 293 AS `product_id`, '/img/books/293-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 293 AS `product_id`, '/img/books/293-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 294 AS `product_id`, '/img/books/books-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 294 AS `product_id`, '/img/books/294-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 294 AS `product_id`, '/img/books/294-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 295 AS `product_id`, '/img/books/books-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 295 AS `product_id`, '/img/books/295-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 295 AS `product_id`, '/img/books/295-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 296 AS `product_id`, '/img/books/books-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 296 AS `product_id`, '/img/books/296-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 296 AS `product_id`, '/img/books/296-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 297 AS `product_id`, '/img/fresh/fresh-01.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 297 AS `product_id`, '/img/fresh/297-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 297 AS `product_id`, '/img/fresh/297-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 298 AS `product_id`, '/img/fresh/fresh-02.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 298 AS `product_id`, '/img/fresh/298-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 298 AS `product_id`, '/img/fresh/298-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 299 AS `product_id`, '/img/fresh/fresh-03.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 299 AS `product_id`, '/img/fresh/299-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 299 AS `product_id`, '/img/fresh/299-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 300 AS `product_id`, '/img/fresh/fresh-04.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 300 AS `product_id`, '/img/fresh/300-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 300 AS `product_id`, '/img/fresh/300-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 301 AS `product_id`, '/img/fresh/fresh-05.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 301 AS `product_id`, '/img/fresh/301-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 301 AS `product_id`, '/img/fresh/301-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);
INSERT INTO `product_image` (`product_id`, `url`, `sort`)
SELECT `src`.`product_id`, `src`.`url`, `src`.`sort`
FROM (
    SELECT 302 AS `product_id`, '/img/fresh/fresh-06.jpg' AS `url`, 0 AS `sort`
    UNION ALL SELECT 302 AS `product_id`, '/img/fresh/302-1.jpg' AS `url`, 1 AS `sort`
    UNION ALL SELECT 302 AS `product_id`, '/img/fresh/302-2.jpg' AS `url`, 2 AS `sort`
) AS `src`
WHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` WHERE `i`.`product_id` = `src`.`product_id`);

-- -----------------------------------------------------------------------------
-- 3. 款式图：204 个 SKU（尺码类规格除外）
-- -----------------------------------------------------------------------------
UPDATE `product_sku` SET `sku_image` = '/img/skincare/201-1.jpg' WHERE `id` = 288 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/201-2.jpg' WHERE `id` = 289 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/202-1.jpg' WHERE `id` = 290 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/202-2.jpg' WHERE `id` = 291 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/203-1.jpg' WHERE `id` = 292 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/203-2.jpg' WHERE `id` = 293 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/204-1.jpg' WHERE `id` = 294 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/204-2.jpg' WHERE `id` = 295 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/205-1.jpg' WHERE `id` = 296 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/205-2.jpg' WHERE `id` = 297 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/206-1.jpg' WHERE `id` = 298 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/skincare/206-2.jpg' WHERE `id` = 299 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/207-1.jpg' WHERE `id` = 300 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/207-2.jpg' WHERE `id` = 301 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/208-1.jpg' WHERE `id` = 302 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/208-2.jpg' WHERE `id` = 303 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/209-1.jpg' WHERE `id` = 304 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/209-2.jpg' WHERE `id` = 305 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/210-1.jpg' WHERE `id` = 306 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/210-2.jpg' WHERE `id` = 307 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/211-1.jpg' WHERE `id` = 308 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/211-2.jpg' WHERE `id` = 309 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/212-1.jpg' WHERE `id` = 310 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bath/212-2.jpg' WHERE `id` = 311 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/213-1.jpg' WHERE `id` = 312 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/213-2.jpg' WHERE `id` = 313 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/214-1.jpg' WHERE `id` = 314 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/214-2.jpg' WHERE `id` = 315 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/215-1.jpg' WHERE `id` = 316 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/215-2.jpg' WHERE `id` = 317 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/216-1.jpg' WHERE `id` = 318 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/216-2.jpg' WHERE `id` = 319 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/217-1.jpg' WHERE `id` = 320 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/217-2.jpg' WHERE `id` = 321 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/218-1.jpg' WHERE `id` = 322 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/bags/218-2.jpg' WHERE `id` = 323 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/219-1.jpg' WHERE `id` = 324 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/219-2.jpg' WHERE `id` = 325 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/220-1.jpg' WHERE `id` = 326 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/220-2.jpg' WHERE `id` = 327 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/221-1.jpg' WHERE `id` = 328 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/221-2.jpg' WHERE `id` = 329 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/222-1.jpg' WHERE `id` = 330 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/222-2.jpg' WHERE `id` = 331 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/223-1.jpg' WHERE `id` = 332 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/223-2.jpg' WHERE `id` = 333 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/224-1.jpg' WHERE `id` = 334 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/home/224-2.jpg' WHERE `id` = 335 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/225-1.jpg' WHERE `id` = 336 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/225-2.jpg' WHERE `id` = 337 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/226-1.jpg' WHERE `id` = 338 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/226-2.jpg' WHERE `id` = 339 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/227-1.jpg' WHERE `id` = 340 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/227-2.jpg' WHERE `id` = 341 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/228-1.jpg' WHERE `id` = 342 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/228-2.jpg' WHERE `id` = 343 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/229-1.jpg' WHERE `id` = 344 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/229-2.jpg' WHERE `id` = 345 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/230-1.jpg' WHERE `id` = 346 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/accessories/230-2.jpg' WHERE `id` = 347 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/231-1.jpg' WHERE `id` = 348 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/231-2.jpg' WHERE `id` = 349 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/232-1.jpg' WHERE `id` = 350 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/232-2.jpg' WHERE `id` = 351 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/233-1.jpg' WHERE `id` = 352 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/233-2.jpg' WHERE `id` = 353 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/234-1.jpg' WHERE `id` = 354 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/234-2.jpg' WHERE `id` = 355 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/235-1.jpg' WHERE `id` = 356 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/235-2.jpg' WHERE `id` = 357 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/236-1.jpg' WHERE `id` = 358 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/sports/236-2.jpg' WHERE `id` = 359 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/237-1.jpg' WHERE `id` = 360 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/237-2.jpg' WHERE `id` = 361 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/238-1.jpg' WHERE `id` = 362 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/238-2.jpg' WHERE `id` = 363 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/239-1.jpg' WHERE `id` = 364 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/239-2.jpg' WHERE `id` = 365 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/240-1.jpg' WHERE `id` = 366 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/240-2.jpg' WHERE `id` = 367 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/241-1.jpg' WHERE `id` = 368 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/241-2.jpg' WHERE `id` = 369 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/242-1.jpg' WHERE `id` = 370 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/watches/242-2.jpg' WHERE `id` = 371 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/243-1.jpg' WHERE `id` = 372 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/243-2.jpg' WHERE `id` = 373 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/244-1.jpg' WHERE `id` = 374 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/244-2.jpg' WHERE `id` = 375 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/245-1.jpg' WHERE `id` = 376 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/245-2.jpg' WHERE `id` = 377 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/246-1.jpg' WHERE `id` = 378 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/246-2.jpg' WHERE `id` = 379 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/247-1.jpg' WHERE `id` = 380 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/247-2.jpg' WHERE `id` = 381 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/248-1.jpg' WHERE `id` = 382 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/drinks/248-2.jpg' WHERE `id` = 383 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/249-1.jpg' WHERE `id` = 384 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/249-2.jpg' WHERE `id` = 385 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/250-1.jpg' WHERE `id` = 386 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/250-2.jpg' WHERE `id` = 387 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/251-1.jpg' WHERE `id` = 388 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/251-2.jpg' WHERE `id` = 389 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/252-1.jpg' WHERE `id` = 390 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/252-2.jpg' WHERE `id` = 391 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/253-1.jpg' WHERE `id` = 392 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/253-2.jpg' WHERE `id` = 393 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/254-1.jpg' WHERE `id` = 394 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/health/254-2.jpg' WHERE `id` = 395 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/255-1.jpg' WHERE `id` = 396 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/255-2.jpg' WHERE `id` = 397 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/256-1.jpg' WHERE `id` = 398 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/256-2.jpg' WHERE `id` = 399 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/257-1.jpg' WHERE `id` = 400 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/257-2.jpg' WHERE `id` = 401 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/258-1.jpg' WHERE `id` = 402 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/258-2.jpg' WHERE `id` = 403 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/259-1.jpg' WHERE `id` = 404 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/259-2.jpg' WHERE `id` = 405 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/260-1.jpg' WHERE `id` = 406 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/appliances/260-2.jpg' WHERE `id` = 407 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/261-1.jpg' WHERE `id` = 408 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/261-2.jpg' WHERE `id` = 409 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/262-1.jpg' WHERE `id` = 410 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/262-2.jpg' WHERE `id` = 411 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/263-1.jpg' WHERE `id` = 412 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/263-2.jpg' WHERE `id` = 413 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/264-1.jpg' WHERE `id` = 414 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/264-2.jpg' WHERE `id` = 415 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/265-1.jpg' WHERE `id` = 416 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/265-2.jpg' WHERE `id` = 417 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/266-1.jpg' WHERE `id` = 418 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/toys/266-2.jpg' WHERE `id` = 419 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/267-1.jpg' WHERE `id` = 420 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/267-2.jpg' WHERE `id` = 421 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/268-1.jpg' WHERE `id` = 422 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/268-2.jpg' WHERE `id` = 423 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/269-1.jpg' WHERE `id` = 424 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/269-2.jpg' WHERE `id` = 425 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/270-1.jpg' WHERE `id` = 426 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/270-2.jpg' WHERE `id` = 427 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/271-1.jpg' WHERE `id` = 428 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/271-2.jpg' WHERE `id` = 429 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/272-1.jpg' WHERE `id` = 430 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/pets/272-2.jpg' WHERE `id` = 431 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/273-1.jpg' WHERE `id` = 432 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/273-2.jpg' WHERE `id` = 433 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/274-1.jpg' WHERE `id` = 434 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/274-2.jpg' WHERE `id` = 435 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/275-1.jpg' WHERE `id` = 436 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/275-2.jpg' WHERE `id` = 437 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/276-1.jpg' WHERE `id` = 438 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/276-2.jpg' WHERE `id` = 439 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/277-1.jpg' WHERE `id` = 440 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/277-2.jpg' WHERE `id` = 441 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/278-1.jpg' WHERE `id` = 442 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/baby/278-2.jpg' WHERE `id` = 443 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/279-1.jpg' WHERE `id` = 444 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/279-2.jpg' WHERE `id` = 445 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/280-1.jpg' WHERE `id` = 446 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/280-2.jpg' WHERE `id` = 447 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/281-1.jpg' WHERE `id` = 448 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/281-2.jpg' WHERE `id` = 449 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/282-1.jpg' WHERE `id` = 450 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/282-2.jpg' WHERE `id` = 451 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/283-1.jpg' WHERE `id` = 452 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/283-2.jpg' WHERE `id` = 453 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/284-1.jpg' WHERE `id` = 454 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/cars/284-2.jpg' WHERE `id` = 455 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/285-1.jpg' WHERE `id` = 456 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/285-2.jpg' WHERE `id` = 457 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/286-1.jpg' WHERE `id` = 458 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/286-2.jpg' WHERE `id` = 459 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/287-1.jpg' WHERE `id` = 460 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/287-2.jpg' WHERE `id` = 461 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/288-1.jpg' WHERE `id` = 462 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/288-2.jpg' WHERE `id` = 463 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/289-1.jpg' WHERE `id` = 464 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/289-2.jpg' WHERE `id` = 465 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/290-1.jpg' WHERE `id` = 466 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/instruments/290-2.jpg' WHERE `id` = 467 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/291-1.jpg' WHERE `id` = 468 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/291-2.jpg' WHERE `id` = 469 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/292-1.jpg' WHERE `id` = 470 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/292-2.jpg' WHERE `id` = 471 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/293-1.jpg' WHERE `id` = 472 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/293-2.jpg' WHERE `id` = 473 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/294-1.jpg' WHERE `id` = 474 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/294-2.jpg' WHERE `id` = 475 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/295-1.jpg' WHERE `id` = 476 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/295-2.jpg' WHERE `id` = 477 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/296-1.jpg' WHERE `id` = 478 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/books/296-2.jpg' WHERE `id` = 479 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/297-1.jpg' WHERE `id` = 480 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/297-2.jpg' WHERE `id` = 481 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/298-1.jpg' WHERE `id` = 482 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/298-2.jpg' WHERE `id` = 483 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/299-1.jpg' WHERE `id` = 484 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/299-2.jpg' WHERE `id` = 485 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/300-1.jpg' WHERE `id` = 486 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/300-2.jpg' WHERE `id` = 487 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/301-1.jpg' WHERE `id` = 488 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/301-2.jpg' WHERE `id` = 489 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/302-1.jpg' WHERE `id` = 490 AND (`sku_image` IS NULL OR `sku_image` = '');
UPDATE `product_sku` SET `sku_image` = '/img/fresh/302-2.jpg' WHERE `id` = 491 AND (`sku_image` IS NULL OR `sku_image` = '');
