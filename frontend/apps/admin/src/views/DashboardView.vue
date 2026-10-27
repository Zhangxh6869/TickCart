<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { CategoryOverview, DashboardVO, SeckillOverview } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchDashboard } from '@/api/dashboard'
import SparkLine from '@/components/SparkLine.vue'

/** 品牌浅蓝：订单量趋势（与项目强调色一致） */
const BRAND = '#6ba3c8'
/** 红：成交额趋势 / 库存告急 */
const DANGER = '#f56c6c'

const loading = ref(false)
const days = ref(7)
const dash = ref<DashboardVO | null>(null)

async function load() {
  loading.value = true
  try {
    dash.value = await fetchDashboard(days.value)
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

/** 核心 KPI：累计成交额 / 成交单数（来自 OrderOverview） */
const order = computed(() => dash.value?.order)
const paidAmount = computed(() => order.value?.paidAmount ?? 0)
const paidCount = computed(() => order.value?.paidOrderCount ?? 0)

/** 核心 KPI：进行中秒杀 / 待开始（来自 SeckillOverview） */
const seckill = computed<SeckillOverview | null>(() => dash.value?.seckill ?? null)

/** 库存告急分类：lowStockCount > 0 的分类，按告急数降序 */
const lowStockCategories = computed<CategoryOverview[]>(() =>
  (dash.value?.category ?? [])
    .filter((c) => c.lowStockCount > 0)
    .sort((a, b) => b.lowStockCount - a.lowStockCount),
)

const totalLowStockProducts = computed(() =>
  lowStockCategories.value.reduce((sum, c) => sum + c.lowStockCount, 0),
)

/** 趋势数据（近 N 天订单量 / 成交额） */
const orderTrend = computed(() => dash.value?.orderTrend ?? [])
const amountTrend = computed(() => dash.value?.amountTrend ?? [])
const orderTrendSum = computed(() => orderTrend.value.reduce((s, p) => s + p.count, 0))
const amountTrendSum = computed(() => amountTrend.value.reduce((s, p) => s + p.amount, 0))

/** 秒杀战报：进行中场次、待开场次 */
const ongoing = computed(() => seckill.value?.ongoing ?? 0)
const pending = computed(() => seckill.value?.pending ?? 0)
const soldQuantity = computed(() => seckill.value?.soldQuantity ?? 0)
const seckillPaidAmount = computed(() => seckill.value?.paidAmount ?? 0)

/** 进行中秒杀结束倒计时 */
const ongoingEndText = computed(() => {
  const t = seckill.value?.ongoingEndTime
  if (!t) return ''
  const diff = new Date(t).getTime() - Date.now()
  if (diff <= 0) return '即将结束'
  const h = Math.floor(diff / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  const s = Math.floor((diff % 60000) / 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return pad(h) + ':' + pad(m) + ':' + pad(s)
})
</script>

<template>
  <div class="dash">
    <div class="dash__head">
      <div class="dash__title">运营总览</div>
      <el-radio-group :model-value="days" class="dash__seg" @update:model-value="switchDays">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </div>

    <div v-loading="loading">
      <!-- 核心 KPI -->
      <div class="dash__kpis">
        <div class="kpi kpi--primary">
          <div class="kpi__k">累计成交额</div>
          <div class="kpi__v">¥{{ formatPrice(paidAmount) }}</div>
          <div class="kpi__x">已付款订单合计（待发货+待收货+已完成）</div>
        </div>
        <div class="kpi kpi--success">
          <div class="kpi__k">累计成交单数</div>
          <div class="kpi__v">{{ paidCount }}</div>
          <div class="kpi__x">
            <template v-if="paidCount > 0">
              客单价 ¥{{ formatPrice(paidAmount / paidCount) }}
            </template>
            <template v-else>暂无成交</template>
          </div>
        </div>
        <div class="kpi kpi--danger">
          <div class="kpi__k">进行中秒杀</div>
          <div class="kpi__v" :class="{ 'kpi__v--mute': !ongoing }">{{ ongoing }}</div>
          <div class="kpi__x" v-if="ongoingEndText">结束倒计时 {{ ongoingEndText }}</div>
          <div class="kpi__x" v-else>当前无进行中场次</div>
        </div>
        <div class="kpi kpi--warn">
          <div class="kpi__k">库存告急商品</div>
          <div class="kpi__v">{{ totalLowStockProducts }}</div>
          <div class="kpi__x">{{ lowStockCategories.length }} 个分类需关注补货</div>
        </div>
      </div>

      <!-- 趋势 -->
      <div class="dash__grid2">
        <div class="dash__card dash__trend">
          <div class="dash__trend-head">
            <h3>近 {{ days }} 天订单量</h3>
            <b>{{ orderTrendSum }} 单</b>
          </div>
          <div class="dash__trend-chart">
            <SparkLine
              :data="orderTrend.map((p) => p.count)"
              :width="520"
              :height="140"
              :color="BRAND"
              area
            />
          </div>
        </div>
        <div class="dash__card dash__trend">
          <div class="dash__trend-head">
            <h3>近 {{ days }} 天成交额</h3>
            <b>¥{{ formatPrice(amountTrendSum) }}</b>
          </div>
          <div class="dash__trend-chart">
            <SparkLine
              :data="amountTrend.map((p) => p.amount)"
              :width="520"
              :height="140"
              :color="DANGER"
              area
            />
          </div>
        </div>
      </div>

      <!-- 秒杀战报 + 库存告急 -->
      <div class="dash__grid2b">
        <div class="dash__card dash__sec">
          <div class="dash__sec-head">
            <h3>秒杀战报</h3>
            <el-tag v-if="ongoing" type="danger" size="small">进行中</el-tag>
            <el-tag v-else size="small">暂无进行中</el-tag>
          </div>
          <div class="dash__skstats">
            <div class="dash__skitem">
              <div class="dash__sknum">{{ ongoing }}</div>
              <div class="dash__sklabel">进行中场次</div>
            </div>
            <div class="dash__skitem">
              <div class="dash__sknum">{{ pending }}</div>
              <div class="dash__sklabel">即将开始</div>
            </div>
            <div class="dash__skitem">
              <div class="dash__sknum">{{ soldQuantity }}</div>
              <div class="dash__sklabel">已售件数</div>
            </div>
            <div class="dash__skitem">
              <div class="dash__sknum">¥{{ formatPrice(seckillPaidAmount) }}</div>
              <div class="dash__sklabel">秒杀成交额</div>
            </div>
          </div>
        </div>

        <div class="dash__card dash__sec">
          <div class="dash__sec-head">
            <h3>库存告急</h3>
            <el-tag size="small" type="warning">按分类预警线</el-tag>
          </div>
          <div v-if="lowStockCategories.length === 0" class="dash__empty">暂无库存告急的分类</div>
          <div
            v-for="c in lowStockCategories"
            :key="c.id"
            class="dash__strow"
            @click="$router.push({ path: '/products', query: { lowStock: 1 } })"
          >
            <div class="dash__strow-cat">{{ c.name }}</div>
            <div class="dash__strow-n">
              <b>{{ c.lowStockCount }}</b> 个商品低于预警线
              <span class="dash__strow-th">({{ c.effectiveThreshold ?? '-' }})</span>
            </div>
            <span class="dash__strow-go">去补货 →</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dash__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.dash__title {
  font-size: 15px;
  font-weight: 500;
}

/* KPI */
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
  background: #c0c4cc;
}
.kpi--primary::before {
  background: #6ba3c8;
}
.kpi--danger::before {
  background: #f56c6c;
}
.kpi--warn::before {
  background: #e6a23c;
}
.kpi--success::before {
  background: #67c23a;
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

/* 双列 */
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
  border-radius: 8px;
  padding: 16px 18px;
}

/* 趋势 */
.dash__trend-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}
.dash__trend-head h3 {
  font-size: 13.5px;
  font-weight: 500;
  color: #303133;
}
.dash__trend-head b {
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.dash__trend-chart {
  width: 100%;
  height: 140px;
  display: flex;
  align-items: flex-end;
}
/* SparkLine 的 svg 带固定 width 属性，这里用 CSS 覆盖成自适应宽度（viewBox 保证等比缩放） */
.dash__trend-chart :deep(svg) {
  width: 100%;
  height: auto;
  display: block;
}

/* 秒杀战报 */
.dash__sec-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.dash__sec-head h3 {
  font-size: 13.5px;
  font-weight: 500;
  color: #303133;
  flex: 1;
}
.dash__skstats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.dash__skitem {
  text-align: center;
  padding: 12px 0;
  background: #f7f8fa;
  border-radius: 6px;
}
.dash__sknum {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.dash__sklabel {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* 库存告急 */
.dash__empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 24px 0;
}
.dash__strow {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f2f5;
  cursor: pointer;
}
.dash__strow:last-child {
  border-bottom: 0;
}
.dash__strow-cat {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dash__strow-n {
  flex: 0 0 auto;
  font-size: 12px;
  color: #909399;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.dash__strow-n b {
  color: #f56c6c;
  font-weight: 600;
}
.dash__strow-th {
  color: #c0c4cc;
}
.dash__strow-go {
  flex: 0 0 auto;
  font-size: 12px;
  color: #409eff;
  cursor: pointer;
  white-space: nowrap;
}
.dash__strow:hover .dash__strow-go {
  text-decoration: underline;
}
</style>

