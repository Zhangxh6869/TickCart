<script setup lang="ts">
/**
 * 商品列表「排序栏 + 网格 + 触底分页 + 空态/失败态」通用组件。
 *
 * 目前由搜索页 `/search` 的结果区使用。原先还跟 `/products`（商品列表页）共用，
 * 那个页面已按「排序只在搜索结果里用、浏览全部商品走首页」的结论删除，
 * 见 `router/index.ts` 里 `/search` 上方的说明。
 *
 * 保留成独立组件而不是内联进 SearchView：列表时序 / 并发竞态 / 失败态这套东西
 * 和「搜索状态（历史、热搜、URL 同步）」是两件事，后续秒杀商品列表也能直接复用。
 *
 * 数据源固定为公开接口 `GET /api/products`（仅返回上架商品），查询条件全部走 props，
 * 父组件只需改 props，组件内部 `watch` 到变化后自动重查并回到第一页。
 */
import { computed, onMounted, ref, watch } from 'vue'
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

/** 排序口径：字段名走后端 `applySort` 的白名单（price / sales / createdAt） */
const sortOptions = [
  { text: '综合', value: 'createdAt', order: 'desc' },
  { text: '销量', value: 'sales', order: 'desc' },
  { text: '价格↓', value: 'price', order: 'desc' },
  { text: '价格↑', value: 'price', order: 'asc' },
]

const products = ref<ProductListItem[]>([])
/** 交给 van-list 的 v-model，只表达「正在加载」，**不用它当并发锁**（原因见 load()） */
const loading = ref(false)
/**
 * 加载失败。
 *
 * ★ 必须有这个状态，不能只靠 `finished` + 空数组：那样「请求失败」会和「没有结果」
 * 长得一模一样 —— 后端一挂（或断网），用户看到的是「没有找到相关商品」，
 * 会以为是自己搜错了词，而不是「服务不可用」。实测踩过。
 * 置 true 后由 van-list 渲染「加载失败，点击重试」，点它清掉 error 并重新 check。
 */
const error = ref(false)
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
 * 列表就永久停在「加载中...」。（历史上那页商品列表加载不出商品，一半原因在此。）
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
    error.value = false
    finished.value = page.value >= res.pages
    page.value += 1
  } catch {
    // 失败只置 error，不动 products/finished：已加载的内容留在页面上，
    // van-list 会在列表底部显示「加载失败，点击重试」。
    if (gen === generation) error.value = true
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
  error.value = false
  finished.value = false
  await load()
}

function selectSort(opt: (typeof sortOptions)[number]) {
  if (sortBy.value === opt.value && order.value === opt.order) return
  sortBy.value = opt.value
  order.value = opt.order
  reload()
}

/**
 * 「没有更多了」只在真有商品时才显示。
 *
 * 空结果时 `res.pages === 0` 会让 `finished` 直接为 true，van-list 就会在
 * van-empty 上方先渲染一行「没有更多了」——「没有更多了」和「没有找到相关商品」
 * 叠在一起很怪。van-list 收到空串不渲染该文案，所以按有无商品给值。
 */
const finishedText = computed(() => (products.value.length > 0 ? '没有更多了' : ''))

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
      v-model:error="error"
      :finished="finished"
      :immediate-check="false"
      :finished-text="finishedText"
      error-text="加载失败，点击重试"
      @load="load"
    />
    <!-- 空态只在「确实查完了、且没有出错」时出现，否则会把「失败」说成「没有结果」 -->
    <van-empty v-if="!loading && !error && products.length === 0" :description="emptyText">
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
