import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 购物车 store。
 *
 * <p>以服务端为准：任何增删改之后都重新拉整表，所以这里重点不是「本地怎么算」，
 * 而是 <b>角标文案</b> 与 <b>结算取哪些项</b> 这两个真正会被消费的口径。</p>
 */

// 必须在 import store 之前 mock：store 在模块加载时就 import 了 api 模块
vi.mock('@/api/cart', () => ({
  fetchCart: vi.fn(),
  fetchCartCount: vi.fn(),
  addToCart: vi.fn(),
  updateCartQuantity: vi.fn(),
  updateCartChecked: vi.fn(),
  updateCartSku: vi.fn(),
  checkAllCart: vi.fn(),
  removeCartItem: vi.fn(),
  removeCheckedCartItems: vi.fn(),
}))

import * as cartApi from '@/api/cart'
import { useCartStore } from '@/stores/cart'
import type { CartItem, CartSummary } from '@tickcart/shared-types'

function item(overrides: Partial<CartItem> = {}): CartItem {
  return {
    id: 1,
    productId: 302,
    skuId: 490,
    quantity: 1,
    checked: 1,
    productName: '牛油果',
    productImage: null,
    specValue: null,
    price: 39.9,
    stock: 10,
    subtotal: 39.9,
    valid: true,
    ...overrides,
  }
}

function summary(overrides: Partial<CartSummary> = {}): CartSummary {
  return {
    items: [],
    checkedCount: 0,
    checkedAmount: 0,
    totalCount: 0,
    ...overrides,
  }
}

const mocked = vi.mocked(cartApi)

describe('useCartStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('初始角标为空串（空串时 van-tabbar 不显示角标）', () => {
    const store = useCartStore()

    expect(store.badge).toBe('')
    expect(store.totalCount).toBe(0)
    expect(store.items).toEqual([])
  })

  it('load 之后透传服务端汇总，角标显示总数', async () => {
    mocked.fetchCart.mockResolvedValue(summary({ items: [item()], totalCount: 3 }))
    const store = useCartStore()

    await store.load()

    expect(store.totalCount).toBe(3)
    expect(store.badge).toBe('3')
    expect(store.items).toHaveLength(1)
  })

  it('checkedItems 只含「已勾选 且 有效」的项（下架商品不能带进结算）', async () => {
    mocked.fetchCart.mockResolvedValue(
      summary({
        items: [
          item({ id: 1, checked: 1, valid: true }),
          item({ id: 2, checked: 0, valid: true }),
          item({ id: 3, checked: 1, valid: false }),
        ],
      }),
    )
    const store = useCartStore()

    await store.load()

    expect(store.checkedItems.map((i) => i.id)).toEqual([1])
  })

  it('静默刷新不置 loading（被 KeepAlive 缓存的页面切回来时不闪）', async () => {
    mocked.fetchCart.mockResolvedValue(summary())
    const store = useCartStore()

    const pending = store.load(true)

    expect(store.loading).toBe(false)
    await pending
    expect(store.loading).toBe(false)
  })

  it('非静默刷新失败时 loading 也要复位（否则页面永远转圈）', async () => {
    mocked.fetchCart.mockRejectedValue(new Error('boom'))
    const store = useCartStore()

    await expect(store.load()).rejects.toThrow('boom')

    expect(store.loading).toBe(false)
  })

  it('refreshCount 只改角标数字，不覆盖列表', async () => {
    mocked.fetchCart.mockResolvedValue(summary({ items: [item()], totalCount: 1 }))
    mocked.fetchCartCount.mockResolvedValue(5)
    const store = useCartStore()
    await store.load()

    await store.refreshCount()

    expect(store.totalCount).toBe(5)
    expect(store.items).toHaveLength(1)
  })

  it('refreshCount 失败时静默：不抛异常、保留原角标（未登录时不该打断浏览）', async () => {
    mocked.fetchCart.mockResolvedValue(summary({ totalCount: 2 }))
    mocked.fetchCartCount.mockRejectedValue(new Error('401'))
    const store = useCartStore()
    await store.load()

    await expect(store.refreshCount()).resolves.toBeUndefined()

    expect(store.totalCount).toBe(2)
  })

  it('add 之后刷新角标（不整表重拉）', async () => {
    mocked.addToCart.mockResolvedValue(undefined)
    mocked.fetchCartCount.mockResolvedValue(4)
    const store = useCartStore()

    await store.add(302, 1, 490)

    expect(mocked.addToCart).toHaveBeenCalledWith({ productId: 302, skuId: 490, quantity: 1 })
    expect(store.totalCount).toBe(4)
  })

  it('改数量 / 切规格都整表重拉（后端可能合并同规格项，不能只改本地）', async () => {
    mocked.updateCartQuantity.mockResolvedValue(undefined)
    mocked.updateCartSku.mockResolvedValue(undefined)
    mocked.fetchCart.mockResolvedValue(summary())
    const store = useCartStore()

    await store.updateQuantity(1, 3)
    await store.changeSku(1, 999)

    expect(mocked.fetchCart).toHaveBeenCalledTimes(2)
  })

  it('reset 清空（退出登录时调用）', async () => {
    mocked.fetchCart.mockResolvedValue(summary({ items: [item()], totalCount: 2 }))
    const store = useCartStore()
    await store.load()

    store.reset()

    expect(store.items).toEqual([])
    expect(store.badge).toBe('')
  })
})
