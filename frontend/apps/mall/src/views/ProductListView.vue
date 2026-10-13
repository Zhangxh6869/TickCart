<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { Category } from '@tickcart/shared-types'
import { fetchCategories } from '@/api/product'
import ProductFeed from '@/components/ProductFeed.vue'

const route = useRoute()
const categories = ref<Category[]>([])

/**
 * 输入框里的文字与「已提交的关键词」必须分开。
 * 直接给 ProductFeed 传 v-model 的输入值会导致**每敲一个字就重查一次**，
 * 所以输入走 inputText、回车/点搜索才提交到 keyword。
 */
const inputText = ref('')
const keyword = ref('')

const categoryId = ref<number | null>(null)

/**
 * 分类是否就绪。★ 它同时是 ProductFeed 的渲染开关（`v-if="ready"`）：
 * 组件挂载时会自己拉第一页，若在拿到 `route.query.categoryId` 之前就挂载，
 * 会先用「全部」发一次请求、再被 query 覆盖成第二次请求（多一次无用请求，
 * 且两次响应回来的先后不确定）。等就绪再挂载，只有一次请求。
 */
const ready = ref(false)

// van-tabs 用 0 表示「全部」，与内部 categoryId(null 表示不限) 互转
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
})

// 监听路由 query（从首页分类点进来）
watch(
  () => route.query.categoryId,
  (val) => {
    if (!ready.value) return
    categoryId.value = val ? Number(val) : null
  }
)

/** 提交输入框内容。Vant 的 clear 按钮也走这里，清空后回到该分类的完整列表。 */
function onSearch() {
  keyword.value = inputText.value.trim()
}
</script>

<template>
  <div class="list">
    <van-search
      v-model="inputText"
      shape="round"
      placeholder="搜索商品"
      @search="onSearch"
      @clear="onSearch"
    />

    <van-tabs v-model:active="activeCategory">
      <van-tab title="全部" :name="0" />
      <van-tab v-for="cat in categories" :key="cat.id" :title="cat.name" :name="cat.id" />
    </van-tabs>

    <!-- 查询条件全走 props，改分类/改关键词由 ProductFeed 自己 watch 后重查 -->
    <ProductFeed v-if="ready" :keyword="keyword" :category-id="categoryId" />
  </div>
</template>
