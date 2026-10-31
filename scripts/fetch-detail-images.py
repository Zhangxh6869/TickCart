#!/usr/bin/env python3
"""给 V12 那批「只有一张主图」的商品补图集图片。

背景
----
V12 只给 102 个商品（id 201~302）配了一张主图，`product_image` 一行都没有，
详情页图集只有 1 张、缩略图条和「图集 N/M」都形同虚设。本脚本按分类下载
Pexels 免费图，每个商品 2 张，落到 `imgs/<分类目录>/<商品id>-1.jpg` / `-2.jpg`。

为什么是「固定清单」而不是「现场检索」
------------------------------------
Pexels 的搜索页对连续请求会直接返回 403（同一 IP 大约十来次就开始拦），
边跑边搜的结果就是「跑到一半整批失败」。所以检索在开发时一次性做完，
把命中的 photo id 固化在下面的 `PLAN` 里；脚本只负责下载 —— 图片 CDN
（`images.pexels.com`）不受这个限流影响，可以稳定重跑。

用法
----
    python scripts/fetch-detail-images.py            # 下载（已存在且清单一致的跳过）
    python scripts/fetch-detail-images.py --force    # 全部重新下载
    python scripts/fetch-detail-images.py --only 201 202

产出
----
- `imgs/<分类目录>/<商品id>-<序号>.jpg`（900×900 方图，JPEG）
- `.tmp/detail-images.json`：本地路径 / Pexels photo id / slug，
  供生成 Flyway 迁移与回写 `imgs/SOURCES.md` 使用

授权
----
Pexels License（https://www.pexels.com/license/），可免费使用，仅作本地演示数据。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMGS = os.path.join(ROOT, "imgs")
OUT_JSON = os.path.join(ROOT, ".tmp", "detail-images.json")

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)
# ★ 图片请求只接受 JPEG：带上 avif/webp 时 CDN 会返回 AVIF，
#   文件名还是 .jpg 但内容不是 JPEG，后续 <img> 在部分环境里会加载失败。
IMG_HEADERS = {"User-Agent": UA, "Accept": "image/jpeg,image/*;q=0.8"}

# 分类图片目录 -> (该分类的商品 id，按商品顺序；Pexels photo id 与 slug，每商品 2 张按顺序取)
PLAN: dict[str, tuple[list[int], list[tuple[int, str]]]] = {
    "skincare": (
        [201, 202, 203, 204, 205, 206],
        [
            (15569182, "bottle-with-a-cosmetic-product"),
            (12969358, "tubes-of-cosmetics-to-makeup-and-a-makeup-brush"),
            (6767761, "glass-bottles-with-droppers"),
            (20849460, "two-small-bottle-on-a-wooden-tray"),
            (24602070, "jar-of-moisturiser"),
            (24602075, "display-of-skincare-products"),
            (11753640, "white-plastic-small-jar"),
            (4735904, "assorted-cosmetics-on-white-surface"),
            (2533266, "close-up-photo-of-pink-lipstick-and-blush-on"),
            (1213558, "pink-lipstick"),
            (2732197, "pressed-powder-lot"),
            (5563664, "cosmetic-products-on-wooden-table"),
        ],
    ),
    "bath": (
        [207, 208, 209, 210, 211, 212],
        [
            (16749129, "photo-of-a-shampoo-bottle"),
            (7440056, "photograph-of-a-white-bottle-with-shampoo"),
            (13516802, "bottles-of-cosmetics"),
            (13516803, "plastic-pump-bottle-on-white-textile"),
            (4154194, "orange-and-black-pump-bottle"),
            (5629136, "arranged-pump-bottles-of-bath-essentials"),
            (8015792, "white-dispenser-pump-and-white-tube"),
            (8467957, "assorted-cosmetic-products-near-black-bowl"),
            (19717724, "close-up-of-two-bottles-on-a-yellow-background"),
            (8054407, "white-and-black-pump-bottle"),
            (13006784, "a-close-up-shot-of-a-plastic-bottle"),
            (8015465, "close-up-photo-of-empty-containers-on-white-surface"),
        ],
    ),
    "bags": (
        [213, 214, 215, 216, 217, 218],
        [
            (21897118, "hand-bags-shoot"),
            (8502477, "photo-of-a-brown-leather-bag-on-white-surface"),
            (22434759, "bags-on-white-background"),
            (21897314, "hand-bags-shoot"),
            (19197737, "close-up-of-of-a-handbag"),
            (36385199, "elegant-gray-handbag-with-festive-decoration"),
            (21897151, "hand-bags-shoot"),
            (22432985, "red-and-gray-bags"),
            (5706269, "pink-small-leather-handbags-on-white-tables"),
            (27046147, "leather-bags-in-a-studio"),
            (22434775, "leather-bags-on-white-background"),
            (27046143, "brown-and-black-bags"),
        ],
    ),
    "home": (
        [219, 220, 221, 222, 223, 224],
        [
            (17974511, "kitchenware-on-white-background"),
            (30694614, "cozy-kitchen-scene-with-elegant-white-dishware"),
            (19599328, "pans-and-blender-in-store"),
            (7303849, "kitchen-counter-with-kitchenware"),
            (16955291, "close-up-of-glasses-near-sink-in-kitchen"),
            (5556176, "plates-and-clear-wine-glasses-on-white-wooden-kitchen-cabinet"),
            (6962755, "wooden-bowls-and-wooden-spoons-on-the-table"),
            (7303977, "wooden-tableware-on-wooden-tray-with-ceramic-pitcher"),
            (29219834, "organized-kitchen-shelf-with-various-items"),
            (10936546, "black-kitchenware-on-a-woven-mat"),
            (10117711, "composition-of-kitchen-utensils"),
            (34433526, "rustic-kitchen-window-with-thermos-and-pots"),
        ],
    ),
    "accessories": (
        [225, 226, 227, 228, 229, 230],
        [
            (9328377, "close-up-photo-of-a-woman-in-a-white-top-wearing-necklaces"),
            (5494377, "tablet-sunglasses-and-earrings-on-paper-background"),
            (8084237, "pink-sunglasses-on-white-surface"),
            (10256324, "woman-wearing-red-framed-sunglasses"),
            (2986445, "various-womens-fashion-accessories"),
            (37575397, "elegant-makeup-and-accessories-flat-lay"),
            (12901704, "black-framed-sunglasses-and-jewelries"),
            (5405629, "top-view-of-vacation-accessories"),
            (5494376, "tablet-and-sunglasses-near-white-daisies"),
            (7819291, "a-person-holding-a-sunglass"),
            (11028523, "a-woman-wearing-sunglasses-and-black-leather-jacket"),
            (23279690, "portrait-of-woman-in-sunglasses"),
        ],
    ),
    "sports": (
        [231, 232, 233, 234, 235, 236],
        [
            (38721839, "black-dumbbell-on-white-background-for-fitness"),
            (4047134, "two-red-2kg-dumbbells-white-surface"),
            (8032962, "close-up-shot-of-a-black-dumbbell"),
            (16080056, "close-up-of-dumbbells"),
            (14100682, "fitness-equipment-on-white-surface"),
            (6516206, "people-workout-using-resistance-bands"),
            (4793245, "assorted-fitness-equipment-with-headset-and-bottle"),
            (7016908, "blue-dumbells-on-gray-surface"),
            (38721834, "iron-dumbbell-on-white-background-for-fitness-use"),
            (16567046, "close-up-of-colorful-dumbbells"),
            (4397830, "purple-dumbbells-and-white-sport-bra-on-table"),
            (17755959, "plastic-sport-water-bottles"),
        ],
    ),
    "watches": (
        [237, 238, 239, 240, 241, 242],
        [
            (190819, "silver-linked-bracelet-silver-and-black-round-chronograph-watch"),
            (16739804, "close-up-of-a-mens-wristwatch"),
            (33524465, "minimalist-black-and-white-analog-wristwatch"),
            (28157826, "elegant-timex-watch-with-leather-strap-display"),
            (39102103, "close-up-of-classic-casio-digital-watch"),
            (8533468, "person-wearing-two-wristwatches"),
            (3656125, "gold-and-black-analog-watch"),
            (6098254, "chronograph-wristwatches-in-close-up-shot"),
            (2222751, "person-wearing-black-round-analog-watch"),
            (5789882, "person-wearing-silver-and-black-watch"),
            (9713527, "a-person-wearing-a-wristwatch"),
            (28383343, "a-man-in-a-white-shirt-and-black-pants-is-adjusting-his-watch"),
        ],
    ),
    "drinks": (
        [243, 244, 245, 246, 247, 248],
        [
            (17313073, "bottles-of-wine"),
            (4254034, "white-and-red-labeled-bottle"),
            (31581609, "craft-beer-bottle-with-red-cap-on-white-background"),
            (5537952, "close-up-shot-of-bottles-of-beer"),
            (32995841, "bottle-of-whiskey-on-gradient-background"),
            (10023483, "close-up-shot-of-a-bottle-of-whiskey"),
            (7253927, "a-dark-brown-liquor-in-a-decanter"),
            (7253705, "close-up-shot-of-a-bottle-of-alcoholic-beverage"),
            (12039663, "glass-bottles-on-white-surface"),
            (11564946, "two-glass-bottles-on-marble-top-table"),
            (18844913, "white-wine-bottles"),
            (3019019, "two-labeled-bottles-of-wine-beside-a-wine-glass"),
        ],
    ),
    "health": (
        [249, 250, 251, 252, 253, 254],
        [
            (31852746, "comprehensive-first-aid-supplies-kit-essentials"),
            (5994625, "covid-essentials-on-red-surface"),
            (6753154, "medication-beside-a-glass-of-water-and-thermometer"),
            (7722956, "blue-and-white-plastic-container"),
            (12585198, "medications-with-orange-background"),
            (9585237, "stethoscope-and-pills"),
            (33394245, "flat-lay-of-veterinary-medical-supplies-on-table"),
            (11211050, "drugs-in-box"),
            (4210557, "syringes-with-medical-drugs-on-yellow-background"),
            (7722562, "an-array-of-syringes-in-thermoforming-packaging"),
            (23655557, "needles-and-syringe-in-hospital"),
            (6129576, "over-the-shoulder-shot-of-medical-practitioner-getting-medicine-from-shelf"),
        ],
    ),
    "appliances": (
        [255, 256, 257, 258, 259, 260],
        [
            (19599328, "pans-and-blender-in-store"),
            (30946798, "vintage-radoco-kitchen-appliances-set-against-blue"),
            (4493668, "slices-of-bread-popping-up-from-toaster"),
            (7534297, "mounted-appliances-in-the-kitchen"),
            (40331524, "vibrant-kitchenware-display-in-cape-town-shop"),
            (5330134, "a-clean-kitchen-with-cupboards-and-kitchenware"),
            (12292645, "kitchen-cooking-utensils-photo"),
            (1599791, "white-wooden-kitchen-cabinet"),
            (8082222, "modern-kitchen-cabinets"),
            (4713253, "photo-of-kitchen-interior"),
            (7546223, "closets-in-kitchen"),
            (2398375, "rectangular-white-island-table-in-kitchen"),
        ],
    ),
    "toys": (
        [261, 262, 263, 264, 265, 266],
        [
            (9372666, "a-robot-figure-doing-a-sit-up"),
            (19946597, "red-figurine-in-a-studio"),
            (7422160, "collectible-figurine-of-fictional-character-in-gray-background"),
            (5257333, "toy-soldier-figures-on-white-background"),
            (6990039, "photo-of-a-red-meeple-near-blue-meeple-pieces"),
            (6990407, "group-of-colorful-human-shape-wooden-blocks"),
            (5257327, "close-up-of-toys-on-a-white-background"),
            (5257358, "grey-military-plastic-models"),
            (5257282, "military-machinery-miniatures-on-white-background"),
            (5257404, "studio-shot-of-grey-model-soldiers-and-a-plastic-plants"),
            (5257325, "military-playset-of-little-plastic-toy-soldiers"),
            (5257293, "toy-soldiers-on-white-sheet"),
        ],
    ),
    "pets": (
        [267, 268, 269, 270, 271, 272],
        [
            (3981763, "flat-lay-shot-of-a-dog-and-animal-accessories"),
            (17742005, "bichon-frise-and-french-bulldog-in-a-t-shirt-and-necklace"),
            (12928245, "animal-feed-in-bowl"),
            (8434744, "a-person-putting-dog-food-on-the-dog-bowl"),
            (13419673, "close-up-of-dog-treats-on-a-container"),
            (8434633, "bowl-of-dog-food"),
            (35651203, "chihuahua-in-a-pet-carrier-against-white-background"),
            (4445456, "a-dog-lying-on-its-bed"),
            (17605538, "boxes-and-vials-of-canine-medicine"),
            (4445609, "a-dog-wearing-face-mask"),
            (1458913, "short-coated-black-and-white-puppy"),
            (18688874, "portrait-of-a-white-and-brown-cat"),
        ],
    ),
    "baby": (
        [273, 274, 275, 276, 277, 278],
        [
            (35727374, "luxury-baby-clothing-set-in-elegant-gift-packaging"),
            (35727373, "minimalist-baby-clothing-on-clothesline"),
            (7566224, "socks-and-pacifiers-on-white-surface"),
            (7566233, "gray-and-white-socks"),
            (30344708, "adorable-baby-on-fluffy-rug-with-diapers"),
            (32950999, "adorable-baby-with-diapers-and-baby-products"),
            (13823041, "brown-baby-shoe"),
            (267278, "pair-of-toddler-s-white-and-gray-shoes"),
            (5982310, "pregnancy-test-kit-beside-a-pacifier"),
            (12188428, "a-baby-surrounded-with-cosmetic-products"),
            (34678851, "adorable-baby-with-toy-ducks-in-studio"),
            (961188, "baby-sitting-on-white-chair"),
        ],
    ),
    "cars": (
        [279, 280, 281, 282, 283, 284],
        [
            (39934707, "close-up-of-car-accessories-on-vehicle-door"),
            (2379998, "red-and-black-hanging-car-decor"),
            (37224323, "modern-car-dashboard-with-touchscreen-display"),
            (35641871, "car-dashboard-with-car-radio-displaying-fm-tuner"),
            (1073741, "vehicle-headrest-monitor"),
            (17893272, "toy-dice-hanging-behind-car-windshield"),
            (38712727, "car-interior-with-rearview-mirror-and-dashboard"),
            (12956058, "car-with-black-interior"),
            (366224, "red-and-black-car-interior"),
            (29566882, "luxury-car-interior-with-red-leather-details"),
            (40448555, "interior-view-of-a-luxury-car-with-illuminated-dashboard"),
            (36841136, "luxury-car-interior-with-modern-dashboard"),
        ],
    ),
    "instruments": (
        [285, 286, 287, 288, 289, 290],
        [
            (15486142, "guitar-on-white-background"),
            (9717768, "brown-acoustic-guitar-with-flowers-on-white-textile"),
            (11168309, "acoustic-guitar-on-a-bed"),
            (18460856, "three-wooden-flutes"),
            (30472403, "kazakh-dombyra-musical-instrument-on-white-background"),
            (30472406, "traditional-kazakh-dombyra-on-white-background"),
            (11480583, "hand-holding-a-kalimba-in-the-snow"),
            (6274911, "a-percussion-instrument-over-the-blanket"),
            (34601478, "golden-trumpet-on-white-background-with-soft-focus"),
            (12266889, "brass-trumpet-on-white-surface"),
            (5901731, "two-beglamas-traditional-instruments"),
            (8904101, "sun-hat-a-guitar-a-colored-pencil-pieces-of-paper"),
        ],
    ),
    "books": (
        [291, 292, 293, 294, 295, 296],
        [
            (32046500, "stack-of-books-with-blue-covers-on-white-background"),
            (32046501, "stack-of-blue-covered-books-on-white-background"),
            (7682364, "brown-hardbound-books-on-white-surface"),
            (4284797, "studio-shot-of-stack-of-books"),
            (3847658, "stack-of-various-books-composed-on-white-surface"),
            (35975197, "stack-of-hardback-books-against-peach-background"),
            (7171398, "stack-of-books-on-white-table"),
            (11911071, "a-stack-of-books"),
            (433333, "pile-of-books"),
            (1383379, "red-and-blue-books"),
            (9291615, "stack-of-books-beside-white-wall"),
            (5706220, "black-framed-eyeglasses-on-stack-of-books"),
        ],
    ),
    "fresh": (
        [297, 298, 299, 300, 301, 302],
        [
            (40324561, "fresh-savoy-cabbage-with-red-pepper"),
            (40581879, "fresh-green-cabbage-with-fennel-and-tomatoes"),
            (3987343, "red-tomatoes-and-green-vegetables-on-white-table"),
            (4963484, "cherry-tomatoes-and-garlic-on-the-plate"),
            (5719961, "close-up-shot-of-fresh-vegetables-on-a-white-surface"),
            (5033529, "variety-of-fresh-vegetables-on-wooden-surface"),
            (12296936, "food-vegetables-cooking-onions"),
            (5758605, "close-up-shot-of-beetroots-on-white-surface"),
            (5377336, "a-person-holding-fresh-carrots"),
            (4203056, "green-cucumber-on-white-surface"),
            (4963581, "avocado-and-broad-beans-on-plate"),
            (4033028, "close-up-of-two-fresh-asparagus-spears-on-light-background"),
        ],
    ),
}


def download(photo_id: int, dest: str) -> int:
    url = (
        f"https://images.pexels.com/photos/{photo_id}/pexels-photo-{photo_id}.jpeg"
        "?auto=compress&cs=tinysrgb&w=900&h=900&fit=crop"
    )
    req = urllib.request.Request(url, headers=IMG_HEADERS)
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = resp.read()
    if len(data) < 4096 or data[:2] != b"\xff\xd8":
        raise ValueError(f"不是有效 JPEG（{len(data)} 字节）")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as fh:
        fh.write(data)
    return len(data)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*", type=int, help="只处理指定商品 id")
    ap.add_argument("--force", action="store_true", help="覆盖已存在的图片")
    ap.add_argument("--manifest-only", action="store_true",
                    help="不联网，按 PLAN 与磁盘上已有的文件重建清单")
    args = ap.parse_args()

    manifest: dict[str, list[dict]] = {}
    failed: list[int] = []
    total = 0

    if args.manifest_only:
        for folder, (product_ids, photos) in PLAN.items():
            for index, pid in enumerate(product_ids):
                picked: list[dict] = []
                for slot in (0, 1):
                    photo_id, slug = photos[index * 2 + slot]
                    local = os.path.join(IMGS, folder, f"{pid}-{slot + 1}.jpg")
                    if not os.path.exists(local):
                        break
                    picked.append(
                        {
                            "file": f"/img/{folder}/{pid}-{slot + 1}.jpg",
                            "pexels_id": photo_id,
                            "slug": slug,
                            "bytes": os.path.getsize(local),
                        }
                    )
                if len(picked) == 2:
                    manifest[str(pid)] = picked
                    total += 2
                else:
                    failed.append(pid)
        os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
        with open(OUT_JSON, "w", encoding="utf-8") as fh:
            json.dump(manifest, fh, ensure_ascii=False, indent=2, sort_keys=True)
        print(f"清单重建：{len(manifest)} 个商品 / {total} 张图；缺图 {failed or '无'}")
        return 0 if not failed else 1

    for folder, (product_ids, photos) in PLAN.items():
        if len(photos) < len(product_ids) * 2:
            print(f"[{folder}] 清单不足：{len(product_ids)} 个商品需要 {len(product_ids) * 2} 张", file=sys.stderr)
            return 2
        for index, pid in enumerate(product_ids):
            if args.only and pid not in args.only:
                continue
            picked: list[dict] = []
            for slot in (0, 1):
                photo_id, slug = photos[index * 2 + slot]
                local = os.path.join(IMGS, folder, f"{pid}-{slot + 1}.jpg")
                try:
                    if args.force and os.path.exists(local):
                        os.remove(local)
                    size = download(photo_id, local)
                except (urllib.error.URLError, TimeoutError, OSError, ValueError) as exc:
                    print(f"[{pid}] 下载 {photo_id} 失败: {exc}", file=sys.stderr)
                    continue
                picked.append(
                    {
                        "file": f"/img/{folder}/{pid}-{slot + 1}.jpg",
                        "pexels_id": photo_id,
                        "slug": slug,
                        "bytes": size,
                    }
                )
                time.sleep(0.3)
            if len(picked) == 2:
                manifest[str(pid)] = picked
                total += len(picked)
                print(f"[{pid}] -> 2 张")
            else:
                failed.append(pid)
                print(f"[{pid}] 只拿到 {len(picked)} 张", file=sys.stderr)

    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
    with open(OUT_JSON, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2, sort_keys=True)

    print(f"\n完成：{len(manifest)} 个商品 / {total} 张图；失败 {len(failed)} 个 {failed or ''}")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
