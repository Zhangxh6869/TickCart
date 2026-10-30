<script setup lang="ts">
import { computed, ref, watch } from 'vue'

/**
 * 填写退货物流单号弹层。
 *
 * <p>只有「退货退款」需要这一步：商家同意退货后，用户把货寄回并回填单号，
 * 商家才能核对是哪件货。单号必填 —— 没有单号商家只能靠用户口头描述，售后会变成扯皮。</p>
 */
const props = defineProps<{
  show: boolean
  /** 退货收货地址（后端在「待寄回」时下发），用户按此寄回 */
  returnAddress?: string | null
  submitting?: boolean
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  submit: [payload: { company?: string; trackingNo: string }]
}>()

const visible = computed({
  get: () => props.show,
  set: (value: boolean) => emit('update:show', value),
})

const company = ref('')
const trackingNo = ref('')
const error = ref('')

// 每次打开都重置：上一单的单号绝不能带到下一单
watch(
  () => props.show,
  (show) => {
    if (show) {
      company.value = ''
      trackingNo.value = ''
      error.value = ''
    }
  },
)

function onSubmit() {
  const no = trackingNo.value.trim()
  if (!no) {
    error.value = '请填写物流单号'
    return
  }
  error.value = ''
  emit('submit', { company: company.value.trim() || undefined, trackingNo: no })
}
</script>

<template>
  <van-popup v-model:show="visible" position="bottom" round :style="{ maxHeight: '80%' }">
    <div class="tracking-sheet">
      <div class="tracking-sheet__title">填写退货物流</div>

      <div v-if="returnAddress" class="tracking-sheet__addr">
        <div class="tracking-sheet__addr-label">请寄回至</div>
        <div class="tracking-sheet__addr-text">{{ returnAddress }}</div>
      </div>
      <div v-else class="tracking-sheet__hint">请按商家提供的地址寄回商品</div>

      <van-field
        v-model="company"
        class="tracking-sheet__field"
        label="快递公司"
        placeholder="选填，如 顺丰"
        maxlength="32"
      />
      <van-field
        v-model="trackingNo"
        class="tracking-sheet__field"
        label="物流单号"
        placeholder="必填"
        maxlength="64"
        :error-message="error"
      />

      <div class="tracking-sheet__tip">商家确认收到货后才会退款，请如实填写</div>

      <div class="tracking-sheet__actions">
        <van-button round block plain :disabled="submitting" @click="visible = false">取消</van-button>
        <van-button
          round
          block
          type="primary"
          color="rgb(107,163,200)"
          :loading="submitting"
          @click="onSubmit"
        >
          提交
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.tracking-sheet {
  padding: 16px 16px calc(16px + env(safe-area-inset-bottom));
}
.tracking-sheet__title {
  font-size: 16px;
  font-weight: 500;
  color: #323233;
  text-align: center;
}
.tracking-sheet__addr {
  margin-top: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f8fa;
}
.tracking-sheet__addr-label {
  font-size: 12px;
  color: #969799;
}
.tracking-sheet__addr-text {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: #323233;
}
.tracking-sheet__hint {
  margin-top: 16px;
  font-size: 13px;
  color: #969799;
}
.tracking-sheet__field {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f8fa;
}
.tracking-sheet__tip {
  margin-top: 10px;
  font-size: 12px;
  color: #969799;
}
.tracking-sheet__actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}
</style>
