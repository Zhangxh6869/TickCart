<script setup lang="ts">
import type { ProductListItem } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { useRouter } from 'vue-router'

defineProps<{ product: ProductListItem }>()
const router = useRouter()
</script>

<template>
  <div class="product-card" @click="router.push({ name: 'product-detail', params: { id: product.id } })">
    <div class="product-card__image">
      <img :src="product.mainImage || '/img/placeholder.png'" :alt="product.name" />
    </div>
    <div class="product-card__body">
      <div class="product-card__name">{{ product.name }}</div>
      <div class="product-card__subtitle">{{ product.subtitle }}</div>
      <div class="product-card__footer">
        <span class="product-card__price">¥{{ formatPrice(product.price) }}</span>
        <span class="product-card__sales">已售 {{ product.sales }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
}
.product-card__image {
  width: 100%;
  aspect-ratio: 1;
  background: #f2f3f5;
  display: flex;
  align-items: center;
  justify-content: center;
}
.product-card__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.product-card__body {
  padding: 8px;
}
.product-card__name {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-card__subtitle {
  font-size: 12px;
  color: #969799;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 6px;
}
.product-card__price {
  color: #ee0a24;
  font-size: 16px;
  font-weight: 600;
}
.product-card__sales {
  color: #969799;
  font-size: 12px;
}
</style>
