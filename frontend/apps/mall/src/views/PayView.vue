<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { OrderDetail, PaymentChannel } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchOrderDetail } from '@/api/order'
import { createPayment, fetchPaymentStatus } from '@/api/payment'
import { parseOrderOrigin } from '@/utils/checkout-route'

/**
 * 收银台。
 *
 * 支付被拆成三段，本页负责首尾两端，中间由渠道页（PayChannelView）完成：
 *   ① 点「确认支付」→ 创建一条待支付流水 → 跳转渠道页（订单**此时仍未变更**）
 *   ② 用户在渠道页付款，渠道延迟回调商城
 *   ③ 带着 `?polling=1` 回来 → 轮询支付状态，直到确认成功 / 失败 / 取消 / 超时
 *
 * 因此这里绝不会「点一下就结束」：付款结论只能由渠道回调产生，前端只负责查询。
 */
const route = useRoute()
const router = useRouter()

const orderNo = String(route.params.orderNo)
const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const submitting = ref(false)
const channel = ref<PaymentChannel>(1)

/** 收银台阶段：idle 选渠道付款 / polling 等待渠道回调 / timeout 轮询超时未确认 */
const phase = ref<'idle' | 'polling' | 'timeout'>('idle')

/**
 * 下单来源（结算页提交时写在 URL 上，随支付链路一路带过来）。
 *
 * <p>收银台自己用不到它，但**不能在跳转时丢掉** —— 支付成功后落到的订单详情，
 * 返回键要靠它决定回首页还是回购物车。</p>
 */
const orderOrigin = computed(() => parseOrderOrigin(route.query))

/** 跳转时要原样带上的 query；没有来源时为空对象。 */
const originQuery = computed(() => (orderOrigin.value ? { from: orderOrigin.value } : {}))

/** 待付款订单的支付时限（与后端超时关单一致：15 分钟） */
const PAY_WINDOW_MS = 15 * 60 * 1000
/** 支付结果轮询间隔；渠道回调约 1 秒到达，300ms 一次让结果尽快显示出来 */
const POLL_INTERVAL_MS = 300
/** 轮询上限：超过仍未确认就交给用户手动重查，避免无限转圈 */
const POLL_TIMEOUT_MS = 30 * 1000

const remainMs = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

let pollTimer: ReturnType<typeof setInterval> | null = null
let pollDeadline = 0

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
const channelLabel = computed(() => channels.find((c) => c.value === channel.value)?.label ?? '渠道')

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

function stopCountdown() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function load() {
  loading.value = true
  try {
    const detail = await fetchOrderDetail(orderNo)
    order.value = detail
    if (detail.status !== 0) {
      // 已支付/已关闭的订单不该停留在收银台；轮询可能同时在跑，先收掉
      stopPolling()
      showToast(detail.status === 1 ? '支付成功' : '该订单无需支付')
      router.replace({ name: 'order-detail', params: { orderNo }, query: originQuery.value })
      return
    }
    startCountdown(detail.createdAt)
  } catch {
    order.value = null
  } finally {
    loading.value = false
  }
}

/** 点「确认支付」：创建支付单后跳转渠道页，订单要等渠道回调才会变更。 */
async function onPay() {
  if (submitting.value || phase.value === 'polling') return
  if (expired.value) {
    showToast('订单已超时，请重新下单')
    return
  }
  submitting.value = true
  try {
    const result = await createPayment(orderNo, channel.value)
    router.push({
      name: 'pay-channel',
      params: { payNo: result.payNo },
      query: {
        orderNo,
        amount: String(result.amount),
        channel: String(result.channel),
        // 来源标记一路带着走，支付成功后回到的订单详情还要靠它决定返回键去哪
        ...originQuery.value,
      },
    })
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

// ---------------- 支付结果轮询 ----------------

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 开始轮询；重复调用安全（已在进行中则忽略）。 */
function startPolling() {
  if (phase.value === 'polling') return
  phase.value = 'polling'
  pollDeadline = Date.now() + POLL_TIMEOUT_MS
  void pollPayment()
  pollTimer = setInterval(() => void pollPayment(), POLL_INTERVAL_MS)
}

/** 单次查询：按订单状态与最新流水状态判定最终结果。 */
async function pollPayment() {
  try {
    const status = await fetchPaymentStatus(orderNo)

    if (status.orderStatus === 1) {
      stopPolling()
      showToast('支付成功')
      router.replace({ name: 'order-detail', params: { orderNo }, query: originQuery.value })
      return
    }
    if (status.orderStatus === 4 || status.orderStatus === 5) {
      // 订单已被取消 / 超时关闭：付款来不及落到订单上，不再继续等待
      stopPolling()
      showToast(status.orderStatusLabel || '订单已关闭')
      router.replace({ name: 'order-detail', params: { orderNo }, query: originQuery.value })
      return
    }
    if (status.payStatus === 2) {
      settleUnpaid('支付失败，请重新支付')
      return
    }
    if (status.payStatus === 3) {
      settleUnpaid('已取消支付')
      return
    }
    if (Date.now() >= pollDeadline) {
      stopPolling()
      phase.value = 'timeout'
    }
  } catch {
    // 单次查询失败（网络抖动）不中断轮询，由上面的超时兜底
  }
}

/** 支付失败 / 用户取消：订单仍是待付款，回到可重新支付的状态。 */
function settleUnpaid(message: string) {
  stopPolling()
  phase.value = 'idle'
  showToast(message)
  // 清掉轮询标记（保留来源标记），避免用户刷新页面后又进入等待态
  router.replace({ name: 'pay', params: { orderNo }, query: originQuery.value })
}

// 从渠道页返回时会带上 ?polling=1；无论组件是新建还是复用，都靠这个标记触发
watch(
  () => route.query.polling,
  (value) => {
    if (value === '1') startPolling()
  },
  { immediate: true }
)

onMounted(load)
onBeforeUnmount(() => {
  stopCountdown()
  stopPolling()
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

      <!-- 支付中：渠道尚未回调，只能等待 -->
      <div v-if="phase === 'polling'" class="card pay__pending">
        <van-loading size="24" color="rgb(107,163,200)" />
        <div class="pay__pending-title">正在确认支付结果…</div>
        <div class="pay__pending-tip">已完成付款请稍候，系统正在等待渠道回调</div>
      </div>

      <!-- 轮询超时：给用户手动重查与查看订单的出口 -->
      <div v-else-if="phase === 'timeout'" class="card pay__pending">
        <div class="pay__pending-title">支付结果确认中</div>
        <div class="pay__pending-tip">
          暂未收到渠道回调。若已扣款，可稍后在「我的订单」中查看状态。
        </div>
        <div class="pay__pending-actions">
          <van-button round block plain size="small" @click="startPolling">重新查询</van-button>
          <van-button
            round
            block
            plain
            size="small"
            @click="router.replace({ name: 'order-detail', params: { orderNo }, query: originQuery })"
          >
            查看订单
          </van-button>
        </div>
      </div>

      <template v-else>
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

        <div class="pay__tip">
          模拟支付环境：点击「确认支付」将跳转至{{ channelLabel }}完成付款，付款成功后才算完成。
        </div>

        <div class="pay__actions">
          <van-button
            round
            block
            type="primary"
            color="rgb(107,163,200)"
            :loading="submitting"
            :disabled="expired"
            @click="onPay"
          >
            确认支付 ¥{{ formatPrice(order.payAmount) }}
          </van-button>
        </div>
      </template>
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
.pay__pending {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 16px;
}
.pay__pending-title {
  margin-top: 16px;
  font-size: 15px;
  color: #323233;
}
.pay__pending-tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #969799;
  text-align: center;
}
.pay__pending-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}
</style>
