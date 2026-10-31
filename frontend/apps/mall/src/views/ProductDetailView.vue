<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showImagePreview, showToast } from 'vant'
import type { ProductDetail, ProductSku } from '@tickcart/shared-types'
import type { SwipeInstance } from 'vant'
import { formatCount, formatPrice } from '@tickcart/shared-utils'
import ShareSheet from '@/components/ShareSheet.vue'
import { fetchProductDetail } from '@/api/product'
import { addFavorite, fetchFavoriteStatus, removeFavorite } from '@/api/favorite'
import { reportViewHistory } from '@/api/view-history'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { buildBuyNowQuery } from '@/utils/checkout-route'
import { buildGallery } from '@/utils/product-gallery'
import { sanitizeRichText } from '@/utils/rich-text'
import type { ShareProduct } from '@/utils/share'

/**
 * 「选择款式」面板的入口模式，决定底部按钮文案与确认后的动作。
 *
 * <p>{@code browse} 只用于详情页快捷行的「款式 N」——那里只是挑款式看，不执行下单动作。</p>
 */
type SkuSheetMode = 'cart' | 'buyNow' | 'browse'

/**
 * 顶部导航的「分享」图标。
 *
 * <p>设计给的 SVG 只保留 viewBox 与 path —— 源文件里的 `t` / `p-id` / `class` / DOCTYPE
 * 都是导出工具留下的元数据，对渲染无意义。`fill` 走 `currentColor`，图标就跟随
 * `.nav__btn` 的文字色（`#323233`），与左侧返回箭头同一套配色（与 `TabBarIcon.vue` 同一做法）。</p>
 *
 * <p>图形包围盒是 1024×921.6，几乎铺满整张画布，所以 viewBox 沿用文件自带的
 * `0 0 1024 1024` 即可，不用像「收藏」那样另收紧一个。</p>
 */
const SHARE_ICON = {
  viewBox: '0 0 1024 1024',
  d: 'M999.72532 455.7L590.25032 71.95c-14.525-13.6-33.4-20.75-52.551-20.75-10.299 0-20.674 2.1-30.5 6.35C479.10032 69.7 460.92532 97.4 460.92532 128v172.05c-113.2 13.8-208.675 59-284.575 135C1.67532 609.899-0.02468 884.5 0.00032 896.1c0.05 38.351 28.375 70.8 66.375 76 3.525 0.5 7.025 0.7 10.475 0.7 33.975 0 64.5-22.55 73.9-56.05 1.925-6.05 53.25-159 310.175-184.9V896c0 30.649 18.2 58.3 46.275 70.5a76.726 76.726 0 0 0 30.5 6.3c19.176 0 38.051-7.15 52.576-20.8L999.75032 567.75c15.475-14.5 24.25-34.8 24.25-56 0-21.25-8.801-41.5-24.275-56.05z m-462 440.3V651.35C145.42532 651.35 76.80032 896 76.80032 896s-0.6-523.7 460.925-523.7V128L947.20032 511.75 537.72532 896z',
}

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
const activeSection = ref<'gallery' | 'params' | 'detail'>('gallery')
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
const paramsRef = ref<HTMLElement | null>(null)
const descRef = ref<HTMLElement | null>(null)
/** 主图轮播实例：程序化切图只能走它的 swipeTo（见 goToImage） */
const swipeRef = ref<SwipeInstance | null>(null)

/**
 * 主图区展示的图片列表。
 *
 * <p>以图集表为准，为空时退回主图；各规格自己的图（{@code skuImage}）追加在末尾并去重。
 * 列表对同一件商品是**恒定**的 —— 切款式只改「当前第几张」，不改列表本身，
 * 否则缩略图会跟着整体重排（规则与理由见 {@code utils/product-gallery.ts}）。</p>
 */
const gallery = computed<string[]>(() =>
  buildGallery(
    (detail.value?.images ?? []).map((image) => image.url),
    detail.value?.mainImage,
    (detail.value?.skus ?? []).map((sku) => sku.skuImage),
  ),
)

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

/**
 * 商品详情富文本。
 *
 * <p>{@code product.detail} 存的是 HTML（图文详情：卖点、参数表、插图），
 * 必须过白名单清洗后才能交给 {@code v-html} —— 详见 {@code utils/rich-text.ts}。</p>
 */
const detailHtml = computed(() => sanitizeRichText(detail.value?.detail))

/**
 * 「商品参数」表的数据。
 *
 * <p>只取详情接口里**已经有的**字段（编号、分类、规格、价格区间、销量），不额外造数据：
 * 参数表一旦出现后端不存在的字段，改库存/改价格时它就会和真实值对不上。
 * 规格名取第一个非空的 {@code specName}，值按 SKU 顺序去重拼接。</p>
 */
const paramRows = computed<{ label: string; value: string }[]>(() => {
  const data = detail.value
  if (!data) return []
  const rows: { label: string; value: string }[] = [{ label: '商品编号', value: String(data.id) }]
  if (data.categoryName) rows.push({ label: '商品分类', value: data.categoryName })

  const specName = data.skus.find((sku) => sku.specName)?.specName
  const specValues = [...new Set(data.skus.map((sku) => sku.specValue).filter(Boolean))]
  if (specName && specValues.length) rows.push({ label: specName, value: specValues.join(' / ') })

  const prices = data.skus.map((sku) => sku.price).filter((price) => price > 0)
  if (prices.length > 1) {
    const min = Math.min(...prices)
    const max = Math.max(...prices)
    if (min !== max) rows.push({ label: '价格区间', value: `¥${formatPrice(min)} ~ ¥${formatPrice(max)}` })
  }

  rows.push({ label: '累计销量', value: `${formatCount(data.sales)} 件` })
  return rows
})

/**
 * 款式卡片上的缩略图。
 *
 * <p>优先该规格自己的图（{@code skuImage}），没有就退回商品主图 —— 尺码类规格
 * （鞋服）本来就没有独立外观，为每个尺码配一张不同的图反而会误导「换个尺码换个样子」。</p>
 */
function skuThumb(sku: ProductSku): string {
  return sku.skuImage || detail.value?.mainImage || ''
}

/** 款式名：规格值可能是空串（后台建的无规格 SKU），这时显示「默认款」而不是一片空白 */
function skuLabel(sku: ProductSku): string {
  return sku.specValue?.trim() || '默认款'
}

/**
 * 切到图集第 {@code index} 张：缩略图高亮与主图一起走。
 *
 * <p>★★ <b>Vant 4 的 {@code van-swipe} 在「active」这件事上双向都不通</b>，两端都要自己接：</p>
 * <ul>
 *   <li><b>父 → 子</b>：轮播只在自己的 {@code state.active} 上维护位置，watch 列表里只有
 *       {@code initialSwipe} 和「子项数量」，**没有 {@code active} 这个 prop**
 *       （见 {@code vant/es/swipe/Swipe.mjs} 末尾）。所以光改 {@code activeImage}，
 *       上面的大图纹丝不动 —— 用户看到的就是「点款式后缩略图高亮了、页码也变了，大图不动」。</li>
 *   <li><b>子 → 父</b>：它 emit 的是 {@code change}，**不是 {@code update:active}**，
 *       所以 {@code v-model:active} 也不会把下标抛回来 —— 手势滑到第 2 张时页码仍停在 1/N。</li>
 * </ul>
 *
 * <p>因此：程序化切图调 {@code swipeTo}（{@code immediate} 免动画），手势滑动靠
 * {@link onSwipeChange} 接 {@code change} 事件。两条路都落到同一个 {@code activeImage}。</p>
 */
function goToImage(index: number) {
  if (index < 0 || index >= gallery.value.length) return
  activeImage.value = index
  swipeRef.value?.swipeTo(index, { immediate: true })
}

/** 手势滑动后同步页码与缩略图高亮（{@code swipeTo} 也会触发它，值相同，不会循环） */
function onSwipeChange(index: number) {
  activeImage.value = index
}

/** 点击主图放大预览（Vant 的图片预览，支持双指缩放） */
function previewImage(index: number) {
  if (!gallery.value.length) return
  showImagePreview({ images: gallery.value, startPosition: index, closeable: true })
}

/**
 * 图文详情里的插图点击放大。
 *
 * <p>用事件委托而不是给每个 {@code <img>} 绑 click：内容是 {@code v-html} 注入的，
 * 拿不到组件实例，逐个绑定只能等 DOM 渲染完再遍历。委托挂在容器上，重新渲染也不会丢。</p>
 */
function previewRichImage(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (target.tagName !== 'IMG') return
  const container = event.currentTarget as HTMLElement
  const images = Array.from(container.querySelectorAll('img')).map((img) => img.src)
  const index = images.indexOf((target as HTMLImageElement).src)
  if (!images.length) return
  showImagePreview({ images, startPosition: Math.max(0, index), closeable: true })
}

// 换规格后库存跟着变，把数量夹回合法区间，避免出现「选了 3 件但只剩 1 件」
watch(selectedSku, () => {
  if (quantity.value > maxQuantity.value) quantity.value = maxQuantity.value
  if (quantity.value < 1) quantity.value = 1
})

onMounted(load)

async function load() {
  const productId = Number(route.params.id)
  try {
    const data = await fetchProductDetail(productId)
    detail.value = data
    // 默认选中第一个有货规格；全部无货时退回首个，保证价格区仍有值可展示
    selectedSku.value = data.skus.find((sku) => sku.stock > 0) ?? data.skus[0] ?? null
    // 详情拿到之后才记浏览：商品不存在（走 catch）时不该留痕
    reportView(productId)
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
 * 记录浏览（「我的 → 浏览记录」的数据来源）。
 *
 * ★ 未登录**不上报**：该接口需要登录，未登录发请求只会拿到 401，而拦截器在 401 时
 *   会清空本地令牌并提示「登录已过期」—— 对一个只是路过看商品的游客是纯误伤。
 * ★ 不 await：埋点是旁路，不能拖慢首屏。失败静默（api 层已带 `silent` 标记不弹提示），
 *   这里再 catch 一次兜底，避免未处理的 rejection 进控制台。
 * ★ 同一商品来回进出会重复上报，由后端唯一键 upsert 收敛为一条，前端不做防抖抑制。
 */
function reportView(productId: number) {
  if (!userStore.isLoggedIn) return
  reportViewHistory(productId).catch(() => {})
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
  nextTick(() => goToImage(gallery.value.indexOf(sku.skuImage as string)))
}

/** 顶部快捷行：滚动到对应区块 */
function scrollToSection(section: 'gallery' | 'params' | 'detail') {
  activeSection.value = section
  const target = { gallery: galleryRef, params: paramsRef, detail: descRef }[section]
  target.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
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
          <svg
            class="nav__share-icon"
            :viewBox="SHARE_ICON.viewBox"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
            focusable="false"
          >
            <path :d="SHARE_ICON.d" fill="currentColor" />
          </svg>
        </button>
      </div>
    </div>

    <van-loading v-if="loading" class="detail__loading" />

    <template v-else-if="detail">
      <!-- 主图 / 图集：点图放大，下方缩略图可直接切换 -->
      <div ref="galleryRef" class="gallery">
        <van-swipe
          v-if="gallery.length"
          ref="swipeRef"
          :active="activeImage"
          class="gallery__swipe"
          :show-indicators="gallery.length > 1"
          @change="onSwipeChange"
        >
          <van-swipe-item v-for="(url, index) in gallery" :key="index">
            <img :src="url" :alt="detail.name" @click="previewImage(index)" />
          </van-swipe-item>
        </van-swipe>
        <div v-else class="gallery__empty">暂无图片</div>

        <!-- 缩略图条：图集多张时才有意义 -->
        <div v-if="gallery.length > 1" class="thumbs">
          <button
            v-for="(url, index) in gallery"
            :key="`thumb-${index}`"
            class="thumbs__item"
            :class="{ 'is-active': activeImage === index }"
            type="button"
            :aria-label="`第 ${index + 1} 张图`"
            @click="goToImage(index)"
          >
            <img :src="url" alt="" />
          </button>
        </div>
      </div>

      <!-- 快捷行：图集页码 / 款式数 / 参数 / 详情 -->
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
          :class="{ 'is-active': activeSection === 'params' }"
          type="button"
          @click="scrollToSection('params')"
        >
          参数
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

      <!-- 款式横向卡片：直接点选规格，卡片上带款式图 -->
      <div v-if="skuCount" class="sku-cards">
        <button
          v-for="sku in detail.skus"
          :key="sku.id"
          class="sku-card"
          :class="{ 'is-active': selectedSku?.id === sku.id, 'is-disabled': sku.stock <= 0 }"
          type="button"
          @click="selectSku(sku)"
        >
          <span class="sku-card__thumb">
            <img v-if="skuThumb(sku)" :src="skuThumb(sku)" alt="" />
            <van-icon v-else name="photo-o" size="18" />
          </span>
          <span class="sku-card__value">{{ skuLabel(sku) }}</span>
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

      <!-- 服务承诺：纯展示，不参与下单逻辑 -->
      <div class="services">
        <span class="services__item"><van-icon name="passed" size="13" />正品保障</span>
        <span class="services__item"><van-icon name="logistics" size="13" />极速发货</span>
        <span class="services__item"><van-icon name="after-sale" size="13" />7 天无理由退换</span>
        <span class="services__item"><van-icon name="coupon-o" size="13" />全场免运费</span>
      </div>

      <!-- 商品参数：只展示详情接口里已有的字段 -->
      <div ref="paramsRef" class="section section--params">
        <div class="section__title">商品参数</div>
        <dl class="params">
          <div v-for="row in paramRows" :key="row.label" class="params__row">
            <dt class="params__label">{{ row.label }}</dt>
            <dd class="params__value">{{ row.value }}</dd>
          </div>
        </dl>
      </div>

      <!-- 商品详情：图文详情走富文本（已过白名单清洗） -->
      <div ref="descRef" class="section">
        <div class="section__title">商品详情</div>
        <div v-if="detailHtml" class="section__rich" v-html="detailHtml" @click="previewRichImage" />
        <!-- 兜底：详情没写图文时，至少把副标题与图集铺出来，不留一句「暂无详情内容」 -->
        <div v-else class="section__fallback">
          <p v-if="detail.subtitle" class="section__text">{{ detail.subtitle }}</p>
          <p v-else class="section__text section__text--empty">商家暂未填写图文详情</p>
          <img v-for="(url, index) in gallery" :key="`desc-${index}`" :src="url" :alt="detail.name" />
        </div>
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
                {{ sku.specName ? `${sku.specName} ` : '' }}{{ skuLabel(sku) }}
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
/* 「分享」图标由内联 SVG 承担，尺寸对齐原来的 van-icon（20px）；
   display: block 不能省 —— 内联元素会在基线处留出缝隙 */
.nav__share-icon {
  display: block;
  width: 20px;
  height: 20px;
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
  /* 图集可点开大图，光标给个提示 */
  cursor: zoom-in;
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

/* ── 图集缩略图条 ── */
.thumbs {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.thumbs::-webkit-scrollbar {
  display: none;
}
.thumbs__item {
  flex: 0 0 auto;
  width: 56px;
  height: 56px;
  padding: 0;
  overflow: hidden;
  border: 2px solid transparent;
  border-radius: 6px;
  background: #f7f8fa;
  cursor: pointer;
}
.thumbs__item.is-active {
  border-color: #ee0a24;
}
.thumbs__item img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
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
  /* ★ 必须给**确定宽度**而不是 min-width：卡片里有个 width:100% 的方图，
     宽度不定时「图撑卡片、卡片撑图」互为参照，浏览器会退化成按图片原始尺寸撑开
     （900px 的图直接把卡片拉成整屏宽）。 */
  width: 104px;
  padding: 8px 10px;
  border: 1px solid #ebedf0;
  border-radius: 6px;
  background: #fafafa;
  text-align: left;
  cursor: pointer;
}
/* 款式图：铺满卡片宽度，方图裁切；有图/无图两种卡片高度一致，横向滚动时不会参差 */
.sku-card__thumb {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 1;
  margin-bottom: 4px;
  overflow: hidden;
  border-radius: 4px;
  background: #f2f3f5;
  color: #c8c9cc;
}
.sku-card__thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
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
/* ── 服务承诺 ── */
.services {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  padding: 0 12px 12px;
  font-size: 12px;
  color: #969799;
}
.services__item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.services__item .van-icon {
  color: #ee0a24;
}

/* ── 商品参数 ── */
.params {
  margin: 0;
  overflow: hidden;
  border: 1px solid #f2f3f5;
  border-radius: 6px;
}
.params__row {
  display: flex;
  gap: 12px;
  padding: 9px 12px;
  font-size: 13px;
  line-height: 1.5;
}
.params__row:nth-child(odd) {
  background: #fafafa;
}
.params__label {
  flex: 0 0 76px;
  color: #969799;
}
.params__value {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: #323233;
  word-break: break-all;
}

/* ── 商品详情（富文本） ──
   内容是 v-html 注入的，样式只能靠 :deep 下钻；标签范围由 rich-text.ts 的白名单限定 */
.section__rich {
  font-size: 14px;
  line-height: 1.7;
  color: #646566;
}
.section__rich :deep(h3) {
  margin: 16px 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}
.section__rich :deep(h3:first-child) {
  margin-top: 0;
}
.section__rich :deep(p) {
  margin: 0 0 10px;
}
.section__rich :deep(ul),
.section__rich :deep(ol) {
  margin: 0 0 10px;
  padding-left: 20px;
}
/* Vant 的基础样式把列表符号清掉了，富文本里的要点必须显式要回来，
   否则「产品亮点」会变成几行没有圆点的缩进文字 */
.section__rich :deep(ul) {
  list-style: disc outside;
}
.section__rich :deep(ol) {
  list-style: decimal outside;
}
.section__rich :deep(li) {
  display: list-item;
  margin-bottom: 4px;
}
.section__rich :deep(img) {
  display: block;
  width: 100%;
  margin: 12px 0;
  border-radius: 8px;
  cursor: zoom-in;
}
.section__rich :deep(table) {
  width: 100%;
  margin: 0 0 12px;
  border-collapse: collapse;
  font-size: 13px;
}
.section__rich :deep(th),
.section__rich :deep(td) {
  padding: 8px 10px;
  border: 1px solid #f2f3f5;
  text-align: left;
  vertical-align: top;
}
.section__rich :deep(th) {
  width: 76px;
  background: #fafafa;
  font-weight: 400;
  color: #969799;
}
.section__rich :deep(td) {
  color: #323233;
}
/* 兜底详情：没有图文时铺副标题 + 图集 */
.section__fallback img {
  display: block;
  width: 100%;
  margin-top: 10px;
  border-radius: 8px;
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
