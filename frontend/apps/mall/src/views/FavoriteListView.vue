<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { ProductListItem } from '@tickcart/shared-types'
import ProductCard from '@/components/ProductCard.vue'
import PageNavBar from '@/components/PageNavBar.vue'
import { fetchFavorites, removeFavorite } from '@/api/favorite'

const router = useRouter()

const list = ref<ProductListItem[]>([])
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
    const res = await fetchFavorites({ page: page.value, size: PAGE_SIZE })
    list.value.push(...res.records)
    // 返回空页说明到底了（商品被删除时记录数会少于 total，靠这一条收口）
    finished.value = res.records.length === 0 || list.value.length >= res.total
  } catch {
    finished.value = true
  } finally {
    loading.value = false
  }
}

/** 取消收藏：就地移除，无需重新拉列表 */
async function onRemove(item: ProductListItem) {
  try {
    await removeFavorite(item.id)
    list.value = list.value.filter((product) => product.id !== item.id)
    showToast('已取消收藏')
    if (list.value.length === 0) finished.value = true
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}
</script>

<template>
  <div class="favorites">
    <PageNavBar title="我的收藏" @click-left="router.back()" />

    <van-empty
      v-if="finished && list.length === 0"
      description="还没有收藏的商品"
      class="favorites__empty"
    />

    <van-list
      v-else
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      class="favorites__list"
      @load="onLoad"
    >
      <div class="favorites__grid">
        <div v-for="item in list" :key="item.id" class="favorites__item">
          <ProductCard :product="item" />
          <button
            class="favorites__remove"
            type="button"
            aria-label="取消收藏"
            @click.stop="onRemove(item)"
          >
            <van-icon name="delete-o" size="14" />
          </button>
        </div>
      </div>
    </van-list>
  </div>
</template>

<style scoped>
.favorites {
  min-height: 100%;
  background: #f7f8fa;
}
.favorites__empty {
  margin-top: 60px;
}
.favorites__list {
  padding: 10px 12px;
}
/* 与首页商品流一致的两列网格；minmax(0,1fr) 防止列宽被卡片文字撑开而溢出 */
.favorites__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.favorites__item {
  position: relative;
}
/* 取消收藏入口：浮在卡片右上角，避免为此改动 ProductCard 的结构 */
.favorites__remove {
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
