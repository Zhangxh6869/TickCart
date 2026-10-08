import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { CartSummary } from '@tickcart/shared-types'
import * as cartApi from '@/api/cart'

const EMPTY_SUMMARY: CartSummary = {
  items: [],
  checkedCount: 0,
  checkedAmount: 0,
  totalCount: 0,
}

/**
 * 购物车状态。
 *
 * <p>以服务端为准：所有增删改后重新拉取列表，避免本地与库存/价格不一致。
 * 角标只依赖 {@code totalCount}，登录态变化时由布局层触发刷新。</p>
 */
export const useCartStore = defineStore('cart', () => {
  const summary = ref<CartSummary>({ ...EMPTY_SUMMARY })
  const loading = ref(false)

  const items = computed(() => summary.value.items)
  const totalCount = computed(() => summary.value.totalCount)
  const checkedCount = computed(() => summary.value.checkedCount)
  const checkedAmount = computed(() => summary.value.checkedAmount)
  /** 勾选且有效的项，结算页消费 */
  const checkedItems = computed(() => summary.value.items.filter((item) => item.valid && item.checked === 1))
  /** tabbar 角标文案，无商品时为空字符串（不显示角标） */
  const badge = computed(() => (summary.value.totalCount > 0 ? String(summary.value.totalCount) : ''))

  /**
   * 拉取购物车列表。
   *
   * @param silent 静默刷新：不置 loading 态。用于被 KeepAlive 缓存的页面切回来时
   *               在后台更新数据——已有内容不会被 loading 占位顶掉，也就不会闪一下。
   *               失败时保留原数据，交由请求拦截器提示。
   */
  async function load(silent = false) {
    if (!silent) loading.value = true
    try {
      summary.value = await cartApi.fetchCart()
    } finally {
      if (!silent) loading.value = false
    }
  }

  /** 只刷新角标数量，不覆盖列表（列表页之外的地方用）。 */
  async function refreshCount() {
    try {
      const count = await cartApi.fetchCartCount()
      summary.value = { ...summary.value, totalCount: count }
    } catch {
      // 未登录或网络异常时静默，不打断浏览
    }
  }

  async function add(productId: number, quantity = 1, skuId = 0) {
    await cartApi.addToCart({ productId, skuId, quantity })
    await refreshCount()
  }

  async function updateQuantity(id: number, quantity: number) {
    await cartApi.updateCartQuantity(id, quantity)
    await load()
  }

  async function toggleChecked(id: number, checked: boolean) {
    await cartApi.updateCartChecked(id, checked ? 1 : 0)
    await load()
  }

  /**
   * 切换某项的规格。后端可能把当前项合并进同规格的另一条（数量相加），
   * 因此必须整表重新拉取，不能只在本地改 skuId。
   */
  async function changeSku(id: number, skuId: number) {
    await cartApi.updateCartSku(id, skuId)
    await load()
  }

  async function checkAll(checked: boolean) {
    await cartApi.checkAllCart(checked ? 1 : 0)
    await load()
  }

  async function remove(id: number) {
    await cartApi.removeCartItem(id)
    await load()
  }

  function reset() {
    summary.value = { ...EMPTY_SUMMARY }
  }

  return {
    summary,
    items,
    loading,
    totalCount,
    checkedCount,
    checkedAmount,
    checkedItems,
    badge,
    load,
    refreshCount,
    add,
    updateQuantity,
    toggleChecked,
    changeSku,
    checkAll,
    remove,
    reset,
  }
})
