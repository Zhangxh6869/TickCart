<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { Category, ProductListItem } from '@tickcart/shared-types'
import { fetchCategories, fetchProducts } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'

const route = useRoute()
const categories = ref<Category[]>([])
const products = ref<ProductListItem[]>([])
const loading = ref(false)
const finished = ref(false)
const page = ref(1)
const keyword = ref('')
const categoryId = ref<number | null>(null)
const sortBy = ref('createdAt')
const order = ref('desc')
/**
 * 分类与 query 是否就绪。★ 它同时是列表区的渲染开关（模板上 `v-if="ready"`），
 * 不只是「允许加载」的标志位 —— 原因见 load() 上方的注释。
 */
const ready = ref(false)

const sortOptions = [
  { text: '综合', value: 'createdAt', order: 'desc' },
  { text: '销量', value: 'sales', order: 'desc' },
  { text: '价格↓', value: 'price', order: 'desc' },
  { text: '价格↑', value: 'price', order: 'asc' },
]

// van-tabs 用 0 表示"全部"，与内部 categoryId(null 表示不限) 互转
const activeCategory = computed<number>({
  get: () => categoryId.value ?? 0,
  set: (val) => {
    categoryId.value = val === 0 ? null : val
  },
})

onMounted(async () => {
  categories.value = await fetchCategories()
  categoryId.value = route.query.categoryId ? Number(route.query.categoryId) : null
  ready.value = true
  await reload()
})

// 监听路由 query（从首页分类点进来）
watch(
  () => route.query.categoryId,
  (val) => {
    if (!ready.value) return
    categoryId.value = val ? Number(val) : null
    reload()
  }
)

/**
 * 分页请求的并发锁。
 *
 * ★ 为什么不能直接用 `loading` 当锁：`loading` 是 van-list 的 `v-model`，
 * van-list 触底时是**先** `loading.value = true; emit('update:loading', true)`，
 * **再** `emit('load')`。所以进入 load() 时 `loading` 已经是 true —— 拿它当守卫
 * 等于把自己刚触发的那次请求挡在门外，`loading` 再也不会回到 false，
 * 页面就永久停在「加载中...」。这是本页原先加载不出商品的第二个成因。
 *
 * 用独立布尔量做锁，`loading` 只负责给 van-list 表达「正在加载」。
 */
let requesting = false

/** 请求代号：切分类/换排序后自增，用来丢弃过期响应，避免慢请求覆盖新结果 */
let generation = 0

/**
 * 拉取下一页。首屏由 onMounted 主动调用 reload()，之后由 van-list 触底触发。
 *
 * ★ 这里**不能**再写 `if (!ready.value) return`（曾经的写法，是第一个成因）。
 * van-list 挂载时 `onMounted → check()` 就会先置 loading 再 emit('load')，
 * 若此时 ready 还是 false 就直接 return，loading 会永久停在 true ——
 * van-list 的 check() 因 `loading === true` 永远早退、不再 emit，
 * 而 onMounted 里紧随其后的 reload() 也会被同一个 loading 拦住。两边互相等。
 *
 * 正确做法是把「就绪」做成**挂载时机**的问题：模板上给列表区加 `v-if="ready"`，
 * 让 van-list 在分类就绪后才挂载（挂载时 ready 已为 true），这里无需再判 ready。
 */
async function load() {
  if (requesting || finished.value) return
  requesting = true
  const gen = generation
  loading.value = true
  try {
    const res = await fetchProducts({
      page: page.value,
      size: 10,
      keyword: keyword.value || undefined,
      categoryId: categoryId.value ?? undefined,
      sortBy: sortBy.value,
      order: order.value,
    })
    if (gen !== generation) return // 已被更新的查询取代，丢弃这批结果
    products.value.push(...res.records)
    finished.value = page.value >= res.pages
    page.value += 1
  } finally {
    if (gen === generation) {
      loading.value = false
      requesting = false
    }
  }
}

async function reload() {
  // 作废进行中的请求，并放开锁，让下面这次加载不被旧请求挡住
  generation += 1
  requesting = false
  page.value = 1
  products.value = []
  finished.value = false
  await load()
}

function onSearch() {
  reload()
}

function selectSort(opt: (typeof sortOptions)[number]) {
  sortBy.value = opt.value
  order.value = opt.order
  reload()
}
</script>

<template>
  <div class="list">
    <van-search v-model="keyword" shape="round" placeholder="搜索商品" @search="onSearch" />

    <van-tabs v-model:active="activeCategory" @change="reload">
      <van-tab title="全部" :name="0" />
      <van-tab v-for="cat in categories" :key="cat.id" :title="cat.name" :name="cat.id" />
    </van-tabs>

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

    <!-- ★ v-if="ready" 不是「懒渲染」，而是让 van-list 在分类/query 就绪后才挂载，
         避免它在 ready 之前触发首次 @load 造成死锁（详见 load() 的注释）。
         分类栏与搜索框留在外面，首屏不会因为等接口而整页空白。 -->
    <template v-if="ready">
      <div class="product-grid">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </div>

      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="load" />
      <van-empty v-if="!loading && products.length === 0" description="暂无商品" />
    </template>
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
