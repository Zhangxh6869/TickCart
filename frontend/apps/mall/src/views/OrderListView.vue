<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import type { OrderSummary } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { cancelOrder, confirmOrder, fetchOrders } from '@/api/order'

const router = useRouter()

/** 状态 tab：status 为 null 表示不筛选 */
const tabs: { label: string; status: number | null }[] = [
  { label: '全部', status: null },
  { label: '待付款', status: 0 },
  { label: '待发货', status: 1 },
  { label: '待收货', status: 2 },
  { label: '已完成', status: 3 },
  { label: '已取消', status: 4 },
]

const activeTab = ref(0)
const orders = ref<OrderSummary[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await fetchOrders({ status: tabs[activeTab.value].status, page: 1, size: 20 })
    orders.value = result.records
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** ISO 时间转 "YYYY-MM-DD HH:mm" */
function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

function goDetail(orderNo: string) {
  router.push({ name: 'order-detail', params: { orderNo } })
}

function goPay(orderNo: string) {
  router.push({ name: 'pay', params: { orderNo } })
}

async function onCancel(order: OrderSummary) {
  try {
    await showConfirmDialog({ title: '取消订单', message: '确认取消这笔订单？库存将自动回补。' })
  } catch {
    return // 用户取消
  }
  try {
    await cancelOrder(order.orderNo)
    showToast('订单已取消')
    await load()
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

async function onConfirm(order: OrderSummary) {
  try {
    await showConfirmDialog({ title: '确认收货', message: '确认已经收到商品？' })
  } catch {
    return // 用户取消
  }
  try {
    await confirmOrder(order.orderNo)
    showToast('已确认收货')
    await load()
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

// 进入页面即加载「全部」tab，否则首屏会一直显示空列表
onMounted(load)
</script>

<template>
  <div class="orders">
    <van-nav-bar title="我的订单" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="activeTab" sticky @change="load">
      <van-tab v-for="tab in tabs" :key="tab.label" :title="tab.label" />
    </van-tabs>

    <van-empty v-if="!loading && orders.length === 0" description="暂无订单" />

    <div v-else class="orders__list">
      <div v-for="order in orders" :key="order.orderNo" class="order" @click="goDetail(order.orderNo)">
        <div class="order__head">
          <span class="order__no">{{ order.orderNo }}</span>
          <span class="order__status">{{ order.statusLabel }}</span>
        </div>

        <div class="order__goods">
          <van-image
            v-for="item in order.items.slice(0, 3)"
            :key="item.productId + '-' + item.skuId"
            class="order__thumb"
            width="60"
            height="60"
            fit="cover"
            :src="item.productImage || ''"
          />
          <div v-if="order.items.length > 3" class="order__more">等 {{ order.items.length }} 件</div>
        </div>

        <div class="order__foot">
          <span class="order__time">{{ formatTime(order.createdAt) }}</span>
          <span class="order__amount">
            实付 <b>¥{{ formatPrice(order.payAmount) }}</b>
          </span>
        </div>

        <div v-if="order.status === 0 || order.status === 2" class="order__actions" @click.stop>
          <van-button
            v-if="order.status === 0"
            size="small"
            round
            plain
            @click="onCancel(order)"
          >
            取消订单
          </van-button>
          <van-button
            v-if="order.status === 0"
            size="small"
            round
            type="primary"
            color="rgb(107,163,200)"
            @click="goPay(order.orderNo)"
          >
            去支付
          </van-button>
          <van-button
            v-if="order.status === 2"
            size="small"
            round
            type="primary"
            @click="onConfirm(order)"
          >
            确认收货
          </van-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.orders {
  min-height: 100vh;
  background: var(--van-background-2, #f7f8fa);
}
.orders__list {
  padding: 10px 12px 12px;
}
.order {
  padding: 12px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: #fff;
}
.order__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.order__no {
  font-size: 12px;
  color: #969799;
}
.order__status {
  font-size: 13px;
  font-weight: 500;
  color: rgb(107, 163, 200);
}
.order__goods {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.order__thumb {
  border-radius: 8px;
  overflow: hidden;
  background: #f7f8fa;
}
.order__more {
  font-size: 12px;
  color: #969799;
}
.order__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #969799;
}
.order__amount b {
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}
.order__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f2f3f5;
}
</style>
