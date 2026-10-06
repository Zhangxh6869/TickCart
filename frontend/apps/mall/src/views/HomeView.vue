<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { Category, ProductListItem } from '@tickcart/shared-types'
import { fetchCategories, fetchProducts } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()
const categories = ref<Category[]>([])
const products = ref<ProductListItem[]>([])
const loading = ref(true)
/** 当前选中的分类；null 表示「全部」（首页默认，展示热销榜） */
const activeCategoryId = ref<number | null>(null)

/** 分类栏数据：首项固定为「全部」，其余为接口返回的启用分类 */
const categoryTabs = computed<{ id: number | null; name: string }[]>(() => [
  { id: null, name: '全部' },
  ...categories.value.map((cat) => ({ id: cat.id as number | null, name: cat.name })),
])

/** 商品区标题：全部时是热销榜，选中分类时是分类名 */
const productTitle = computed(() => {
  if (activeCategoryId.value === null) return '热销商品'
  return categories.value.find((cat) => cat.id === activeCategoryId.value)?.name ?? '热销商品'
})

// 请求序号：连点分类时丢弃过期响应，避免先发的慢请求覆盖后发的快请求
let requestSeq = 0

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

/** 切换分类：原地刷新下方商品，不跳转页面。 */
function selectCategory(id: number | null) {
  if (activeCategoryId.value === id) return
  activeCategoryId.value = id
  loadProducts()
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

    <!-- 分类栏：搜索栏下方一行，横向可滑动，点击后原地切换下方商品 -->
    <div class="cat-bar">
      <div
        v-for="tab in categoryTabs"
        :key="tab.id ?? 'all'"
        class="cat-bar__item"
        :class="{ 'is-active': activeCategoryId === tab.id }"
        @click="selectCategory(tab.id)"
      >
        <span class="cat-bar__name">{{ tab.name }}</span>
        <span class="cat-bar__bar" />
      </div>
    </div>

    <div class="section">
      <div class="section__header">
        <span class="section__title">{{ productTitle }}</span>
        <span class="section__more" @click="goList">更多 ›</span>
      </div>
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
  gap: 20px;
  /* 按首行基线对齐：选中项放大后，其余分类名仍与其处在同一水平线上 */
  align-items: baseline;
  /* 与搜索框（margin 0 12px）左对齐，右侧留白靠 overflow 自然延伸 */
  padding: 12px 12px 0;
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
  cursor: pointer;
}
.cat-bar__name {
  font-size: 14px;
  font-weight: 500;
  color: #646566;
  white-space: nowrap;
  /* 0.15s 的加速过渡：点中后名称迅速放大变深 */
  transition: font-size 0.15s ease-out, color 0.15s ease-out;
}
/* 选中态：名称放大、加粗、颜色加深 */
.cat-bar__item.is-active .cat-bar__name {
  font-size: 18px;
  font-weight: 700;
  color: #323233;
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
.section__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.section__title {
  font-size: 16px;
  font-weight: 600;
}
.section__more {
  font-size: 13px;
  color: #969799;
}
/* 首页热销商品：一行 5 个的小卡片网格（仅首页，商品列表页不受影响） */
.product-grid {
  display: grid;
  /* minmax(0, 1fr) 而非 1fr：否则列宽会被卡片内文字的最小内容宽度撑开，导致 5 列溢出 */
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
}
/* 商品图：竖版长方形（宽:高 = 3:4） */
.product-grid :deep(.product-card__image) {
  aspect-ratio: 3 / 4;
}
.product-grid :deep(.product-card) {
  border-radius: 4px;
}
/* 移动端优先：窄屏下每张卡仅约 1/5 屏宽，压缩内边距与字号并隐藏次要信息，避免文字溢出 */
.product-grid :deep(.product-card__body) {
  padding: 4px 2px;
}
.product-grid :deep(.product-card__name) {
  font-size: 11px;
}
.product-grid :deep(.product-card__subtitle),
.product-grid :deep(.product-card__sales) {
  display: none;
}
.product-grid :deep(.product-card__footer) {
  justify-content: center;
  margin-top: 4px;
}
.product-grid :deep(.product-card__price) {
  font-size: 11px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 宽屏（PC）下卡片随之变宽，恢复完整信息与常规字号 */
@media (min-width: 600px) {
  .product-grid :deep(.product-card__body) {
    padding: 8px;
  }
  .product-grid :deep(.product-card__name) {
    font-size: 14px;
  }
  .product-grid :deep(.product-card__subtitle) {
    display: block;
  }
  .product-grid :deep(.product-card__sales) {
    display: inline;
  }
  .product-grid :deep(.product-card__footer) {
    justify-content: space-between;
    margin-top: 6px;
  }
  .product-grid :deep(.product-card__price) {
    font-size: 16px;
  }
}
.loading {
  padding: 20px;
  text-align: center;
}
</style>
