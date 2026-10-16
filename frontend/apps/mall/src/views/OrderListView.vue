<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import type { OrderSummary } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { cancelOrder, confirmOrder, fetchOrders } from '@/api/order'
import PageNavBar from '@/components/PageNavBar.vue'
import { formatCountdown, remainMsOf, useNow } from '@/composables/usePayCountdown'

const route = useRoute()
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

/**
 * 首屏定位到哪个 tab。
 *
 * 「我的」页的订单宫格会带 `?status=0/1/2/3` 进来，直接落到对应状态；
 * 其它入口（以及 status 缺失、非法、为数组时）一律回到「全部」。
 * 本页不在 App.vue 的 KeepAlive 名单里，每次进入都是新实例，读一次 query 就够。
 */
function initialTab(): number {
  const status = Number(route.query.status)
  const index = tabs.findIndex((tab) => tab.status === status)
  return index < 0 ? 0 : index
}

const activeTab = ref(initialTab())
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

// 整页共用一个每秒跳一次的时钟：列表里可能有多张待付款卡片，
// 各自起一个定时器会白白多出 N 个（见 composables/usePayCountdown）
const now = useNow()

/** 待付款订单的支付倒计时文案；非待付款、或倒计时已结束返回空串（不展示） */
function payCountdown(order: OrderSummary): string {
  if (order.status !== 0) return ''
  const remainMs = remainMsOf(order.createdAt, now.value)
  return remainMs > 0 ? formatCountdown(remainMs) : ''
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
    <PageNavBar title="我的订单" @click-left="router.back()" />

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

        <!-- 待付款订单的支付倒计时：与收银台、订单详情同一个实现 -->
        <div v-if="payCountdown(order)" class="order__countdown">
          剩余支付时间 {{ payCountdown(order) }}，超时自动关闭
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
.order__countdown {
  margin-top: 8px;
  font-size: 12px;
  color: rgb(107, 163, 200);
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
