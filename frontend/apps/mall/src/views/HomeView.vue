<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { Category, ProductListItem } from '@tickcart/shared-types'
import { fetchCategories, fetchProducts } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()
const categories = ref<Category[]>([])
const products = ref<ProductListItem[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [cats, page] = await Promise.all([
      fetchCategories(),
      fetchProducts({ page: 1, size: 8, sortBy: 'sales', order: 'desc' }),
    ])
    categories.value = cats
    products.value = page.records
  } finally {
    loading.value = false
  }
})

function goCategory(id: number) {
  router.push({ name: 'product-list', query: { categoryId: id } })
}

function goAll() {
  router.push({ name: 'product-list' })
}
</script>

<template>
  <div class="home">
    <van-search
      shape="round"
      placeholder="搜索商品"
      readonly
      @click="router.push({ name: 'product-list' })"
    />

    <div class="section">
      <div class="section__header">
        <span class="section__title">商品分类</span>
        <span class="section__more" @click="goAll">全部 ›</span>
      </div>
      <van-grid :column-num="4" :border="false">
        <van-grid-item
          v-for="cat in categories"
          :key="cat.id"
          :text="cat.name"
          @click="goCategory(cat.id)"
        />
      </van-grid>
    </div>

    <div class="section">
      <div class="section__header">
        <span class="section__title">热销商品</span>
        <span class="section__more" @click="goAll">更多 ›</span>
      </div>
      <van-loading v-if="loading" class="loading" />
      <div v-else class="product-grid">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.section {
  background: #fff;
  margin: 12px;
  border-radius: 8px;
  padding: 12px;
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
