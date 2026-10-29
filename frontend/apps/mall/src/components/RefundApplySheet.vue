<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatPrice } from '@tickcart/shared-utils'

/**
 * 申请退款弹层。
 *
 * <p>只收集「原因 + 说明」两样东西 —— <b>金额只展示不可改</b>：
 * 退多少由后端按「支付成功那条流水」算（`RefundService.resolveRefundAmount`），
 * 前端能改金额等于把退款口子敞开。</p>
 *
 * <p>原因码与后端 {@code RefundReason} 一一对应，改这里必须同步改枚举。</p>
 */
const props = defineProps<{
  show: boolean
  /** 可退金额（后端算好的订单实付/支付成功金额），仅用于展示 */
  amount: number
  /** 提交中：由父组件控制，避免连点提交两次申请 */
  submitting?: boolean
  /** 订单是否已发货：已发货走「仅退款」，文案要提醒拒收 */
  shipped?: boolean
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  submit: [payload: { reasonCode: number; reason?: string }]
}>()

/** 原因码 1不想要了 2地址或信息填错 3商品有问题 4其他 */
const REASONS = [
  { code: 1, label: '不想要了' },
  { code: 2, label: '地址或信息填错' },
  { code: 3, label: '商品有问题' },
  { code: 4, label: '其他' },
] as const

const visible = computed({
  get: () => props.show,
  set: (value: boolean) => emit('update:show', value),
})

const reasonCode = ref<number>(1)
const reason = ref('')

// 每次打开都重置：上次填的说明不该带到下一次申请
watch(
  () => props.show,
  (show) => {
    if (show) {
      reasonCode.value = 1
      reason.value = ''
    }
  },
)

function onSubmit() {
  emit('submit', {
    reasonCode: reasonCode.value,
    reason: reason.value.trim() || undefined,
  })
}
</script>

<template>
  <van-popup v-model:show="visible" position="bottom" round :style="{ maxHeight: '80%' }">
    <div class="refund-sheet">
      <div class="refund-sheet__title">申请退款</div>

      <div class="refund-sheet__amount">
        退款金额
        <b>¥{{ formatPrice(amount) }}</b>
      </div>
      <div class="refund-sheet__hint">
        {{
          shipped
            ? '商品已发出，退款成功后如收到货请拒收'
            : '提交后订单将进入「退款中」，由商家审核'
        }}
      </div>

      <div class="refund-sheet__label">退款原因</div>
      <van-radio-group v-model="reasonCode">
        <div
          v-for="item in REASONS"
          :key="item.code"
          class="refund-sheet__reason"
          @click="reasonCode = item.code"
        >
          <span>{{ item.label }}</span>
          <van-radio :name="item.code" />
        </div>
      </van-radio-group>

      <van-field
        v-model="reason"
        class="refund-sheet__field"
        type="textarea"
        rows="2"
        autosize
        maxlength="200"
        show-word-limit
        placeholder="补充说明（选填）"
      />

      <div class="refund-sheet__actions">
        <van-button round block plain :disabled="submitting" @click="visible = false">
          取消
        </van-button>
        <van-button
          round
          block
          type="primary"
          color="rgb(107,163,200)"
          :loading="submitting"
          @click="onSubmit"
        >
          提交申请
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.refund-sheet {
  padding: 16px 16px calc(16px + env(safe-area-inset-bottom));
}
.refund-sheet__title {
  font-size: 16px;
  font-weight: 500;
  color: #323233;
  text-align: center;
}
.refund-sheet__amount {
  margin-top: 16px;
  font-size: 14px;
  color: #646566;
}
.refund-sheet__amount b {
  margin-left: 6px;
  font-size: 20px;
  font-weight: 500;
  color: #323233;
}
.refund-sheet__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #969799;
}
.refund-sheet__label {
  margin: 18px 0 8px;
  font-size: 13px;
  color: #646566;
}
.refund-sheet__reason {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 2px;
  border-bottom: 1px solid #f2f3f5;
  font-size: 14px;
  color: #323233;
}
.refund-sheet__field {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f8fa;
}
.refund-sheet__actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}
</style>
