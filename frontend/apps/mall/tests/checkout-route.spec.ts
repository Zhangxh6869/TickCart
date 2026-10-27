import { describe, expect, it } from 'vitest'
import {
  buildBuyNowQuery,
  buildOrderOriginQuery,
  parseBuyNowSelection,
  parseOrderOrigin,
  CHECKOUT_MODE_BUY_NOW,
} from '@/utils/checkout-route'

/**
 * 结算路由约定。
 *
 * <p>「立即购买」与「购物车结算」是两条链路，靠 query 上的 mode 区分，而这里是唯一的约定点。
 * 写岔了不会报错，只会静默走错分支 —— 所以非法输入必须逐个钉住。</p>
 */
describe('buildBuyNowQuery', () => {
  it('默认 1 件，数值一律转成字符串（URL query 只能放字符串）', () => {
    expect(buildBuyNowQuery(302, 490)).toEqual({
      mode: CHECKOUT_MODE_BUY_NOW,
      productId: '302',
      skuId: '490',
      quantity: '1',
    })
  })

  it('显式数量覆盖默认值', () => {
    expect(buildBuyNowQuery(302, 490, 3).quantity).toBe('3')
  })
})

describe('parseBuyNowSelection', () => {
  it('正常参数解析成数值', () => {
    expect(
      parseBuyNowSelection({ mode: 'buyNow', productId: '302', skuId: '490', quantity: '2' }),
    ).toEqual({ productId: 302, skuId: 490, quantity: 2 })
  })

  it('不是立即购买来源时返回 null（调用方回落到购物车结算）', () => {
    expect(parseBuyNowSelection({ productId: '302' })).toBeNull()
    expect(parseBuyNowSelection({ mode: 'cart', productId: '302' })).toBeNull()
  })

  it('商品 ID 缺失或非法时整条链接判定失效', () => {
    expect(parseBuyNowSelection({ mode: 'buyNow' })).toBeNull()
    expect(parseBuyNowSelection({ mode: 'buyNow', productId: 'abc' })).toBeNull()
    expect(parseBuyNowSelection({ mode: 'buyNow', productId: '0' })).toBeNull()
    expect(parseBuyNowSelection({ mode: 'buyNow', productId: '-1' })).toBeNull()
    expect(parseBuyNowSelection({ mode: 'buyNow', productId: '1.5' })).toBeNull()
  })

  it('规格 ID 非法只退回「无规格」，不判定整条链接失效', () => {
    expect(parseBuyNowSelection({ mode: 'buyNow', productId: '302', skuId: 'abc' })).toEqual({
      productId: 302,
      skuId: 0,
      quantity: 1,
    })
  })

  it('数量非法退回 1 件', () => {
    expect(parseBuyNowSelection({ mode: 'buyNow', productId: '302', quantity: '0' })?.quantity).toBe(1)
    expect(parseBuyNowSelection({ mode: 'buyNow', productId: '302', quantity: '-3' })?.quantity).toBe(1)
    expect(parseBuyNowSelection({ mode: 'buyNow', productId: '302', quantity: 'x' })?.quantity).toBe(1)
  })

  it('省略 skuId / quantity 时用默认值', () => {
    expect(parseBuyNowSelection({ mode: 'buyNow', productId: '302' })).toEqual({
      productId: 302,
      skuId: 0,
      quantity: 1,
    })
  })
})

describe('下单来源标记', () => {
  it('立即购买与购物车分别带不同标记', () => {
    expect(buildOrderOriginQuery(true)).toEqual({ from: 'buyNow' })
    expect(buildOrderOriginQuery(false)).toEqual({ from: 'cart' })
  })

  it('只认两个合法值，其他一律视为「不是下单进来的」', () => {
    expect(parseOrderOrigin({ from: 'cart' })).toBe('cart')
    expect(parseOrderOrigin({ from: 'buyNow' })).toBe('buyNow')
    expect(parseOrderOrigin({ from: 'detail' })).toBeNull()
    expect(parseOrderOrigin({})).toBeNull()
  })
})
