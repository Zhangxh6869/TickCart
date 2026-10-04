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

    <van-swipe class="banner" :autoplay="3000" indicator-color="#fff">
      <van-swipe-item v-for="n in 3" :key="n">
        <div class="banner__item">TickCart 限时秒杀 · 敬请期待</div>
      </van-swipe-item>
    </van-swipe>

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
.banner {
  height: 140px;
  margin: 0 12px;
  border-radius: 8px;
  overflow: hidden;
}
.banner__item {
  height: 140px;
  background: linear-gradient(135deg, #ee0a24, #ff6034);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
}
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
.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.loading {
  padding: 20px;
  text-align: center;
}
</style>
