<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { SeckillActivity, UserAddress } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchAddresses } from '@/api/address'
import { buySeckill, fetchSeckillActivities, fetchSeckillResult } from '@/api/seckill'
import { useUserStore } from '@/stores/user'
import AddressPickerSheet from '@/components/AddressPickerSheet.vue'

// 显式声明组件名：App.vue 里用 <KeepAlive include="SeckillView"> 缓存秒杀页（tab 页之一）
defineOptions({ name: 'SeckillView' })

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
/** 抢购请求发出后到拿到结果之前，禁止再次点击 / 切场次 */
const buying = ref(false)
/** 页面失活/卸载后停止轮询，避免后台页面继续打接口 */
let active = true

const selectedAddress = computed(
  () => addresses.value.find((item) => item.id === selectedAddressId.value) ?? null,
)

// ---------------- 时间与状态 ----------------

/** 解析后端返回的无时区时间（`2026-10-19T18:00:00`），按本地时区解释 */
function parseTime(value: string): number {
  return value ? new Date(value).getTime() : 0
}

function formatClock(value: string): string {
  const date = new Date(value)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

/** 带日期的时刻：`10-21 18:00`（卡片时间区用，年月在秒杀场景里是噪音，只保留月-日） */
function formatDateTime(value: string): string {
  const date = new Date(value)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day} ${formatClock(value)}`
}

/**
 * 本地按时间推导状态：到点自动从「即将开始」变「立即抢购」，不必重新请求。
 * 0 已下线 / 1 即将开始 / 2 进行中 / 3 已结束（与 B4 的卡片状态机一一对应）。
 */
function statusOf(activity: SeckillActivity): number {
  if (activity.enabled !== 1) return 0
  if (now.value < parseTime(activity.startTime)) return 1
  if (now.value < parseTime(activity.endTime)) return 2
  return 3
}

function formatDuration(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return [h, m, s].map((n) => String(n).padStart(2, '0')).join(':')
}

// ---------------- 场次分组 ----------------

interface Session {
  /** 场次开始时刻 HH:mm；同组活动的开始时间落在同一分钟 */
  time: string
  items: SeckillActivity[]
}

/**
 * 把活动按开始时刻（HH:mm）分组成「场次」。
 *
 * <p>后端没有场次表（`seckill_activity` 只有 start_time / end_time），这里纯前端推导：
 * 同一分钟开抢的活动视为同一场，按时间升序排列。这样不用改表、不用改接口，
 * 又能让顶部倒计时与下方商品产生确定的对应关系（B4 的「距结束」与任一张卡都对不上）。</p>
 */
const sessions = computed<Session[]>(() => {
  const map = new Map<string, SeckillActivity[]>()
  for (const item of activities.value) {
    const key = formatClock(item.startTime)
    const list = map.get(key)
    if (list) list.push(item)
    else map.set(key, [item])
  }
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([time, items]) => ({ time, items }))
})

/** 场次状态：同组活动开始时间一致，取第一条即可 */
function sessionStatus(session: Session): number {
  return session.items.length > 0 ? statusOf(session.items[0]) : 0
}

function sessionStatusLabel(session: Session): string {
  const status = sessionStatus(session)
  if (status === 1) return '即将开始'
  if (status === 2) return '已开抢'
  return '已结束'
}

/** van-tabs 的选中下标；数据刷新后由 load 负责收敛到合法范围 */
const activeIndex = ref(0)

const activeSession = computed<Session | null>(() => sessions.value[activeIndex.value] ?? null)

const sessionItems = computed<SeckillActivity[]>(() => activeSession.value?.items ?? [])

// ---------------- 场次切换 ----------------

/**
 * 切场次。
 *
 * <p>数据本来就在内存里（`sessions` 是 `activities` 的切片），切换只触发一次同步重渲染，
 * 不存在「清空 → 等接口 → 再填」的空窗，所以**不需要**首页分类切换那套「锁定最小高度」
 * 来防文档塌高：高度在新场次渲染的同一帧就是真实高度，加了只是死代码。</p>
 */
function onTabClick(tab: { name: string | number }) {
  onSessionClick(Number(tab.name))
}

function onSessionClick(index: number) {
  // 抢购进行中禁止切场次：切走后回来状态会错乱（按钮 loading 挂在被切走的卡片上）
  if (buying.value) return
  if (index === activeIndex.value) return
  activeIndex.value = index
}

// ---------------- 数据加载 ----------------

async function load() {
  loading.value = true
  try {
    const [list] = await Promise.all([fetchSeckillActivities(), loadAddresses()])
    activities.value = list
    // 首屏默认落在第一个「已开抢」的场次；都还没开始则第一场；都结束了则最后一场。
    // 之后刷新（onActivated / 抢购后）保持用户当前选中的场次，越界时才重新收敛。
    const started = sessions.value.findIndex((session) => sessionStatus(session) === 2)
    if (activeIndex.value >= sessions.value.length) {
      activeIndex.value = sessions.value.length > 0 ? sessions.value.length - 1 : 0
    } else if (started >= 0 && !userPickedSession) {
      activeIndex.value = started
    }
    userPickedSession = true
  } finally {
    loading.value = false
  }
}

/** 用户是否手动切过场次：切过之后刷新不再自动跳回「进行中」那一场 */
let userPickedSession = false

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

// ---------------- 卡片展示 ----------------

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

/**
 * 卡片内的场次时间区间：`10-21 00:00 ~ 10-22 00:00`（取活动自身的开抢/结束时刻）。
 *
 * <p>带日期后一行到 ~130px，和倒计时挤同一行会互相顶掉，所以卡片时间区改成两行：
 * 第一行区间、第二行倒计时（见 .sk-card__time 的 column 布局）。</p>
 */
function timeRange(activity: SeckillActivity): string {
  return `${formatDateTime(activity.startTime)} ~ ${formatDateTime(activity.endTime)}`
}

/**
 * 卡片内的倒计时文案。
 *
 * <p>原来挂在页面标题条右上角、只反映「当前选中场次」；现在下沉到每张卡片，
 * 一张卡只讲自己这场：未开始「距开抢」、进行中「距结束」、结束给静态文案。
 * 依赖 `now` 每秒刷新，所以切场次 / 到点都会自动重算。</p>
 */
function cardCountdown(activity: SeckillActivity): string {
  const status = statusOf(activity)
  if (status === 1) return `距开抢 ${formatDuration(parseTime(activity.startTime) - now.value)}`
  if (status === 2) return `距结束 ${formatDuration(parseTime(activity.endTime) - now.value)}`
  return '已结束'
}

/**
 * 是否在「走秒」中（未开始 / 进行中）。
 *
 * <p>只有真正在倒数的两种状态才标红：「已结束」是静态结论，跟着标红会让人
 * 误以为还在跑，所以保持次要灰。</p>
 */
function isCounting(activity: SeckillActivity): boolean {
  const status = statusOf(activity)
  return status === 1 || status === 2
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
    <!-- 标题条：白底吸顶，与下方场次栏组成吸顶区（两者高度必须配套：
         这里 44px，场次栏的 sticky offset-top 也是 44）。
         右上角不再放全局倒计时：时间与倒计时都下沉到各自的商品卡里。 -->
    <div class="sk-head">
      <span class="sk-head__title">限时秒杀</span>
    </div>

    <!-- 地址栏：页面级状态，一进页面就能看到、能改（三态与 B4 一致） -->
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

    <!-- 场次栏：与订单列表页同款 van-tabs（白底、品牌浅蓝下划线）。
         ★ 这里只借它做「场次导航」，内容区在 tabs 外面自己渲染 ——
           场次切换不需要滑动面板，也不需要请求，换的是同一份内存数据的切片。
         ★ 吸顶用 CSS sticky 自己写，**不用 Vant 的 `sticky` prop**：
           实测 prop 传了但 .van-sticky 的计算样式一直是 static（Vant 的 sticky
           靠 JS 在滚动后切 fixed，本项目里没被触发），而标题条 .sk-head 的
           position: sticky 是验证过能用的。top: 44px = .sk-head 的高度。 -->
    <van-tabs
      class="sk-tabs"
      :model-value="activeIndex"
      :line-width="20"
      color="rgb(107, 163, 200)"
      @click-tab="onTabClick"
    >
      <van-tab v-for="session in sessions" :key="session.time">
        <template #title>
          <span class="sk-tabs__time">{{ session.time }}</span>
          <span
            class="sk-tabs__state"
            :class="{ 'sk-tabs__state--on': sessionStatus(session) === 2 }"
          >
            {{ sessionStatusLabel(session) }}
          </span>
        </template>
      </van-tab>
    </van-tabs>

    <!-- 商品网格：双列白卡。场次数据在内存里，切场次即换切片，无需高度锁 -->
    <div class="sk-body">
      <van-loading v-if="loading && activities.length === 0" class="sk-body__loading" />
      <van-empty v-else-if="activities.length === 0" description="暂无秒杀活动" />

      <div
        v-for="item in sessionItems"
        :key="item.id"
        class="sk-card"
        :class="{ 'sk-card--sold-out': item.remainingStock <= 0 }"
      >
        <div class="sk-card__img-wrap">
          <van-image :src="item.productImage || ''" fit="cover" class="sk-card__img" />
          <!-- 限购角标：固定 30×20 的盒子，文案「限购N」不留空格（30px 宽度只够
               3 个字宽，11px 字号下正好 28px，留 1px 呼吸位） -->
          <span class="sk-card__limit">限购{{ item.limitPerUser }}</span>
          <span v-if="item.remainingStock <= 0" class="sk-card__mask">已抢光</span>
        </div>
        <div class="sk-card__body">
          <!-- 场次时间下沉到卡片：第一行「10-21 00:00 ~ 10-22 00:00」区间，
               第二行本场倒计时/状态（带日期后一行放不下，改列排） -->
          <div class="sk-card__time">
            <span class="sk-card__time-range">{{ timeRange(item) }}</span>
            <span
              class="sk-card__time-cd"
              :class="{ 'sk-card__time-cd--live': isCounting(item) }"
            >
              {{ cardCountdown(item) }}
            </span>
          </div>
          <div class="sk-card__title">{{ item.productName || item.title }}</div>
          <div class="sk-card__sub">
            {{ item.title }}<template v-if="item.specValue"> · {{ item.specValue }}</template>
          </div>
          <div class="sk-card__price">
            <span class="sk-card__price-now">¥{{ formatPrice(item.seckillPrice) }}</span>
            <span v-if="item.originalPrice" class="sk-card__price-origin">
              ¥{{ formatPrice(item.originalPrice) }}
            </span>
          </div>
          <div class="sk-card__progress">
            <div class="sk-card__progress-track">
              <div class="sk-card__progress-bar" :style="{ width: `${soldPercent(item)}%` }" />
            </div>
            <span class="sk-card__progress-text">
              {{ item.remainingStock > 0 ? `剩 ${item.remainingStock} 件` : '已抢光' }}
            </span>
          </div>
          <van-button
            class="sk-card__btn"
            round
            :class="canBuy(item) ? 'sk-card__btn--buy' : 'sk-card__btn--off'"
            :disabled="!canBuy(item) || buying"
            :loading="buying && confirming?.id === item.id"
            @click="onBuyClick(item)"
          >
            {{ buttonText(item) }}
          </van-button>
        </div>
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
            <div class="confirm__name">{{ confirming.productName || confirming.title }}</div>
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
          <van-button class="confirm__submit" block round :loading="submitting" @click="onConfirmBuy">
            确认抢购
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
/* 页面根：必须「不透明 + 至少一屏高」（与 .home / .cart / .member 同一条约定）——
   tab 互切时旧页是 fixed 留在视口里让位的（见 App.vue 的 .page-slide-tab-*），
   进入页不盖住视口的话旧页会从下面透出来。
   高度按 tabbar 的 50px 占位算（.layout--tabbar 的 padding-bottom）。 */
.seckill {
  min-height: calc(100vh - 50px);
  background: var(--van-background-2, #f7f8fa);
}

/* ── 标题条 ────────────────────────────────────────────────────────
   白底、44px、吸顶。场次栏的 sticky offset-top 依赖这个高度，改高度必须同步改模板里的 44。 */
.sk-head {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 0.5px solid #f2f3f5;
}

.sk-head__title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}

/* ── 地址栏（沿用 B4 的白条结构，只把提示底色换成浅蓝） ── */
.addr-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 0.5px solid #f2f3f5;
}

.addr-bar--hint {
  background: #f0f7fb;
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
  font-weight: 500;
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

/* ── 场次栏 ────────────────────────────────────────────────────────
   与订单列表页的 van-tabs 同款，只是把 title 换成两行（时刻 + 状态）。
   --van-tabs-line-height 放大到 52px 以容纳两行文字；下划线由 Vant 自己定位。

   ★ 吸顶：CSS sticky，top = .sk-head 的 44px（两者组成吸顶区）。
   不用 Vant 的 `sticky` prop —— 实测该 prop 下 .van-sticky 的计算样式恒为 static，
   滚动后不会切 fixed（本项目里 .sk-head 的 position: sticky 验证可用，故同一套写法）。
   z-index 2：高于卡片，低于标题条（3）。 */
.sk-tabs {
  position: sticky;
  top: 44px;
  z-index: 2;
  --van-tabs-line-height: 52px;
  background: #fff;
}

/* 两行标题：时刻 14/500，状态 10/400；进行中那一场状态字用品牌蓝 */
/* 两行标题：时刻 14/500，状态 10/400；进行中那一场状态字用品牌蓝。
   ★ 必须把 .van-tab__text 改成 block 并放开高度：它默认是 inline 级盒子，
     高度只按一行算（实测 18px）且 overflow: hidden，第二行会被整行裁掉
     （表现为标题只剩一行、尾部出现省略号点）。 */
.sk-tabs :deep(.van-tab__text) {
  display: block;
  height: auto;
  padding: 4px 0;
  line-height: normal;
}

.sk-tabs__time {
  display: block;
  font-size: 14px;
  line-height: 18px;
  color: #323233;
}

.sk-tabs__state {
  display: block;
  font-size: 10px;
  line-height: 14px;
  color: #969799;
}

.sk-tabs__state--on {
  color: rgb(107, 163, 200);
}

/* 场次栏自身白底、下边发丝线；吸顶后要盖住下面的卡片 */
.sk-tabs :deep(.van-tabs__wrap) {
  border-bottom: 0.5px solid #f2f3f5;
}

/* ── 商品网格 ── */
.sk-body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 12px;
}

.sk-body__loading {
  display: block;
  grid-column: 1 / -1;
  margin: 48px auto;
}

/* 空态也要跨两列，否则会被挤在左半格 */
.sk-body :deep(.van-empty) {
  grid-column: 1 / -1;
}

.sk-card {
  overflow: hidden;
  border-radius: 10px;
  background: #fff;
}

/* 已抢光整卡降透明（同购物车失效项 .cart-item--invalid），图片另有蒙层 */
.sk-card--sold-out {
  opacity: 0.6;
}

.sk-card__img-wrap {
  position: relative;
  aspect-ratio: 1;
  background: #f2f3f5;
}

.sk-card__img {
  width: 100%;
  height: 100%;
}

/* 限购角标：品牌浅蓝底白字，贴在图片左上角。
   ★ 尺寸由 **一个变量** 推导，三者锁死在同一比例上（原设计 30:20:11）：
       --sk-badge-h  →  height
       × 1.5         →  min-width（30/20 = 1.5，角标是 3:2 的扁盒子）
       × 0.55        →  font-size（11/20 = 0.55）
     以后想放大角标只改 --sk-badge-h 一个值，宽和字号自动跟着走，
     不会出现「盒子变大了字还是原来那么小」的脱节。
   ★ 当前 24px → 36×24、字号 13.2px：比最初的 30×20/11px 整体放大 1.2 倍，
     一位数「限购1」在 13.2px 下约 33.6px，36px 宽刚好留出左右各 1px 的呼吸位
     （30×20 时是 0 呼吸位，字贴边）。
   ★ min-width 而不是 width：两位数「限购10」约 41.5px 时自动撑宽，不裁字。
   ★ overflow: hidden 仍留着做最后兜底（极端长文案不许盖住商品图）。 */
.sk-card__limit {
  --sk-badge-h: 24px;
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: calc(var(--sk-badge-h) * 1.5);
  height: var(--sk-badge-h);
  border-radius: 0 0 8px 0;
  background: rgb(107, 163, 200);
  color: #fff;
  font-size: calc(var(--sk-badge-h) * 0.55);
  line-height: 1;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
}

/* 抢光蒙层：盖在图片上，文案居中 */
.sk-card__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.6);
  color: #969799;
  font-size: 12px;
}

.sk-card__body {
  padding: 8px 10px 10px;
}

/* ── 卡片内的场次时间 ──
   两行：第一行「10-21 00:00 ~ 10-22 00:00」区间（10px 次要灰），
   第二行本场倒计时（11px，走秒时标红）。
   ★ 为什么不再挤一行：带上日期后区间约 126px，卡片内容区只有 158px，
     再和倒计时抢同一行必有一方被截断。列排后两段各占一行，谁都不用省略号。
   数字用 tabular-nums 等宽，每秒跳动时不会左右抖。 */
.sk-card__time {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 4px;
  font-variant-numeric: tabular-nums;
}

/* 时间区间：13px 正文黑（#323233，与商品名同色，从「次要灰」升级为可读信息），
   单行不换行；宽度不够时省略号兜底（正常数据 158px 内能放下，见 §11.9 实测） */
.sk-card__time-range {
  font-size: 13px;
  line-height: 16px;
  color: #323233;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 默认灰：只给「已结束」这类静态结论用 */
.sk-card__time-cd {
  font-size: 12px;
  line-height: 16px;
  color: #969799;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 走秒中（距开抢 / 距结束）：红色强调紧迫感。
   ★ 这里用 Vant 的标准红 #ee0a24，与页面其它装饰色（品牌浅蓝）刻意区分：
     蓝色是「品牌/可点」，红色只表示「时间在跑」。12px 小字加 500 才压得住。 */
.sk-card__time-cd--live {
  color: #ee0a24;
  font-weight: 500;
}

.sk-card__title {
  font-size: 15px;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sk-card__sub {
  margin-top: 2px;
  font-size: 12px;
  color: #969799;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sk-card__price {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-top: 5px;
}

/* 价格用正文色（与 ProductCard 同源，勿改回红色） */
.sk-card__price-now {
  font-size: 16px;
  font-weight: 500;
  color: #323233;
}

/* 划线原价（秒杀前的价格）：与副行同灰 #969799，比秒杀价更「退后」 */
.sk-card__price-origin {
  font-size: 11px;
  color: #969799;
  text-decoration: line-through;
}

.sk-card__progress {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 7px;
}

.sk-card__progress-track {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: #f2f3f5;
  overflow: hidden;
}

/* 进度条填充：中性灰 #969799（与划线原价、副行同一灰阶）。
   ★ 从品牌浅蓝改为灰色后，蓝色在卡片里只剩「立即抢购」按钮一个落点，
     进度条的语义变成「售出量」这一客观数据，不再抢按钮的视觉优先级。
     轨道保持 #f2f3f5，靠明度差区分已售/未售。 */
.sk-card__progress-bar {
  height: 100%;
  border-radius: 2px;
  background: #969799;
  transition: width 0.3s;
}

.sk-card__progress-text {
  flex: none;
  font-size: 11px;
  color: #969799;
  white-space: nowrap;
}

/* 卡片按钮：通栏小胶囊，高 30px。
   ★ 不用 type="primary"：Vant 的 primary 会把禁用态也染成蓝，而这里「未开始 / 已抢光」
     必须是灰底。两个修饰类直接给背景与文字色，禁用时的透明度由 Vant 自己处理。 */
.sk-card__btn {
  width: 100%;
  height: 30px;
  margin-top: 8px;
  font-size: 13px;
}

.sk-card__btn--buy {
  --van-button-primary-background: rgb(107, 163, 200);
  --van-button-primary-border-color: rgb(107, 163, 200);
  border-color: rgb(107, 163, 200);
  background: rgb(107, 163, 200);
  color: #fff;
  font-weight: 600;
}

.sk-card__btn--off {
  border-color: #f2f3f5;
  background: #f2f3f5;
  color: #969799;
}

/* ── 确认层（结构沿用 B4，只换色） ── */
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
  font-weight: 500;
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
  font-weight: 500;
  color: #323233;
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

/* 确认键：品牌浅蓝底白字 600（与「结算」「退出登录」同一套变量覆盖方式） */
.confirm__submit {
  --van-button-primary-background: rgb(107, 163, 200);
  --van-button-primary-border-color: rgb(107, 163, 200);
  border-color: rgb(107, 163, 200);
  background: rgb(107, 163, 200);
  color: #fff;
  font-weight: 600;
}
</style>
