<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { Category, ProductListItem } from '@tickcart/shared-types'
import { fetchCategories, fetchProducts } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()
const categories = ref<Category[]>([])
const products = ref<ProductListItem[]>([])
const loading = ref(true)
/** 分类栏元素，用于把选中的分类滚到屏幕中间 */
const catBarRef = ref<HTMLElement | null>(null)
/** 当前选中的分类；null 表示「全部」（首页默认，展示热销榜） */
const activeCategoryId = ref<number | null>(null)

/** 分类栏数据：首项固定为「全部」（渲染时钉在分类栏最左侧，不随其余分类横向滑动） */
const categoryTabs = computed<{ id: number | null; name: string }[]>(() => [
  { id: null, name: '全部' },
  ...categories.value.map((cat) => ({ id: cat.id as number | null, name: cat.name })),
])

// 请求序号：连点分类时丢弃过期响应，避免先发的慢请求覆盖后发的快请求
let requestSeq = 0
// 居中滚动序号：连点分类时丢弃过期的滚动，避免两次滚动互相打架
let centerSeq = 0

/** 把当前选中的分类横向滚到分类栏可视区的正中间。 */
function scrollActiveToCenter(token: number) {
  const bar = catBarRef.value
  if (!bar || token !== centerSeq) return
  // 「全部」钉在最左侧、无法居中，此时直接回到起点
  if (activeCategoryId.value === null) {
    bar.scrollTo({ left: 0, behavior: 'smooth' })
    return
  }
  const item = bar.querySelector<HTMLElement>('.cat-bar__item.is-active')
  if (!item) return
  const barRect = bar.getBoundingClientRect()
  const itemRect = item.getBoundingClientRect()
  // 选中项中心当前相对可视区左缘的偏移，换算成让它落在可视区中点所需的 scrollLeft
  const centerOffset = itemRect.left + itemRect.width / 2 - barRect.left
  const left = Math.max(0, bar.scrollLeft + centerOffset - bar.clientWidth / 2)
  bar.scrollTo({ left, behavior: 'smooth' })
}

/** 选中项字号有 0.15s 过渡，宽度在过渡中还会变，所以等过渡结束再校正一次。 */
async function centerActiveCategory() {
  const token = ++centerSeq
  await nextTick()
  scrollActiveToCenter(token)
  window.setTimeout(() => scrollActiveToCenter(token), 200)
}

/** 按当前选中的分类拉取首页商品（固定 8 条，按销量倒序）。 */
async function loadProducts() {
  const seq = ++requestSeq
  loading.value = true
  try {
    const page = await fetchProducts({
      page: 1,
      size: 8,
      categoryId: activeCategoryId.value ?? undefined,
      sortBy: 'sales',
      order: 'desc',
    })
    if (seq !== requestSeq) return
    products.value = page.records
  } catch {
    if (seq !== requestSeq) return
    products.value = []
    showToast('商品加载失败')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

/** 切换分类：原地刷新下方商品、不跳页，并把选中的分类滚到屏幕中间。 */
function selectCategory(id: number | null) {
  if (activeCategoryId.value !== id) {
    activeCategoryId.value = id
    loadProducts()
  }
  centerActiveCategory()
}

/** 跳商品列表页，带上当前分类，保证列表页与首页看到的是同一批商品。 */
function goList() {
  router.push({
    name: 'product-list',
    query: activeCategoryId.value === null ? {} : { categoryId: activeCategoryId.value },
  })
}

onMounted(async () => {
  try {
    categories.value = await fetchCategories()
  } catch {
    showToast('分类加载失败')
  }
  await loadProducts()
})
</script>

<template>
  <div class="home">
    <van-search
      placeholder="搜索商品"
      readonly
      show-action
      @click="goList"
    >
      <template #left-icon>
        <svg
          class="search-icon"
          viewBox="0 0 1024 1024"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden="true"
        >
          <path
            d="M932.571429 493.714286C932.571429 261.485714 744.228571 73.142857 512 73.142857S91.428571 261.485714 91.428571 493.714286s188.342857 420.571429 420.571429 420.571428c89.6 0 171.885714-27.428571 239.542857-74.971428l111.542857 111.542857 51.2-51.2-106.057143-106.057143c76.8-76.8 124.342857-182.857143 124.342858-299.885714zM164.571429 493.714286C164.571429 301.714286 320 146.285714 512 146.285714s347.428571 155.428571 347.428571 347.428572-155.428571 347.428571-347.428571 347.428571S164.571429 685.714286 164.571429 493.714286z"
            fill="currentColor"
          />
        </svg>
      </template>
      <template #action>
        <button class="search-btn" type="button" @click.stop="goList">搜索</button>
      </template>
    </van-search>

    <!-- 分类栏：搜索栏下方一行；「全部」钉在左侧不动，其余分类横向滑动，点击后原地切换下方商品 -->
    <div ref="catBarRef" class="cat-bar">
      <div
        v-for="tab in categoryTabs"
        :key="tab.id ?? 'all'"
        class="cat-bar__item"
        :class="{
          'is-active': activeCategoryId === tab.id,
          'cat-bar__item--pinned': tab.id === null,
        }"
        @click="selectCategory(tab.id)"
      >
        <span class="cat-bar__name">{{ tab.name }}</span>
        <span class="cat-bar__bar" />
      </div>
    </div>

    <div class="section">
      <van-loading v-if="loading" class="loading" />
      <van-empty v-else-if="products.length === 0" description="暂无商品" />
      <div v-else class="product-grid">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.home {
  /* 品牌色，用于搜索框描边、搜索按钮底色与分类栏选中小横杆 */
  --color-brand: rgb(107, 163, 200);
  /* 搜索图标与「搜索商品」占位文字共用的颜色 */
  --color-search-icon: #515151;
  /* 用 padding 而非给搜索框加 margin-top：否则 margin 会塌陷穿透 .layout，凭空多出 12px 页面高度 */
  padding-top: 12px;
}
/* 搜索区：整个 van-search 作为圆角长方形容器（品牌绿描边），按钮嵌在容器内部 */
.home :deep(.van-search) {
  /* 图标与占位文字同色：直接复用 Vant 自己的两个颜色变量 */
  --van-search-left-icon-color: var(--color-search-icon);
  --van-field-placeholder-text-color: var(--color-search-icon);
  margin: 0 12px;
  padding: 2px; /* 内部元素与边框保持 2px 间距 */
  border: 2px solid var(--color-brand);
  border-radius: 6px;
  background: rgb(232, 240, 248); /* #E8F0F8，搜索框底色 */
}
/* 自定义搜索图标：尺寸对齐 Vant 默认图标（--van-field-icon-size = 18px），颜色走 currentColor */
.search-icon {
  display: block;
  width: var(--van-field-icon-size);
  height: var(--van-field-icon-size);
}
/* 输入区不再单独描边/填色，融入外层容器 */
.home :deep(.van-search__content) {
  border: none;
  background: transparent;
  /* 图标距搜索框左侧 10px = 外层 .van-search 的 2px + 这里的 8px */
  padding-left: 8px;
}
/* 图标与右侧「搜索商品」文字的间距同为 10px（Vant 默认是 --van-padding-base = 4px） */
.home :deep(.van-field__left-icon) {
  margin-right: 10px;
}
/* 去掉 Vant 默认的 0 8px 内边距，与边框的间距统一由外层 padding 控制 */
.home :deep(.van-search__action) {
  padding: 0;
}
.search-btn {
  height: var(--van-search-input-height); /* 34px，与输入区等高 */
  padding: 0 14px;
  border: none;
  border-radius: 4px; /* 容器圆角 6px - 内缩 2px */
  background: var(--color-brand);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.section {
  background: #fff;
  margin: 12px;
  border-radius: 8px;
  padding: 12px;
}
/* 分类栏：紧贴搜索栏下方，单行横向排列，超出部分横向滚动（隐藏滚动条） */
.cat-bar {
  display: flex;
  /* 项间距用 .cat-bar__item 的 margin-right 实现，不用 gap：
     钉住的「全部」需要把右侧那段间距一起画进自己的背景里（见下） */
  /* 按首行基线对齐：选中项放大后，其余分类名仍与其处在同一水平线上 */
  align-items: baseline;
  /* 高度写死 44px（16px 上内边距 + 28px 内容区）。
     内容区 28px = 名称行高 22px + 间距 3px + 小横杆 3px，本身已恒定：
     选中态放大走 transform（不占布局），名称盒高不随切换变化。
     这里再写死一次是为了兜底——将来若误把放大改回 font-size，
     行高（flex 取最高项）就会先降后升，下方商品区会跟着上下抖一下 */
  height: 44px;
  /* 左内边距交给钉住的「全部」项自己承担（见下），否则滚动时分类名会从它左侧那 12px 里漏出来。
     上内边距 16px 与 .cat-bar__name 的行高 22px 是一对：两者相加正好 44px 填满高度，
     并让名称基线与小横杆都停在改动前的位置（基线 y=87、横杆 95~98） */
  padding: 16px 12px 0 0;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.cat-bar::-webkit-scrollbar {
  display: none;
}
.cat-bar__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  /* 名称与小横杆的间距 */
  gap: 3px;
  /* 不被 flex 压缩，保证横向滚动而不是挤成一团 */
  flex: 0 0 auto;
  /* 项间距 */
  margin-right: 20px;
  cursor: pointer;
}
.cat-bar__item:last-child {
  margin-right: 0;
}
/* 「全部」钉在分类栏最左侧：横向滑动时原地不动，其余分类从它后面滑过 */
.cat-bar__item--pinned {
  position: sticky;
  left: 0;
  /* 左 12px：自身盒子贴住滚动区左边缘（x=0），文字被顶到 12px，与搜索框左对齐；
     右 16px：把「项间距」一并画进背景，滑到它后面的分类名从标签外侧才露出来 */
  padding: 0 16px 0 12px;
  margin-right: 0;
  /* 实底，与 App.vue 的页面底色同源：盖住滑到它后面的分类名 */
  background: var(--van-background);
  z-index: 1;
}
.cat-bar__name {
  font-size: 14px;
  font-weight: 500;
  color: #646566;
  white-space: nowrap;
  /* 行高写死 22px：名称盒高不随选中状态变化（放大走 transform，不占布局），
     小横杆的纵向位置才不会跟着动；22px 与容器的上内边距 16px 配套，合计 44px */
  line-height: 22px;
  /* 0.15s 的加速过渡：点中后名称迅速放大变深。
     放大走 transform 而不是 font-size —— transform 不参与布局，
     所以既没有横向回流，也不会让 flex 的基线锚点（由最高项决定）浮动 */
  transition: transform 0.15s ease-out, color 0.15s ease-out;
}
/* 选中态：名称放大、加粗、颜色加深。
   1.2857 = 18 / 14，即把 14px 等效放大到 18px（改字号要同步改这个比值）；
   translateY 抵消「绕盒中心缩放会把基线推低」的量 = (scale - 1) × 6px ≈ 1.72px，
   其中 6px 是本项目字体栈在 14px 下「基线到行盒中心」的距离，换字体需重新量；
   两项用同一条缓动同步插值，故每一帧都精确抵消 */
.cat-bar__item.is-active .cat-bar__name {
  font-weight: 700;
  color: #323233;
  transform: translateY(-1.72px) scale(1.2857);
}
/* 小横杆：仅选中项显示，未选中时保留占位（避免切换时整行高度跳动） */
.cat-bar__bar {
  width: 18px;
  height: 3px;
  border-radius: 2px;
  background: var(--color-brand);
  opacity: 0;
  transform: scaleX(0.2);
  transition: opacity 0.15s ease-out, transform 0.15s ease-out;
}
.cat-bar__item.is-active .cat-bar__bar {
  opacity: 1;
  transform: scaleX(1);
}
/* 首页商品网格：一行两个（与商品列表页一致）。
   两列后卡片宽度充足，直接沿用 ProductCard 的默认样式，不再为挤进一行而压缩字号/隐藏信息 */
.product-grid {
  display: grid;
  /* minmax(0, 1fr) 而非 1fr：否则列宽会被卡片内文字的最小内容宽度撑开而溢出 */
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.loading {
  padding: 20px;
  text-align: center;
}
</style>
