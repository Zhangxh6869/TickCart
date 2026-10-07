<script setup lang="ts">
import { computed, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import type { CartItem } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const cartStore = useCartStore()

const items = computed(() => cartStore.items)
/** 有效项（失效项不参与全选判断） */
const validItems = computed(() => items.value.filter((item) => item.valid))
const allChecked = computed(
  () => validItems.value.length > 0 && validItems.value.every((item) => item.checked === 1)
)

/** van-submit-bar 的价格单位是分，需四舍五入避免浮点误差 */
const totalPriceInCents = computed(() => Math.round(cartStore.checkedAmount * 100))

/**
 * 本页被 KeepAlive 缓存（见 App.vue），onMounted 一辈子只跑一次，所以改用 onActivated：
 *   首次激活 → 带 loading 正常加载；
 *   之后切回 → 静默刷新，既能拿到别处加购/下单后的最新数据，又不会闪一下 loading。
 * onActivated 在首次挂载后也会触发，因此不需要再写 onMounted。
 */
let activatedBefore = false
onActivated(() => {
  cartStore.load(activatedBefore)
  activatedBefore = true
})

async function onToggleItem(item: CartItem, value: boolean) {
  try {
    await cartStore.toggleChecked(item.id, value)
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

async function onToggleAll() {
  try {
    await cartStore.checkAll(!allChecked.value)
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

async function onQuantityChange(item: CartItem, quantity: number | string) {
  const next = Number(quantity)
  if (!Number.isFinite(next) || next === item.quantity) return
  try {
    await cartStore.updateQuantity(item.id, next)
  } catch {
    // 库存不足等错误已由拦截器提示，重新拉取以恢复真实数量
    await cartStore.load()
  }
}

async function onRemove(item: CartItem) {
  try {
    await showConfirmDialog({ title: '移除商品', message: `确认将「${item.productName}」移出购物车？` })
  } catch {
    return // 用户取消
  }
  try {
    await cartStore.remove(item.id)
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

function onCheckout() {
  if (cartStore.checkedItems.length === 0) {
    showToast('请先选择要结算的商品')
    return
  }
  router.push({ name: 'checkout' })
}

function goHome() {
  router.push({ name: 'home' })
}
</script>

<template>
  <div class="cart">
    <van-nav-bar title="购物车" />

    <van-empty v-if="!cartStore.loading && items.length === 0" description="购物车还是空的">
      <van-button round type="primary" class="cart__empty-btn" @click="goHome">去逛逛</van-button>
    </van-empty>

    <div v-else class="cart__list">
      <div
        v-for="item in items"
        :key="item.id"
        class="cart-item"
        :class="{ 'cart-item--invalid': !item.valid }"
      >
        <van-checkbox
          class="cart-item__check"
          :model-value="item.checked === 1"
          :disabled="!item.valid"
          @update:model-value="(value: boolean) => onToggleItem(item, value)"
        />

        <van-image class="cart-item__image" width="76" height="76" fit="cover" :src="item.productImage || ''" />

        <div class="cart-item__body">
          <div class="cart-item__name">{{ item.productName }}</div>
          <div v-if="item.specValue" class="cart-item__spec">{{ item.specValue }}</div>
          <div v-if="!item.valid" class="cart-item__reason">{{ item.invalidReason }}</div>

          <div class="cart-item__foot">
            <span class="cart-item__price">¥{{ formatPrice(item.price) }}</span>
            <van-stepper
              v-if="item.valid"
              :model-value="item.quantity"
              :min="1"
              :max="item.stock"
              integer
              button-size="24"
              @change="(value: number | string) => onQuantityChange(item, value)"
            />
            <span v-else class="cart-item__quantity">×{{ item.quantity }}</span>
          </div>
        </div>

        <van-icon name="delete-o" class="cart-item__delete" @click="onRemove(item)" />
      </div>
    </div>

    <van-submit-bar
      v-if="items.length > 0"
      class="cart__bar"
      :price="totalPriceInCents"
      :button-text="`结算(${cartStore.checkedCount})`"
      button-color="rgb(107,163,200)"
      @submit="onCheckout"
    >
      <van-checkbox :model-value="allChecked" class="cart__all" @click="onToggleAll">全选</van-checkbox>
    </van-submit-bar>
  </div>
</template>

<style scoped>
.cart {
  min-height: 100vh;
  padding-bottom: 50px;
  background: var(--van-background-2, #f7f8fa);
}
.cart__empty-btn {
  width: 120px;
}
.cart__list {
  padding: 10px 12px 0;
}
.cart-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: #fff;
}
.cart-item--invalid {
  opacity: 0.6;
}
.cart-item__check {
  margin-top: 26px;
}
.cart-item__image {
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  background: #f7f8fa;
}
.cart-item__body {
  flex: 1;
  min-width: 0;
}
.cart-item__name {
  font-size: 14px;
  line-height: 1.4;
  color: #323233;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.cart-item__spec {
  display: inline-block;
  margin-top: 4px;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: #969799;
  background: #f7f8fa;
}
.cart-item__reason {
  margin-top: 4px;
  font-size: 12px;
  color: #ee0a24;
}
.cart-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.cart-item__price {
  font-size: 16px;
  font-weight: 500;
  color: #323233;
}
.cart-item__quantity {
  font-size: 13px;
  color: #969799;
}
.cart-item__delete {
  padding: 4px;
  font-size: 18px;
  color: #c8c9cc;
}
.cart__bar {
  bottom: 50px;
}
.cart__all {
  margin-right: 12px;
}
</style>
