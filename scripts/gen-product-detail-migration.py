#!/usr/bin/env python3
"""生成 `V23__product_detail_content.sql`：补齐商品详情的图文内容、图集与款式图。

要解决的问题
------------
详情页「商品详情」区此前只有一行字：商品 1/2/6/7 的 `detail` 是空串（页面显示
「暂无详情内容」），201~302 是一句「本商品为演示数据，规格与库存仅用于功能验证」。
同时 201~302 在 `product_image` 里一行都没有（图集只有主图一张），
`product_sku.sku_image` 有 356 行是 NULL（款式卡片上没有款式图）。

本脚本按「分类模板 + 商品自身卖点/规格」组装详情 HTML，并补图集与款式图，
产出一个可重复生成的 Flyway 迁移。**内容全部来自库里已有的字段**（商品名、副标题、
SKU 规格值、分类、图集），只有分类级的通用说明（使用建议、售后条款）是模板文案。

用法
----
    python scripts/gen-product-detail-migration.py

前置
----
- `docker exec tickcart-mysql` 可用（脚本只读它，不写库）
- 已跑过 `scripts/fetch-detail-images.py`，`.tmp/detail-images.json` 存在

输出
----
`backend/src/main/resources/db/migration/V23__product_detail_content.sql`
"""

from __future__ import annotations

import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(ROOT, ".tmp", "detail-images.json")
OUT = os.path.join(
    ROOT, "backend", "src", "main", "resources", "db", "migration", "V23__product_detail_content.sql"
)

MYSQL = ["docker", "exec", "tickcart-mysql", "mysql", "--default-character-set=utf8mb4",
         "-uroot", "-proot", "-N", "-B", "tickcart", "-e"]

# 分类级模板：卖点补充、参数补充、使用建议。
# 商品自己的卖点（副标题）与规格（SKU）在运行时拼进去，这里只放「同类商品通用」的部分。
CATEGORY_COPY: dict[str, dict] = {
    "手机数码": {
        "bullets": ["出厂预装正版系统，开箱即用", "全国联保一年，官方售后网点可查"],
        "params": [("包装清单", "主机 × 1、数据线 × 1、说明书 × 1")],
        "usage": "首次使用建议先充满电；避免在高温或潮湿环境中长时间使用，"
                 "清洁时用干燥软布擦拭，不要使用腐蚀性清洁剂。",
    },
    "鞋类": {
        "bullets": ["鞋楦按亚洲脚型开发，久走不夹脚", "鞋底耐磨橡胶配方，湿滑地面也稳"],
        "params": [("鞋面材质", "按款式而定，详见商品标题"), ("适用场景", "日常通勤 / 休闲出行")],
        "usage": "新鞋建议先短时间试穿磨合；皮鞋避免长时间浸水，淋湿后阴干，"
                 "不要靠近热源烘烤。",
    },
    "服饰": {
        "bullets": ["版型经过多次试身调整，上身不显臃肿", "面料亲肤，可机洗不易变形"],
        "params": [("面料成分", "按款式而定，详见商品标题"), ("洗涤方式", "机洗冷水，反面洗涤")],
        "usage": "深浅色分开洗涤，水温不超过 30℃；针织与真丝类建议手洗或装洗衣袋，"
                 "避免暴晒。",
    },
    "护肤彩妆": {
        "bullets": ["配方温和，日常护理可长期使用", "质地细腻好推开，上脸不搓泥"],
        "params": [("适用肤质", "各种肤质"), ("保质期", "未开封 3 年，开封后建议 12 个月内用完"),
                   ("储存方式", "阴凉干燥处保存，避免阳光直射")],
        "usage": "取适量于掌心，均匀涂抹于清洁后的肌肤并轻拍至吸收；"
                 "首次使用建议先在耳后小面积试用。",
    },
    "个人洗护": {
        "bullets": ["泡沫细腻易冲洗，洗后不假滑", "淡香配方，留香自然不冲鼻"],
        "params": [("适用人群", "成人通用"), ("保质期", "未开封 3 年"),
                   ("储存方式", "阴凉干燥处保存，避免阳光直射")],
        "usage": "取适量加水揉搓起泡后使用，再用清水充分冲净；避免接触眼睛，"
                 "不慎入眼请立即用清水冲洗。",
    },
    "箱包": {
        "bullets": ["内部分区合理，日常通勤与短途出行都能装", "五金件与走线加固，长期使用不易开线"],
        "params": [("材质", "按款式而定，详见商品标题"), ("适用场景", "通勤 / 出差 / 旅行")],
        "usage": "避免长时间受潮与暴晒；皮具沾水后及时用干布吸干，"
                 "定期使用专用护理剂保养可延长寿命。",
    },
    "家居百货": {
        "bullets": ["尺寸经过实测，日常使用不占地方", "材质安全，接触食物/皮肤的部分均符合国标"],
        "params": [("材质", "按款式而定，详见商品标题"), ("适用场景", "家庭日常使用")],
        "usage": "首次使用前请清洗干净；避免骤冷骤热，以免材质开裂；"
                 "带有涂层或木质的器具请勿使用钢丝球刷洗。",
    },
    "配饰": {
        "bullets": ["细节打磨到位，日常佩戴不勾衣物", "附赠收纳盒，送礼自用都合适"],
        "params": [("材质", "按款式而定，详见商品标题"), ("保养", "避免接触香水与汗液")],
        "usage": "佩戴后建议用软布擦拭表面再收纳；避免与硬物碰撞，"
                 "长期不戴时请密封保存以防氧化。",
    },
    "运动": {
        "bullets": ["家用场景友好，收纳不占地", "握感与防滑做过实测，出汗也不打滑"],
        "params": [("适用场景", "居家健身 / 户外运动"), ("材质", "按款式而定，详见商品标题")],
        "usage": "运动前请充分热身；器械类请定期检查连接件是否松动，"
                 "出现破损立即停止使用。",
    },
    "手表": {
        "bullets": ["走时稳定，日常佩戴误差小", "表带可自行更换，通勤运动换着戴"],
        "params": [("防水等级", "生活防水，避免游泳与热水淋浴"), ("包装清单", "手表 × 1、说明书 × 1")],
        "usage": "避免在热水澡、桑拿等高温高湿环境佩戴；石英表建议 1~2 年更换一次电池，"
                 "长期不戴请取出电池。",
    },
    "酒水": {
        "bullets": ["原瓶进口/原厂直供，全程控温运输", "礼盒装可选，节日送礼体面"],
        "params": [("储存条件", "阴凉避光，卧放或竖放均可"), ("温馨提示", "未成年人禁止饮酒，请适量饮用")],
        "usage": "开瓶后建议尽快饮用；红葡萄酒醒酒 20~30 分钟风味更佳，"
                 "未饮完可塞回软木塞冷藏保存 2~3 天。",
    },
    "医药健康": {
        "bullets": ["家庭常备，日常护理与应急都能用上", "独立包装/分格收纳，取用干净卫生"],
        "params": [("适用人群", "成人及 3 岁以上儿童（具体见包装说明）"),
                   ("储存方式", "阴凉干燥处，儿童接触不到的地方")],
        "usage": "请按包装或说明书标注的用法用量使用；本品不能替代药品与专业诊疗，"
                 "症状持续或加重请及时就医。",
    },
    "家电": {
        "bullets": ["一键操作，老人小孩也能上手", "机身可拆洗，日常清洁不费劲"],
        "params": [("额定电压", "220V / 50Hz"), ("保修", "整机保修 1 年，主要部件 2 年")],
        "usage": "使用前请确认电压匹配并可靠接地；清洁前务必断电，"
                 "不要将机身浸入水中冲洗。",
    },
    "模玩盲盒": {
        "bullets": ["细节涂装到位，摆桌面很有氛围", "关节/结构可动，把玩与展示两不误"],
        "params": [("适用年龄", "15 岁以上"), ("材质", "PVC / ABS")],
        "usage": "避免暴晒与高温，防止材质变形褪色；可动部件请勿大力掰动，"
                 "细小零件请远离婴幼儿。",
    },
    "宠物": {
        "bullets": ["材质安全无异味，宠物接受度高", "结构简单好清洗，日常打理省心"],
        "params": [("适用宠物", "猫 / 犬通用（具体见商品标题）"), ("清洁方式", "可水洗，晾干后使用")],
        "usage": "首次使用请先清洗晾干；观察宠物适应情况，如出现啃咬吞咽风险请及时收纳。",
    },
    "母婴": {
        "bullets": ["母婴级材质，通过相关安全检测", "结构圆润无锐角，宝宝使用更放心"],
        "params": [("适用年龄", "详见商品标题"), ("执行标准", "符合国家母婴用品相关标准")],
        "usage": "使用前请清洗并消毒；请勿让宝宝单独使用，成人需在旁看护；"
                 "出现破损、变形请立即停用。",
    },
    "车类": {
        "bullets": ["安装简单，多数车型通用", "用料耐高温，夏季暴晒不变形"],
        "params": [("适用车型", "轿车 / SUV 通用（具体见商品标题）"), ("安装方式", "免工具安装")],
        "usage": "安装前请清洁接触面并保持干燥；涉及电路的产品请确认接口匹配，"
                 "不确定时建议到店安装。",
    },
    "乐器": {
        "bullets": ["出厂已调音，开箱即可上手", "附赠基础教程与配件，零基础也能学"],
        "params": [("材质", "按款式而定，详见商品标题"), ("包装清单", "乐器 × 1、配件若干（见商品标题）")],
        "usage": "每次弹奏后用软布擦拭琴身与琴弦；避免阳光直射与温湿剧变，"
                 "长期不用请松弦并放入琴包。",
    },
    "图书": {
        "bullets": ["正版授权，印刷清晰不伤眼", "纸张厚实不透印，翻阅手感好"],
        "params": [("装帧", "平装 / 精装（见商品标题）"), ("开本", "16 开")],
        "usage": "建议在光线充足处阅读，每 40 分钟休息一次；存放时避免受潮与重压。",
    },
    "食品生鲜": {
        "bullets": ["产地直发，全程冷链锁鲜", "下单后 48 小时内发出，坏果包赔"],
        "params": [("储存方式", "冷藏保存，建议 3 天内食用"), ("保质期", "以包装标注为准")],
        "usage": "收到后请及时开箱检查并冷藏；生食类请清洗干净，"
                 "如发现变质请拍照留存并联系售后。",
    },
}

DEFAULT_COPY = {
    "bullets": ["正品保障，支持 7 天无理由退换", "现货直发，下单后尽快安排出库"],
    "params": [("包装清单", "详见商品标题")],
    "usage": "请按商品说明使用与保养；如有疑问可在订单详情发起咨询。",
}

AFTER_SALE = (
    "7 天无理由退换 · 正品保障 · 全场免运费。"
    "签收后如发现破损、错发或与描述不符，可在「我的 - 订单详情」发起售后，客服会尽快处理。"
)

# 尺码类规格不配款式图：同一件衣服的 M 码和 L 码外观一样，逐码配图反而会误导
SIZE_SPECS = {"尺码", "均码"}


def sql_text(value: str) -> str:
    """把 Python 字符串转成 MySQL 单引号字面量"""
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def query(sql: str) -> list[list[str]]:
    result = subprocess.run(MYSQL + [sql], capture_output=True)
    if result.returncode != 0:
        sys.stderr.write(result.stderr.decode("utf-8", "ignore"))
        raise SystemExit("查询失败，请确认 tickcart-mysql 容器在运行")
    out = result.stdout.decode("utf-8", "ignore")
    return [line.split("\t") for line in out.splitlines() if line]


def split_selling_points(subtitle: str) -> list[str]:
    """副标题「绵密泡沫，敏感肌适用」→ 两条卖点；分不出两条就整句当一条"""
    parts = [p.strip(" 。.") for p in subtitle.replace("；", "，").split("，")]
    return [p for p in parts if p]


def build_detail(row: dict, images: list[str], copy: dict) -> str:
    bullets = split_selling_points(row["subtitle"])[:3]
    for extra in copy["bullets"]:
        if len(bullets) >= 4:
            break
        if extra not in bullets:
            bullets.append(extra)

    # 参数表只放「详情页拿不到」的信息（规格 + 分类通用参数）；
    # 商品编号/分类/销量这类实时字段由详情页的「商品参数」区块展示，两边不重复。
    params: list[tuple[str, str]] = []
    if row["spec_name"] and row["spec_values"]:
        params.append((row["spec_name"], " / ".join(row["spec_values"])))
    params.extend(copy["params"])

    bullet_html = "".join(f"<li>{b}</li>" for b in bullets)
    param_html = "".join(f"<tr><th>{k}</th><td>{v}</td></tr>" for k, v in params)

    blocks = [
        '<section class="pd-block"><h3>产品亮点</h3>',
        f"<ul>{bullet_html}</ul></section>",
    ]
    if images:
        blocks.append(f'<img src="{images[0]}" alt="{row["name"]}">')
    blocks.append('<section class="pd-block"><h3>规格参数</h3>')
    blocks.append(f"<table>{param_html}</table></section>")
    if len(images) > 1:
        blocks.append(f'<img src="{images[1]}" alt="{row["name"]}">')
    blocks.append(f'<section class="pd-block"><h3>使用与保养</h3><p>{copy["usage"]}</p></section>')
    blocks.append(f'<section class="pd-block"><h3>售后保障</h3><p>{AFTER_SALE}</p></section>')
    return "".join(blocks)


def main() -> int:
    if not os.path.exists(MANIFEST):
        raise SystemExit("缺少 .tmp/detail-images.json，请先跑 scripts/fetch-detail-images.py")
    with open(MANIFEST, encoding="utf-8") as fh:
        manifest = json.load(fh)

    # 商品基础信息 + 分类名
    products = {}
    for pid, name, subtitle, category, main_image in query(
        "SELECT p.id, p.name, COALESCE(p.subtitle,''), COALESCE(c.name,'其他'), COALESCE(p.main_image,'') "
        "FROM product p LEFT JOIN product_category c ON c.id = p.category_id "
        "WHERE p.deleted = 0 ORDER BY p.id"
    ):
        products[int(pid)] = {
            "id": pid,
            "name": name,
            "subtitle": subtitle,
            "category": category,
            "main_image": main_image,
            "spec_name": "",
            "spec_values": [],
            "skus": [],
        }

    # 图集（含本次新增的）：按 sort 排序
    galleries: dict[int, list[str]] = {}
    for pid, url in query(
        "SELECT product_id, url FROM product_image ORDER BY product_id, sort, id"
    ):
        galleries.setdefault(int(pid), []).append(url)

    # SKU：规格名/值、是否有图、id
    for pid, sid, spec_name, spec_value, sku_image in query(
        "SELECT product_id, id, COALESCE(spec_name,''), COALESCE(spec_value,''), COALESCE(sku_image,'') "
        "FROM product_sku WHERE deleted = 0 ORDER BY product_id, id"
    ):
        product = products.get(int(pid))
        if not product:
            continue
        product["skus"].append(
            {"id": int(sid), "spec_name": spec_name, "spec_value": spec_value, "sku_image": sku_image}
        )
        if not product["spec_name"] and spec_name:
            product["spec_name"] = spec_name
        if spec_value and spec_value not in product["spec_values"]:
            product["spec_values"].append(spec_value)

    # 本次新增的图集（201~302）：先算进 galleries，详情 HTML 才能引用
    for pid, entries in manifest.items():
        pid = int(pid)
        if pid not in products:
            continue
        existing = galleries.setdefault(pid, [])
        for entry in entries:
            if entry["file"] not in existing:
                existing.append(entry["file"])

    lines: list[str] = []
    add = lines.append

    add("-- =============================================================================")
    add("-- TickCart 秒杀商城 · 数据库迁移 V23")
    add("-- 内容：补齐商品详情的图文内容、图集与款式图")
    add("--")
    add("-- 背景")
    add("--   详情页的「商品详情」区此前只有一行字：商品 1/2/6/7 的 detail 为空（显示「暂无详情内容」），")
    add("--   201~302 是一句「本商品为演示数据…」。同时 201~302 在 product_image 里一行都没有，")
    add("--   图集只有主图一张；product_sku.sku_image 有 356 行为 NULL，款式卡片上没有款式图。")
    add("--")
    add("--   本迁移按「分类模板 + 商品自身卖点/规格」为每个商品组装图文详情（HTML），")
    add("--   并补图集与款式图。详情 HTML 由 scripts/gen-product-detail-migration.py 生成，")
    add("--   内容只用到库里已有的字段；分类级通用说明（使用建议、售后条款）为模板文案。")
    add("--")
    add("-- 幂等性")
    add("--   · detail 只在「空」或仍是占位文案时覆盖 —— 后台手工改过的详情不会被冲掉。")
    add("--   · 图集按「该商品已有任何图集行就整批跳过」守卫（沿用 V9 的写法）。")
    add("--   · sku_image 只在 NULL/空串时写入。")
    add("--")
    add("-- 款式图口径")
    add("--   尺码类规格（鞋服的「尺码」「均码」）**不配款式图**：同一件衣服的 M 码与 L 码")
    add("--   外观完全一致，逐码配图会让人误以为「换个尺码换个样子」。详情页对这类规格")
    add("--   回退显示商品主图，见 ProductDetailView.vue 的 skuThumb()。")
    add("-- =============================================================================")
    add("")

    detail_updates: list[str] = []
    image_inserts: list[str] = []
    sku_updates: list[str] = []

    for pid in sorted(products):
        product = products[pid]
        copy = CATEGORY_COPY.get(product["category"], DEFAULT_COPY)
        images = galleries.get(pid, [])
        new_images = manifest.get(str(pid), [])

        # 详情里插的图必须**确实是这件商品**的：
        # 本次补的 2 张是按商品逐一定向找的，直接用；没有补图的商品只用它自己的主图。
        # 不能顺手拿图集第 2/3 张 —— 1/2/6/7 的图集里混着别的机型（V7 留下的），
        # 101~156 的图集是「同分类不同商品」的图，插进详情等于给商品配错图。
        detail_images = [entry["file"] for entry in new_images] or images[:1]

        html = build_detail(product, detail_images, copy)
        detail_updates.append(
            f"UPDATE `product` SET `detail` = {sql_text(html)}\n"
            f"WHERE `id` = {pid} AND (`detail` IS NULL OR `detail` = '' "
            f"OR `detail` LIKE '%本商品为演示数据%');"
        )

        if new_images:
            rows = []
            # sort 0 = 商品主图：其他商品的图集都把它放在第一位，201~302 也要一致，
            # 否则从列表点进详情时首图会和列表缩略图对不上。
            if product["main_image"]:
                rows.append(
                    f"    SELECT {pid} AS `product_id`, "
                    f"{sql_text(product['main_image'])} AS `url`, 0 AS `sort`"
                )
            for entry in new_images:
                sort = int(entry["file"].rsplit("-", 1)[1].split(".")[0])
                rows.append(f"    SELECT {pid} AS `product_id`, {sql_text(entry['file'])} AS `url`, {sort} AS `sort`")
            image_inserts.append(
                "INSERT INTO `product_image` (`product_id`, `url`, `sort`)\nSELECT "
                "`src`.`product_id`, `src`.`url`, `src`.`sort`\nFROM (\n"
                + "\n    UNION ALL ".join(rows).replace("    UNION ALL     SELECT", "    UNION ALL SELECT")
                + "\n) AS `src`\nWHERE NOT EXISTS (SELECT 1 FROM `product_image` `i` "
                  f"WHERE `i`.`product_id` = `src`.`product_id`);"
            )

        # 款式图：只补「非尺码」且没有图的规格，按图集顺序循环取
        if images:
            slot = 0
            for sku in product["skus"]:
                if sku["sku_image"] or sku["spec_name"] in SIZE_SPECS:
                    continue
                image = images[slot % len(images)]
                slot += 1
                sku_updates.append(
                    f"UPDATE `product_sku` SET `sku_image` = {sql_text(image)} "
                    f"WHERE `id` = {sku['id']} AND (`sku_image` IS NULL OR `sku_image` = '');"
                )

    add("-- -----------------------------------------------------------------------------")
    add("-- 1. 图文详情（HTML）")
    add("-- -----------------------------------------------------------------------------")
    lines.extend(detail_updates)
    add("")
    add("-- -----------------------------------------------------------------------------")
    add(f"-- 2. 图集：{len(image_inserts)} 个商品各补 2 张（201~302 此前没有任何图集行）")
    add("-- -----------------------------------------------------------------------------")
    lines.extend(image_inserts)
    add("")
    add("-- -----------------------------------------------------------------------------")
    add(f"-- 3. 款式图：{len(sku_updates)} 个 SKU（尺码类规格除外）")
    add("-- -----------------------------------------------------------------------------")
    lines.extend(sku_updates)
    add("")

    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines))

    print(f"已生成 {OUT}")
    print(f"  详情 {len(detail_updates)} 个商品 / 图集 {len(image_inserts)} 个商品 / 款式图 {len(sku_updates)} 个 SKU")
    return 0


if __name__ == "__main__":
    sys.exit(main())
