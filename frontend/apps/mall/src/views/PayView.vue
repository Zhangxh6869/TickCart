<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { OrderDetail, PaymentChannel } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchOrderDetail } from '@/api/order'
import { payOrder } from '@/api/payment'

const route = useRoute()
const router = useRouter()

const orderNo = String(route.params.orderNo)
const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const paying = ref(false)
const channel = ref<PaymentChannel>(1)

/** 待付款订单的支付时限（与后端超时关单一致：15 分钟） */
const PAY_WINDOW_MS = 15 * 60 * 1000
const remainMs = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const channels: { value: PaymentChannel; label: string; icon: string }[] = [
  { value: 1, label: '微信支付', icon: 'wechat-pay' },
  { value: 2, label: '支付宝', icon: 'alipay' },
]

const countdown = computed(() => {
  const total = Math.max(0, Math.floor(remainMs.value / 1000))
  const mm = String(Math.floor(total / 60)).padStart(2, '0')
  const ss = String(total % 60).padStart(2, '0')
  return `${mm}:${ss}`
})
const expired = computed(() => remainMs.value <= 0)

function startCountdown(createdAt: string) {
  const deadline = new Date(createdAt.replace(' ', 'T')).getTime() + PAY_WINDOW_MS
  const tick = () => {
    remainMs.value = deadline - Date.now()
    if (remainMs.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }
  tick()
  timer = setInterval(tick, 1000)
}

async function load() {
  loading.value = true
  try {
    order.value = await fetchOrderDetail(orderNo)
    if (order.value.status !== 0) {
      // 已支付/已关闭的订单不该停留在收银台
      showToast('该订单无需支付')
      router.replace({ name: 'order-detail', params: { orderNo } })
      return
    }
    startCountdown(order.value.createdAt)
  } catch {
    order.value = null
  } finally {
    loading.value = false
  }
}

async function onPay() {
  if (paying.value) return
  if (expired.value) {
    showToast('订单已超时，请重新下单')
    return
  }
  paying.value = true
  try {
    const result = await payOrder(orderNo, channel.value)
    showToast(result.status === 1 ? '支付成功' : '支付未完成')
    router.replace({ name: 'order-detail', params: { orderNo } })
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    paying.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="pay">
    <van-nav-bar title="收银台" left-arrow @click-left="router.back()" />

    <template v-if="order">
      <div class="pay__amount">
        <div class="pay__label">支付金额</div>
        <div class="pay__value">¥{{ formatPrice(order.payAmount) }}</div>
        <div class="pay__countdown">
          <template v-if="!expired">剩余支付时间 {{ countdown }}</template>
          <template v-else>支付时间已结束</template>
        </div>
      </div>

      <div class="card">
        <div class="pay__order">
          <span>订单编号</span>
          <span class="pay__muted">{{ order.orderNo }}</span>
        </div>
      </div>

      <div class="card card--channels">
        <div class="pay__section-title">选择支付方式</div>
        <div
          v-for="item in channels"
          :key="item.value"
          class="channel"
          :class="{ 'channel--active': channel === item.value }"
          @click="channel = item.value"
        >
          <van-icon :name="item.icon" class="channel__icon" />
          <span class="channel__label">{{ item.label }}</span>
          <van-icon v-if="channel === item.value" name="success" class="channel__check" />
        </div>
      </div>

      <div class="pay__tip">模拟支付环境，点击「确认支付」将直接完成付款</div>

      <div class="pay__actions">
        <van-button
          round
          block
          type="primary"
          color="rgb(107,163,200)"
          :loading="paying"
          :disabled="expired"
          @click="onPay"
        >
          确认支付 ¥{{ formatPrice(order.payAmount) }}
        </van-button>
      </div>
    </template>

    <van-empty v-else-if="!loading" description="订单不存在" />
  </div>
</template>

<style scoped>
.pay {
  min-height: 100vh;
  padding-bottom: 24px;
  background: var(--van-background-2, #f7f8fa);
}
.pay__amount {
  padding: 28px 16px 24px;
  text-align: center;
  background: #fff;
}
.pay__label {
  font-size: 13px;
  color: #969799;
}
.pay__value {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 600;
  color: #323233;
}
.pay__countdown {
  margin-top: 8px;
  font-size: 13px;
  color: rgb(107, 163, 200);
}
.card {
  padding: 14px;
  margin: 10px 12px 0;
  border-radius: 10px;
  background: #fff;
}
.card--channels {
  padding-bottom: 6px;
}
.pay__order {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #646566;
}
.pay__muted {
  color: #969799;
}
.pay__section-title {
  margin-bottom: 10px;
  font-size: 13px;
  color: #969799;
}
.channel {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-top: 1px solid #f2f3f5;
}
.channel:first-of-type {
  border-top: none;
}
.channel__icon {
  font-size: 22px;
  color: rgb(107, 163, 200);
}
.channel__label {
  flex: 1;
  font-size: 15px;
  color: #323233;
}
.channel__check {
  font-size: 18px;
  color: rgb(107, 163, 200);
}
.pay__tip {
  margin: 12px 16px 0;
  font-size: 12px;
  line-height: 1.5;
  color: #969799;
}
.pay__actions {
  margin: 20px 16px 0;
}
</style>
