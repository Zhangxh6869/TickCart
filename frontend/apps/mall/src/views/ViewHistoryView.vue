<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import type { ViewHistoryItem } from '@tickcart/shared-types'
import ProductCard from '@/components/ProductCard.vue'
import { clearViewHistory, fetchViewHistory, removeViewHistory } from '@/api/view-history'

const router = useRouter()

const list = ref<ViewHistoryItem[]>([])
const loading = ref(false)
const finished = ref(false)
/** 已加载页码；van-list 首次挂载就会触发一次 load，因此从 0 起算 */
const page = ref(0)
const PAGE_SIZE = 10

/**
 * van-list 的加载回调：不提前 return，出错时直接置 finished，
 * 否则组件会一直重试同一个失败请求。
 */
async function onLoad() {
  page.value += 1
  try {
    const res = await fetchViewHistory({ page: page.value, size: PAGE_SIZE })
    list.value.push(...res.records)
    // 返回空页说明到底了（商品被删除时记录数会少于 total，靠这一条收口）
    finished.value = res.records.length === 0 || list.value.length >= res.total
  } catch {
    finished.value = true
  } finally {
    loading.value = false
  }
}

/** 删除单条：就地移除，无需重新拉列表 */
async function onRemove(item: ViewHistoryItem) {
  try {
    await removeViewHistory(item.id)
    list.value = list.value.filter((product) => product.id !== item.id)
    showToast('已删除')
    if (list.value.length === 0) finished.value = true
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

/** 清空：不可逆操作，先二次确认 */
async function onClear() {
  try {
    await showConfirmDialog({ title: '提示', message: '确认清空全部浏览记录？' })
  } catch {
    return // 用户点了取消
  }
  try {
    await clearViewHistory()
    list.value = []
    // 必须显式置 finished：否则 van-list 会以为还有数据，继续触底加载
    finished.value = true
    showToast('已清空')
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

// ---------------- 按浏览日期分组 ----------------

/**
 * 取 ISO 时间串的日期部分：`2026-10-16T22:18:56` → `2026-10-16`。
 *
 * ★ 刻意**不做** `new Date()` 解析：后端返回的是无时区的本地时间串，
 * 交给 Date 解析会按浏览器时区换算，跨零点时区下会把「今天」算成「昨天」。
 * 直接截取日期部分与本地日期比较，结果稳定。
 */
function dateKeyOf(value: string): string {
  return value.slice(0, 10)
}

/** 本地日期的 `YYYY-MM-DD`（不用 toISOString：那是 UTC，同样会错一天） */
function localDateKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 分组：今天 / 昨天 / 具体日期（同年只到「M月D日」，跨年带年份）。
 *
 * ★ 分组是对**累积后的完整列表**算的，不是按页算的 ——
 * 所以翻页时同一天会并进已有分组，不会出现第二个「今天」标题。
 * 接口返回已按浏览时间倒序，这里只做相邻同日期归并，不重新排序。
 */
const groups = computed(() => {
  const now = new Date()
  const todayKey = localDateKey(now)
  const yesterdayKey = localDateKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1))

  const labelOf = (key: string): string => {
    if (key === todayKey) return '今天'
    if (key === yesterdayKey) return '昨天'
    const [year, month, day] = key.split('-')
    // 同年省略年份，跨年必须带上，否则「10月14日」会有歧义
    return year === String(now.getFullYear())
      ? `${Number(month)}月${Number(day)}日`
      : `${year}年${Number(month)}月${Number(day)}日`
  }

  const result: { key: string; label: string; items: ViewHistoryItem[] }[] = []
  for (const item of list.value) {
    const key = dateKeyOf(item.viewTime)
    const last = result[result.length - 1]
    if (last && last.key === key) {
      last.items.push(item)
    } else {
      result.push({ key, label: labelOf(key), items: [item] })
    }
  }
  return result
})
</script>

<template>
  <div class="view-history">
    <!-- 「清空」只在有记录时出现：空态给一个点了没反应的按钮不如不给 -->
    <van-nav-bar
      title="浏览记录"
      left-arrow
      :right-text="list.length ? '清空' : ''"
      @click-left="router.back()"
      @click-right="onClear"
    />

    <van-empty
      v-if="finished && list.length === 0"
      description="还没有浏览记录"
      class="view-history__empty"
    />

    <van-list
      v-else
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      class="view-history__list"
      @load="onLoad"
    >
      <!-- 分组标题 + 两列网格。分组是展示层切分，数据仍是同一个分页列表 -->
      <section v-for="group in groups" :key="group.key" class="view-history__group">
        <h2 class="view-history__date">{{ group.label }}</h2>
        <div class="view-history__grid">
          <div v-for="item in group.items" :key="item.id" class="view-history__item">
            <ProductCard :product="item" />
            <button
              class="view-history__remove"
              type="button"
              aria-label="删除记录"
              @click.stop="onRemove(item)"
            >
              <van-icon name="delete-o" size="14" />
            </button>
          </div>
        </div>
      </section>
    </van-list>
  </div>
</template>

<style scoped>
.view-history {
  min-height: 100%;
  background: #f7f8fa;
}
.view-history__empty {
  margin-top: 60px;
}
.view-history__list {
  padding: 10px 12px;
}
/* 分组之间留出间隔；首个分组不额外留白，避免与列表 padding 叠成一段空白 */
.view-history__group + .view-history__group {
  margin-top: 6px;
}
.view-history__date {
  margin: 8px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--van-text-color, #323233);
}
.view-history__group:first-child .view-history__date {
  margin-top: 0;
}
/* 与首页商品流、收藏页一致的两列网格；minmax(0,1fr) 防止列宽被卡片文字撑开而溢出 */
.view-history__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.view-history__item {
  position: relative;
}
/* 删除入口：浮在卡片右上角，避免为此改动 ProductCard 的结构 */
.view-history__remove {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  border: none;
  border-radius: 50%;
  cursor: pointer;
}
</style>
