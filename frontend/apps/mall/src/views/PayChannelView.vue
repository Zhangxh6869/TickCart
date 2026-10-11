<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MockChannelResult } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { submitMockChannelResult } from '@/api/payment'

/**
 * 模拟第三方支付渠道页。
 *
 * 本页扮演的是**渠道自己的收银台**（微信 / 支付宝），不属于商城页面：
 * 真实接入后本页整体删除，由渠道 App 内的付款界面取代。
 *
 * 因此这里**没有任何改订单的能力**：用户点「确认付款」只是把付款结果交给渠道，
 * 渠道再延迟回调商城。本页随即退回收银台，由收银台轮询确认最终结果。
 */
const route = useRoute()
const router = useRouter()

const payNo = String(route.params.payNo)
const orderNo = String(route.query.orderNo ?? '')
const amount = Number(route.query.amount ?? 0)
const channel = Number(route.query.channel ?? 1)

const submitting = ref(false)

const brand = computed(() =>
  channel === 2
    ? { name: '支付宝', icon: 'alipay', color: 'rgb(22, 119, 255)' }
    : { name: '微信支付', icon: 'wechat-pay', color: 'rgb(7, 193, 96)' }
)

/**
 * 把付款结果交给渠道，然后退回商城收银台。
 *
 * 「取消支付」与左上角返回同义：都表示用户放弃这笔支付。之所以让返回也提交取消，
 * 是为了让用户立刻看到明确结果，而不是回到收银台干等轮询超时。
 */
async function submit(result: MockChannelResult) {
  if (submitting.value) return
  submitting.value = true
  try {
    await submitMockChannelResult(payNo, result)
  } catch {
    // 失败提示由请求拦截器统一处理；仍然退回商城，由收银台轮询兜底
  } finally {
    submitting.value = false
    backToCashier()
  }
}

/** 退回收银台并带上轮询标记，收银台据此开始确认支付结果。 */
function backToCashier() {
  router.replace({ name: 'pay', params: { orderNo }, query: { polling: '1' } })
}
</script>

<template>
  <div class="pay-channel">
    <van-nav-bar :title="brand.name" left-arrow @click-left="submit('cancel')" />

    <div class="pay-channel__notice">模拟支付渠道 · 不会发生真实扣款</div>

    <div class="pay-channel__body">
      <van-icon :name="brand.icon" class="pay-channel__icon" :style="{ color: brand.color }" />
      <div class="pay-channel__title">确认付款</div>
      <div class="pay-channel__amount">¥{{ formatPrice(amount) }}</div>
      <div class="pay-channel__order">订单号 {{ orderNo }}</div>
    </div>

    <div class="pay-channel__actions">
      <van-button
        round
        block
        type="primary"
        :color="brand.color"
        :loading="submitting"
        @click="submit('success')"
      >
        确认付款
      </van-button>
      <van-button
        round
        block
        plain
        class="pay-channel__cancel"
        :disabled="submitting"
        @click="submit('cancel')"
      >
        取消支付
      </van-button>
    </div>

    <p class="pay-channel__tip">
      付款结果将由渠道异步通知商城。返回收银台后会自动确认支付结果，请勿重复提交。
    </p>
  </div>
</template>

<style scoped>
.pay-channel {
  min-height: 100vh;
  padding-bottom: 24px;
  background: #fff;
}
.pay-channel__notice {
  padding: 10px 16px;
  font-size: 12px;
  color: #969799;
  text-align: center;
  background: #f7f8fa;
}
.pay-channel__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 16px 40px;
}
.pay-channel__icon {
  font-size: 52px;
}
.pay-channel__title {
  margin-top: 16px;
  font-size: 15px;
  color: #646566;
}
.pay-channel__amount {
  margin-top: 8px;
  font-size: 32px;
  font-weight: 600;
  color: #323233;
}
.pay-channel__order {
  margin-top: 10px;
  font-size: 12px;
  color: #969799;
}
.pay-channel__actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 24px;
}
.pay-channel__cancel {
  color: #646566;
  border-color: #dcdee0;
}
.pay-channel__tip {
  margin: 20px 24px 0;
  font-size: 12px;
  line-height: 1.6;
  color: #969799;
}
</style>
