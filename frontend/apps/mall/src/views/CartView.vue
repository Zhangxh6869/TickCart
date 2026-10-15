<script setup lang="ts">
import { computed, onActivated, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import type { SwipeCellInstance } from 'vant'
import type { CartItem, ProductSku } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchProductDetail } from '@/api/product'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

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
 *
 * 未登录时**不发请求**（购物车接口要鉴权，必然 401），就地展示登录引导。
 * 同时把 activatedBefore 复位，这样从登录页回来后的第一次加载会带 loading 态，
 * 不会先闪一下「购物车还是空的」再出现商品。
 */
let activatedBefore = false
onActivated(() => {
  if (!userStore.isLoggedIn) {
    activatedBefore = false
    return
  }
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

/**
 * 每个购物车条目对应一个 SwipeCell 实例，用于在删除被取消/失败时收起滑出的删除按钮。
 * 列表项被移除后 Vue 会把 ref 回调置空，这里同步清理，避免 Map 持有已销毁实例。
 */
const swipeCells = new Map<number, SwipeCellInstance>()

function setSwipeRef(id: number, el: unknown) {
  if (el) swipeCells.set(id, el as SwipeCellInstance)
  else swipeCells.delete(id)
}

/** position 仅用于 close 事件的载荷，传 'right' 表示收起右侧（删除）展开态 */
function closeSwipe(id: number) {
  swipeCells.get(id)?.close('right')
}

async function onRemove(item: CartItem) {
  try {
    await showConfirmDialog({ title: '移除商品', message: `确认将「${item.productName}」移出购物车？` })
  } catch {
    closeSwipe(item.id) // 用户取消，收起滑出的删除按钮，避免停在展开态
    return
  }
  try {
    await cartStore.remove(item.id)
  } catch {
    // 错误提示由请求拦截器统一处理；同样收起展开态
    closeSwipe(item.id)
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

/** 点卡片进商品详情页（勾选框/步进器/款式行都 stop 了冒泡，不会误触发） */
function goDetail(item: CartItem) {
  router.push({ name: 'product-detail', params: { id: item.productId } })
}

/* ── 款式切换面板 ── */

/** 面板当前对应的购物车项 */
const skuPopupItem = ref<CartItem | null>(null)
const skuPopupVisible = ref(false)
/** 该商品的全部款式，打开面板时按需拉取 */
const skuOptions = ref<ProductSku[]>([])
const skuLoading = ref(false)
/** 提交中：防止连点造成并发切换 */
const skuSubmitting = ref(false)

/** 按规格名分组展示（如「口味」「颜色分类」）；规格名为空时归入「款式」组 */
const skuGroups = computed(() => {
  const groups = new Map<string, ProductSku[]>()
  for (const sku of skuOptions.value) {
    const title = sku.specName?.trim() || '款式'
    const list = groups.get(title)
    if (list) list.push(sku)
    else groups.set(title, [sku])
  }
  return Array.from(groups, ([title, skus]) => ({ title, skus }))
})

/** 当前项在款式列表里对应的 SKU，用来取规格名与实时库存 */
const selectedSkuOption = computed(
  () => skuOptions.value.find((sku) => sku.id === skuPopupItem.value?.skuId) ?? null
)

/** 摘要行文案「已选 尺码 M」；规格名缺失时退化成「已选 M」 */
const selectedSkuLabel = computed(() => {
  const value = selectedSkuOption.value?.specValue ?? skuPopupItem.value?.specValue ?? ''
  const name = selectedSkuOption.value?.specName?.trim()
  return name ? `已选 ${name} ${value}` : `已选 ${value}`
})

/** 摘要行库存文案 */
const selectedSkuStock = computed(() => {
  const stock = selectedSkuOption.value?.stock ?? skuPopupItem.value?.stock ?? 0
  return stock > 0 ? `库存 ${stock} 件` : '该款式已售罄'
})

/**
 * 打开款式面板。
 *
 * <p>购物车列表只带当前项自己的 specValue，没有该商品的全部款式，
 * 所以这里按需拉一次商品详情（含 SKU 列表）。请求期间用户可能已经点了别的商品，
 * 用 item 引用比对丢弃过期结果，避免把 A 的款式渲染到 B 的面板上。</p>
 */
async function openSkuPopup(item: CartItem) {
  skuPopupItem.value = item
  skuOptions.value = []
  skuPopupVisible.value = true
  skuLoading.value = true
  try {
    const detail = await fetchProductDetail(item.productId)
    if (skuPopupItem.value !== item) return
    skuOptions.value = detail.skus ?? []
  } catch {
    // 商品已下架/被删除时拿不到款式，直接收起面板（错误文案由请求拦截器提示）
    if (skuPopupItem.value === item) skuPopupVisible.value = false
  } finally {
    if (skuPopupItem.value === item) skuLoading.value = false
  }
}

/** 选中款式：点即生效，无需确认 */
async function onSelectSku(sku: ProductSku) {
  const item = skuPopupItem.value
  if (!item || skuSubmitting.value) return
  // 相同款式：保持原样，仅收起面板
  if (sku.id === item.skuId) {
    skuPopupVisible.value = false
    return
  }
  if (sku.stock <= 0) return
  skuSubmitting.value = true
  try {
    await cartStore.changeSku(item.id, sku.id)
    skuPopupVisible.value = false
    showToast('已切换款式')
  } catch {
    // 库存不足等错误由请求拦截器提示，保持原规格不变
  } finally {
    skuSubmitting.value = false
  }
}

/** 未登录时的入口：跳登录页，登录成功后由 redirect 带回购物车 */
function goLogin() {
  router.push({ name: 'login', query: { redirect: '/cart' } })
}
</script>

<template>
  <div class="cart">
    <van-nav-bar title="购物车" />

    <!-- 未登录：不跳转登录页，就地提示并给一个去登录的入口 -->
    <van-empty v-if="!userStore.isLoggedIn" description="请先登录后查看购物车">
      <van-button round type="primary" class="cart__empty-btn cart__empty-btn--accent" @click="goLogin">去登录</van-button>
    </van-empty>

    <van-empty v-else-if="!cartStore.loading && items.length === 0" description="购物车还是空的">
      <van-button round type="primary" class="cart__empty-btn cart__empty-btn--brand" @click="goHome">去逛逛</van-button>
    </van-empty>

    <!-- 删除入口改为左滑露出：卡片本身不再放删除图标（SwipeCell 的删除按钮只在左滑后可见） -->
    <div v-else class="cart__list">
      <van-swipe-cell
        v-for="item in items"
        :key="item.id"
        :ref="(el: unknown) => setSwipeRef(item.id, el)"
        class="cart-item-cell"
      >
        <div class="cart-item" :class="{ 'cart-item--invalid': !item.valid }" @click="goDetail(item)">
          <van-checkbox
            class="cart-item__check"
            :model-value="item.checked === 1"
            :disabled="!item.valid"
            @click.stop
            @update:model-value="(value: boolean) => onToggleItem(item, value)"
          />

          <van-image class="cart-item__image" width="76" height="76" fit="cover" :src="item.productImage || ''" />

          <div class="cart-item__body">
            <div class="cart-item__name">{{ item.productName }}</div>
            <!-- 款式行：点击弹出全部款式面板，选中即切换（stop 冒泡，避免连带跳到详情页） -->
            <button
              v-if="item.specValue"
              class="cart-item__spec"
              type="button"
              @click.stop="openSkuPopup(item)"
            >
              <span class="cart-item__spec-text">{{ item.specValue }}</span>
              <van-icon name="arrow" size="12" />
            </button>
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
                @click.stop
                @change="(value: number | string) => onQuantityChange(item, value)"
              />
              <span v-else class="cart-item__quantity">×{{ item.quantity }}</span>
            </div>
          </div>
        </div>

        <template #right>
          <van-button square type="danger" text="删除" class="cart-item__delete" @click="onRemove(item)" />
        </template>
      </van-swipe-cell>
    </div>

    <van-submit-bar
      v-if="userStore.isLoggedIn && items.length > 0"
      class="cart__bar"
      :price="totalPriceInCents"
      :button-text="`结算(${cartStore.checkedCount})`"
      button-color="rgb(107,163,200)"
      @submit="onCheckout"
    >
      <van-checkbox :model-value="allChecked" class="cart__all" @click="onToggleAll">全选</van-checkbox>
    </van-submit-bar>

    <!-- 款式切换面板：底部弹出，头部是「已选」摘要，款式做成自适应宽度的胶囊标签，选中即切换 -->
    <van-popup v-model:show="skuPopupVisible" position="bottom" round>
      <div class="sku-picker">
        <div class="sku-picker__head">
          <span class="sku-picker__title">选择款式</span>
          <button class="sku-picker__close" type="button" aria-label="关闭" @click="skuPopupVisible = false">
            <van-icon name="cross" size="18" />
          </button>
        </div>

        <!-- 已选摘要：缩略图 + 当前款式与库存 + 单价 -->
        <div v-if="skuPopupItem" class="sku-picker__summary">
          <van-image
            class="sku-picker__thumb"
            width="52"
            height="52"
            fit="cover"
            :src="skuPopupItem.productImage || ''"
          />
          <div class="sku-picker__summary-text">
            <div class="sku-picker__summary-line">{{ selectedSkuLabel }}</div>
            <div class="sku-picker__summary-stock">{{ selectedSkuStock }}</div>
          </div>
          <div class="sku-picker__summary-price">¥{{ formatPrice(skuPopupItem.price) }}</div>
        </div>

        <van-loading v-if="skuLoading" class="sku-picker__loading" />

        <div v-else class="sku-picker__body">
          <div v-for="group in skuGroups" :key="group.title" class="sku-picker__group">
            <div class="sku-picker__group-title">{{ group.title }}</div>
            <div class="sku-picker__tags">
              <button
                v-for="sku in group.skus"
                :key="sku.id"
                class="sku-picker__tag"
                :class="{ 'is-active': sku.id === skuPopupItem?.skuId, 'is-disabled': sku.stock <= 0 }"
                type="button"
                :disabled="sku.stock <= 0"
                @click="onSelectSku(sku)"
              >
                <span>{{ sku.specValue }}</span>
                <van-icon v-if="sku.id === skuPopupItem?.skuId" name="success" size="12" />
              </button>
            </div>
          </div>
          <div v-if="!skuGroups.length" class="sku-picker__empty">该商品暂无可选款式</div>
        </div>
      </div>
    </van-popup>
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
/* 未登录空态的主按钮（「去登录」）：底色用品牌浅蓝 rgb(107,163,200)，
   与「去逛逛」「退出登录」「结算」保持同一品牌色（不再跟 tabbar 选中色同源）。
   覆盖的是 Vant primary 按钮自己的两个变量（默认取 --van-primary-color = #1989fa）；
   文字色保持 Vant 默认的白，字重保持普通按钮的 400，都不用动。 */
.cart__empty-btn--accent {
  --van-button-primary-background: rgb(107, 163, 200);
  --van-button-primary-border-color: rgb(107, 163, 200);
}
/* 已登录空态的主按钮（「去逛逛」）：底色与字体样式对齐「我的」页的「退出登录」按钮 ——
   即品牌浅蓝 rgb(107,163,200)（与购物车底部「结算」按钮同色）+ 白字 + 字重 600。
   本按钮仍是 type="primary"（「去逛逛」是引导性操作，不是危险操作），
   所以覆盖的是 primary 的底色变量；「退出登录」那边覆盖的是 danger 的两个变量。
   两边色值必须保持一致，改一处记得改另一处。 */
.cart__empty-btn--brand {
  --van-button-primary-background: rgb(107, 163, 200);
  --van-button-primary-border-color: rgb(107, 163, 200);
  /* 字重也要与「退出登录」一致：那边（同「结算」按钮）用的是
     var(--van-font-bold) = 600，而普通 van-button 默认只有 400。
     用 Vant 自己的变量而不是写死 600，以后改 --van-font-bold 两处会一起变。
     本类直接写在 <van-button> 标签上（= 子组件根节点本身），普通选择器即可命中；
     若像 MemberView 那样把类挂在**父级包裹 div** 上，则必须用 :deep(.van-button)。 */
  font-weight: var(--van-font-bold, 600);
}
.cart__list {
  padding: 10px 12px 0;
}
/* 间距挂在外层 SwipeCell 上，不挂卡片：
   SwipeCell 是 overflow:hidden 的 BFC，卡片自带的 margin-bottom 会被算进它的高度，
   于是 height:100% 的删除按钮会比卡片高出 10px。放外层后按钮高度正好等于卡片高度。 */
.cart-item-cell {
  margin-bottom: 10px;
}
.cart-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
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
/* 款式行：可点按钮（点击弹款式面板），右侧箭头提示可切换 */
.cart-item__spec {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  max-width: 100%;
  margin-top: 4px;
  padding: 1px 6px;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  color: #969799;
  background: #f7f8fa;
  cursor: pointer;
}
.cart-item__spec-text {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
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
/* 左滑露出的删除按钮：高度撑满整行，与卡片等高 */
.cart-item__delete {
  height: 100%;
}
.cart__bar {
  bottom: 50px;
}
.cart__all {
  margin-right: 12px;
}

/* ── 款式切换面板 ── */
.sku-picker {
  padding: 16px 16px 20px;
}
.sku-picker__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.sku-picker__title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}
.sku-picker__close {
  padding: 4px;
  border: none;
  background: none;
  color: #969799;
  cursor: pointer;
}
.sku-picker__loading {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}
/* 款式多时面板内部滚动，最多占视口一半，避免顶掉标题 */
.sku-picker__body {
  max-height: 46vh;
  overflow-y: auto;
}
.sku-picker__group + .sku-picker__group {
  margin-top: 14px;
}
.sku-picker__group-title {
  margin-bottom: 8px;
  font-size: 13px;
  color: #646566;
}
/* 已选摘要：缩略图 + 当前款式与库存 + 单价 */
.sku-picker__summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 10px;
  border-radius: 10px;
  background: #f7f8fa;
}
.sku-picker__thumb {
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  background: #ebedf0;
}
.sku-picker__summary-text {
  flex: 1;
  min-width: 0;
}
.sku-picker__summary-line {
  font-size: 13px;
  color: #323233;
}
.sku-picker__summary-stock {
  margin-top: 3px;
  font-size: 12px;
  color: #969799;
}
.sku-picker__summary-price {
  font-size: 16px;
  font-weight: 600;
  color: #ee0a24;
}
/* 款式标签：宽度随文字自适应的胶囊，选中填浅蓝。
   浅蓝底（rgb(184,212,232)）偏亮，文字必须用深色 —— 白字在它上面的对比度只有 1.4:1。 */
.sku-picker__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.sku-picker__tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 15px;
  border: 1px solid #ebedf0;
  border-radius: 16px;
  background: #fafafa;
  font-size: 13px;
  color: #323233;
  cursor: pointer;
}
.sku-picker__tag.is-active {
  border-color: rgb(184, 212, 232);
  background: rgb(184, 212, 232);
  color: #323233;
}
.sku-picker__tag.is-disabled {
  border-color: #f2f3f5;
  background: #f7f8fa;
  color: #c8c9cc;
  cursor: not-allowed;
}
.sku-picker__empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #969799;
}
</style>
