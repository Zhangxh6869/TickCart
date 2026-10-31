/**
 * 详情页图集的组装规则。
 *
 * <p>单独抽成纯函数是为了能测：图集的**顺序**决定了两件事 —— 缩略图条的顺序，
 * 以及「点某个款式该跳到第几张」。顺序一旦随选择漂移，用户点完款式会看到缩略图
 * 整体重排（甚至同一件商品两次点同一个款式跳到的位置不一样）。</p>
 *
 * <p><b>规则</b>：先按图集表顺序铺商品自己的图（图集为空时退回主图），
 * 再把各规格自己的图**按规格顺序**追加到末尾，并去重。
 * 这样列表对同一件商品是**恒定**的，切款式只改「当前是第几张」，不改列表本身。</p>
 *
 * <p>★ 早先的做法是「把当前选中规格的图插到最前面」，看似更贴合「图跟着规格走」，
 * 但每次切款式首图都会变 → 缩略图整体错位，且下标随之变化，很容易出现
 * 「高亮在第 2 张、主图却停在第 1 张」这种不同步。</p>
 */

/** 过滤掉 null / 空串，保留原顺序 */
function compact(urls: readonly (string | null | undefined)[]): string[] {
  const result: string[] = []
  for (const url of urls) {
    if (url) result.push(url)
  }
  return result
}

/**
 * 组装图集列表。
 *
 * @param images    图集表里的图片地址（已按 sort 排序）
 * @param mainImage 商品主图，图集为空时兜底
 * @param skuImages 各规格自己的图片（sku_image），按 SKU 顺序
 */
export function buildGallery(
  images: readonly (string | null | undefined)[],
  mainImage: string | null | undefined,
  skuImages: readonly (string | null | undefined)[] = [],
): string[] {
  const base = compact(images)
  const gallery = base.length ? [...base] : compact([mainImage])
  for (const url of compact(skuImages)) {
    if (!gallery.includes(url)) gallery.push(url)
  }
  return gallery
}
