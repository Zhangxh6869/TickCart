<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { ProductDetail, ProductSku } from '@tickcart/shared-types'
import { formatCount, formatPrice } from '@tickcart/shared-utils'
import ShareSheet from '@/components/ShareSheet.vue'
import { fetchProductDetail } from '@/api/product'
import { addFavorite, fetchFavoriteStatus, removeFavorite } from '@/api/favorite'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { buildBuyNowQuery } from '@/utils/checkout-route'
import type { ShareProduct } from '@/utils/share'

/**
 * 「选择款式」面板的入口模式，决定底部按钮文案与确认后的动作。
 *
 * <p>{@code browse} 只用于详情页快捷行的「款式 N」——那里只是挑款式看，不执行下单动作。</p>
 */
type SkuSheetMode = 'cart' | 'buyNow' | 'browse'

/**
 * 底部「收藏」图标的两个状态。
 *
 * <p>设计给的 SVG 是一条 path、两段闭合子路径，靠 nonzero 填充规则挖空：
 * 未收藏的描边星就是**两段都留着**的原样，已收藏的实心星则是把内圈那段单独取出来。
 * 两个状态都能从这一份文件里得到，不用再要一张实心图。
 * 只保留 viewBox 与 path —— 源文件里的 `t` / `p-id` / `class` / DOCTYPE 都是导出工具
 * 留下的元数据，对渲染无意义（与 `TabBarIcon.vue` 同一套做法）。</p>
 *
 * <p>★ 未收藏这条**必须保留两段子路径**。只留外圈会把它填成实心，而且因为描边很厚
 * （85 单位），填出来是圆胖的一团，空心感完全没了 —— 这个坑踩过一次。</p>
 *
 * <p>viewBox 必须跟着状态一起换：外层轮廓铺满整张 1065×1024 画布，内层只有 894×854
 * 且偏在画布中间，共用一个 viewBox 的话「实心」那档会看起来小一圈。</p>
 */
const STAR_OUTLINE = {
  viewBox: '0 0 1065 1024',
  // 外圈（描边） + 内圈（挖空），两段缺一不可
  d: 'M964.340451 303.311757l-209.677811-30.424833a32.072845 32.072845 0 0 1-24.086326-17.62105l-93.683133-190.155209a116.755298 116.755298 0 0 0-209.424271 0l-93.683133 190.155209a32.199615 32.199615 0 0 1-24.213096 17.494279L100.02164 303.311757a116.755298 116.755298 0 0 0-64.652771 199.155889l152.124167 147.813983a31.946075 31.946075 0 0 1 9.25422 28.396511L160.364226 887.7221A116.501758 116.501758 0 0 0 329.602363 1010.435595l187.619806-98.500398a32.072845 32.072845 0 0 1 29.917753 0l187.366266 98.500398a115.994678 115.994678 0 0 0 122.967036-8.87391 115.994678 115.994678 0 0 0 46.397871-114.093125l-35.74918-208.79042a31.946075 31.946075 0 0 1 9.25422-28.396511l152.124168-147.813983a116.755298 116.755298 0 0 0-64.652771-199.155889z m5.577886 138.432992l-152.124167 147.940753a116.628528 116.628528 0 0 0-33.467317 103.317664l36.12949 208.663649a32.199615 32.199615 0 0 1-46.524641 33.847628L586.565435 837.014044a118.910391 118.910391 0 0 0-108.768779 0l-187.366266 98.500399a32.072845 32.072845 0 0 1-46.524642-33.847628l35.74918-208.663649a116.755298 116.755298 0 0 0-33.594087-103.317664l-152.124168-147.940753a32.072845 32.072845 0 0 1 17.74782-54.63793l209.55104-30.424833a116.882069 116.882069 0 0 0 87.978477-63.38507l93.683133-190.155209a32.072845 32.072845 0 0 1 57.553644 0l93.683133 190.155209a116.755298 116.755298 0 0 0 87.851706 63.38507l209.677811 30.424833a32.072845 32.072845 0 0 1 17.74782 54.63793z',
}
const STAR_FILLED = {
  viewBox: '84.308 85.231 894.731 853.965',
  d: 'M969.918337 441.744749l-152.124167 147.940753a116.628528 116.628528 0 0 0-33.467317 103.317664l36.12949 208.663649a32.199615 32.199615 0 0 1-46.524641 33.847628L586.565435 837.014044a118.910391 118.910391 0 0 0-108.768779 0l-187.366266 98.500399a32.072845 32.072845 0 0 1-46.524642-33.847628l35.74918-208.663649a116.755298 116.755298 0 0 0-33.594087-103.317664l-152.124168-147.940753a32.072845 32.072845 0 0 1 17.74782-54.63793l209.55104-30.424833a116.882069 116.882069 0 0 0 87.978477-63.38507l93.683133-190.155209a32.072845 32.072845 0 0 1 57.553644 0l93.683133 190.155209a116.755298 116.755298 0 0 0 87.851706 63.38507l209.677811 30.424833a32.072845 32.072845 0 0 1 17.74782 54.63793z',
}

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
/** 选择款式面板：加入购物车 / 立即购买都先经过它 */
const skuSheetVisible = ref(false)
/** 面板本次的入口模式，决定底部按钮文案与确认后的动作 */
const skuSheetMode = ref<SkuSheetMode>('browse')
/** 面板内选购的数量，范围 1 ~ 当前规格库存 */
const quantity = ref(1)
/** 加购提交中：避免连点重复下单 */
const submitting = ref(false)
/** 是否已收藏；未登录时恒为 false */
const favorited = ref(false)
/** 分享面板 */
const shareSheetVisible = ref(false)

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
/**
 * 是否还有可购的规格。
 *
 * <p>底部两个按钮据此决定能否点击：不能只看「当前选中的规格」——当前那档售罄时
 * 用户还得能点进面板换一档。无规格商品则看商品自身的库存。</p>
 */
const hasStock = computed(() =>
  skuCount.value ? (detail.value?.skus ?? []).some((sku) => sku.stock > 0) : currentStock.value > 0,
)
/** 数量上限；库存为 0 时也至少给 1，步进器不会被夹成 0 */
const maxQuantity = computed(() => Math.max(1, currentStock.value))
/** 面板头部的封面图：优先当前规格自己的图，没有就退回商品主图 */
const sheetImage = computed(() => selectedSku.value?.skuImage || detail.value?.mainImage || '')
/** 面板底部按钮文案：跟随入口模式，浏览态只做「确定」 */
const skuSheetConfirmText = computed(() => {
  if (skuSheetMode.value === 'cart') return '加入购物车'
  if (skuSheetMode.value === 'buyNow') return '立即购买'
  return '确定'
})
const isSeckill = computed(() => detail.value?.isSeckill === 1)
/** 分享只需要这几个字段：价格跟着当前选中规格走，分享出去的才是用户看到的那一档 */
const shareProduct = computed<ShareProduct>(() => ({
  id: detail.value?.id ?? 0,
  name: detail.value?.name ?? '',
  price: currentPrice.value,
  mainImage: detail.value?.mainImage ?? null,
}))
/** 价格拆成整数/小数两段，整数走大字号（对齐参考图的 ¥16 版式） */
const priceParts = computed(() => {
  const [integer, decimal] = formatPrice(currentPrice.value).split('.')
  return { integer, decimal }
})
/** 当前收藏状态对应的星形：未收藏描边、已收藏实心 */
const starIcon = computed(() => (favorited.value ? STAR_FILLED : STAR_OUTLINE))

// 换规格后库存跟着变，把数量夹回合法区间，避免出现「选了 3 件但只剩 1 件」
watch(selectedSku, () => {
  if (quantity.value > maxQuantity.value) quantity.value = maxQuantity.value
  if (quantity.value < 1) quantity.value = 1
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

/** 分享：打开「分享至」面板。分享是浏览态行为，未登录也允许 */
function onShare() {
  shareSheetVisible.value = true
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

/**
 * 打开「选择款式」面板。
 *
 * <p>加入购物车与立即购买都先经过它：用户在面板里挑款式、定数量，再点底部按钮执行。
 * 无规格商品没有款式可选，直接按原动作走，不弹一个空面板。</p>
 */
function openSkuSheet(mode: SkuSheetMode) {
  if (!hasStock.value) {
    showToast('该商品已售罄')
    return
  }
  if (!skuCount.value) {
    if (mode === 'cart') {
      if (!requireLogin()) return
      // 提示由请求拦截器统一处理，这里只吞掉 rejection 避免未捕获告警
      submitAddToCart(1).catch(() => undefined)
    } else if (mode === 'buyNow') {
      submitBuyNow(1)
    }
    return
  }
  skuSheetMode.value = mode
  quantity.value = 1
  skuSheetVisible.value = true
}

/**
 * 面板底部确认：按入口模式执行对应动作。
 *
 * <p>加购失败（库存不足等）时面板留在原地，方便用户改数量重试；跳转类动作先收起面板，
 * 否则登录页/结算页会被面板压住。</p>
 */
async function confirmSkuSheet() {
  if (submitting.value) return
  const mode = skuSheetMode.value
  if (mode === 'browse') {
    skuSheetVisible.value = false
    return
  }
  if (!canBuy.value) {
    showToast('该规格已售罄')
    return
  }
  if (mode === 'buyNow') {
    skuSheetVisible.value = false
    submitBuyNow(quantity.value)
    return
  }
  if (!requireLogin()) {
    skuSheetVisible.value = false
    return
  }
  submitting.value = true
  try {
    await submitAddToCart(quantity.value)
    skuSheetVisible.value = false
  } catch {
    // 库存不足等错误提示由请求拦截器统一处理，面板保持打开便于调整数量
  } finally {
    submitting.value = false
  }
}

/** 加入购物车；数量由面板决定 */
async function submitAddToCart(count: number) {
  await cartStore.add(detail.value!.id, count, selectedSku.value?.id ?? 0)
  showToast('已加入购物车')
}

/**
 * 立即购买：进确认订单页，**不加入购物车**。
 *
 * <p>商品、规格与数量通过路由 query 传给结算页，由结算页自己重新拉一次详情来展示 ——
 * 不把本页的数据带过去，价格与库存才会以服务端最新值结算。</p>
 */
function submitBuyNow(count: number) {
  const target = {
    name: 'checkout' as const,
    query: buildBuyNowQuery(detail.value!.id, selectedSku.value?.id ?? 0, count),
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
        <button class="quick-bar__item" type="button" :disabled="!skuCount" @click="openSkuSheet('browse')">
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
          <svg
            class="action-bar__star"
            :viewBox="starIcon.viewBox"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
            focusable="false"
          >
            <path :d="starIcon.d" fill="currentColor" />
          </svg>
          <span>收藏</span>
        </button>
        <button
          class="action-bar__btn action-bar__btn--cart"
          type="button"
          :disabled="!hasStock"
          @click="openSkuSheet('cart')"
        >
          {{ hasStock ? '加入购物车' : '已售罄' }}
        </button>
        <button
          class="action-bar__btn action-bar__btn--buy"
          type="button"
          :disabled="!hasStock"
          @click="openSkuSheet('buyNow')"
        >
          立即购买
        </button>
      </div>

      <!-- 选择款式面板：加入购物车 / 立即购买都先经过它，选好款式与数量再执行 -->
      <van-popup v-model:show="skuSheetVisible" position="bottom" round class="sku-sheet">
        <div class="sku-popup">
          <!-- 头部：封面 + 当前价 + 数量，随选中的规格实时联动 -->
          <div class="sku-popup__head">
            <span class="sku-popup__cover">
              <img v-if="sheetImage" :src="sheetImage" alt="" />
              <van-icon v-else name="photo-o" size="22" />
            </span>
            <div class="sku-popup__summary">
              <div class="sku-popup__amount">
                <span class="sku-popup__symbol">¥</span>{{ formatPrice(currentPrice) }}
              </div>
              <div class="sku-popup__qty">
                <span class="sku-popup__qty-label">数量</span>
                <van-stepper v-model="quantity" :min="1" :max="maxQuantity" integer />
              </div>
            </div>
            <button class="sku-popup__close" type="button" aria-label="关闭" @click="skuSheetVisible = false">
              <van-icon name="cross" size="18" />
            </button>
          </div>

          <div class="sku-popup__title">规格（{{ skuCount }}）</div>

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

          <!-- 底部确认：文案跟着入口走（加入购物车 / 立即购买 / 确定） -->
          <div class="sku-popup__foot">
            <button
              class="sku-popup__submit"
              :class="`sku-popup__submit--${skuSheetMode}`"
              type="button"
              :disabled="!canBuy || submitting"
              @click="confirmSkuSheet"
            >
              {{ submitting ? '处理中…' : skuSheetConfirmText }}
            </button>
          </div>
        </div>
      </van-popup>

      <!-- 分享面板（含引导蒙层 / 海报 / 举报 / 反馈），未登录也能用 -->
      <ShareSheet v-model:show="shareSheetVisible" :product="shareProduct" />
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
  /* 与上面的详情内容区分界：一条实色线 + 一层上浮阴影。
     原来只有 `0 -1px 0 rgba(0,0,0,.06)`，叠在白底上约等于 #f0f0f0，基本看不出来；
     换成实色 #ebedf0 后分界清楚，再补一层朝上的柔和阴影，让它有「浮在内容之上」的层次。
     阴影不占版面，操作栏高度仍是 56px。 */
  box-shadow: 0 -1px 0 #ebedf0, 0 -3px 8px rgba(0, 0, 0, 0.07);
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
  /* 图标色 = 设计给的 SVG 自带的 #2c2c2c（path 的 fill 走 currentColor） */
  color: #2c2c2c;
  cursor: pointer;
}
/* 已收藏：星形换成实心并转橘黄（与「加入购物车」同色） */
.action-bar__icon.is-favorited {
  color: #ff9500;
}
/* 「收藏」两个字固定黑色，不跟图标的状态色走 */
.action-bar__icon span {
  color: #000;
}
/* 图标由内联 SVG 承担后，尺寸对齐原来的 van-icon（20px）；
   display: block 不能省 —— 内联元素会在基线处留出缝隙 */
.action-bar__star {
  display: block;
  width: 21px;
  height: 20px;
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

/* ── 选择款式面板 ──
   面板整体收在视口内：头部与底部固定，只有中间的规格列表滚动 */
.sku-sheet {
  max-height: 86vh;
  overflow: hidden;
}
.sku-popup {
  display: flex;
  flex-direction: column;
  max-height: 86vh;
  padding: 16px;
}
.sku-popup__head {
  display: flex;
  flex: none;
  align-items: flex-start;
  gap: 12px;
}
.sku-popup__cover {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  overflow: hidden;
  border-radius: 8px;
  background: #f2f3f5;
  color: #c8c9cc;
}
.sku-popup__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.sku-popup__summary {
  flex: 1;
  min-width: 0;
}
.sku-popup__amount {
  display: flex;
  align-items: baseline;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  color: #ee0a24;
}
.sku-popup__symbol {
  margin-right: 1px;
  font-size: 14px;
  font-weight: 600;
}
.sku-popup__qty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
}
.sku-popup__qty-label {
  font-size: 13px;
  color: #646566;
}
.sku-popup__close {
  flex: none;
  padding: 4px;
  border: none;
  background: none;
  color: #969799;
  cursor: pointer;
}
.sku-popup__title {
  flex: none;
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}
.sku-popup__body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
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
  min-width: 0;
  overflow: hidden;
  color: #323233;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.sku-popup__price {
  flex: none;
  font-weight: 600;
  color: #ee0a24;
}
.sku-popup__stock {
  flex: none;
  font-size: 12px;
  color: #969799;
}
.sku-popup__foot {
  flex: none;
  padding-top: 16px;
}
/* 用普通按钮而非 van-button：三个入口各一种底色，普通按钮更好控 */
.sku-popup__submit {
  display: block;
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 22px;
  background: #ee0a24;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
}
.sku-popup__submit--cart {
  background: #ff9500;
}
.sku-popup__submit--browse {
  background: var(--van-primary-color, #1989fa);
}
.sku-popup__submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
