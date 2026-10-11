<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { ProductDetail, ProductSku } from '@tickcart/shared-types'
import { formatCount, formatPrice } from '@tickcart/shared-utils'
import { fetchProductDetail } from '@/api/product'
import { addFavorite, fetchFavoriteStatus, removeFavorite } from '@/api/favorite'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { buildBuyNowQuery } from '@/utils/checkout-route'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

const detail = ref<ProductDetail | null>(null)
const loading = ref(true)
/** 当前选中的规格：价格、库存、可购状态都以它为准 */
const selectedSku = ref<ProductSku | null>(null)
/** 图集当前页下标（从 0 开始） */
const activeImage = ref(0)
/** 顶部快捷行高亮的区块 */
const activeSection = ref<'gallery' | 'detail'>('gallery')
/** 长标题是否已展开；只有折叠态下真的被截断才显示「更多」 */
const titleExpanded = ref(false)
const titleOverflow = ref(false)
/** 规格选择弹层 */
const skuPopupVisible = ref(false)
/** 是否已收藏；未登录时恒为 false */
const favorited = ref(false)

const titleRef = ref<HTMLElement | null>(null)
const galleryRef = ref<HTMLElement | null>(null)
const descRef = ref<HTMLElement | null>(null)

/**
 * 主图区展示的图片列表。
 *
 * <p>以图集表为准，为空时退回主图，保证主图区始终有内容。</p>
 *
 * <p>选中规格后，该规格自己的图片（{@code skuImage}）插到最前面：同一件商品的不同规格
 * 外观往往不同（颜色/容量），主图跟着规格走才符合预期；已经在图集里就不重复插，
 * 只把页码切过去。规格没配图时列表保持不变，也就不会打断用户正在看的那一页。</p>
 */
const gallery = computed<string[]>(() => {
  const urls = (detail.value?.images ?? []).map((image) => image.url).filter(Boolean)
  const base = urls.length ? urls : detail.value?.mainImage ? [detail.value.mainImage] : []
  const skuImage = selectedSku.value?.skuImage
  if (!skuImage) return base
  return base.includes(skuImage) ? base : [skuImage, ...base]
})

const currentPrice = computed(() => selectedSku.value?.price ?? detail.value?.price ?? 0)
const currentStock = computed(() => selectedSku.value?.stock ?? detail.value?.stock ?? 0)
const canBuy = computed(() => currentStock.value > 0)
const skuCount = computed(() => detail.value?.skus.length ?? 0)
const isSeckill = computed(() => detail.value?.isSeckill === 1)
/** 价格拆成整数/小数两段，整数走大字号（对齐参考图的 ¥16 版式） */
const priceParts = computed(() => {
  const [integer, decimal] = formatPrice(currentPrice.value).split('.')
  return { integer, decimal }
})

onMounted(load)

async function load() {
  try {
    const data = await fetchProductDetail(Number(route.params.id))
    detail.value = data
    // 默认选中第一个有货规格；全部无货时退回首个，保证价格区仍有值可展示
    selectedSku.value = data.skus.find((sku) => sku.stock > 0) ?? data.skus[0] ?? null
  } catch {
    // 具体错误文案由请求拦截器统一提示，这里只结束 loading，避免页面一直转圈
    detail.value = null
  } finally {
    loading.value = false
  }
  await nextTick()
  measureTitle()
  await loadFavoriteStatus()
}

/**
 * 回显收藏状态。未登录不发请求；请求失败也不阻塞页面，按钮保持未收藏态。
 * 从登录页 redirect 回来会重新挂载本组件，因此登录后状态能自动补上。
 */
async function loadFavoriteStatus() {
  if (!userStore.isLoggedIn) return
  try {
    favorited.value = await fetchFavoriteStatus(Number(route.params.id))
  } catch {
    // 忽略：收藏状态只是回显，失败不影响浏览与下单
  }
}

/** 折叠两行时 scrollHeight 大于可视高度，说明标题被截断，才需要「更多」 */
function measureTitle() {
  const el = titleRef.value
  if (!el) return
  titleOverflow.value = el.scrollHeight > el.clientHeight + 1
}

/** 选中规格（无货不可选）；选中后把主图区切到该规格自己的图 */
function selectSku(sku: ProductSku) {
  if (sku.stock <= 0) return
  selectedSku.value = sku
  if (!sku.skuImage) return
  // gallery 是 computed：要等它按新的 selectedSku 重算完，才能拿到 SKU 图的新下标
  nextTick(() => {
    const index = gallery.value.indexOf(sku.skuImage as string)
    if (index >= 0) activeImage.value = index
  })
}

/** 顶部快捷行：滚动到对应区块 */
function scrollToSection(section: 'gallery' | 'detail') {
  activeSection.value = section
  const el = section === 'gallery' ? galleryRef.value : descRef.value
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function goBack() {
  // 直接打开详情链接时没有可回退的历史，退回首页而不是退出应用
  if (window.history.state?.back) router.back()
  else router.replace({ name: 'home' })
}

function onShare() {
  showToast('分享功能将在阶段2开通')
}

function onCart() {
  // 购物车页自己处理未登录态（就地提示登录），这里不再拦到登录页，与底部 tabbar 行为保持一致
  router.push({ name: 'cart' })
}

/** 收藏 / 取消收藏：未登录先带 redirect 去登录 */
async function onFavorite() {
  if (!requireLogin()) return
  const productId = Number(route.params.id)
  try {
    if (favorited.value) {
      await removeFavorite(productId)
      favorited.value = false
      showToast('已取消收藏')
    } else {
      await addFavorite(productId)
      favorited.value = true
      showToast('已收藏')
    }
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

/** 需要登录的操作统一走这里：未登录时带 redirect 跳登录页 */
function requireLogin(): boolean {
  if (userStore.isLoggedIn) return true
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function onAddToCart() {
  if (!canBuy.value) {
    showToast('该规格已售罄')
    return
  }
  if (!requireLogin()) return
  try {
    await cartStore.add(detail.value!.id, 1, selectedSku.value?.id ?? 0)
    showToast('已加入购物车')
  } catch {
    // 库存不足等错误提示由请求拦截器统一处理
  }
}

/**
 * 立即购买：直接进确认订单页，**不加入购物车**。
 *
 * <p>商品与规格通过路由 query 传给结算页，由结算页自己重新拉一次详情来展示 ——
 * 不把本页的数据带过去，价格与库存才会以服务端最新值结算。</p>
 */
function onBuyNow() {
  if (!canBuy.value) {
    showToast('该规格已售罄')
    return
  }
  const target = {
    name: 'checkout' as const,
    query: buildBuyNowQuery(detail.value!.id, selectedSku.value?.id ?? 0),
  }
  if (!userStore.isLoggedIn) {
    // 登录后直接回到确认订单页，不用让用户再点一次「立即购买」
    router.push({ name: 'login', query: { redirect: router.resolve(target).fullPath } })
    return
  }
  router.push(target)
}
</script>

<template>
  <div class="detail">
    <!-- 顶部导航：白底，左返回、右分享/购物车 -->
    <div class="nav">
      <button class="nav__btn" type="button" aria-label="返回" @click="goBack">
        <van-icon name="arrow-left" size="20" />
      </button>
      <div class="nav__right">
        <button class="nav__btn" type="button" aria-label="分享" @click="onShare">
          <van-icon name="share-o" size="20" />
        </button>
        <button class="nav__btn" type="button" aria-label="购物车" @click="onCart">
          <van-icon name="cart-o" size="20" />
        </button>
      </div>
    </div>

    <van-loading v-if="loading" class="detail__loading" />

    <template v-else-if="detail">
      <!-- 主图 / 图集 -->
      <div ref="galleryRef" class="gallery">
        <van-swipe
          v-if="gallery.length"
          v-model:active="activeImage"
          class="gallery__swipe"
          :show-indicators="gallery.length > 1"
        >
          <van-swipe-item v-for="(url, index) in gallery" :key="index">
            <img :src="url" :alt="detail.name" />
          </van-swipe-item>
        </van-swipe>
        <div v-else class="gallery__empty">暂无图片</div>
      </div>

      <!-- 快捷行：图集页码 / 款式数 / 详情 -->
      <div class="quick-bar">
        <button
          class="quick-bar__item"
          :class="{ 'is-active': activeSection === 'gallery' }"
          type="button"
          @click="scrollToSection('gallery')"
        >
          图集 {{ gallery.length ? activeImage + 1 : 0 }}/{{ gallery.length }}
        </button>
        <button class="quick-bar__item" type="button" :disabled="!skuCount" @click="skuPopupVisible = true">
          款式 {{ skuCount }}
        </button>
        <button
          class="quick-bar__item"
          :class="{ 'is-active': activeSection === 'detail' }"
          type="button"
          @click="scrollToSection('detail')"
        >
          详情
        </button>
      </div>

      <!-- 款式横向卡片：直接点选规格 -->
      <div v-if="skuCount" class="sku-cards">
        <button
          v-for="sku in detail.skus"
          :key="sku.id"
          class="sku-card"
          :class="{ 'is-active': selectedSku?.id === sku.id, 'is-disabled': sku.stock <= 0 }"
          type="button"
          @click="selectSku(sku)"
        >
          <span class="sku-card__value">{{ sku.specValue }}</span>
          <span class="sku-card__price">¥{{ formatPrice(sku.price) }}</span>
          <span class="sku-card__stock">{{ sku.stock > 0 ? `库存 ${sku.stock}` : '已售罄' }}</span>
        </button>
      </div>

      <!-- 标题 -->
      <div class="title">
        <h1 ref="titleRef" class="title__name" :class="{ 'is-expanded': titleExpanded }">
          {{ detail.name }}
        </h1>
        <button
          v-if="titleOverflow && !titleExpanded"
          class="title__more"
          type="button"
          @click="titleExpanded = true"
        >
          更多
          <van-icon name="arrow-down" size="12" />
        </button>
        <p v-if="detail.subtitle" class="title__subtitle">{{ detail.subtitle }}</p>
      </div>

      <!-- 价格行 -->
      <div class="price">
        <div class="price__left">
          <span class="price__symbol">¥</span>
          <span class="price__integer">{{ priceParts.integer }}</span>
          <span class="price__decimal">.{{ priceParts.decimal }}</span>
          <span v-if="isSeckill" class="price__tag">秒杀</span>
        </div>
        <div class="price__right">总销 {{ formatCount(detail.sales) }}</div>
      </div>

      <!-- 商品详情 -->
      <div ref="descRef" class="section">
        <div class="section__title">商品详情</div>
        <p v-if="detail.detail" class="section__text">{{ detail.detail }}</p>
        <p v-else class="section__text section__text--empty">暂无详情内容</p>
      </div>

      <!-- 底部操作栏：sticky 而非 fixed，这样能跟着页面一起滑入滑出 -->
      <div class="action-bar">
        <button
          class="action-bar__icon"
          type="button"
          :class="{ 'is-favorited': favorited }"
          @click="onFavorite"
        >
          <van-icon :name="favorited ? 'star' : 'star-o'" size="20" />
          <span>收藏</span>
        </button>
        <button
          class="action-bar__btn action-bar__btn--cart"
          type="button"
          :disabled="!canBuy"
          @click="onAddToCart"
        >
          {{ canBuy ? '加入购物车' : '已售罄' }}
        </button>
        <button
          class="action-bar__btn action-bar__btn--buy"
          type="button"
          :disabled="!canBuy"
          @click="onBuyNow"
        >
          立即购买
        </button>
      </div>

      <!-- 规格选择弹层 -->
      <van-popup v-model:show="skuPopupVisible" position="bottom" round>
        <div class="sku-popup">
          <div class="sku-popup__head">
            <span class="sku-popup__title">选择规格</span>
            <button class="sku-popup__close" type="button" aria-label="关闭" @click="skuPopupVisible = false">
              <van-icon name="cross" size="18" />
            </button>
          </div>
          <div class="sku-popup__body">
            <button
              v-for="sku in detail.skus"
              :key="sku.id"
              class="sku-popup__item"
              :class="{ 'is-active': selectedSku?.id === sku.id, 'is-disabled': sku.stock <= 0 }"
              type="button"
              @click="selectSku(sku)"
            >
              <span class="sku-popup__thumb">
                <img
                  v-if="sku.skuImage || detail.mainImage"
                  :src="(sku.skuImage || detail.mainImage) as string"
                  alt=""
                />
                <van-icon v-else name="photo-o" size="18" />
              </span>
              <span class="sku-popup__name">
                {{ sku.specName ? `${sku.specName} ` : '' }}{{ sku.specValue }}
              </span>
              <span class="sku-popup__price">¥{{ formatPrice(sku.price) }}</span>
              <span class="sku-popup__stock">{{ sku.stock > 0 ? `库存 ${sku.stock}` : '已售罄' }}</span>
            </button>
          </div>
        </div>
      </van-popup>
    </template>
  </div>
</template>

<style scoped>
.detail {
  /* 纵向 flex：内容不足一屏时靠 .action-bar 的 margin-top:auto 把操作栏顶到底部
     （只写 min-height 不行——多出来的空间不会分给子元素，操作栏会停在内容末尾） */
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #fff;
}
/* 各区块按自身高度排布，不被 flex 压缩 */
.detail > * {
  flex-shrink: 0;
}
.detail__loading {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

/* ── 顶部导航 ── */
.nav {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  padding: 0 4px;
  background: #fff;
}
.nav__right {
  display: flex;
  align-items: center;
}
.nav__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  background: none;
  color: #323233;
  cursor: pointer;
}

/* ── 主图 / 图集 ── */
.gallery {
  /* 快捷行点击「图集」时滚到此处，避开吸顶导航的高度 */
  scroll-margin-top: 44px;
  background: #fff;
}
.gallery__swipe {
  width: 100%;
}
.gallery__swipe :deep(.van-swipe-item) {
  display: flex;
  align-items: center;
  justify-content: center;
}
.gallery__swipe img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: contain;
}
.gallery__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1;
  background: #f7f8fa;
  color: #c8c9cc;
  font-size: 14px;
}

/* ── 快捷行 ── */
.quick-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid #f2f3f5;
}
.quick-bar__item {
  padding: 0;
  border: none;
  background: none;
  font-size: 14px;
  color: #969799;
  cursor: pointer;
}
.quick-bar__item.is-active {
  color: #323233;
  font-weight: 600;
}
.quick-bar__item:disabled {
  color: #c8c9cc;
  cursor: default;
}

/* ── 款式卡片 ── */
.sku-cards {
  display: flex;
  gap: 8px;
  padding: 12px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.sku-cards::-webkit-scrollbar {
  display: none;
}
.sku-card {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 96px;
  padding: 8px 10px;
  border: 1px solid #ebedf0;
  border-radius: 6px;
  background: #fafafa;
  text-align: left;
  cursor: pointer;
}
.sku-card.is-active {
  border-color: #ee0a24;
  background: #fff5f5;
}
.sku-card.is-disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.sku-card__value {
  font-size: 13px;
  color: #323233;
}
.sku-card__price {
  font-size: 14px;
  font-weight: 600;
  color: #ee0a24;
}
.sku-card__stock {
  font-size: 11px;
  color: #969799;
}

/* ── 标题 ── */
.title {
  padding: 0 12px;
}
.title__name {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
  color: #323233;
}
.title__name.is-expanded {
  -webkit-line-clamp: unset;
  overflow: visible;
}
.title__more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 2px;
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  color: #969799;
  cursor: pointer;
}
.title__subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #969799;
}

/* ── 价格行 ── */
.price {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 10px 12px 14px;
}
.price__symbol {
  font-size: 16px;
  font-weight: 600;
  color: #ee0a24;
}
.price__integer {
  font-size: 26px;
  font-weight: 700;
  color: #ee0a24;
}
.price__decimal {
  font-size: 15px;
  font-weight: 600;
  color: #ee0a24;
}
.price__tag {
  margin-left: 6px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #ee0a24;
  color: #fff;
  font-size: 11px;
}
.price__right {
  font-size: 12px;
  color: #969799;
}

/* ── 商品详情 ── */
.section {
  scroll-margin-top: 44px;
  padding: 14px 12px;
  border-top: 8px solid #f7f8fa;
}
.section__title {
  margin-bottom: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}
.section__text {
  font-size: 14px;
  line-height: 1.7;
  color: #646566;
  white-space: pre-wrap;
}
.section__text--empty {
  color: #c8c9cc;
}

/* ── 底部操作栏 ──
   用 sticky 而不是 fixed：fixed 相对视口定位，页面切换时不会跟着一起滑入滑出。
   margin-top:auto 负责「内容不足一屏」时贴底，sticky 负责「内容超一屏」时滚动吸底 */
.action-bar {
  position: sticky;
  bottom: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 56px;
  margin-top: auto;
  padding: 0 12px;
  background: #fff;
  box-shadow: 0 -1px 0 rgba(0, 0, 0, 0.06);
}
.action-bar__icon {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  width: 44px;
  padding: 0;
  border: none;
  background: none;
  font-size: 11px;
  color: #646566;
  cursor: pointer;
}
/* 已收藏：实心星 + 与 tabbar 选中态同源的强调色 */
.action-bar__icon.is-favorited {
  color: var(--van-tabbar-item-active-color, rgb(59, 130, 246));
}
.action-bar__btn {
  flex: 1;
  height: 40px;
  border: none;
  border-radius: 20px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
}
.action-bar__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.action-bar__btn--cart {
  background: #ff9500;
}
.action-bar__btn--buy {
  background: #ee0a24;
}

/* ── 规格弹层 ── */
.sku-popup {
  padding: 16px 16px 20px;
}
.sku-popup__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.sku-popup__title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}
.sku-popup__close {
  padding: 4px;
  border: none;
  background: none;
  color: #969799;
  cursor: pointer;
}
.sku-popup__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 40vh;
  overflow-y: auto;
}
.sku-popup__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #ebedf0;
  border-radius: 6px;
  background: #fafafa;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}
.sku-popup__item.is-active {
  border-color: #ee0a24;
  background: #fff5f5;
}
.sku-popup__item.is-disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.sku-popup__thumb {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  overflow: hidden;
  color: #c8c9cc;
  background: #f2f3f5;
  border-radius: 4px;
}
.sku-popup__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.sku-popup__name {
  flex: 1;
  color: #323233;
}
.sku-popup__price {
  font-weight: 600;
  color: #ee0a24;
}
.sku-popup__stock {
  font-size: 12px;
  color: #969799;
}
</style>
