<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import type { OrderDetail } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import {
  applyRefund,
  cancelOrder,
  cancelRefund,
  confirmOrder,
  fetchOrderDetail,
} from '@/api/order'
import PageNavBar from '@/components/PageNavBar.vue'
import RefundApplySheet from '@/components/RefundApplySheet.vue'
import { usePayCountdown } from '@/composables/usePayCountdown'
import { ORDER_ORIGIN_BUY_NOW, ORDER_ORIGIN_CART, parseOrderOrigin } from '@/utils/checkout-route'

const route = useRoute()
const router = useRouter()

const orderNo = String(route.params.orderNo)
const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const acting = ref(false)

/** 退款申请弹层开关 */
const refundSheetVisible = ref(false)

/** 取消成功后「回首页」的延时句柄：组件卸载时要清掉，避免页面已销毁还在跳转 */
let redirectTimer: ReturnType<typeof setTimeout> | null = null

/** 取消订单后停留多久再回首页（毫秒）：让用户看清「已取消」再离开 */
const CANCEL_REDIRECT_DELAY_MS = 1000

/** 状态说明文案（待付款那条带倒计时，单独在 tip 里拼，不放这里） */
const statusTips: Record<number, string> = {
  1: '商家正在备货，请耐心等待',
  2: '商品已发出，请及时查收',
  3: '交易已完成，感谢你的购买',
  4: '订单已取消，库存已回补',
  5: '超时未支付，订单已自动关闭',
  6: '退款申请已提交，等待商家审核',
  7: '退款已完成，款项将原路退回',
}

// 支付倒计时与收银台、订单列表共用同一个实现（见 composables/usePayCountdown）
const { countdown, expired } = usePayCountdown(computed(() => order.value?.createdAt))

const tip = computed(() => {
  const status = order.value?.status
  if (status === undefined) return ''
  if (status === 0) {
    return expired.value
      ? '支付时间已结束，订单即将自动关闭'
      : `剩余支付时间 ${countdown.value}，超时订单将自动关闭`
  }
  return statusTips[status] ?? ''
})
const canCancel = computed(() => order.value?.status === 0)
const canConfirm = computed(() => order.value?.status === 2)
/** P0 只支持「未发货退款」：只有待发货能申请 */
const canRefund = computed(() => order.value?.status === 1)
/** 只有「待审核」的退款单能撤销；已审核的只能等结果 */
const canCancelRefund = computed(() => order.value?.status === 6 && order.value?.refund?.status === 0)

/** 退款单状态标签配色：待审核黄、已退款绿、已拒绝红、已撤销灰 */
const refundTagType = computed<'warning' | 'success' | 'danger' | 'default'>(() => {
  const status = order.value?.refund?.status
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'default'
})

/** 下单来源（结算页提交时写在 URL 上）；从订单列表等入口进来时为 null */
const orderOrigin = computed(() => parseOrderOrigin(route.query))

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
    showToast({ message: '订单已取消', duration: CANCEL_REDIRECT_DELAY_MS })
    await load()
    // 取消后这笔交易就结束了，没有别的可做：稍作停留让用户看清结果，再回首页。
    // 用 replace 而不是 push —— 不该再退回一个已取消订单的详情页。
    redirectTimer = setTimeout(() => {
      redirectTimer = null
      router.replace({ name: 'home' })
    }, CANCEL_REDIRECT_DELAY_MS)
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

/** 弹层里展示的退款金额：订单实付。真正的退款金额由后端按支付成功流水算 */
const refundableAmount = computed(() => order.value?.payAmount ?? 0)

function onApplyRefund() {
  refundSheetVisible.value = true
}

/** 提交退款申请。金额不提交 —— 后端自己算，前端传金额等于把退款口子敞开 */
async function onSubmitRefund(payload: { reasonCode: number; reason?: string }) {
  acting.value = true
  try {
    await applyRefund(orderNo, payload)
    refundSheetVisible.value = false
    showToast('退款申请已提交')
    await load()
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    acting.value = false
  }
}

/** 撤销退款申请：仅「待审核」可撤销，撤销后订单回到待发货 */
async function onCancelRefund() {
  try {
    await showConfirmDialog({
      title: '撤销退款',
      message: '撤销后订单将回到「待发货」，确认撤销这笔退款申请？',
    })
  } catch {
    return // 用户取消
  }
  acting.value = true
  try {
    await cancelRefund(orderNo)
    showToast('已撤销退款申请')
    await load()
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    acting.value = false
  }
}

function onPay() {
  router.push({
    name: 'pay',
    params: { orderNo },
    // 把下单来源一起带进支付链路：支付成功后回到的订单详情还要靠它决定返回键去哪
    query: orderOrigin.value ? { from: orderOrigin.value } : {},
  })
}

/**
 * 返回键。
 *
 * <p>从结算页下单进来的，返回目标是**下单来源**而不是上一页 —— 下单流程已经结束，
 * 退回商品详情或结算页都没有意义：立即购买来源回首页，购物车结算来源回购物车。
 * 其它入口（订单列表等）没有来源标记，走正常历史回退。</p>
 *
 * <p>用 replace 不用 push：不该再退回一个刚下完单的详情页。</p>
 */
function onBack() {
  if (orderOrigin.value === ORDER_ORIGIN_CART) {
    router.replace({ name: 'cart' })
  } else if (orderOrigin.value === ORDER_ORIGIN_BUY_NOW) {
    router.replace({ name: 'home' })
  } else {
    router.back()
  }
}

onMounted(load)
onBeforeUnmount(() => {
  if (redirectTimer) {
    clearTimeout(redirectTimer)
    redirectTimer = null
  }
})
</script>

<template>
  <div class="detail">
    <PageNavBar title="订单详情" @click-left="onBack" />

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

      <!-- 退款进度：只要申请过就展示，被拒绝/撤销的记录也留着，方便用户回看 -->
      <div v-if="order.refund" class="card card--refund">
        <div class="card__row card__row--between">
          <span class="refund__title">退款进度</span>
          <van-tag :type="refundTagType" plain>{{ order.refund.statusLabel }}</van-tag>
        </div>
        <div class="row">
          <span>退款金额</span>
          <span class="row__amount">¥{{ formatPrice(order.refund.amount) }}</span>
        </div>
        <div class="row">
          <span>退款原因</span>
          <span class="row__muted">{{ order.refund.reasonLabel }}</span>
        </div>
        <div v-if="order.refund.reason" class="row">
          <span>补充说明</span>
          <span class="row__muted">{{ order.refund.reason }}</span>
        </div>
        <div class="row">
          <span>申请时间</span>
          <span class="row__muted">{{ formatTime(order.refund.createdAt) }}</span>
        </div>
        <div v-if="order.refund.auditRemark" class="row">
          <span>审核意见</span>
          <span class="row__muted">{{ order.refund.auditRemark }}</span>
        </div>
        <div v-if="order.refund.refundTime" class="row">
          <span>退款时间</span>
          <span class="row__muted">{{ formatTime(order.refund.refundTime) }}</span>
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

      <div
        v-if="canCancel || canConfirm || canRefund || canCancelRefund"
        class="detail__actions"
      >
        <van-button v-if="canCancel" round plain block :loading="acting" @click="onCancel">取消订单</van-button>
        <van-button v-if="canCancel" round block type="primary" color="rgb(107,163,200)" @click="onPay">去支付</van-button>
        <van-button v-if="canConfirm" round block type="primary" :loading="acting" @click="onConfirm">确认收货</van-button>
        <van-button v-if="canRefund" round plain block :loading="acting" @click="onApplyRefund">申请退款</van-button>
        <van-button v-if="canCancelRefund" round plain block :loading="acting" @click="onCancelRefund">撤销退款</van-button>
      </div>
    </template>

    <van-empty v-else-if="!loading" description="订单不存在" />

    <RefundApplySheet
      v-model:show="refundSheetVisible"
      :amount="refundableAmount"
      :submitting="acting"
      @submit="onSubmitRefund"
    />
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
.card__row--between {
  justify-content: space-between;
  margin-bottom: 4px;
}
.refund__title {
  font-size: 14px;
  font-weight: 500;
  color: #323233;
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
