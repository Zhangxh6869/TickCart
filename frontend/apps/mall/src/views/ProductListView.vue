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
// 首屏由 van-list 自动触发 @load，需等分类与 query 就绪后再允许加载
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

async function reload() {
  page.value = 1
  products.value = []
  finished.value = false
  await load()
}

async function load() {
  if (!ready.value || loading.value || finished.value) return
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
    products.value.push(...res.records)
    finished.value = page.value >= res.pages
    page.value += 1
  } finally {
    loading.value = false
  }
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

    <div class="product-grid">
      <ProductCard v-for="p in products" :key="p.id" :product="p" />
    </div>

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="load" />
    <van-empty v-if="!loading && products.length === 0" description="暂无商品" />
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
