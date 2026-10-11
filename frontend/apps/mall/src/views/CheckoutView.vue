<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { CheckoutItem, UserAddress } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchAddresses } from '@/api/address'
import { createBuyNowOrder, createOrder } from '@/api/order'
import { fetchProductDetail } from '@/api/product'
import { useCartStore } from '@/stores/cart'
import { parseBuyNowSelection } from '@/utils/checkout-route'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const addresses = ref<UserAddress[]>([])
const selectedAddressId = ref<number | null>(null)
const remark = ref('')
const submitting = ref(false)
const addressPopupVisible = ref(false)
/** 点「管理收货地址」后，等弹层关闭动画结束再跳转（原因见 goAddressManage） */
const pendingAddressNav = ref(false)

/** 立即购买来源的待结算商品；购物车结算时恒为 null */
const buyNowItem = ref<CheckoutItem | null>(null)

/** 本次进入是否为「立即购买」（商品详情页直达，不经过购物车） */
const buyNowSelection = computed(() => parseBuyNowSelection(route.query))

const selectedAddress = computed(
  () => addresses.value.find((item) => item.id === selectedAddressId.value) ?? null
)

/**
 * 待结算商品。
 * 两种来源（购物车已勾选项 / 立即购买的单件商品）在这里归一成同一结构，
 * 下面的渲染与金额计算只认它，不再各算一套。
 */
const items = computed<CheckoutItem[]>(() => {
  if (buyNowSelection.value) return buyNowItem.value ? [buyNowItem.value] : []
  return cartStore.checkedItems
})
const totalAmount = computed(() => items.value.reduce((sum, item) => sum + item.subtotal, 0))
const totalQuantity = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

/**
 * 本页被 KeepAlive 缓存（见 App.vue 的 CACHED_VIEWS），从收货地址页返回时**不会重新挂载**，
 * 所以数据同步不能只写在 onMounted 里。
 * 首次挂载走 onMounted；之后每次重新进入走 onActivated。
 * firstActivated 用来跳过紧跟 onMounted 的那一次 activated（Vue 首次挂载会连着触发两个钩子），
 * 不跳过会白白多请求一轮。
 */
let firstActivated = true
onMounted(() => void prepare())
onActivated(() => {
  if (firstActivated) {
    firstActivated = false
    return
  }
  // 重新进入时保留用户已经选中的地址，别被「默认地址」覆盖回初始值
  void prepare(true)
})

/**
 * 同步结算页需要的全部数据。
 * @param keepSelection 是否保留当前已选地址（被缓存后重新进入时为 true）
 */
async function prepare(keepSelection = false) {
  const buyNow = buyNowSelection.value
  if (buyNow) {
    // 商品已失效时 loadBuyNowItem 内部已提示并跳走，这里直接结束
    if (!(await loadBuyNowItem(buyNow.productId, buyNow.skuId, buyNow.quantity))) return
  } else {
    buyNowItem.value = null
    await cartStore.load()
    // 没有可结算的商品时退回购物车，避免停留在空结算页
    if (cartStore.checkedItems.length === 0) {
      showToast('请先选择要结算的商品')
      router.replace({ name: 'cart' })
      return
    }
  }
  await loadAddresses(keepSelection ? selectedAddressId.value : null)
}

/**
 * 拉取立即购买的商品，组装成结算项。
 *
 * <p>这里重新请求一次详情，而不是把商品详情页的数据带过来：价格与库存以服务端最新为准，
 * 避免用户在详情页停留很久之后按过期价格下单。</p>
 *
 * @returns 组装成功返回 true；商品/规格已失效时提示并跳走，返回 false
 */
async function loadBuyNowItem(productId: number, skuId: number, quantity: number): Promise<boolean> {
  try {
    const detail = await fetchProductDetail(productId)
    const sku = skuId > 0 ? detail.skus.find((item) => item.id === skuId) : undefined
    if (skuId > 0 && !sku) {
      showToast('商品规格已失效，请重新选择')
      router.replace({ name: 'product-detail', params: { id: productId } })
      return false
    }
    const price = sku?.price ?? detail.price
    buyNowItem.value = {
      productId: detail.id,
      skuId: sku?.id ?? 0,
      productName: detail.name,
      productImage: sku?.skuImage || detail.mainImage || '',
      specValue: sku?.specValue ?? null,
      price,
      quantity,
      subtotal: price * quantity,
    }
    return true
  } catch {
    // 错误提示由请求拦截器统一处理；商品已下架等情况不该把用户留在结算页
    router.replace({ name: 'home' })
    return false
  }
}

/**
 * 拉取地址列表并决定选中项。
 * @param preferId 希望保留的地址 ID（可能已在别处被删除）
 */
async function loadAddresses(preferId: number | null = null) {
  try {
    addresses.value = await fetchAddresses()
    // 优先保留用户已选的地址；它已被删除时回落到默认地址，再否则取第一条
    const kept = preferId === null ? undefined : addresses.value.find((item) => item.id === preferId)
    const preferred =
      kept ?? addresses.value.find((item) => item.isDefault === 1) ?? addresses.value[0]
    selectedAddressId.value = preferred ? preferred.id : null
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

function fullAddress(address: UserAddress) {
  return `${address.province}${address.city}${address.district}${address.detail}`
}

function openAddressPopup() {
  pendingAddressNav.value = false
  addressPopupVisible.value = true
}

function chooseAddress(address: UserAddress) {
  selectedAddressId.value = address.id
  addressPopupVisible.value = false
}

/**
 * 「管理收货地址」不能在这里直接 router.push。
 *
 * 弹层关闭时，它那层全屏遮罩（`.van-overlay`，z-index 2001、黑色 0.7）要淡出约 300ms，
 * 而地址页从底部升起来要 450ms —— 前 300ms 它还盖不住上半屏。
 * 两件事同时发生的话，遮罩一淡下去，上半屏就露出确认订单页（它 opacity 恒为 1，本来就不淡出），
 * 观感就是「上拉之后闪了一下确认订单界面」。
 *
 * 改成等弹层完全关闭（@closed）再跳转，两个动作串行，不再重叠。
 * 代价是跳转比点击晚约 300ms，但正好接在弹层收起的动作后面，不会觉得卡。
 */
function goAddressManage() {
  pendingAddressNav.value = true
  addressPopupVisible.value = false
}

function onAddressPopupClosed() {
  if (!pendingAddressNav.value) return
  pendingAddressNav.value = false
  router.push({ name: 'address' })
}

async function onSubmit() {
  if (!selectedAddress.value) {
    showToast('请先选择收货地址')
    return
  }
  const buyNow = buyNowSelection.value
  submitting.value = true
  try {
    const addressId = selectedAddress.value.id
    const trimmedRemark = remark.value.trim() || undefined
    // 立即购买把商品随请求带上；购物车结算仍由服务端读购物车已勾选项
    const result =
      buyNow && buyNowItem.value
        ? await createBuyNowOrder({
            addressId,
            productId: buyNowItem.value.productId,
            skuId: buyNowItem.value.skuId,
            quantity: buyNowItem.value.quantity,
            remark: trimmedRemark,
          })
        : await createOrder({ addressId, remark: trimmedRemark })
    // 下单成功只作轻提示：默认 2s 偏长，会把刚跳过去的订单详情挡在上面
    showToast({ message: '下单成功', duration: 1200 })
    // 立即购买不经过购物车，购物车角标本来就没变，不需要刷新
    if (!buyNow) await cartStore.refreshCount()
    // 本页被 KeepAlive 缓存，不清空的话下次进结算页还会带着上一单的备注
    remark.value = ''
    router.replace({ name: 'order-detail', params: { orderNo: result.orderNo } })
  } catch {
    // 库存不足等错误已由拦截器提示；重新拉一次以反映最新库存
    if (buyNow) await prepare(true)
    else await cartStore.load()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="checkout">
    <van-nav-bar title="确认订单" left-arrow @click-left="router.back()" />

    <div class="checkout__body">
      <div class="card" @click="openAddressPopup">
        <template v-if="selectedAddress">
          <div class="card__row">
            <span class="card__name">{{ selectedAddress.receiver }}</span>
            <span class="card__phone">{{ selectedAddress.phone }}</span>
          </div>
          <div class="card__addr">{{ fullAddress(selectedAddress) }}</div>
        </template>
        <div v-else class="card__empty">请选择收货地址</div>
        <van-icon name="arrow" class="card__arrow" />
      </div>

      <div class="card card--goods">
        <div v-for="item in items" :key="`${item.productId}-${item.skuId}`" class="goods">
          <van-image class="goods__image" width="70" height="70" fit="cover" :src="item.productImage || ''" />
          <div class="goods__body">
            <div class="goods__name">{{ item.productName }}</div>
            <div v-if="item.specValue" class="goods__spec">{{ item.specValue }}</div>
            <div class="goods__foot">
              <span class="goods__price">¥{{ formatPrice(item.price) }}</span>
              <span class="goods__qty">×{{ item.quantity }}</span>
            </div>
          </div>
        </div>

        <van-field
          v-model="remark"
          label="备注"
          placeholder="选填，如对配送的要求"
          maxlength="255"
          class="checkout__remark"
        />
      </div>

      <div class="card">
        <!-- 配送方式：当前只有一种（快递配送、免运费），按展示型处理，不落库、不加订单字段 -->
        <div class="summary">
          <span>配送方式</span>
          <span>快递配送 · 免运费</span>
        </div>
        <div class="summary">
          <span>商品件数</span>
          <span>{{ totalQuantity }} 件</span>
        </div>
        <div class="summary">
          <span>商品金额</span>
          <span>¥{{ formatPrice(totalAmount) }}</span>
        </div>
        <div class="summary summary--total">
          <span>实付款</span>
          <span class="summary__amount">¥{{ formatPrice(totalAmount) }}</span>
        </div>
      </div>
    </div>

    <van-submit-bar
      class="checkout__bar"
      :price="Math.round(totalAmount * 100)"
      button-text="提交订单"
      button-color="rgb(107,163,200)"
      :loading="submitting"
      @submit="onSubmit"
    />

    <van-popup
      v-model:show="addressPopupVisible"
      position="bottom"
      round
      :style="{ maxHeight: '70%' }"
      @closed="onAddressPopupClosed"
    >
      <div class="addr-popup">
        <div class="addr-popup__title">选择收货地址</div>
        <div
          v-for="item in addresses"
          :key="item.id"
          class="addr-popup__item"
          :class="{ 'addr-popup__item--active': item.id === selectedAddressId }"
          @click="chooseAddress(item)"
        >
          <div class="card__row">
            <span class="card__name">{{ item.receiver }}</span>
            <span class="card__phone">{{ item.phone }}</span>
            <van-tag v-if="item.isDefault === 1" type="danger" plain>默认</van-tag>
          </div>
          <div class="card__addr">{{ fullAddress(item) }}</div>
        </div>
        <van-empty v-if="addresses.length === 0" description="还没有收货地址" />
        <div class="addr-popup__actions">
          <van-button block round plain type="primary" @click="goAddressManage">管理收货地址</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.checkout {
  min-height: 100vh;
  padding-bottom: 50px;
  background: var(--van-background-2, #f7f8fa);
}
.checkout__body {
  padding: 10px 12px 0;
}
.card {
  position: relative;
  padding: 14px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: #fff;
}
.card--goods {
  padding: 14px 0 0;
}
.card__row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card__name {
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}
.card__phone {
  font-size: 13px;
  color: #969799;
}
.card__addr {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: #646566;
}
.card__empty {
  font-size: 14px;
  color: #969799;
}
.card__arrow {
  position: absolute;
  top: 50%;
  right: 14px;
  transform: translateY(-50%);
  color: #c8c9cc;
}
.goods {
  display: flex;
  gap: 10px;
  padding: 0 14px 12px;
}
.goods__image {
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  background: #f7f8fa;
}
.goods__body {
  flex: 1;
  min-width: 0;
}
.goods__name {
  font-size: 14px;
  line-height: 1.4;
  color: #323233;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.goods__spec {
  margin-top: 4px;
  font-size: 12px;
  color: #969799;
}
.goods__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.goods__price {
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}
.goods__qty {
  font-size: 13px;
  color: #969799;
}
.checkout__remark {
  border-top: 1px solid #f2f3f5;
}
.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #646566;
  padding: 4px 0;
}
.summary--total {
  margin-top: 6px;
  padding-top: 10px;
  border-top: 1px solid #f2f3f5;
  font-size: 14px;
  color: #323233;
}
.summary__amount {
  font-size: 17px;
  font-weight: 500;
  color: #323233;
}
.checkout__bar {
  bottom: 0;
}
.addr-popup {
  padding: 16px 12px calc(16px + env(safe-area-inset-bottom));
}
.addr-popup__title {
  margin-bottom: 12px;
  text-align: center;
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}
.addr-popup__item {
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #f7f8fa;
}
.addr-popup__item--active {
  border-color: rgb(107, 163, 200);
  background: rgba(107, 163, 200, 0.08);
}
.addr-popup__actions {
  margin-top: 12px;
}
</style>
