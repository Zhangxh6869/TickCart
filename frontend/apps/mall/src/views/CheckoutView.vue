<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { UserAddress } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchAddresses } from '@/api/address'
import { createOrder } from '@/api/order'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const cartStore = useCartStore()

const addresses = ref<UserAddress[]>([])
const selectedAddressId = ref<number | null>(null)
const remark = ref('')
const submitting = ref(false)
const addressPopupVisible = ref(false)

const selectedAddress = computed(
  () => addresses.value.find((item) => item.id === selectedAddressId.value) ?? null
)
const items = computed(() => cartStore.checkedItems)
const totalAmount = computed(() => cartStore.checkedAmount)
const totalQuantity = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

onMounted(async () => {
  await cartStore.load()
  // 没有可结算的商品时退回购物车，避免停留在空结算页
  if (cartStore.checkedItems.length === 0) {
    showToast('请先选择要结算的商品')
    router.replace({ name: 'cart' })
    return
  }
  await loadAddresses()
})

async function loadAddresses() {
  try {
    addresses.value = await fetchAddresses()
    const preferred = addresses.value.find((item) => item.isDefault === 1) ?? addresses.value[0]
    selectedAddressId.value = preferred ? preferred.id : null
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

function fullAddress(address: UserAddress) {
  return `${address.province}${address.city}${address.district}${address.detail}`
}

function chooseAddress(address: UserAddress) {
  selectedAddressId.value = address.id
  addressPopupVisible.value = false
}

function goAddressManage() {
  addressPopupVisible.value = false
  router.push({ name: 'address' })
}

async function onSubmit() {
  if (!selectedAddress.value) {
    showToast('请先选择收货地址')
    return
  }
  submitting.value = true
  try {
    const result = await createOrder({
      addressId: selectedAddress.value.id,
      remark: remark.value.trim() || undefined,
    })
    showToast('下单成功')
    await cartStore.refreshCount()
    router.replace({ name: 'order-detail', params: { orderNo: result.orderNo } })
  } catch {
    // 库存不足等错误已由拦截器提示，刷新购物车以反映最新库存
    await cartStore.load()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="checkout">
    <van-nav-bar title="确认订单" left-arrow @click-left="router.back()" />

    <div class="checkout__body">
      <div class="card" @click="addressPopupVisible = true">
        <template v-if="selectedAddress">
          <div class="card__row">
            <span class="card__name">{{ selectedAddress.receiver }}</span>
            <span class="card__phone">{{ selectedAddress.phone }}</span>
          </div>
          <div class="card__addr">{{ fullAddress(selectedAddress) }}</div>
        </template>
        <div v-else class="card__empty">请选择收货地址</div>
        <van-icon name="arrow" class="card__arrow" />
      </div>

      <div class="card card--goods">
        <div v-for="item in items" :key="item.id" class="goods">
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

        <van-field
          v-model="remark"
          label="备注"
          placeholder="选填，如对配送的要求"
          maxlength="255"
          class="checkout__remark"
        />
      </div>

      <div class="card">
        <!-- 配送方式：当前只有一种（快递配送、免运费），按展示型处理，不落库、不加订单字段 -->
        <div class="summary">
          <span>配送方式</span>
          <span>快递配送 · 免运费</span>
        </div>
        <div class="summary">
          <span>商品件数</span>
          <span>{{ totalQuantity }} 件</span>
        </div>
        <div class="summary">
          <span>商品金额</span>
          <span>¥{{ formatPrice(totalAmount) }}</span>
        </div>
        <div class="summary summary--total">
          <span>实付款</span>
          <span class="summary__amount">¥{{ formatPrice(totalAmount) }}</span>
        </div>
      </div>
    </div>

    <van-submit-bar
      class="checkout__bar"
      :price="Math.round(totalAmount * 100)"
      button-text="提交订单"
      button-color="rgb(107,163,200)"
      :loading="submitting"
      @submit="onSubmit"
    />

    <van-popup v-model:show="addressPopupVisible" position="bottom" round :style="{ maxHeight: '70%' }">
      <div class="addr-popup">
        <div class="addr-popup__title">选择收货地址</div>
        <div
          v-for="item in addresses"
          :key="item.id"
          class="addr-popup__item"
          :class="{ 'addr-popup__item--active': item.id === selectedAddressId }"
          @click="chooseAddress(item)"
        >
          <div class="card__row">
            <span class="card__name">{{ item.receiver }}</span>
            <span class="card__phone">{{ item.phone }}</span>
            <van-tag v-if="item.isDefault === 1" type="danger" plain>默认</van-tag>
          </div>
          <div class="card__addr">{{ fullAddress(item) }}</div>
        </div>
        <van-empty v-if="addresses.length === 0" description="还没有收货地址" />
        <div class="addr-popup__actions">
          <van-button block round plain type="primary" @click="goAddressManage">管理收货地址</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.checkout {
  min-height: 100vh;
  padding-bottom: 50px;
  background: var(--van-background-2, #f7f8fa);
}
.checkout__body {
  padding: 10px 12px 0;
}
.card {
  position: relative;
  padding: 14px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: #fff;
}
.card--goods {
  padding: 14px 0 0;
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
.card__empty {
  font-size: 14px;
  color: #969799;
}
.card__arrow {
  position: absolute;
  top: 50%;
  right: 14px;
  transform: translateY(-50%);
  color: #c8c9cc;
}
.goods {
  display: flex;
  gap: 10px;
  padding: 0 14px 12px;
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
.checkout__remark {
  border-top: 1px solid #f2f3f5;
}
.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #646566;
  padding: 4px 0;
}
.summary--total {
  margin-top: 6px;
  padding-top: 10px;
  border-top: 1px solid #f2f3f5;
  font-size: 14px;
  color: #323233;
}
.summary__amount {
  font-size: 17px;
  font-weight: 500;
  color: #323233;
}
/* 提交栏抬到 tabbar 之上，与购物车页的 .cart__bar 保持同一套写法。
   别写 bottom: 0 —— van-submit-bar 自带 z-index: 100，会整块压住 tabbar，
   结算页的「首页 / 购物车 / 我的」就完全点不到了（实测点 tabbar 的坐标命中的
   是提交栏里的价格文本，真点一下会直接下单）。
   底部留白不用另外加：.layout--tabbar 的 50px 内边距 + .checkout 自身的
   padding-bottom: 50px，正好等于「提交栏 50 + tabbar 50」。 */
.checkout__bar {
  bottom: 50px;
}
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
.addr-popup__actions {
  margin-top: 12px;
}
</style>
