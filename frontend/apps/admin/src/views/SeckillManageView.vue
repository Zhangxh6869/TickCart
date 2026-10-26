<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import type {
  ProductDetail,
  ProductListItem,
  SeckillActivity,
  SeckillActivityQuery,
  SeckillActivitySaveRequest,
  SeckillOverview,
  SeckillStatus,
} from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchProductDetail, fetchProducts } from '@/api/product'
import {
  batchChangeSeckillEnabled,
  changeSeckillEnabled,
  copySeckillActivity,
  createSeckillActivity,
  deleteSeckillActivity,
  fetchSeckillActivities,
  fetchSeckillOverview,
  updateSeckillActivity,
} from '@/api/seckill'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 与后端 SeckillActivityStatus 一一对应；进行中用 danger（紧迫感），终态一律弱化 */
const STATUS_META: Record<number, { label: string; tag: TagType }> = {
  0: { label: '已下线', tag: 'info' },
  1: { label: '即将开始', tag: 'warning' },
  2: { label: '进行中', tag: 'danger' },
  3: { label: '已结束', tag: 'info' },
}

/**
 * 筛选 chips。
 *
 * 「在场」是默认视图 —— 已结束的活动不能删也改不了价，会一直堆在库里；
 * 打开页面想看的只是「现在有什么场」，而不是「历史上建过什么」。
 */
interface StatusChip {
  key: string
  label: string
  status?: SeckillStatus
  activeOnly?: boolean
  count: number
  warn?: boolean
  mute?: boolean
}

const CHIP_DEFS: Array<Omit<StatusChip, 'count'>> = [
  { key: 'active', label: '在场', activeOnly: true },
  { key: 'all', label: '全部' },
  { key: 'ongoing', label: '进行中', status: 2, warn: true },
  { key: 'pending', label: '即将开始', status: 1 },
  { key: 'ended', label: '已结束', status: 3, mute: true },
  { key: 'disabled', label: '已下线', status: 0, mute: true },
]

/** 排序项：默认走后端「在场优先」沉底排序 */
const SORT_OPTIONS = [
  { label: '默认：在场优先', sortBy: undefined, order: undefined },
  { label: '开始时间 从新到旧', sortBy: 'startTime' as const, order: 'desc' as const },
  { label: '开始时间 从旧到新', sortBy: 'startTime' as const, order: 'asc' as const },
  { label: '结束时间 从新到旧', sortBy: 'endTime' as const, order: 'desc' as const },
]

const loading = ref(false)
const list = ref<SeckillActivity[]>([])
const total = ref(0)
const overview = ref<SeckillOverview | null>(null)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: null as SeckillStatus | null,
  activeOnly: true as boolean | undefined,
  sortBy: undefined as 'startTime' | 'endTime' | 'id' | undefined,
  order: undefined as 'asc' | 'desc' | undefined,
})

const sortIndex = ref(0)
const dateRange = ref<[string, string] | null>(null)
const activeChip = ref('active')
const selected = ref<SeckillActivity[]>([])

const chips = computed<StatusChip[]>(() => {
  const o = overview.value
  return CHIP_DEFS.map((def) => {
    let count = 0
    if (def.key === 'active') count = (o?.ongoing ?? 0) + (o?.pending ?? 0)
    else if (def.key === 'all') count = o?.total ?? 0
    else if (def.key === 'ongoing') count = o?.ongoing ?? 0
    else if (def.key === 'pending') count = o?.pending ?? 0
    else if (def.key === 'ended') count = o?.ended ?? 0
    else count = o?.disabled ?? 0
    return { ...def, count }
  })
})

/* ---------------- 时间与进度 ---------------- */

const pad = (n: number) => String(n).padStart(2, '0')

/** `10-19 00:00`：列表里不带年份，省宽度 */
function formatTime(value: string): string {
  if (!value) return '-'
  const d = new Date(value)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 完整时间：抽屉里用 */
function formatFull(value: string): string {
  if (!value) return '-'
  const d = new Date(value)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 把毫秒差写成「2 天 3 小时」；不足一天精确到分钟 */
function spanText(ms: number): string {
  const minutes = Math.floor(Math.abs(ms) / 60000)
  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  const mins = minutes % 60
  if (days > 0) return `${days} 天 ${hours} 小时`
  if (hours > 0) return mins > 0 ? `${hours} 小时 ${mins} 分` : `${hours} 小时`
  return `${mins} 分`
}

interface RelTime {
  text: string
  level: 'danger' | 'warn' | 'muted'
}

/** 相对时间：进行中红字倒计时、即将开始橙字、已结束灰字 */
function relTimeOf(row: SeckillActivity): RelTime {
  const now = Date.now()
  if (row.status === 2) {
    return { text: `还剩 ${spanText(new Date(row.endTime).getTime() - now)}`, level: 'danger' }
  }
  if (row.status === 1) {
    return { text: `${spanText(new Date(row.startTime).getTime() - now)}后开抢`, level: 'warn' }
  }
  if (row.status === 3) {
    return { text: `已结束 ${spanText(now - new Date(row.endTime).getTime())}`, level: 'muted' }
  }
  return { text: '已下线', level: 'muted' }
}

/** 进度：已售 / 总库存（与前台进度条同口径，soldCount 含待付款） */
function progressOf(row: SeckillActivity): number {
  if (!row.seckillStock) return 0
  return Math.min(100, Math.round((row.soldCount / row.seckillStock) * 1000) / 10)
}

/** 折扣：0.01 vs 419 会算出 99.99%，显示上限 99.9 */
function offText(row: SeckillActivity): string {
  if (!row.originalPrice || row.originalPrice <= 0) return ''
  const pct = (1 - row.seckillPrice / row.originalPrice) * 100
  if (pct <= 0) return ''
  return `-${pct > 99.9 ? '99.9' : Math.round(pct)}%`
}

/* ---------------- 列表 ---------------- */

async function loadOverview() {
  overview.value = await fetchSeckillOverview()
}

async function load() {
  loading.value = true
  try {
    const params: SeckillActivityQuery = {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      sortBy: query.sortBy,
      order: query.order,
      beginDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
    }
    if (query.status !== null) {
      params.status = query.status
    } else if (query.activeOnly) {
      params.activeOnly = true
    }
    const result = await fetchSeckillActivities(params)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await Promise.all([load(), loadOverview()])
}

onMounted(refresh)

function selectChip(chip: StatusChip) {
  activeChip.value = chip.key
  query.status = chip.status ?? null
  query.activeOnly = chip.activeOnly
  query.page = 1
  void load()
}

function onSearch() {
  query.page = 1
  void load()
}

function onSortChange(index: number) {
  const opt = SORT_OPTIONS[index]
  query.sortBy = opt?.sortBy
  query.order = opt?.order
  query.page = 1
  void load()
}

function onDateChange() {
  query.page = 1
  void load()
}

function onSizeChange(size: number) {
  query.size = size
  query.page = 1
  void load()
}

/* ---------------- 上下线 / 删除 / 复制 ---------------- */

async function onToggleEnabled(row: SeckillActivity) {
  const next = row.enabled === 1 ? 0 : 1
  const action = next === 1 ? '上线' : '下线'
  try {
    await ElMessageBox.confirm(
      next === 1
        ? '上线后活动会出现在前台并预热库存；同一规格同一时间只能有一场上线中的活动。'
        : '下线后前台不再展示，已成交订单不受影响。',
      `${action}确认`,
      { type: 'warning' },
    )
  } catch {
    return
  }
  await changeSeckillEnabled(row.id, next)
  ElMessage.success(`已${action}`)
  await refresh()
}

async function onDelete(row: SeckillActivity) {
  try {
    await ElMessageBox.confirm('删除后不可恢复；有成交记录的活动会被拒绝删除。', '删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  await deleteSeckillActivity(row.id)
  ElMessage.success('已删除')
  await refresh()
}

async function onCopy(row: SeckillActivity) {
  const id = await copySeckillActivity(row.id)
  ElMessage.success(`已复制为草稿 #${id}，时间已平移到原结束时间的次日，改完时间再上线`)
  await refresh()
}

/** 批量下线「已结束但仍上线」的活动：只是把数据收尾，不改任何业务语义 */
async function onOffEndedButEnabled() {
  const count = overview.value?.endedButEnabled ?? 0
  if (count === 0) return
  try {
    await ElMessageBox.confirm(
      `将下线 ${count} 场已结束但仍处于上线状态的活动。前台本来就不展示它们，这个操作只是把数据收尾。`,
      '批量下线',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const ended = await fetchSeckillActivities({ page: 1, size: 100, status: 3 })
  const ids = ended.records.filter((a) => a.enabled === 1).map((a) => a.id)
  if (ids.length === 0) return
  const n = await batchChangeSeckillEnabled(ids, 0)
  ElMessage.success(`已下线 ${n} 场`)
  await refresh()
}

function onSelectionChange(rows: SeckillActivity[]) {
  selected.value = rows
}

async function onBatchOff() {
  const ids = selected.value.filter((a) => a.enabled === 1).map((a) => a.id)
  if (ids.length === 0) {
    ElMessage.warning('选中的活动都已是下线状态')
    return
  }
  try {
    await ElMessageBox.confirm(`将下线 ${ids.length} 场活动。`, '批量下线', { type: 'warning' })
  } catch {
    return
  }
  const n = await batchChangeSeckillEnabled(ids, 0)
  ElMessage.success(`已下线 ${n} 场`)
  await refresh()
}

/* ---------------- 新建 / 编辑抽屉 ---------------- */

interface FormState {
  title: string
  productId: number | null
  skuId: number | null
  seckillPrice: number
  seckillStock: number
  limitPerUser: number
  timeRange: [string, string] | null
}

const emptyForm = (): FormState => ({
  title: '',
  productId: null,
  skuId: null,
  seckillPrice: 0.01,
  seckillStock: 10,
  limitPerUser: 1,
  timeRange: null,
})

const drawerVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const form = ref<FormState>(emptyForm())

const products = ref<ProductListItem[]>([])
const productLoading = ref(false)
const productKeyword = ref('')
const skuOptions = ref<ProductDetail['skus']>([])

const isEditing = computed(() => editingId.value !== null)

const filteredProducts = computed(() => {
  const kw = productKeyword.value.trim()
  if (!kw) return products.value
  return products.value.filter((p) => p.name.includes(kw) || String(p.id).includes(kw))
})

const selectedProduct = computed(
  () => products.value.find((p) => p.id === form.value.productId) ?? null,
)

/** 原价：选中规格优先，否则商品价 —— 与后端 validateProduct 的口径一致 */
const originalPrice = computed(() => {
  const sku = skuOptions.value.find((s) => s.id === form.value.skuId)
  if (sku?.price) return Number(sku.price)
  return selectedProduct.value ? Number(selectedProduct.value.price) : null
})

const discountText = computed(() => {
  const original = originalPrice.value
  const price = form.value.seckillPrice
  if (!original || !price || price >= original) return ''
  // 折扣 = 秒杀价 / 原价 × 10（20 / 68 → 2.9 折）。低于 0.1 折时写数字没意义
  const zhe = (price / original) * 10
  const zheText = zhe < 0.1 ? '不到 0.1 折' : `${zhe.toFixed(1)} 折`
  return `省 ¥${formatPrice(original - price)}（${zheText}）`
})

/** 编辑已开始的活动：价格与库存被锁（与后端规则一致） */
const priceLocked = computed(() => {
  if (!isEditing.value) return false
  const row = list.value.find((item) => item.id === editingId.value)
  return row ? row.status === 2 || row.status === 3 : false
})

/**
 * 拉全在售商品。
 *
 * 后端单页上限 100（size=200 会被截断成 100），在售 162 个时会静默漏掉 62 个 ——
 * 所以这里翻页拉全，而不是赌「一页装得下」。
 */
async function loadProducts() {
  if (products.value.length > 0) return
  productLoading.value = true
  try {
    const all: ProductListItem[] = []
    for (let page = 1; page <= 10; page++) {
      const result = await fetchProducts({ page, size: 100, status: 1 })
      all.push(...result.records)
      if (all.length >= result.total || result.records.length === 0) break
    }
    products.value = all
  } finally {
    productLoading.value = false
  }
}

async function loadSkus(productId: number) {
  skuOptions.value = []
  const detail = await fetchProductDetail(productId)
  skuOptions.value = detail.skus ?? []
}

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  skuOptions.value = []
  productKeyword.value = ''
  void loadProducts()
  drawerVisible.value = true
}

function openEdit(row: SeckillActivity) {
  editingId.value = row.id
  form.value = {
    title: row.title,
    productId: row.productId,
    skuId: row.skuId || null,
    seckillPrice: Number(row.seckillPrice),
    seckillStock: row.seckillStock,
    limitPerUser: row.limitPerUser,
    timeRange: [row.startTime, row.endTime],
  }
  productKeyword.value = ''
  void loadProducts()
  void loadSkus(row.productId).then(() => {
    form.value.skuId = row.skuId || null
  })
  drawerVisible.value = true
}

function onSelectProduct(product: ProductListItem) {
  if (form.value.productId === product.id) return
  form.value.productId = product.id
  form.value.skuId = null
  if (!form.value.title) form.value.title = `${product.name} 限时秒杀`
  void loadSkus(product.id)
}

function onSelectSku(skuId: number) {
  form.value.skuId = skuId
}

/** 排期快捷模板：常用时段一点就好，省得手填两次 */
function applyQuickRange(kind: 'tonight' | 'tomorrow-am' | 'tomorrow-pm' | 'weekend') {
  const now = new Date()
  const at = (dayOffset: number, hour: number) => {
    const d = new Date(now)
    d.setDate(d.getDate() + dayOffset)
    d.setHours(hour, 0, 0, 0)
    return d
  }
  const toLocal = (d: Date) =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`

  let start: Date
  let end: Date
  if (kind === 'tonight') {
    start = at(0, 20)
    end = at(0, 22)
  } else if (kind === 'tomorrow-am') {
    start = at(1, 10)
    end = at(1, 12)
  } else if (kind === 'tomorrow-pm') {
    start = at(1, 20)
    end = at(1, 22)
  } else {
    const day = now.getDay()
    const toSaturday = (6 - day + 7) % 7 || 7
    start = at(toSaturday, 0)
    end = at(toSaturday + 1, 0)
  }
  form.value.timeRange = [toLocal(start), toLocal(end)]
}

function buildPayload(): SeckillActivitySaveRequest | null {
  if (!form.value.title.trim()) {
    ElMessage.warning('请填写活动标题')
    return null
  }
  if (!form.value.productId) {
    ElMessage.warning('请选择商品')
    return null
  }
  if (!form.value.skuId) {
    ElMessage.warning('请选择规格 —— 前台不让用户选规格，抢到的就是这个')
    return null
  }
  if (!form.value.timeRange) {
    ElMessage.warning('请选择活动时间')
    return null
  }
  return {
    title: form.value.title.trim(),
    productId: form.value.productId,
    skuId: form.value.skuId,
    seckillPrice: form.value.seckillPrice,
    seckillStock: form.value.seckillStock,
    limitPerUser: form.value.limitPerUser,
    startTime: form.value.timeRange[0],
    endTime: form.value.timeRange[1],
  }
}

async function save(uploadAfter: boolean) {
  const payload = buildPayload()
  if (!payload) return
  submitting.value = true
  try {
    let id = editingId.value
    if (id === null) {
      id = await createSeckillActivity(payload)
    } else {
      await updateSeckillActivity(id, payload)
    }
    if (uploadAfter) {
      try {
        await changeSeckillEnabled(id, 1)
        ElMessage.success('已保存并上线')
      } catch (e) {
        ElMessage.warning(`已保存为草稿，但上线失败：${(e as Error).message || '请检查时间是否与已有活动冲突'}`)
      }
    } else {
      ElMessage.success('已保存为草稿')
    }
    drawerVisible.value = false
    await refresh()
  } finally {
    submitting.value = false
  }
}

/* ---------------- 详情抽屉 ---------------- */

const detailVisible = ref(false)
const detail = ref<SeckillActivity | null>(null)

function openDetail(row: SeckillActivity) {
  detail.value = row
  detailVisible.value = true
}

/** 库存对账：DB 已售、Redis 剩余、DB 推算剩余三个口径并列 */
const reconcile = computed(() => {
  const row = detail.value
  if (!row) return null
  return {
    stock: row.seckillStock,
    sold: row.soldCount,
    remaining: row.remainingStock,
    dbRemaining: Math.max(row.seckillStock - row.soldCount, 0),
  }
})

async function onCopyFromDetail() {
  if (!detail.value) return
  await onCopy(detail.value)
  detailVisible.value = false
}

function onEditFromDetail() {
  if (!detail.value) return
  const row = detail.value
  detailVisible.value = false
  openEdit(row)
}

watch(drawerVisible, (open) => {
  if (!open) {
    editingId.value = null
    form.value = emptyForm()
  }
})
</script>

<template>
  <div class="sk">
    <!-- KPI：刻意不写「今日 N 场」—— 演示数据可能停在前几天，那种指标会全是 0 -->
    <div class="sk__kpis">
      <div class="kpi kpi--danger">
        <div class="kpi__k">进行中</div>
        <div class="kpi__v" :class="{ 'kpi__v--mute': !overview?.ongoing }">
          {{ overview?.ongoing ?? 0 }}
        </div>
        <div class="kpi__x">
          <template v-if="overview?.ongoingEndTime">
            最快一场 <b>{{ spanText(new Date(overview.ongoingEndTime).getTime() - Date.now()) }}</b> 后结束
          </template>
          <template v-else>当前没有正在抢的活动</template>
        </div>
      </div>
      <div class="kpi kpi--warn">
        <div class="kpi__k">即将开始</div>
        <div class="kpi__v" :class="{ 'kpi__v--mute': !overview?.pending }">
          {{ overview?.pending ?? 0 }}
        </div>
        <div class="kpi__x">
          <template v-if="overview?.nextStartTime">
            最近一场 <b class="kpi__x--warn">{{ spanText(new Date(overview.nextStartTime).getTime() - Date.now()) }}</b> 后开抢
          </template>
          <template v-else>没有排期</template>
        </div>
      </div>
      <div class="kpi">
        <div class="kpi__k">累计成交</div>
        <div class="kpi__v">¥{{ formatPrice(overview?.paidAmount ?? 0) }}</div>
        <div class="kpi__x">
          {{ overview?.paidOrderCount ?? 0 }} 单 · 售出 {{ overview?.soldQuantity ?? 0 }} 件
        </div>
      </div>
    </div>

    <!-- 已结束但仍上线：不影响前台（前台按 end_time 过滤），只是数据没收尾 -->
    <div v-if="overview?.endedButEnabled" class="sk__alert">
      <span class="sk__alert-icon">!</span>
      <div class="sk__alert-body">
        <b>{{ overview.endedButEnabled }} 场活动已结束但仍处于「上线」状态</b>
        <span>前台已不展示，但后台仍记为上线 —— 会让「上线中」这类筛选失去意义。</span>
      </div>
      <el-button link type="warning" @click="onOffEndedButEnabled">
        一键下线这 {{ overview.endedButEnabled }} 场
      </el-button>
    </div>

    <!-- 筛选 -->
    <el-card class="sk__filter" shadow="never">
      <div class="chips">
        <span
          v-for="chip in chips"
          :key="chip.key"
          class="chip"
          :class="{
            'is-on': activeChip === chip.key,
            'chip--warn': chip.warn && activeChip !== chip.key,
            'chip--mute': chip.mute,
          }"
          @click="selectChip(chip)"
        >
          {{ chip.label }}
          <b>{{ chip.count }}</b>
        </span>
      </div>

      <div class="sk__row">
        <el-input
          v-model="query.keyword"
          class="sk__search"
          placeholder="活动标题 / 商品名"
          clearable
          :prefix-icon="Search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日"
          end-placeholder="结束日"
          class="sk__date"
          @change="onDateChange"
        />
        <el-select v-model="sortIndex" class="sk__sort" @change="onSortChange">
          <el-option v-for="(opt, i) in SORT_OPTIONS" :key="opt.label" :label="opt.label" :value="i" />
        </el-select>
        <div class="sk__spacer" />
        <el-button type="primary" @click="openCreate">新建活动</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <div v-if="selected.length > 0" class="sk__batch">
        <span>已选 <b>{{ selected.length }}</b> 场</span>
        <div class="sk__spacer" />
        <el-button size="small" @click="selected = []">取消选择</el-button>
        <el-button size="small" @click="onBatchOff">批量下线</el-button>
      </div>

      <el-table v-loading="loading" :data="list" row-key="id" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="40" />

        <el-table-column label="活动" min-width="200">
          <template #default="{ row }">
            <div class="acell">
              <img v-if="row.productImage" :src="row.productImage" class="acell__img" alt="" />
              <div v-else class="acell__img acell__img--ph">无图</div>
              <div class="acell__b">
                <div class="acell__t">{{ row.title }}</div>
                <div class="acell__sub">#{{ row.id }} · 限购 {{ row.limitPerUser }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="商品 / 规格" min-width="156">
          <template #default="{ row }">
            <div class="pcell__n">{{ row.productName || '商品已删除' }}</div>
            <div class="pcell__s">
              <em v-if="row.specValue">{{ row.specValue }}</em>
              <em v-else class="pcell__s--miss">未配规格</em>
              <span v-if="row.skuId">#{{ row.skuId }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="时间" width="162">
          <template #default="{ row }">
            <div class="tcell__r">{{ formatTime(row.startTime) }} → {{ formatTime(row.endTime) }}</div>
            <div
              class="tcell__rel"
              :class="{
                'tcell__rel--danger': relTimeOf(row).level === 'danger',
                'tcell__rel--warn': relTimeOf(row).level === 'warn',
              }"
            >
              {{ relTimeOf(row).text }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="价格" width="132">
          <template #default="{ row }">
            <div class="prcell__now">
              ¥{{ formatPrice(row.seckillPrice) }}
              <span v-if="offText(row)" class="prcell__off">{{ offText(row) }}</span>
            </div>
            <div class="prcell__old">¥{{ formatPrice(row.originalPrice) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="进度" width="132">
          <template #default="{ row }">
            <div class="bar">
              <i
                class="bar__in"
                :class="{
                  'bar__in--full': progressOf(row) >= 100,
                  'bar__in--zero': progressOf(row) === 0,
                }"
                :style="{ width: `${progressOf(row)}%` }"
              />
            </div>
            <div class="barcell__t">
              <span><b>{{ row.soldCount }}</b> / {{ row.seckillStock }}</span>
              <span :class="{ 'barcell__t--full': progressOf(row) >= 100 }">
                {{ progressOf(row) }}%
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="成交" width="88">
          <template #default="{ row }">
            <div class="money" :class="{ 'money--none': !row.paidOrderCount }">
              {{ row.paidOrderCount ? `¥${formatPrice(row.paidAmount)}` : '—' }}
            </div>
            <div class="money__c">{{ row.paidOrderCount }} 单</div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <div class="stcell">
              <el-tag :type="STATUS_META[row.status]?.tag ?? 'info'" size="small">
                {{ row.statusLabel }}
              </el-tag>
              <span v-if="row.status === 3 && row.enabled === 1" class="stcell__x">仍为上线</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="118" align="right" fixed="right">
          <template #default="{ row }">
            <div class="sk__ops">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-dropdown trigger="click">
                <el-button link type="primary">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="onCopy(row)">复制为新活动</el-dropdown-item>
                    <el-dropdown-item @click="onToggleEnabled(row)">
                      {{ row.enabled === 1 ? '下线' : '上线' }}
                    </el-dropdown-item>
                    <el-dropdown-item divided @click="onDelete(row)">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="sk__empty">
            <p>{{ activeChip === 'active' ? '现在没有进行中或即将开始的活动' : '没有符合条件的活动' }}</p>
            <p class="sk__empty-tip">
              {{ activeChip === 'active' ? '切到「全部」可以看到历史活动，或直接新建一场' : '换个状态或关键字试试' }}
            </p>
          </div>
        </template>
      </el-table>

      <el-pagination
        class="sk__pager"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="query.page"
        :page-size="query.size"
        :page-sizes="[10, 20, 50]"
        @size-change="onSizeChange"
        @current-change="(p: number) => { query.page = p; load() }"
      />
    </el-card>

    <!-- 新建 / 编辑抽屉 -->
    <el-drawer v-model="drawerVisible" size="760px" :with-header="false">
      <div class="drawer">
        <div class="drawer__head">
          <h3>{{ isEditing ? `编辑活动 #${editingId}` : '新建秒杀活动' }}</h3>
          <el-button link @click="drawerVisible = false">关闭</el-button>
        </div>

        <div class="drawer__body">
          <div class="drawer__main">
            <div class="sec">
              <div class="sec__head">
                <span class="sec__n">1</span>选商品
                <span class="sec__tip">在售 {{ products.length }} 个</span>
              </div>
              <el-input
                v-model="productKeyword"
                placeholder="搜商品名或 ID"
                size="small"
                clearable
                :prefix-icon="Search"
                class="picker__search"
              />
              <div v-loading="productLoading" class="picker">
                <div
                  v-for="p in filteredProducts"
                  :key="p.id"
                  class="picker__item"
                  :class="{ 'is-on': form.productId === p.id }"
                  @click="onSelectProduct(p)"
                >
                  <img v-if="p.mainImage" :src="p.mainImage" class="picker__img" alt="" />
                  <div v-else class="picker__img picker__img--ph">无图</div>
                  <span class="picker__name">{{ p.name }}</span>
                  <span class="picker__id">#{{ p.id }}</span>
                  <span class="picker__price">¥{{ formatPrice(p.price) }}</span>
                </div>
              </div>
            </div>

            <div class="sec">
              <div class="sec__head">
                <span class="sec__n">2</span>选规格
                <span class="sec__tip">前台不让用户选规格，抢到的就是这里配的</span>
              </div>
              <div v-if="skuOptions.length" class="specs">
                <div
                  v-for="s in skuOptions"
                  :key="s.id"
                  class="spec"
                  :class="{ 'is-on': form.skuId === s.id }"
                  @click="onSelectSku(s.id)"
                >
                  {{ s.specValue || '默认' }}
                  <span>¥{{ formatPrice(s.price) }} · 库存 {{ s.stock }}</span>
                </div>
              </div>
              <div v-else class="sec__hint">先选商品</div>
              <div class="sec__hint">
                原价取所选规格价 <b v-if="originalPrice">¥{{ formatPrice(originalPrice) }}</b>，秒杀价必须低于它。
                不选规格会建出「无规格」订单。
              </div>
            </div>

            <div class="sec">
              <div class="sec__head"><span class="sec__n">3</span>定价与库存</div>
              <div class="fgrid">
                <div class="fitem">
                  <div class="flabel">秒杀价</div>
                  <el-input-number
                    v-model="form.seckillPrice"
                    :min="0.01"
                    :precision="2"
                    :step="1"
                    :disabled="priceLocked"
                    class="fnum"
                    controls-position="right"
                  />
                  <div class="sec__hint">
                    <b v-if="discountText" class="sec__hint--danger">{{ discountText }}</b>
                    <template v-else>必须低于原价</template>
                  </div>
                </div>
                <div class="fitem">
                  <div class="flabel">秒杀库存</div>
                  <el-input-number
                    v-model="form.seckillStock"
                    :min="1"
                    :step="10"
                    :disabled="priceLocked"
                    class="fnum"
                    controls-position="right"
                  />
                  <div class="sec__hint">独立于商品库存，超过规格库存也可卖</div>
                </div>
                <div class="fitem">
                  <div class="flabel">每人限购</div>
                  <el-input-number
                    v-model="form.limitPerUser"
                    :min="1"
                    :max="99"
                    class="fnum"
                    controls-position="right"
                  />
                </div>
                <div class="fitem fitem--full">
                  <div class="flabel">活动标题</div>
                  <el-input v-model="form.title" maxlength="64" show-word-limit />
                </div>
              </div>
            </div>

            <div class="sec">
              <div class="sec__head"><span class="sec__n">4</span>排期</div>
              <div class="quick">
                <span class="qbtn" @click="applyQuickRange('tonight')">今晚 20:00–22:00</span>
                <span class="qbtn" @click="applyQuickRange('tomorrow-am')">明天 10:00–12:00</span>
                <span class="qbtn" @click="applyQuickRange('tomorrow-pm')">明天 20:00–22:00</span>
                <span class="qbtn" @click="applyQuickRange('weekend')">周末全天</span>
              </div>
              <el-date-picker
                v-model="form.timeRange"
                type="datetimerange"
                value-format="YYYY-MM-DDTHH:mm:ss"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                style="width: 100%"
              />
              <div class="sec__hint">
                同一规格同一时间只能有一场上线中的活动；冲突时保存会提示具体是哪一场。
              </div>
            </div>

            <el-alert
              v-if="isEditing && priceLocked"
              type="warning"
              :closable="false"
              show-icon
              title="活动已开始，秒杀价与库存已锁定"
              description="改价会让已下单用户的价格与展示不符；改库存会让 Redis 与数据库的账对不上。标题、限购与时间仍可修改 —— 但把结束时间改到未来会让活动重新生效。"
            />
          </div>

          <div class="preview">
            <div class="preview__k">前台预览</div>
            <div class="pcard">
              <div
                class="pcard__img"
                :style="{ backgroundImage: selectedProduct?.mainImage ? `url(${selectedProduct.mainImage})` : '' }"
              >
                <span class="pcard__badge">
                  {{ form.timeRange ? formatTime(form.timeRange[0]) : '未排期' }}
                </span>
              </div>
              <div class="pcard__b">
                <div class="pcard__t">{{ form.title || '活动标题' }}</div>
                <div class="pcard__s">
                  {{ skuOptions.find((s) => s.id === form.skuId)?.specValue || '未选规格' }}
                  · 每人限 {{ form.limitPerUser }} 件
                </div>
                <div class="pcard__pr">
                  <span class="pcard__now">¥{{ formatPrice(form.seckillPrice) }}</span>
                  <span v-if="originalPrice" class="pcard__old">¥{{ formatPrice(originalPrice) }}</span>
                </div>
                <div class="pcard__bar"><i /></div>
                <div class="pcard__meta">
                  <span>已抢 0%</span>
                  <span>剩 {{ form.seckillStock }} 件</span>
                </div>
              </div>
            </div>
            <div class="sec__hint">改价格、库存、标题，右侧实时变化 —— 不用保存后再去前台确认。</div>
          </div>
        </div>

        <div class="drawer__foot">
          <span class="drawer__foot-tip">保存后是草稿，不会出现在前台</span>
          <div class="sk__spacer" />
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button :loading="submitting" @click="save(false)">保存为草稿</el-button>
          <el-button type="primary" :loading="submitting" @click="save(true)">保存并上线</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" size="660px" :with-header="false">
      <div v-if="detail" class="drawer">
        <div class="drawer__head">
          <h3>活动 #{{ detail.id }} · {{ detail.title }}</h3>
          <el-button link @click="detailVisible = false">关闭</el-button>
        </div>

        <div class="drawer__body drawer__body--single">
          <div class="dhead">
            <img v-if="detail.productImage" :src="detail.productImage" class="dhead__img" alt="" />
            <div class="dhead__b">
              <div class="dhead__name">{{ detail.productName || '商品已删除' }}</div>
              <div class="dhead__sub">
                商品 #{{ detail.productId }}
                <template v-if="detail.specValue"> · {{ detail.specValue }} #{{ detail.skuId }}</template>
                <template v-else> · 未配规格</template>
              </div>
              <div class="dhead__price">
                <span class="dhead__now">¥{{ formatPrice(detail.seckillPrice) }}</span>
                <span class="dhead__old">¥{{ formatPrice(detail.originalPrice) }}</span>
                <el-tag v-if="offText(detail)" size="small" type="warning" effect="plain">
                  {{ offText(detail) }}
                </el-tag>
              </div>
            </div>
            <el-tag :type="STATUS_META[detail.status]?.tag ?? 'info'">{{ detail.statusLabel }}</el-tag>
          </div>

          <div class="sec">
            <div class="sec__head">库存对账</div>
            <div class="rec">
              <div class="rec__row"><span>配置总库存</span><b>{{ reconcile?.stock }} 件</b></div>
              <div class="rec__row"><span>DB 已售（sold_count）</span><b>{{ reconcile?.sold }} 件</b></div>
              <div class="rec__row">
                <span>Redis 剩余</span>
                <b v-if="detail.preloaded">{{ reconcile?.remaining }} 件</b>
                <b v-else class="rec__muted">
                  {{ detail.status === 3 || detail.status === 0 ? '已释放（活动不在场）' : '未预热' }}
                </b>
              </div>
              <div class="rec__row"><span>DB 推算剩余</span><b>{{ reconcile?.dbRemaining }} 件</b></div>
              <div class="rec__row">
                <span>成交</span>
                <b>¥{{ formatPrice(detail.paidAmount) }} · {{ detail.paidOrderCount }} 单</b>
              </div>
            </div>
            <div class="sec__hint">
              剩余库存以 Redis 为准（高峰期 DB 的 sold_count 会滞后）；已结束或已下线的活动不预热，Redis key 到期属正常。
            </div>
          </div>

          <div class="sec">
            <div class="sec__head">时间</div>
            <div class="rec">
              <div class="rec__row"><span>开始</span><b>{{ formatFull(detail.startTime) }}</b></div>
              <div class="rec__row"><span>结束</span><b>{{ formatFull(detail.endTime) }}</b></div>
              <div class="rec__row"><span>状态</span><b>{{ relTimeOf(detail).text }}</b></div>
              <div class="rec__row">
                <span>上线标记</span>
                <b>{{ detail.enabled === 1 ? '上线' : '下线（草稿）' }}</b>
              </div>
            </div>
          </div>
        </div>

        <div class="drawer__foot">
          <div class="sk__spacer" />
          <el-button @click="onCopyFromDetail">复制为新活动</el-button>
          <el-button @click="onToggleEnabled(detail); detailVisible = false">
            {{ detail.enabled === 1 ? '下线' : '上线' }}
          </el-button>
          <el-button type="primary" @click="onEditFromDetail">编辑</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.sk__kpis {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}
.kpi {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 18px;
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
  background: #e4e7ed;
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
}
.kpi__v--mute {
  color: #c0c4cc;
}
.kpi__x {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.kpi__x b {
  color: #f56c6c;
  font-weight: 600;
}
.kpi__x--warn {
  color: #e6a23c !important;
}

.sk__alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 12px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
  font-size: 13px;
  color: #b88230;
}
.sk__alert-icon {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #e6a23c;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.sk__alert-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sk__alert-body b {
  color: #8a5a00;
}
.sk__alert-body span {
  font-size: 12px;
  opacity: 0.9;
}

.sk__filter {
  margin-bottom: 12px;
}
.chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #e4e7ed;
  border-radius: 15px;
  background: #fafbfc;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
  transition: 0.15s;
}
.chip:hover {
  border-color: #c6e2ff;
  background: #f4f9ff;
  color: #409eff;
}
.chip b {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 0 5px;
  line-height: 15px;
  font-variant-numeric: tabular-nums;
}
.chip.is-on {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}
.chip.is-on b {
  background: rgba(255, 255, 255, 0.22);
  border-color: transparent;
  color: #fff;
}
.chip--warn b {
  color: #f56c6c;
  border-color: #fde2e2;
  background: #fef0f0;
}
.chip--mute {
  color: #909399;
}

.sk__row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
}
.sk__search {
  width: 230px;
}
.sk__date {
  width: 230px;
}
.sk__sort {
  width: 168px;
}
.sk__spacer {
  flex: 1;
}

.sk__batch {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 10px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
}
.sk__batch b {
  color: #409eff;
}

.acell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.acell__img {
  width: 42px;
  height: 42px;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid #ebeef5;
  flex: none;
  background: #f7f8fa;
}
.acell__img--ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #c0c4cc;
}
.acell__b {
  min-width: 0;
}
.acell__t {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.acell__sub {
  font-size: 11.5px;
  color: #c0c4cc;
  margin-top: 3px;
}

.pcell__n {
  font-size: 12.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pcell__s {
  font-size: 11.5px;
  color: #909399;
  margin-top: 3px;
  display: flex;
  align-items: center;
  gap: 5px;
}
.pcell__s em {
  font-style: normal;
  background: #f4f4f5;
  border: 1px solid #e9e9eb;
  color: #606266;
  border-radius: 3px;
  padding: 0 4px;
  line-height: 15px;
}
.pcell__s--miss {
  background: #fef0f0 !important;
  border-color: #fde2e2 !important;
  color: #f56c6c !important;
}

.tcell__r {
  font-size: 12.5px;
  color: #606266;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.tcell__rel {
  font-size: 11.5px;
  color: #c0c4cc;
  margin-top: 3px;
}
.tcell__rel--danger {
  color: #f56c6c;
  font-weight: 600;
}
.tcell__rel--warn {
  color: #e6a23c;
}

.prcell__now {
  font-size: 14px;
  font-weight: 600;
  color: #f56c6c;
  font-variant-numeric: tabular-nums;
}
.prcell__off {
  display: inline-block;
  margin-left: 6px;
  font-size: 11px;
  font-weight: 500;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffd8a8;
  border-radius: 3px;
  padding: 0 4px;
  vertical-align: 1px;
}
.prcell__old {
  font-size: 11.5px;
  color: #c0c4cc;
  text-decoration: line-through;
  font-variant-numeric: tabular-nums;
  margin-top: 2px;
}

.bar {
  height: 5px;
  border-radius: 3px;
  background: #f0f2f5;
  overflow: hidden;
  margin-bottom: 5px;
}
.bar__in {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #79bbff, #409eff);
}
.bar__in--full {
  background: linear-gradient(90deg, #95d475, #67c23a);
}
.bar__in--zero {
  background: #dcdfe6;
}
.barcell__t {
  font-size: 11.5px;
  color: #909399;
  display: flex;
  justify-content: space-between;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}
.barcell__t b {
  color: #303133;
  font-weight: 600;
}
.barcell__t--full {
  color: #67c23a;
  font-weight: 600;
}

.money {
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.money--none {
  color: #c0c4cc;
  font-weight: 400;
}
.money__c {
  font-size: 11.5px;
  color: #909399;
  font-weight: 400;
  margin-top: 2px;
}

.stcell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: flex-start;
}
.stcell__x {
  font-size: 11px;
  color: #c0c4cc;
}

.sk__ops {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
}
/* Element Plus 给相邻按钮加了 12px 左边距，三个 link 按钮会撑出操作列 */
.sk__ops :deep(.el-button + .el-button) {
  margin-left: 0;
}

.sk__empty {
  padding: 20px 0;
  color: #909399;
  font-size: 13px;
}
.sk__empty-tip {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

.sk__pager {
  margin-top: 14px;
  justify-content: flex-end;
}

/* ---------- 抽屉 ---------- */
.drawer {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.drawer__head {
  flex: none;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid #ebeef5;
}
.drawer__head h3 {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}
.drawer__body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 22px;
  padding: 18px 20px;
  overflow: auto;
}
.drawer__body--single {
  display: block;
}
.drawer__main {
  min-width: 0;
}
.drawer__foot {
  flex: none;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-top: 1px solid #ebeef5;
  background: #fafbfc;
}
.drawer__foot-tip {
  font-size: 12px;
  color: #909399;
}

.sec {
  margin-bottom: 20px;
}
.sec__head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
}
.sec__n {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #ecf5ff;
  color: #409eff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}
.sec__tip {
  font-size: 11.5px;
  color: #909399;
  font-weight: 400;
  margin-left: auto;
}
.sec__hint {
  font-size: 11.5px;
  color: #909399;
  line-height: 1.5;
  margin-top: 6px;
}
.sec__hint b {
  color: #303133;
}
.sec__hint--danger {
  color: #f56c6c !important;
}

.picker__search {
  margin-bottom: 8px;
}
.picker {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  max-height: 190px;
  overflow: auto;
}
.picker__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
}
.picker__item:last-child {
  border-bottom: 0;
}
.picker__item:hover {
  background: #f7fbff;
}
.picker__item.is-on {
  background: #ecf5ff;
}
.picker__img {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  object-fit: cover;
  border: 1px solid #ebeef5;
  flex: none;
}
.picker__img--ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #c0c4cc;
  background: #f7f8fa;
}
.picker__name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.picker__id {
  font-size: 11.5px;
  color: #c0c4cc;
}
.picker__price {
  font-size: 12px;
  color: #606266;
  font-variant-numeric: tabular-nums;
}

.specs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.spec {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 5px 10px;
  font-size: 12.5px;
  cursor: pointer;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 6px;
}
.spec:hover {
  border-color: #c6e2ff;
  color: #409eff;
}
.spec.is-on {
  border-color: #409eff;
  background: #ecf5ff;
  color: #409eff;
  font-weight: 500;
}
.spec span {
  font-size: 11.5px;
  color: #909399;
  font-variant-numeric: tabular-nums;
}
.spec.is-on span {
  color: #79bbff;
}

.fgrid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}
.fitem {
  min-width: 0;
}
.fitem--full {
  grid-column: 1 / -1;
}
.flabel {
  font-size: 12.5px;
  color: #606266;
  margin-bottom: 6px;
}
.fnum {
  width: 100%;
}

.quick {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.qbtn {
  border: 1px solid #e4e7ed;
  background: #fafbfc;
  border-radius: 12px;
  padding: 2px 10px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
}
.qbtn:hover {
  border-color: #c6e2ff;
  color: #409eff;
  background: #f4f9ff;
}

.preview {
  position: sticky;
  top: 0;
  align-self: start;
}
.preview__k {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}
.pcard {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}
.pcard__img {
  height: 140px;
  background: #f7f8fa center / cover no-repeat;
  position: relative;
}
.pcard__badge {
  position: absolute;
  left: 8px;
  top: 8px;
  background: rgba(0, 0, 0, 0.62);
  color: #fff;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 9px;
}
.pcard__b {
  padding: 10px 12px 12px;
}
.pcard__t {
  font-size: 13.5px;
  font-weight: 500;
  line-height: 1.4;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.pcard__s {
  font-size: 11.5px;
  color: #909399;
  margin-bottom: 8px;
}
.pcard__pr {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.pcard__now {
  font-size: 19px;
  font-weight: 600;
  color: #f56c6c;
  font-variant-numeric: tabular-nums;
}
.pcard__old {
  font-size: 11.5px;
  color: #c0c4cc;
  text-decoration: line-through;
}
.pcard__bar {
  height: 5px;
  border-radius: 3px;
  background: #f0f2f5;
  margin: 9px 0 5px;
  overflow: hidden;
}
.pcard__bar i {
  display: block;
  height: 100%;
  width: 0;
  background: linear-gradient(90deg, #79bbff, #409eff);
}
.pcard__meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #909399;
  font-variant-numeric: tabular-nums;
}

/* ---------- 详情 ---------- */
.dhead {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid #f2f4f7;
}
.dhead__img {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid #ebeef5;
  flex: none;
}
.dhead__b {
  flex: 1;
  min-width: 0;
}
.dhead__name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
}
.dhead__sub {
  font-size: 12.5px;
  color: #909399;
}
.dhead__price {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 8px;
}
.dhead__now {
  font-size: 20px;
  font-weight: 600;
  color: #f56c6c;
  font-variant-numeric: tabular-nums;
}
.dhead__old {
  font-size: 12px;
  color: #c0c4cc;
  text-decoration: line-through;
}

.rec {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafbfc;
  padding: 4px 14px;
}
.rec__row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 12.5px;
}
.rec__row:last-child {
  border-bottom: 0;
}
.rec__row span {
  color: #909399;
}
.rec__row b {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.rec__muted {
  color: #c0c4cc;
  font-weight: 400 !important;
}
</style>
