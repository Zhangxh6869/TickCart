<script setup lang="ts">
import { computed } from 'vue'
import type { UserAddress } from '@tickcart/shared-types'

/**
 * 收货地址选择弹层。
 *
 * <p>从结算页的 `.addr-popup` 抽出来共用：秒杀页与结算页的交互完全一致
 * （点条目选中、底部「管理收货地址」跳地址页）。</p>
 */
const props = defineProps<{
  show: boolean
  addresses: UserAddress[]
  selectedId: number | null
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  select: [address: UserAddress]
  manage: []
}>()

const visible = computed({
  get: () => props.show,
  set: (value: boolean) => emit('update:show', value),
})

function fullAddress(address: UserAddress): string {
  return `${address.province}${address.city}${address.district}${address.detail}`
}
</script>

<template>
  <van-popup v-model:show="visible" position="bottom" round :style="{ maxHeight: '70%' }">
    <div class="addr-popup">
      <div class="addr-popup__title">选择收货地址</div>
      <div
        v-for="item in addresses"
        :key="item.id"
        class="addr-popup__item"
        :class="{ 'addr-popup__item--active': item.id === selectedId }"
        @click="emit('select', item)"
      >
        <div class="addr-popup__row">
          <span class="addr-popup__name">{{ item.receiver }}</span>
          <span class="addr-popup__phone">{{ item.phone }}</span>
          <van-tag v-if="item.isDefault === 1" type="danger" plain>默认</van-tag>
        </div>
        <div class="addr-popup__detail">{{ fullAddress(item) }}</div>
      </div>
      <van-empty v-if="addresses.length === 0" description="还没有收货地址" />
      <div class="addr-popup__actions">
        <van-button block round plain type="primary" @click="emit('manage')">
          管理收货地址
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.addr-popup {
  padding: 16px 12px calc(16px + env(safe-area-inset-bottom));
}

.addr-popup__title {
  margin-bottom: 12px;
  text-align: center;
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}

.addr-popup__item {
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #f7f8fa;
}

.addr-popup__item--active {
  border-color: rgb(107, 163, 200);
  background: rgba(107, 163, 200, 0.08);
}

.addr-popup__row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.addr-popup__name {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}

.addr-popup__phone {
  font-size: 13px;
  color: #646566;
}

.addr-popup__detail {
  font-size: 13px;
  line-height: 1.5;
  color: #646566;
}

.addr-popup__actions {
  margin-top: 12px;
}
</style>
