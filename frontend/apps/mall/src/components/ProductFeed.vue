<script setup lang="ts">
/**
 * 商品列表「排序栏 + 网格 + 触底分页」公共组件。
 *
 * 由 `/products`（分类列表页）与 `/search`（搜索页）共用：
 * 排序口径、分页时序、竞态处理只有一份实现，避免两边各写一套再各自踩坑。
 *
 * 数据源固定为公开接口 `GET /api/products`（仅返回上架商品），查询条件全部走 props，
 * 父组件只需改 props，组件内部 `watch` 到变化后自动重查并回到第一页。
 */
import { onMounted, ref, watch } from 'vue'
import type { ProductListItem } from '@tickcart/shared-types'
import { fetchProducts } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'

const props = withDefaults(
  defineProps<{
    /** 关键词；空串表示不限 */
    keyword?: string
    /** 分类 ID；null 表示不限 */
    categoryId?: number | null
    /** 每页条数 */
    size?: number
    /** 空结果文案 */
    emptyText?: string
  }>(),
  {
    keyword: '',
    categoryId: null,
    size: 10,
    emptyText: '暂无商品',
  }
)

/**
 * 结果总数（双向绑定给父组件，用于「为你找到 N 件商品」这类文案）。
 * 父组件不绑定时就是一个普通本地 ref，不影响使用。
 */
const total = defineModel<number>('total', { default: 0 })

/** 排序口径与 `/products` 原有行为保持一致 */
const sortOptions = [
  { text: '综合', value: 'createdAt', order: 'desc' },
  { text: '销量', value: 'sales', order: 'desc' },
  { text: '价格↓', value: 'price', order: 'desc' },
  { text: '价格↑', value: 'price', order: 'asc' },
]

const products = ref<ProductListItem[]>([])
/** 交给 van-list 的 v-model，只表达「正在加载」，**不用它当并发锁**（原因见 load()） */
const loading = ref(false)
const finished = ref(false)
const page = ref(1)
const sortBy = ref('createdAt')
const order = ref('desc')

/**
 * 分页请求的并发锁。
 *
 * ★ 为什么不能直接用 `loading` 当锁：`loading` 是 van-list 的 `v-model`，
 * van-list 触底时是**先** `loading.value = true; emit('update:loading', true)`，
 * **再** `emit('load')`。所以进入 load() 时 `loading` 已经是 true —— 拿它当守卫
 * 等于把自己刚触发的那次请求挡在门外，`loading` 再也不会回到 false，
 * 列表就永久停在「加载中...」。（`/products` 原先加载不出商品，一半原因在此。）
 */
let requesting = false

/** 请求代号：换查询条件时自增，用来丢弃过期响应，避免慢请求覆盖新结果 */
let generation = 0

async function load() {
  if (requesting || finished.value) return
  requesting = true
  const gen = generation
  loading.value = true
  try {
    const res = await fetchProducts({
      page: page.value,
      size: props.size,
      keyword: props.keyword || undefined,
      categoryId: props.categoryId ?? undefined,
      sortBy: sortBy.value,
      order: order.value,
    })
    if (gen !== generation) return // 已被更新的查询取代，丢弃这批结果
    products.value.push(...res.records)
    total.value = res.total
    finished.value = page.value >= res.pages
    page.value += 1
  } finally {
    if (gen === generation) {
      loading.value = false
      requesting = false
    }
  }
}

/** 回到第一页重新查询（换关键词 / 换分类 / 换排序都走这里） */
async function reload() {
  generation += 1
  requesting = false // 放开锁，否则会被上一个未完成的请求挡住
  page.value = 1
  products.value = []
  total.value = 0
  finished.value = false
  await load()
}

function selectSort(opt: (typeof sortOptions)[number]) {
  if (sortBy.value === opt.value && order.value === opt.order) return
  sortBy.value = opt.value
  order.value = opt.order
  reload()
}

// 查询条件变化即重查。父组件只管改 props，不用自己调 reload()。
watch(() => [props.keyword, props.categoryId], reload)

/**
 * 首屏由组件自己拉起，并把 van-list 的 `immediate-check` 关掉。
 *
 * ★ 别改回 `immediate-check`（默认 true）：那样 van-list 会在挂载时自己先 check 一次，
 * 与我们这次 onMounted 的加载**并发**，两者谁先谁后取决于微任务顺序，时序不确定。
 * 关掉之后触发路径只有两条，都是确定的：
 *   ① 本次 onMounted 的 reload() 拉第一页；
 *   ② 之后 `loading` 由 true 回到 false 会触发 van-list 内部的
 *      `watch(() => [props.loading, …], check)` —— 首屏不足一屏时自动续拉下一页，
 *      用户滚动到底时由 scroll 触发 check。
 */
onMounted(reload)
</script>

<template>
  <div class="feed">
    <div class="sort-bar">
      <span
        v-for="opt in sortOptions"
        :key="opt.text"
        class="sort-bar__item"
        :class="{ active: sortBy === opt.value && order === opt.order }"
        @click="selectSort(opt)"
      >
        {{ opt.text }}
      </span>
    </div>

    <!-- 排序栏与商品网格之间的信息行（如「为你找到 N 件商品」）。放在组件内部，
         是为了让这一行的纵向位置在所有使用方完全一致，不用各自写一遍外边距。 -->
    <slot name="summary" />

    <div class="product-grid">
      <ProductCard v-for="p in products" :key="p.id" :product="p" />
    </div>

    <van-list
      v-model:loading="loading"
      :finished="finished"
      :immediate-check="false"
      finished-text="没有更多了"
      @load="load"
    />
    <van-empty v-if="!loading && products.length === 0" :description="emptyText">
      <slot name="empty" />
    </van-empty>
  </div>
</template>

<style scoped>
.sort-bar {
  display: flex;
  gap: 16px;
  padding: 10px 12px;
  background: #fff;
  font-size: 14px;
}
.sort-bar__item {
  color: #646566;
}
.sort-bar__item.active {
  color: #ee0a24;
  font-weight: 600;
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  padding: 12px;
}
</style>
