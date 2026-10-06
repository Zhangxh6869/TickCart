<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import type { OrderDetail } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { cancelOrder, confirmOrder, fetchOrderDetail } from '@/api/order'

const route = useRoute()
const router = useRouter()

const orderNo = String(route.params.orderNo)
const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const acting = ref(false)

/** 状态说明文案 */
const statusTips: Record<number, string> = {
  0: '请尽快完成支付，超时订单将自动关闭',
  1: '商家正在备货，请耐心等待',
  2: '商品已发出，请及时查收',
  3: '交易已完成，感谢你的购买',
  4: '订单已取消，库存已回补',
  5: '超时未支付，订单已自动关闭',
}

const tip = computed(() => (order.value ? statusTips[order.value.status] ?? '' : ''))
const canCancel = computed(() => order.value?.status === 0)
const canConfirm = computed(() => order.value?.status === 2)

async function load() {
  loading.value = true
  try {
    order.value = await fetchOrderDetail(orderNo)
  } catch {
    order.value = null
  } finally {
    loading.value = false
  }
}

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

async function onCancel() {
  try {
    await showConfirmDialog({ title: '取消订单', message: '确认取消这笔订单？库存将自动回补。' })
  } catch {
    return // 用户取消
  }
  acting.value = true
  try {
    await cancelOrder(orderNo)
    showToast('订单已取消')
    await load()
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    acting.value = false
  }
}

async function onConfirm() {
  try {
    await showConfirmDialog({ title: '确认收货', message: '确认已经收到商品？' })
  } catch {
    return // 用户取消
  }
  acting.value = true
  try {
    await confirmOrder(orderNo)
    showToast('已确认收货')
    await load()
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    acting.value = false
  }
}

function onPay() {
  router.push({ name: 'pay', params: { orderNo } })
}

onMounted(load)
</script>

<template>
  <div class="detail">
    <van-nav-bar title="订单详情" left-arrow @click-left="router.back()" />

    <template v-if="order">
      <div class="banner">
        <div class="banner__status">{{ order.statusLabel }}</div>
        <div class="banner__tip">{{ tip }}</div>
      </div>

      <div class="card">
        <div class="card__row">
          <span class="card__name">{{ order.receiver }}</span>
          <span class="card__phone">{{ order.receiverPhone }}</span>
        </div>
        <div class="card__addr">{{ order.receiverAddr }}</div>
      </div>

      <div class="card card--goods">
        <div v-for="item in order.items" :key="item.productId + '-' + item.skuId" class="goods">
          <van-image class="goods__image" width="70" height="70" fit="cover" :src="item.productImage || ''" />
          <div class="goods__body">
            <div class="goods__name">{{ item.productName }}</div>
            <div v-if="item.specValue" class="goods__spec">{{ item.specValue }}</div>
            <div class="goods__foot">
              <span class="goods__price">¥{{ formatPrice(item.price) }}</span>
              <span class="goods__qty">×{{ item.quantity }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="row"><span>商品总额</span><span>¥{{ formatPrice(order.totalAmount) }}</span></div>
        <div class="row row--total">
          <span>实付款</span>
          <span class="row__amount">¥{{ formatPrice(order.payAmount) }}</span>
        </div>
      </div>

      <div class="card">
        <div class="row"><span>订单编号</span><span class="row__muted">{{ order.orderNo }}</span></div>
        <div class="row"><span>下单时间</span><span class="row__muted">{{ formatTime(order.createdAt) }}</span></div>
        <div v-if="order.payTime" class="row"><span>支付时间</span><span class="row__muted">{{ formatTime(order.payTime) }}</span></div>
        <div v-if="order.shipTime" class="row"><span>发货时间</span><span class="row__muted">{{ formatTime(order.shipTime) }}</span></div>
        <div v-if="order.finishTime" class="row"><span>完成时间</span><span class="row__muted">{{ formatTime(order.finishTime) }}</span></div>
        <div v-if="order.cancelTime" class="row"><span>关闭时间</span><span class="row__muted">{{ formatTime(order.cancelTime) }}</span></div>
        <div v-if="order.remark" class="row"><span>买家备注</span><span class="row__muted">{{ order.remark }}</span></div>
      </div>

      <div v-if="canCancel || canConfirm" class="detail__actions">
        <van-button v-if="canCancel" round plain block :loading="acting" @click="onCancel">取消订单</van-button>
        <van-button v-if="canCancel" round block type="primary" color="rgb(107,163,200)" @click="onPay">去支付</van-button>
        <van-button v-if="canConfirm" round block type="primary" :loading="acting" @click="onConfirm">确认收货</van-button>
      </div>
    </template>

    <van-empty v-else-if="!loading" description="订单不存在" />
  </div>
</template>

<style scoped>
.detail {
  min-height: 100vh;
  padding-bottom: 24px;
  background: var(--van-background-2, #f7f8fa);
}
.banner {
  padding: 20px 16px;
  background: rgb(107, 163, 200);
  color: #fff;
}
.banner__status {
  font-size: 18px;
  font-weight: 500;
}
.banner__tip {
  margin-top: 4px;
  font-size: 13px;
  opacity: 0.9;
}
.card {
  padding: 14px;
  margin: 10px 12px 0;
  border-radius: 10px;
  background: #fff;
}
.card--goods {
  padding: 14px 14px 2px;
}
.card__row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card__name {
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}
.card__phone {
  font-size: 13px;
  color: #969799;
}
.card__addr {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: #646566;
}
.goods {
  display: flex;
  gap: 10px;
  padding-bottom: 12px;
}
.goods__image {
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  background: #f7f8fa;
}
.goods__body {
  flex: 1;
  min-width: 0;
}
.goods__name {
  font-size: 14px;
  line-height: 1.4;
  color: #323233;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.goods__spec {
  margin-top: 4px;
  font-size: 12px;
  color: #969799;
}
.goods__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.goods__price {
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}
.goods__qty {
  font-size: 13px;
  color: #969799;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 0;
  font-size: 13px;
  color: #646566;
}
.row--total {
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px solid #f2f3f5;
  font-size: 14px;
  color: #323233;
}
.row__amount {
  font-size: 17px;
  font-weight: 500;
  color: #323233;
}
.row__muted {
  color: #969799;
}
.detail__actions {
  display: flex;
  gap: 10px;
  margin: 16px 12px 0;
}
</style>
