<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { SeckillActivity, UserAddress } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchAddresses } from '@/api/address'
import { buySeckill, fetchSeckillActivities, fetchSeckillResult } from '@/api/seckill'
import { useUserStore } from '@/stores/user'
import PageNavBar from '@/components/PageNavBar.vue'
import AddressPickerSheet from '@/components/AddressPickerSheet.vue'

const router = useRouter()
const userStore = useUserStore()

const activities = ref<SeckillActivity[]>([])
const loading = ref(false)

const addresses = ref<UserAddress[]>([])
const selectedAddressId = ref<number | null>(null)
const addressSheetVisible = ref(false)
const addressHint = ref(false)

/** 每秒刷新，驱动倒计时与按钮状态重算 */
const now = ref(Date.now())
let tickTimer: ReturnType<typeof setInterval> | undefined

/** 待确认的抢购活动 */
const confirming = ref<SeckillActivity | null>(null)
const submitting = ref(false)
/** 抢购请求发出后到拿到结果之前，禁止再次点击 */
const buying = ref(false)
/** 页面失活/卸载后停止轮询，避免后台页面继续打接口 */
let active = true

const selectedAddress = computed(
  () => addresses.value.find((item) => item.id === selectedAddressId.value) ?? null,
)

/** 顶部倒计时：取最近一个「未结束」活动的结束时间 */
const nearestEndAt = computed(() => {
  const timestamps = activities.value
    .map((item) => parseTime(item.endTime))
    .filter((ts) => ts > now.value)
  return timestamps.length > 0 ? Math.min(...timestamps) : 0
})

const countdownText = computed(() => {
  if (nearestEndAt.value === 0) return ''
  return formatDuration(nearestEndAt.value - now.value)
})

async function load() {
  loading.value = true
  try {
    const [list] = await Promise.all([fetchSeckillActivities(), loadAddresses()])
    activities.value = list
  } finally {
    loading.value = false
  }
}

/**
 * 加载地址并默认选中默认地址。
 *
 * <p>不做跨页面记忆：秒杀是「当下这一次」寄到哪里，记住上次选择反而容易让人误以为改过了。</p>
 */
async function loadAddresses() {
  if (!userStore.isLoggedIn) {
    addresses.value = []
    selectedAddressId.value = null
    return
  }
  try {
    addresses.value = await fetchAddresses()
    const fallback = addresses.value.find((item) => item.isDefault === 1) ?? addresses.value[0]
    selectedAddressId.value = fallback ? fallback.id : null
  } catch {
    addresses.value = []
  }
}

onMounted(() => {
  void load()
  tickTimer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onActivated(() => {
  active = true
  void load()
})

onDeactivated(() => {
  active = false
})

onUnmounted(() => {
  active = false
  if (tickTimer) clearInterval(tickTimer)
})

// ---------------- 状态推导 ----------------

/** 解析后端返回的无时区时间（`2026-10-19T18:00:00`），按本地时区解释 */
function parseTime(value: string): number {
  return value ? new Date(value).getTime() : 0
}

function formatDuration(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return [h, m, s].map((n) => String(n).padStart(2, '0')).join(':')
}

function formatClock(value: string): string {
  const date = new Date(value)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

/** 本地按时间推导状态：到点自动从「即将开始」变「立即抢购」，不必重新请求 */
function statusOf(activity: SeckillActivity): number {
  if (activity.enabled !== 1) return 0
  if (now.value < parseTime(activity.startTime)) return 1
  if (now.value < parseTime(activity.endTime)) return 2
  return 3
}

function canBuy(activity: SeckillActivity): boolean {
  return statusOf(activity) === 2 && activity.remainingStock > 0
}

function buttonText(activity: SeckillActivity): string {
  const status = statusOf(activity)
  if (status === 1) return `${formatClock(activity.startTime)} 开抢`
  if (status === 3 || status === 0) return '已结束'
  if (activity.remainingStock <= 0) return '已抢光'
  return '立即抢购'
}

/** 已抢比例：库存 0 时按 100% 展示，避免除零 */
function soldPercent(activity: SeckillActivity): number {
  const total = activity.seckillStock || 0
  if (total <= 0) return 100
  const sold = Math.max(0, total - activity.remainingStock)
  return Math.min(100, Math.round((sold / total) * 100))
}

function maskPhone(phone: string): string {
  return phone && phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone ?? ''
}

function fullAddress(address: UserAddress): string {
  return `${address.province}${address.city}${address.district}${address.detail}`
}

// ---------------- 地址 ----------------

function openAddressSheet() {
  if (!userStore.isLoggedIn) {
    goLogin()
    return
  }
  if (addresses.value.length === 0) {
    // 一个地址都没有：直接去地址页新增，少一层空弹层
    router.push({ name: 'address' })
    return
  }
  addressSheetVisible.value = true
}

function chooseAddress(address: UserAddress) {
  selectedAddressId.value = address.id
  addressSheetVisible.value = false
  addressHint.value = false
}

function goAddressManage() {
  addressSheetVisible.value = false
  router.push({ name: 'address' })
}

// ---------------- 抢购 ----------------

function goLogin() {
  router.push({ name: 'login', query: { redirect: '/seckill' } })
}

function onBuyClick(activity: SeckillActivity) {
  if (!canBuy(activity)) return
  if (!userStore.isLoggedIn) {
    goLogin()
    return
  }
  if (!selectedAddress.value) {
    // 未选地址不弹确认层：直接提示并高亮地址栏，少一次无意义的弹层开合
    showToast('请选择收货地址')
    addressHint.value = true
    return
  }
  confirming.value = activity
}

async function onConfirmBuy() {
  const activity = confirming.value
  const address = selectedAddress.value
  if (!activity || !address || submitting.value) return

  submitting.value = true
  buying.value = true
  try {
    const accepted = await buySeckill(activity.id, { addressId: address.id, quantity: 1 })
    confirming.value = null
    const result = await waitForResult(accepted.requestId)
    if (!active) return

    if (!result) {
      showToast({ message: '仍在排队，可稍后在订单列表查看', duration: 2000 })
      return
    }
    if (result.status === 'SUCCESS' && result.orderNo) {
      showToast({ message: '抢购成功', duration: 1200 })
      router.push({ name: 'order-detail', params: { orderNo: result.orderNo } })
      return
    }
    showToast(result.message || '抢购失败')
  } catch {
    // 售罄/限购/地址无效等已由拦截器提示
  } finally {
    submitting.value = false
    buying.value = false
    // 无论成败都刷新一次：失败多半是库存或限购变化，成功则离开页面
    void load()
  }
}

/**
 * 轮询抢购结果。
 *
 * <p>指数退避：绝大多数订单 1 秒内建好，退避能让 90% 的请求在 3 次内结束；
 * 15 秒仍未出结果就放弃，避免用户挂着页面一直烧请求。</p>
 */
async function waitForResult(requestId: string) {
  const delays = [200, 400, 800, 1600, 2000]
  const deadline = Date.now() + 15000
  let attempt = 0
  while (active && Date.now() < deadline) {
    await sleep(delays[Math.min(attempt, delays.length - 1)])
    attempt++
    try {
      return await fetchSeckillResult(requestId)
    } catch {
      // 结果还没写出来（404），继续等
    }
  }
  return null
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
</script>

<template>
  <div class="seckill">
    <PageNavBar title="限时秒杀" @click-left="router.back()">
      <template #right>
        <span v-if="countdownText" class="seckill__countdown">距结束 {{ countdownText }}</span>
      </template>
    </PageNavBar>

    <!-- 地址栏：页面级状态，一进页面就能看到、能改 -->
    <div
      class="addr-bar"
      :class="{ 'addr-bar--hint': addressHint }"
      @click="openAddressSheet"
    >
      <svg class="addr-bar__icon" viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M12 2c-3.87 0-7 3.13-7 7 0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5z"
          fill="currentColor"
        />
      </svg>
      <div class="addr-bar__body">
        <template v-if="selectedAddress">
          <div class="addr-bar__row">
            <span class="addr-bar__name">{{ selectedAddress.receiver }}</span>
            <span class="addr-bar__phone">{{ maskPhone(selectedAddress.phone) }}</span>
          </div>
          <div class="addr-bar__detail">{{ fullAddress(selectedAddress) }}</div>
        </template>
        <div v-else class="addr-bar__placeholder">
          {{ addresses.length === 0 && userStore.isLoggedIn ? '请添加收货地址' : '请选择收货地址' }}
        </div>
      </div>
      <van-icon name="arrow" class="addr-bar__arrow" />
    </div>

    <div class="seckill__body">
      <van-loading v-if="loading && activities.length === 0" class="seckill__loading" />
      <van-empty v-else-if="activities.length === 0" description="暂无秒杀活动" />

      <div v-for="item in activities" :key="item.id" class="card">
        <div class="card__main">
          <van-image :src="item.productImage || ''" fit="cover" class="card__img" />
          <div class="card__info">
            <div class="card__title">{{ item.title }}</div>
            <div class="card__sub">{{ item.productName }} {{ item.specValue || '' }}</div>
            <div class="card__price">
              <span class="card__price-now">¥{{ formatPrice(item.seckillPrice) }}</span>
              <span class="card__price-origin">¥{{ formatPrice(item.originalPrice) }}</span>
            </div>
            <div class="card__progress">
              <div class="card__progress-track">
                <div class="card__progress-bar" :style="{ width: `${soldPercent(item)}%` }" />
              </div>
              <span class="card__progress-text">
                {{ item.remainingStock > 0 ? `剩 ${item.remainingStock} 件` : '已抢光' }}
              </span>
            </div>
          </div>
        </div>
        <van-button
          class="card__btn"
          round
          :type="canBuy(item) ? 'danger' : 'default'"
          :disabled="!canBuy(item) || buying"
          :loading="buying && confirming?.id === item.id"
          @click="onBuyClick(item)"
        >
          {{ buttonText(item) }}
        </van-button>
      </div>
    </div>

    <AddressPickerSheet
      v-model:show="addressSheetVisible"
      :addresses="addresses"
      :selected-id="selectedAddressId"
      @select="chooseAddress"
      @manage="goAddressManage"
    />

    <!-- 抢购确认：商品/价格/地址只读展示，地址在页面上选 -->
    <van-popup
      :show="confirming !== null"
      position="bottom"
      round
      @update:show="(value: boolean) => { if (!value) confirming = null }"
    >
      <div v-if="confirming" class="confirm">
        <div class="confirm__title">确认抢购</div>
        <div class="confirm__goods">
          <van-image :src="confirming.productImage || ''" fit="cover" class="confirm__img" />
          <div class="confirm__goods-info">
            <div class="confirm__name">{{ confirming.productName }}</div>
            <div class="confirm__spec">{{ confirming.specValue || '无规格' }} × 1</div>
            <div class="confirm__price">¥{{ formatPrice(confirming.seckillPrice) }}</div>
          </div>
        </div>
        <div class="confirm__addr">
          <span class="confirm__label">寄送至</span>
          <span class="confirm__addr-text">
            {{ selectedAddress ? `${selectedAddress.receiver} ${fullAddress(selectedAddress)}` : '未选择' }}
          </span>
        </div>
        <div class="confirm__actions">
          <van-button block round plain @click="confirming = null">取消</van-button>
          <van-button
            block
            round
            type="danger"
            :loading="submitting"
            @click="onConfirmBuy"
          >
            确认抢购
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.seckill {
  min-height: 100vh;
  background: #f7f8fa;
}

.seckill__countdown {
  font-size: 13px;
  color: #ee0a24;
  font-variant-numeric: tabular-nums;
}

/* 地址栏 */
.addr-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #f2f3f5;
}

.addr-bar--hint {
  background: #fff7f7;
  animation: addr-shake 0.4s;
}

@keyframes addr-shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  75% { transform: translateX(4px); }
}

.addr-bar__icon {
  width: 18px;
  height: 18px;
  flex: none;
  color: rgb(107, 163, 200);
}

.addr-bar__body {
  flex: 1;
  min-width: 0;
}

.addr-bar__row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.addr-bar__name {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}

.addr-bar__phone {
  font-size: 13px;
  color: #646566;
}

.addr-bar__detail {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.4;
  color: #969799;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.addr-bar__placeholder {
  font-size: 14px;
  color: #969799;
}

.addr-bar__arrow {
  flex: none;
  color: #c8c9cc;
}

/* 活动列表 */
.seckill__body {
  padding: 12px;
}

.seckill__loading {
  display: block;
  margin: 48px auto;
}

.card {
  padding: 12px;
  margin-bottom: 12px;
  border-radius: 12px;
  background: #fff;
}

.card__main {
  display: flex;
  gap: 12px;
}

.card__img {
  width: 96px;
  height: 96px;
  flex: none;
  border-radius: 8px;
  overflow: hidden;
  background: #f2f3f5;
}

.card__info {
  flex: 1;
  min-width: 0;
}

.card__title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__sub {
  margin-top: 4px;
  font-size: 12px;
  color: #969799;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__price {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 6px;
}

.card__price-now {
  font-size: 18px;
  font-weight: 700;
  color: #ee0a24;
}

.card__price-origin {
  font-size: 12px;
  color: #c8c9cc;
  text-decoration: line-through;
}

.card__progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.card__progress-track {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: #ffe1e1;
  overflow: hidden;
}

.card__progress-bar {
  height: 100%;
  border-radius: 3px;
  background: #ee0a24;
  transition: width 0.3s;
}

.card__progress-text {
  font-size: 11px;
  color: #969799;
  white-space: nowrap;
}

.card__btn {
  width: 100%;
  margin-top: 12px;
}

/* 确认层 */
.confirm {
  padding: 16px 16px calc(16px + env(safe-area-inset-bottom));
}

.confirm__title {
  margin-bottom: 16px;
  text-align: center;
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}

.confirm__goods {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  background: #f7f8fa;
}

.confirm__img {
  width: 72px;
  height: 72px;
  flex: none;
  border-radius: 8px;
  overflow: hidden;
}

.confirm__goods-info {
  flex: 1;
  min-width: 0;
}

.confirm__name {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}

.confirm__spec {
  margin-top: 4px;
  font-size: 12px;
  color: #969799;
}

.confirm__price {
  margin-top: 6px;
  font-size: 16px;
  font-weight: 700;
  color: #ee0a24;
}

.confirm__addr {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.5;
}

.confirm__label {
  flex: none;
  color: #969799;
}

.confirm__addr-text {
  color: #323233;
}

.confirm__actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}
</style>
