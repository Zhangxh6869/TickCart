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

/**
 * 下单来源，写在订单详情页的 URL 上（`?from=…`）。
 *
 * <p>结算页提交成功后把它带给订单详情页；订单详情页的「返回」据此直接回到来源页，
 * 而不是回上一页 —— 下单流程已经结束，退回商品详情或结算页都没有意义。</p>
 *
 * <p>为什么放在 URL 而不是内存里：一是刷新订单详情后返回目标不能丢；
 * 二是「从订单列表点进来的详情」必须没有来源标记，不能被上一次下单的来源串味。</p>
 */
export const ORDER_ORIGIN_CART = 'cart'
export const ORDER_ORIGIN_BUY_NOW = 'buyNow'

export type OrderOrigin = typeof ORDER_ORIGIN_CART | typeof ORDER_ORIGIN_BUY_NOW

/** 结算页提交成功后，给订单详情页带的 query。 */
export function buildOrderOriginQuery(buyNow: boolean): { from: OrderOrigin } {
  return { from: buyNow ? ORDER_ORIGIN_BUY_NOW : ORDER_ORIGIN_CART }
}

/** 解析订单详情页的来源标记；不是从结算页下单进来的返回 null。 */
export function parseOrderOrigin(query: LocationQuery): OrderOrigin | null {
  const from = query.from
  return from === ORDER_ORIGIN_CART || from === ORDER_ORIGIN_BUY_NOW ? from : null
}
