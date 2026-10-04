<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { ProductDetail, ProductSku } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchProductDetail } from '@/api/product'

const route = useRoute()
const router = useRouter()

const detail = ref<ProductDetail | null>(null)
const loading = ref(true)
const selectedSku = ref<ProductSku | null>(null)

const currentPrice = computed(() => selectedSku.value?.price ?? detail.value?.price ?? 0)
const currentStock = computed(() => selectedSku.value?.stock ?? detail.value?.stock ?? 0)
const canBuy = computed(() => currentStock.value > 0)

onMounted(async () => {
  try {
    detail.value = await fetchProductDetail(Number(route.params.id))
    // 默认选中第一个有货 SKU
    selectedSku.value = detail.value.skus.find((s) => s.stock > 0) ?? detail.value.skus[0] ?? null
  } finally {
    loading.value = false
  }
})

function addToCart() {
  // 购物车在阶段2实现，这里仅提示并引导
  if (!canBuy.value) {
    showToast('库存不足')
    return
  }
  showToast('已加入购物车（阶段2开通）')
}
</script>

<template>
  <div class="detail">
    <van-nav-bar title="商品详情" left-arrow @click-left="router.back()" />

    <van-loading v-if="loading" class="loading" />

    <template v-else-if="detail">
      <div class="detail__image">
        <img :src="detail.mainImage || '/img/placeholder.png'" :alt="detail.name" />
      </div>

      <div class="detail__card">
        <div class="detail__price">¥{{ formatPrice(currentPrice) }}</div>
        <div class="detail__name">{{ detail.name }}</div>
        <div class="detail__subtitle">{{ detail.subtitle }}</div>
        <div class="detail__meta">
          <span>销量 {{ detail.sales }}</span>
          <span>库存 {{ currentStock }}</span>
        </div>
      </div>

      <div v-if="detail.skus.length" class="detail__card">
        <div class="detail__label">选择规格</div>
        <div class="sku-list">
          <div
            v-for="sku in detail.skus"
            :key="sku.id"
            class="sku-list__item"
            :class="{ active: selectedSku?.id === sku.id, disabled: sku.stock <= 0 }"
            @click="sku.stock > 0 && (selectedSku = sku)"
          >
            {{ sku.specValue }}
          </div>
        </div>
      </div>

      <div v-if="detail.detail" class="detail__card">
        <div class="detail__label">商品详情</div>
        <div class="detail__content">{{ detail.detail }}</div>
      </div>

      <van-action-bar>
        <van-action-bar-icon icon="chat-o" text="客服" />
        <van-action-bar-icon icon="cart-o" text="购物车" />
        <van-action-bar-button
          type="danger"
          :text="canBuy ? '加入购物车' : '已售罄'"
          :disabled="!canBuy"
          @click="addToCart"
        />
      </van-action-bar>
    </template>
  </div>
</template>

<style scoped>
.detail {
  padding-bottom: 60px;
}
.loading {
  padding: 40px;
  text-align: center;
}
.detail__image {
  width: 100%;
  aspect-ratio: 1;
  background: #f2f3f5;
}
.detail__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.detail__card {
  background: #fff;
  margin: 10px 12px;
  padding: 12px;
  border-radius: 8px;
}
.detail__price {
  color: #ee0a24;
  font-size: 22px;
  font-weight: 700;
}
.detail__name {
  font-size: 17px;
  font-weight: 600;
  margin-top: 4px;
}
.detail__subtitle {
  font-size: 13px;
  color: #969799;
  margin-top: 2px;
}
.detail__meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #969799;
  margin-top: 8px;
}
.detail__label {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}
.detail__content {
  font-size: 14px;
  color: #646566;
  line-height: 1.6;
}
.sku-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.sku-list__item {
  padding: 6px 14px;
  border: 1px solid #ebedf0;
  border-radius: 16px;
  font-size: 13px;
  background: #f7f8fa;
}
.sku-list__item.active {
  border-color: #ee0a24;
  color: #ee0a24;
  background: #fff5f5;
}
.sku-list__item.disabled {
  color: #c8c9cc;
  text-decoration: line-through;
}
</style>
