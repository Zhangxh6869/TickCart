<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { DailyPoint, DashboardVO, LowStockItem, SeckillActivity } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchDashboard } from '@/api/dashboard'
import TrendChart from '@/components/TrendChart.vue'

/** 品牌浅蓝：订单量趋势 */
const BRAND = '#6ba3c8'
/** 红：成交额趋势 / 库存告急 */
const DANGER = '#f56c6c'

/** 秒杀状态标签配色（与秒杀管理页一致：进行中用 danger 表达紧迫感） */
const STATUS_TAG: Record<number, 'primary' | 'success' | 'info' | 'warning' | 'danger'> = {
  0: 'info',
  1: 'warning',
  2: 'danger',
  3: 'info',
}

const loading = ref(false)
const days = ref(7)
const dash = ref<DashboardVO | null>(null)
const router = useRouter()

/**
 * 一次取两倍天数：后 N 天当「本期」，前 N 天当「上期」，用来算涨跌幅。
 * 后端上限 90 天，days 最大 30 => 60，不会触顶。
 */
async function load() {
  loading.value = true
  try {
    dash.value = await fetchDashboard(days.value * 2)
  } finally {
    loading.value = false
  }
}

function switchDays(d: number) {
  if (days.value === d) return
  days.value = d
  load()
}

onMounted(load)

/* ---------------- KPI ---------------- */

const order = computed(() => dash.value?.order)
const paidAmount = computed(() => order.value?.paidAmount ?? 0)
const paidCount = computed(() => order.value?.paidOrderCount ?? 0)
const avgOrder = computed(() => (paidCount.value > 0 ? paidAmount.value / paidCount.value : 0))

const seckill = computed(() => dash.value?.seckill)
const ongoing = computed(() => seckill.value?.ongoing ?? 0)
const pending = computed(() => seckill.value?.pending ?? 0)
const soldQuantity = computed(() => seckill.value?.soldQuantity ?? 0)
const seckillPaid = computed(() => seckill.value?.paidAmount ?? 0)

/** 库存告急是 SKU 级（ProductService.lowStock），total 才是真实 SKU 数 */
const lowStockTotal = computed(() => dash.value?.lowStock?.total ?? 0)
const outOfStock = computed(() => dash.value?.lowStock?.outOfStock ?? 0)

/** 进行中秒杀结束倒计时 */
const ongoingEndText = computed(() => {
  const t = seckill.value?.ongoingEndTime
  if (!t) return ''
  const diff = new Date(t).getTime() - Date.now()
  if (diff <= 0) return '即将结束'
  return spanText(diff)
})

/* ---------------- 趋势 + 涨跌幅 ---------------- */

/** 把「两倍天数」的序列切成 上期 / 本期 */
function splitTrend(all: DailyPoint[]): { prev: DailyPoint[]; cur: DailyPoint[] } {
  const n = days.value
  if (all.length < n * 2) return { prev: [], cur: all.slice(-n) }
  return { prev: all.slice(0, n), cur: all.slice(-n) }
}

const orderParts = computed(() => splitTrend(dash.value?.orderTrend ?? []))
const amountParts = computed(() => splitTrend(dash.value?.amountTrend ?? []))

const orderCur = computed(() => orderParts.value.cur)
const amountCur = computed(() => amountParts.value.cur)
const orderSum = computed(() => orderCur.value.reduce((s, p) => s + p.count, 0))
const amountSum = computed(() => amountCur.value.reduce((s, p) => s + p.amount, 0))

interface Delta {
  text: string
  dir: 'up' | 'down' | 'flat'
}

/** 环比：上期为 0 时不算百分比（除零无意义），改为「新增 / 持平」 */
function deltaOf(cur: number, prev: number, hasPrev: boolean): Delta {
  if (!hasPrev) return { text: '—', dir: 'flat' }
  if (prev <= 0) {
    return cur > 0 ? { text: '较上周期新增', dir: 'up' } : { text: '与上周期持平', dir: 'flat' }
  }
  const pct = Math.round(((cur - prev) / prev) * 100)
  if (pct === 0) return { text: '与上周期持平', dir: 'flat' }
  return { text: `较上周期 ${pct > 0 ? '↑' : '↓'}${Math.abs(pct)}%`, dir: pct > 0 ? 'up' : 'down' }
}

const orderDelta = computed(() =>
  deltaOf(
    orderSum.value,
    orderParts.value.prev.reduce((s, p) => s + p.count, 0),
    orderParts.value.prev.length > 0,
  ),
)
const amountDelta = computed(() =>
  deltaOf(
    amountSum.value,
    amountParts.value.prev.reduce((s, p) => s + p.amount, 0),
    amountParts.value.prev.length > 0,
  ),
)

/** 折线图数据：把 DailyPoint 映射成图表需要的 day/value */
const orderSeries = computed(() => orderCur.value.map((p) => ({ day: p.day, value: p.count })))
const amountSeries = computed(() => amountCur.value.map((p) => ({ day: p.day, value: p.amount })))

/** 悬浮提示的数值格式（模板里不能写 TS 类型注解，所以放 script） */
const fmtCount = (v: number) => `${v} 单`
const fmtAmount = (v: number) => `¥${formatPrice(v)}`

/* ---------------- 秒杀战报 ---------------- */

const activities = computed<SeckillActivity[]>(() => dash.value?.seckillActivities ?? [])

/** 库存告急明细（SKU 级） */
const lowItems = computed<LowStockItem[]>(() => dash.value?.lowStock?.items ?? [])

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

/** 时长文案：天/时/分，用于倒计时与距开抢 */
function spanText(ms: number): string {
  if (ms <= 0) return '0 分'
  const d = Math.floor(ms / 86400000)
  const h = Math.floor((ms % 86400000) / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  if (d > 0) return `${d} 天 ${h} 时`
  if (h > 0) return `${pad2(h)}:${pad2(m)}`
  return `${m} 分`
}

/** 战报行右侧的时间文案：进行中显示剩余，待开始显示距开抢，终态显示状态 */
function activityTime(a: SeckillActivity): { label: string; strong: boolean } {
  const now = Date.now()
  if (a.status === 2) {
    return { label: `剩 ${spanText(new Date(a.endTime).getTime() - now)}`, strong: true }
  }
  if (a.status === 1) {
    return { label: `距开抢 ${spanText(new Date(a.startTime).getTime() - now)}`, strong: false }
  }
  return { label: a.status === 3 ? '已结束' : '已下线', strong: false }
}

/** 库存剩余比例（后端已算好 stock/threshold，0 表示断货） */
function remainPct(item: LowStockItem): number {
  return Math.max(0, Math.min(100, Math.round((item.remainRatio ?? 0) * 100)))
}

function goRestock() {
  // 商品管理页读 lowStock=1 会直接启用「只看库存告急」（商品级口径）
  router.push({ path: '/products', query: { lowStock: '1' } })
}
</script>

<template>
  <div class="dash" v-loading="loading">
    <!-- 页头：标题由顶栏提供，这里放副标题与时间范围 -->
    <div class="dash__head">
      <div class="dash__head-t">
        <h1>运营总览</h1>
        <span>订单 / 秒杀 / 库存一次看全</span>
      </div>
      <el-radio-group :model-value="days" class="dash__seg" @update:model-value="switchDays">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 核心 KPI -->
    <div class="dash__kpis">
      <div class="kpi kpi--primary">
        <div class="kpi__k">累计成交额</div>
        <div class="kpi__v"><span class="sym">¥</span>{{ formatPrice(paidAmount) }}</div>
        <div class="kpi__x">已付款订单合计（待发货 + 待收货 + 已完成）</div>
      </div>
      <div class="kpi kpi--success">
        <div class="kpi__k">累计成交单数</div>
        <div class="kpi__v">{{ paidCount }}</div>
        <div class="kpi__x">
          <template v-if="paidCount > 0">客单价 <b class="is-plain">¥{{ formatPrice(avgOrder) }}</b></template>
          <template v-else>暂无成交</template>
        </div>
      </div>
      <div class="kpi kpi--danger">
        <div class="kpi__k">进行中秒杀</div>
        <div class="kpi__v" :class="{ 'kpi__v--mute': !ongoing }">{{ ongoing }}</div>
        <div class="kpi__x">
          <template v-if="ongoingEndText">最快一场 <b>{{ ongoingEndText }}</b> 后结束</template>
          <template v-else>当前无进行中场次</template>
        </div>
      </div>
      <div class="kpi kpi--warn">
        <div class="kpi__k">库存告急 SKU</div>
        <div class="kpi__v">{{ lowStockTotal }}</div>
        <div class="kpi__x">
          <template v-if="outOfStock > 0"><b>{{ outOfStock }}</b> 个已断货</template>
          <template v-else>均未断货，需尽快补货</template>
        </div>
      </div>
    </div>

    <!-- 趋势 -->
    <div class="dash__grid2">
      <div class="dash__card dash__trend">
        <div class="dash__trend-head">
          <h2>近 {{ days }} 天订单量</h2>
          <div class="dash__sum">{{ orderSum }}<em>单</em></div>
          <div class="dash__delta" :class="`is-${orderDelta.dir}`">{{ orderDelta.text }}</div>
        </div>
        <div class="dash__sub">按下单时间统计，含全部状态</div>
        <TrendChart :data="orderSeries" :height="200" :color="BRAND" :format="fmtCount" />
        <div class="dash__legend">
          <span><i :style="{ background: BRAND }"></i>每日订单量</span>
        </div>
      </div>

      <div class="dash__card dash__trend">
        <div class="dash__trend-head">
          <h2>近 {{ days }} 天成交额</h2>
          <div class="dash__sum"><span class="sym">¥</span>{{ formatPrice(amountSum) }}</div>
          <div class="dash__delta" :class="`is-${amountDelta.dir}`">{{ amountDelta.text }}</div>
        </div>
        <div class="dash__sub">按支付时间统计，仅已付款订单</div>
        <TrendChart :data="amountSeries" :height="200" :color="DANGER" :format="fmtAmount" />
        <div class="dash__legend">
          <span><i :style="{ background: DANGER }"></i>每日成交额</span>
        </div>
      </div>
    </div>

    <!-- 秒杀战报 + 库存告急 -->
    <div class="dash__grid2b">
      <div class="dash__card dash__sec">
        <div class="dash__sec-head">
          <h2>秒杀战报</h2>
          <span class="dash__badge" :class="{ 'is-warn': ongoing > 0 }">
            <template v-if="ongoing > 0">{{ ongoing }} 场进行中</template>
            <template v-else-if="pending > 0">{{ pending }} 场待开始</template>
            <template v-else>暂无在场活动</template>
          </span>
        </div>

        <div v-if="activities.length === 0" class="dash__empty">还没有创建过秒杀活动</div>
        <div v-for="a in activities" :key="a.id" class="skrow">
          <div class="skrow__t">
            <b>{{ a.title }}</b>
            <span>
              <template v-if="a.specValue">{{ a.specValue }} · </template>
              秒杀价 ¥{{ formatPrice(a.seckillPrice) }} · 限购 {{ a.limitPerUser }}
            </span>
          </div>
          <el-tag :type="STATUS_TAG[a.status] ?? 'info'" size="small">{{ a.statusLabel }}</el-tag>
          <div class="skrow__time" :class="{ 'is-strong': activityTime(a).strong }">
            {{ activityTime(a).label }}
            <em>已售 {{ a.soldCount }}/{{ a.seckillStock }}</em>
          </div>
        </div>

        <div v-if="activities.length > 0" class="dash__sec-foot">
          在场 {{ ongoing + pending }} 场 · 累计已售 {{ soldQuantity }} 件 · 秒杀成交 ¥{{ formatPrice(seckillPaid) }}
        </div>
      </div>

      <div class="dash__card dash__sec">
        <div class="dash__sec-head">
          <h2>库存告急</h2>
          <span class="dash__badge" :class="{ 'is-warn': outOfStock > 0 }">
            <template v-if="lowStockTotal > 0">共 {{ lowStockTotal }} 个 SKU</template>
            <template v-else>库存充足</template>
          </span>
        </div>

        <div v-if="lowItems.length === 0" class="dash__empty">暂无低于预警线的规格</div>
        <div v-for="it in lowItems" :key="it.skuId" class="strow">
          <div class="strow__t">
            <b>{{ it.productName }}</b>
            <span>
              <template v-if="it.specValue">{{ it.specValue }}</template>
              <template v-else>默认规格</template>
              <template v-if="it.categoryName"> · {{ it.categoryName }}</template>
            </span>
          </div>
          <div class="strow__bar">
            <i :style="{ width: `${remainPct(it)}%` }"></i>
          </div>
          <div class="strow__n">剩 <b>{{ it.stock }}</b> / 预警 {{ it.threshold }}</div>
          <span class="strow__go" @click="goRestock">去补货 →</span>
        </div>

        <div v-if="lowItems.length > 0" class="dash__sec-foot">
          按 SKU 统计，低于所属分类的预警线即计入；点击任一行去商品管理补货
        </div>
      </div>
    </div>

    <div class="dash__foot">
      成交口径 = 待发货 + 待收货 + 已完成（与订单管理页头一致）；库存告急为 SKU 级（低于分类预警线）；
      趋势按自然日聚合，无数据的日期补 0。
    </div>
  </div>
</template>

<style scoped>
/* ---------- 页头 ---------- */
.dash__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.dash__head-t {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.dash__head-t h1 {
  font-size: 15px;
  font-weight: 500;
  margin: 0;
}
.dash__head-t span {
  font-size: 12px;
  color: #909399;
}

/* ---------- KPI ---------- */
.dash__kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.kpi {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 15px 18px;
  position: relative;
  overflow: hidden;
}
.kpi::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #dcdfe6;
}
.kpi--primary::before {
  background: #6ba3c8;
}
.kpi--success::before {
  background: #67c23a;
}
.kpi--danger::before {
  background: #f56c6c;
}
.kpi--warn::before {
  background: #e6a23c;
}
.kpi__k {
  font-size: 12.5px;
  color: #909399;
}
.kpi__v {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: -0.5px;
  font-variant-numeric: tabular-nums;
  color: #303133;
}
.kpi__v .sym {
  font-size: 15px;
  font-weight: 500;
  color: #606266;
  margin-right: 1px;
}
.kpi__v--mute {
  color: #c0c4cc;
}
.kpi__x {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}
.kpi__x b {
  color: #f56c6c;
  font-weight: 600;
}
.kpi__x b.is-plain {
  color: #606266;
}

/* ---------- 卡片与栅格 ---------- */
.dash__grid2,
.dash__grid2b {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 16px;
}
.dash__card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04), 0 2px 8px rgba(0, 0, 0, 0.03);
}

/* ---------- 趋势 ---------- */
.dash__trend {
  padding: 16px 18px;
}
.dash__trend-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.dash__trend-head h2 {
  font-size: 13.5px;
  font-weight: 500;
  margin: 0;
  color: #303133;
}
.dash__sum {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #303133;
}
.dash__sum em {
  font-style: normal;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
  margin-left: 2px;
}
.dash__sum .sym {
  font-size: 13px;
  font-weight: 500;
  color: #909399;
  margin-right: 1px;
}
.dash__delta {
  font-size: 12px;
  padding: 1px 7px;
  border-radius: 9px;
  background: #f4f4f5;
  color: #909399;
}
.dash__delta.is-up {
  background: #f0f9eb;
  color: #529b2e;
}
.dash__delta.is-down {
  background: #fef0f0;
  color: #f56c6c;
}
.dash__sub {
  font-size: 11.5px;
  color: #c0c4cc;
  margin: 4px 0 8px;
}
.dash__legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #606266;
  margin-top: 10px;
}
.dash__legend span {
  display: flex;
  align-items: center;
  gap: 5px;
}
.dash__legend i {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}

/* ---------- 战报 / 库存 ---------- */
.dash__sec {
  padding: 16px 18px;
}
.dash__sec-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.dash__sec-head h2 {
  font-size: 13.5px;
  font-weight: 500;
  margin: 0;
  flex: 1;
  color: #303133;
}
.dash__badge {
  font-size: 11px;
  color: #909399;
  background: #f0f2f5;
  padding: 0 7px;
  border-radius: 8px;
  line-height: 17px;
}
.dash__badge.is-warn {
  background: #fdf6ec;
  color: #b88230;
  border: 1px solid #faecd8;
}
.dash__empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 28px 0;
}

/* 战报行 */
.skrow {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px dashed #f0f2f5;
  font-size: 13px;
}
.skrow:last-of-type {
  border-bottom: 0;
}
.skrow__t {
  flex: 1;
  min-width: 0;
}
.skrow__t b {
  display: block;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.skrow__t span {
  font-size: 11.5px;
  color: #909399;
}
.skrow__time {
  text-align: right;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.skrow__time.is-strong {
  color: #f56c6c;
  font-weight: 600;
}
.skrow__time em {
  display: block;
  font-style: normal;
  font-size: 11px;
  color: #c0c4cc;
  font-weight: 400;
}

/* 库存行 */
.strow {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px dashed #f0f2f5;
  font-size: 13px;
}
.strow:last-of-type {
  border-bottom: 0;
}
.strow__t {
  flex: 1;
  min-width: 0;
}
.strow__t b {
  display: block;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.strow__t span {
  font-size: 11.5px;
  color: #909399;
}
.strow__bar {
  flex: 0 0 22%;
  height: 6px;
  background: #f0f2f5;
  border-radius: 3px;
  overflow: hidden;
  position: relative;
}
.strow__bar i {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: #f56c6c;
  border-radius: 3px;
}
.strow__n {
  flex: 0 0 auto;
  font-size: 12px;
  color: #909399;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.strow__n b {
  color: #f56c6c;
  font-weight: 600;
}
.strow__go {
  flex: 0 0 auto;
  font-size: 12px;
  color: #409eff;
  cursor: pointer;
  white-space: nowrap;
}
.strow__go:hover {
  text-decoration: underline;
}

.dash__sec-foot {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f5f7fa;
  font-size: 11.5px;
  color: #c0c4cc;
  line-height: 1.6;
}
.dash__foot {
  font-size: 12px;
  color: #c0c4cc;
  line-height: 1.7;
}
</style>
