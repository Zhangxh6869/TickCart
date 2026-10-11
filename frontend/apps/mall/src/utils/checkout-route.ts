import type { LocationQuery } from 'vue-router'

/**
 * 确认订单页的来源标记。
 *
 * <p>商品详情页「立即购买」会带上它直达结算页；购物车结算不传任何 query。
 * 结算页据此决定待结算商品从哪来，是两条链路唯一的约定，所以集中在这里维护，
 * 避免两端各写一遍字符串而悄悄写岔。</p>
 */
export const CHECKOUT_MODE_BUY_NOW = 'buyNow'

/** 立即购买要在结算页展示的商品。 */
export interface BuyNowSelection {
  productId: number
  skuId: number
  quantity: number
}

/** 由商品详情页构造结算页的 query（购物车结算不传 query）。 */
export function buildBuyNowQuery(productId: number, skuId: number, quantity = 1) {
  return {
    mode: CHECKOUT_MODE_BUY_NOW,
    productId: String(productId),
    skuId: String(skuId),
    quantity: String(quantity),
  }
}

/**
 * 解析结算页 query 里的立即购买参数。
 *
 * <p>不是立即购买来源、或商品 ID 缺失/非法时返回 null，调用方据此回落到购物车结算。
 * 数量与规格 ID 非法时退回默认值（1 件 / 无规格），不因此判定整条链接失效。</p>
 */
export function parseBuyNowSelection(query: LocationQuery): BuyNowSelection | null {
  if (query.mode !== CHECKOUT_MODE_BUY_NOW) return null

  const productId = Number(query.productId)
  if (!Number.isInteger(productId) || productId <= 0) return null

  const skuId = Number(query.skuId ?? 0)
  const quantity = Number(query.quantity ?? 1)
  return {
    productId,
    skuId: Number.isInteger(skuId) && skuId > 0 ? skuId : 0,
    quantity: Number.isInteger(quantity) && quantity > 0 ? quantity : 1,
  }
}
