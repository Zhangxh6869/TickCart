<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import type { AdminOrderDetail, AdminOrderSummary, OrderOverview } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import {
  approveRefund,
  batchShipOrders,
  closeOrder,
  fetchOrderDetail,
  fetchOrderOverview,
  fetchOrders,
  rejectRefund,
  shipOrder,
} from '@/api/order'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 与后端 OrderStatus 一一对应；「待发货」用 danger 提示管理员需要处理 */
const STATUS_META: Record<number, { label: string; tag: TagType }> = {
  0: { label: '待付款', tag: 'warning' },
  1: { label: '待发货', tag: 'danger' },
  2: { label: '待收货', tag: 'primary' },
  3: { label: '已完成', tag: 'success' },
  4: { label: '已取消', tag: 'info' },
  5: { label: '已超时关闭', tag: 'info' },
  6: { label: '退款中', tag: 'warning' },
  7: { label: '已退款', tag: 'info' },
}

/**
 * 状态筛选 chips。
 *
 * `countKey` 指向概览接口的字段 —— 计数由后端一次 GROUP BY 算好，
 * 不用「本页 N 条」这种会随翻页跳动的假计数。
 */
type CountKey =
  | 'total'
  | 'pendingPayment'
  | 'pendingShipment'
  | 'pendingReceipt'
  | 'completed'
  | 'cancelled'
  | 'timeoutClosed'
  | 'refunding'
  | 'refunded'

interface StatusChip {
  label: string
  value: number | null
  countKey: CountKey
  /** 待发货 / 退款中：计数用红底，提示需要处理 */
  warn?: boolean
  /** 终态：整体弱化，避免和待办抢注意力 */
  mute?: boolean
}

const STATUS_CHIPS: StatusChip[] = [
  { label: '全部', value: null, countKey: 'total' },
  { label: '待付款', value: 0, countKey: 'pendingPayment' },
  { label: '待发货', value: 1, countKey: 'pendingShipment', warn: true },
  { label: '待收货', value: 2, countKey: 'pendingReceipt' },
  { label: '已完成', value: 3, countKey: 'completed' },
  { label: '退款中', value: 6, countKey: 'refunding', warn: true },
  { label: '已退款', value: 7, countKey: 'refunded', mute: true },
  { label: '已取消', value: 4, countKey: 'cancelled', mute: true },
  { label: '已超时', value: 5, countKey: 'timeoutClosed', mute: true },
]

/** 排序项：默认新单在前；「等最久优先」按付款时间升序 */
const SORT_OPTIONS = [
  { label: '下单时间 从新到旧', sortBy: 'createdAt', order: 'desc' },
  { label: '下单时间 从旧到新', sortBy: 'createdAt', order: 'asc' },
  { label: '付款时间 等最久优先', sortBy: 'payTime', order: 'asc' },
] as const

const DAY_MS = 24 * 60 * 60 * 1000

type WaitLevel = 'danger' | 'warn' | 'muted'

interface WaitInfo {
  text: string
  level: WaitLevel
}

/** 列表行：在原 VO 上挂一个「等待时长」，模板里不再反复算 */
interface OrderRow extends AdminOrderSummary {
  wait: WaitInfo | null
}

const loading = ref(false)
const list = ref<AdminOrderSummary[]>([])
const total = ref(0)
const overview = ref<OrderOverview | null>(null)
const tableRef = ref()

const query = reactive({
  page: 1,
  size: 20,
  status: null as number | null,
  keyword: '',
})

const sortIndex = ref(0)
const dateRange = ref<[string, string] | null>(null)

const dateShortcuts = [
  {
    text: '今天',
    value: () => {
      const end = new Date()
      return [end, end]
    },
  },
  {
    text: '近 7 天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 6)
      return [start, end]
    },
  },
  {
    text: '近 30 天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 29)
      return [start, end]
    },
  },
]

// ---------------- 数据加载 ----------------

async function load() {
  loading.value = true
  try {
    const sort = SORT_OPTIONS[sortIndex.value]
    const res = await fetchOrders({
      page: query.page,
      size: query.size,
      status: query.status,
      keyword: query.keyword.trim() || undefined,
      sortBy: sort.sortBy,
      order: sort.order,
      beginDate: dateRange.value?.[0] ?? null,
      endDate: dateRange.value?.[1] ?? null,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadOverview() {
  overview.value = await fetchOrderOverview()
}

/** 列表与概览一起刷：发货/关闭后两边的计数都要跟着变 */
async function reload() {
  await Promise.all([load(), loadOverview()])
}

onMounted(reload)

// ---------------- 筛选 ----------------

function countOf(chip: StatusChip): number {
  return overview.value ? overview.value[chip.countKey] : 0
}

function selectStatus(value: number | null) {
  query.status = value
  query.page = 1
  // 切到「待发货」时自动按「等最久优先」排：一进来第一条就是积压最久的那单
  if (value === 1 && sortIndex.value === 0) {
    sortIndex.value = 2
  }
  load()
}

function onSearch() {
  query.page = 1
  load()
}

function onSortChange() {
  query.page = 1
  load()
}

function onDateChange() {
  query.page = 1
  load()
}

function onSizeChange(size: number) {
  query.size = size
  query.page = 1
  load()
}

// ---------------- 展示辅助 ----------------

function statusTag(status: number): TagType {
  return STATUS_META[status]?.tag ?? 'info'
}

/** 后端返回 ISO 字符串（2026-10-08T12:00:00），这里只取到分钟 */
function formatTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

/** 相对时间：天/小时/分钟三档，够用且不引入 dayjs */
function durationText(ms: number): string {
  const safe = Math.max(0, ms)
  const days = Math.floor(safe / DAY_MS)
  if (days >= 1) {
    return `${days} 天 ${Math.floor((safe % DAY_MS) / (60 * 60 * 1000))} 小时`
  }
  const hours = Math.floor(safe / (60 * 60 * 1000))
  if (hours >= 1) {
    return `${hours} 小时 ${Math.floor((safe % (60 * 60 * 1000)) / 60000)} 分`
  }
  return `${Math.max(1, Math.floor(safe / 60000))} 分钟`
}

/**
 * 状态下方的副文案。
 *
 * ★ 待发货写「已等」而不是「下单于」：管理员关心这单等了多久。
 * ≥3 天标红 —— 本项目 48 单待发货全部超过 3 天，打开就该是一片红。
 */
function waitInfo(row: AdminOrderSummary): WaitInfo | null {
  const now = Date.now()
  if (row.status === 1) {
    const from = row.payTime || row.createdAt
    const diff = now - new Date(from).getTime()
    return {
      text: `已等 ${durationText(diff)}`,
      level: diff >= 3 * DAY_MS ? 'danger' : diff >= DAY_MS ? 'warn' : 'muted',
    }
  }
  if (row.status === 2 && row.shipTime) {
    return { text: `已发出 ${durationText(now - new Date(row.shipTime).getTime())}`, level: 'muted' }
  }
  if (row.status === 4 && row.cancelTime) {
    return { text: `${formatTime(row.cancelTime)} 取消`, level: 'muted' }
  }
  if (row.status === 5 && row.cancelTime) {
    return { text: `${formatTime(row.cancelTime)} 关闭`, level: 'muted' }
  }
  if (row.status === 0) {
    return { text: '等待买家付款', level: 'muted' }
  }
  return null
}

const rows = computed<OrderRow[]>(() =>
  list.value.map((row) => ({ ...row, wait: waitInfo(row) })),
)

function firstItem(row: AdminOrderSummary) {
  return row.items && row.items.length > 0 ? row.items[0] : null
}

function thumbOf(row: AdminOrderSummary): string {
  return firstItem(row)?.productImage || ''
}

function productNameOf(row: AdminOrderSummary): string {
  return firstItem(row)?.productName || '-'
}

function specText(row: AdminOrderSummary): string {
  const item = firstItem(row)
  if (!item) return '-'
  const spec = item.specValue ? `${item.specValue} × ${item.quantity}` : `× ${item.quantity}`
  const more = row.items.length > 1 ? ` 等 ${row.items.length} 件` : ''
  return `${spec}${more}`
}

/** 图片加载失败时只隐藏图片，留下灰色占位块（21 条明细快照本来就没存图） */
function hideBrokenImage(event: Event) {
  const img = event.target as HTMLImageElement | null
  if (img) img.style.visibility = 'hidden'
}

// ---------------- 勾选与批量发货 ----------------

const selected = ref<AdminOrderSummary[]>([])

const selectedCount = computed(() => selected.value.length)
const selectedAmount = computed(() =>
  selected.value.reduce((sum, row) => sum + Number(row.payAmount || 0), 0),
)

function onSelectionChange(picked: AdminOrderSummary[]) {
  selected.value = picked
}

function clearSelection() {
  selected.value = []
  tableRef.value?.clearSelection()
}

async function onBatchShip() {
  if (selectedCount.value === 0) return
  const count = selectedCount.value
  const amount = formatPrice(selectedAmount.value)
  try {
    await ElMessageBox.confirm(
      `确认对选中的 ${count} 单发货？合计 ¥${amount}。发货后订单进入「待收货」，不可撤销。`,
      '批量发货',
      { type: 'warning', confirmButtonText: '确认发货' },
    )
  } catch {
    return
  }

  try {
    const res = await batchShipOrders(selected.value.map((row) => row.orderNo))
    if (res.failed.length > 0) {
      // 部分成功：必须把失败原因列出来，不能只提示「成功」
      await ElMessageBox.alert(
        h('div', { style: 'max-height: 260px; overflow: auto' }, [
          h(
            'p',
            { style: 'margin: 0 0 8px; color: #606266' },
            `已发货 ${res.success.length} 单，以下 ${res.failed.length} 单未处理：`,
          ),
          h(
            'ul',
            { style: 'margin: 0; padding-left: 18px; line-height: 1.9; color: #f56c6c' },
            res.failed.map((f) => h('li', `${f.orderNo} — ${f.reason}`)),
          ),
        ]),
        '部分订单未发货',
        { type: 'warning' },
      )
    } else {
      ElMessage.success(`已发货 ${res.success.length} 单`)
    }
    clearSelection()
    await reload()
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

// ---------------- 单条操作 ----------------

async function shipByNo(orderNo: string) {
  try {
    await ElMessageBox.confirm(
      `确认对订单「${orderNo}」发货？发货后订单进入「待收货」，不可撤销。`,
      '发货',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await shipOrder(orderNo)
    ElMessage.success('已发货')
    await reload()
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

function onShip(row: AdminOrderSummary) {
  return shipByNo(row.orderNo)
}

// ---------------- 详情抽屉 ----------------

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminOrderDetail | null>(null)
/** 抽屉打开时的列表行：补详情接口没有的字段（如秒杀标） */
const detailSeckill = ref(false)

async function openDetail(row: AdminOrderSummary) {
  detailSeckill.value = Boolean(row.seckill)
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await fetchOrderDetail(row.orderNo)
  } catch {
    drawerVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function onShipFromDrawer() {
  if (!detail.value) return
  const orderNo = detail.value.orderNo
  drawerVisible.value = false
  await shipByNo(orderNo)
}

/**
 * 后台关闭订单：仅待付款可关，关掉后库存会回补。
 *
 * 在此之前后台只能干等 15 分钟超时关单，「用户打电话要求取消」无解。
 */
async function onCloseOrder() {
  if (!detail.value) return
  const orderNo = detail.value.orderNo
  try {
    await ElMessageBox.confirm(
      `关闭订单「${orderNo}」？关闭后库存会回补，且不可撤销。`,
      '关闭订单',
      { type: 'warning', confirmButtonText: '确认关闭' },
    )
  } catch {
    return
  }
  try {
    await closeOrder(orderNo)
    ElMessage.success('订单已关闭')
    drawerVisible.value = false
    await reload()
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

/** 退款单状态配色：待审核黄、已退款绿、已拒绝红、已撤销灰 */
function refundStatusTag(status: number): TagType {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

/**
 * 同意退款：置已退款 + 回补库存 + 回退销量。
 *
 * 这一步不可撤销（订单进终态、库存已经加回去），所以必须二次确认；
 * 确认框里把金额写出来，避免看错单退错款。
 */
async function onApproveRefund() {
  const refund = detail.value?.refund
  if (!refund) return
  try {
    await ElMessageBox.confirm(
      `同意退款 ¥${formatPrice(refund.amount)}？确认后订单变为「已退款」，库存自动回补，且不可撤销。`,
      '同意退款',
      { type: 'warning', confirmButtonText: '确认退款' },
    )
  } catch {
    return
  }
  try {
    await approveRefund(refund.refundNo)
    ElMessage.success('已退款')
    drawerVisible.value = false
    await reload()
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

/** 拒绝退款：原因必填 —— 只回「已拒绝」不说理由，用户只能再提一次，工单会来回滚 */
async function onRejectRefund() {
  const refund = detail.value?.refund
  if (!refund) return
  let remark = ''
  try {
    const result = await ElMessageBox.prompt('请填写拒绝原因（会展示给用户）', '拒绝退款', {
      type: 'warning',
      confirmButtonText: '确认拒绝',
      inputPlaceholder: '例如：商品已发出，请收到后申请退货退款',
      inputValidator: (value: string) => (value && value.trim() ? true : '请填写拒绝原因'),
    })
    remark = result.value.trim()
  } catch {
    return
  }
  try {
    await rejectRefund(refund.refundNo, remark)
    ElMessage.success('已拒绝退款，订单回到待发货')
    drawerVisible.value = false
    await reload()
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

interface TimelineNode {
  label: string
  time: string
  state: 'done' | 'current' | 'off' | 'stop'
}
/**
 * 状态时间线：四个节点 + 取消/超时的终止分支。
 *
 * 比 el-descriptions 平铺好在「一眼看出卡在哪一步」——
 * 待发货时第三个节点会红着写「已等 18 天 22 小时」。
 */
function timelineNodes(d: AdminOrderDetail): TimelineNode[] {
  const created: TimelineNode = { label: '下单', time: formatTime(d.createdAt), state: 'done' }
  // 待付款：流程停在「支付」这一步，标成 current 才能一眼看出卡在哪
  const paid: TimelineNode = d.payTime
    ? { label: '支付', time: formatTime(d.payTime), state: 'done' }
    : {
        label: '支付',
        time: d.status === 0 ? '等待买家付款' : '未支付',
        state: d.status === 0 ? 'current' : 'off',
      }

  if (d.status === 4 || d.status === 5) {
    // 终止分支：末节点用「stop」而不是「done」——绿色对勾读起来像「顺利完成」，
    // 而取消/超时是流程中断，灰点更贴近事实
    return [
      created,
      paid,
      {
        label: d.status === 4 ? '已取消' : '已超时关闭',
        time: formatTime(d.cancelTime),
        state: 'stop',
      },
    ]
  }

  // 退款分支：P0 只支持未发货退款，货没出库，所以时间线到「退款」就结束，
  // 不能继续画「未发货 / 待买家确认」——那会让管理员以为还要发货
  if (d.status === 6 || d.status === 7) {
    const refund = d.refund
    return [
      created,
      paid,
      d.status === 6
        ? {
            label: '退款中',
            time: refund ? `申请于 ${formatTime(refund.createdAt)}` : '等待商家审核',
            state: 'current',
          }
        : {
            label: '已退款',
            time: refund?.refundTime ? formatTime(refund.refundTime) : '退款完成',
            state: 'stop',
          },
    ]
  }

  let ship: TimelineNode
  if (d.status === 1) {
    const from = d.payTime || d.createdAt
    ship = {
      label: '发货',
      time: `已等 ${durationText(Date.now() - new Date(from).getTime())}`,
      state: 'current',
    }
  } else if (d.shipTime) {
    ship = { label: '发货', time: formatTime(d.shipTime), state: 'done' }
  } else {
    ship = { label: '发货', time: '未发货', state: 'off' }
  }

  let finish: TimelineNode
  if (d.status === 3) {
    finish = { label: '完成', time: formatTime(d.finishTime), state: 'done' }
  } else if (d.status === 2) {
    finish = { label: '完成', time: '待买家确认', state: 'current' }
  } else {
    finish = { label: '完成', time: '待买家确认', state: 'off' }
  }

  return [created, paid, ship, finish]
}

const nodes = computed<TimelineNode[]>(() => (detail.value ? timelineNodes(detail.value) : []))

const CHANNEL_LABEL: Record<number, string> = { 0: '模拟支付', 1: '微信支付', 2: '支付宝' }

const PAY_STATUS_LABEL: Record<number, string> = {
  0: '待支付',
  1: '支付成功',
  2: '支付失败',
  3: '已关闭',
}

/**
 * 支付结论。
 *
 * ★ 只看 payStatus，不看 payNo 是否存在：已取消的订单也可能挂着一条「待支付」流水，
 * 拿流水号当「已付款」的证据是错的（这正是老版本会误导的地方）。
 *
 * 订单已经取消/超时关闭时，直接把结论写成「未支付成功」，避免出现
 * 「已取消 + 待支付」这种自相矛盾的展示。
 */
const payConclusion = computed<{ text: string; ok: boolean }>(() => {
  const d = detail.value
  if (!d) return { text: '-', ok: false }
  if (d.payStatus === 1) return { text: '支付成功', ok: true }
  if (d.payStatus === null || d.payStatus === undefined) return { text: '未发起支付', ok: false }

  const label = PAY_STATUS_LABEL[d.payStatus] ?? '未知状态'
  if (d.status === 4) return { text: `未支付成功（订单已取消 · 流水${label}）`, ok: false }
  if (d.status === 5) return { text: `未支付成功（订单已超时关闭 · 流水${label}）`, ok: false }
  return { text: label, ok: false }
})

function channelText(channel: number | null | undefined): string {
  if (channel === null || channel === undefined) return '-'
  return CHANNEL_LABEL[channel] ?? '-'
}

const detailQuantity = computed(() =>
  detail.value ? detail.value.items.reduce((sum, item) => sum + item.quantity, 0) : 0,
)

async function copy(text: string, label: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制`)
  } catch {
    ElMessage.warning('浏览器不允许复制，请手动选中')
  }
}
</script>

<template>
  <div class="om">
    <!-- 页头 KPI：刻意不写「今日 N 单」—— 演示数据可能停在前几天，那种指标会全是 0 -->
    <div class="om__kpis">
      <div class="kpi kpi--danger">
        <div class="kpi__k">待发货</div>
        <div class="kpi__v kpi__v--danger">{{ overview?.pendingShipment ?? 0 }}</div>
        <div class="kpi__x">
          <template v-if="overview?.oldestPendingShipmentTime">
            最早一单已等
            <b>{{ durationText(Date.now() - new Date(overview.oldestPendingShipmentTime).getTime()) }}</b>
          </template>
          <template v-else>暂无积压</template>
        </div>
      </div>
      <div class="kpi kpi--primary">
        <div class="kpi__k">待收货</div>
        <div class="kpi__v">{{ overview?.pendingReceipt ?? 0 }}</div>
        <div class="kpi__x">已发出，等买家确认收货</div>
      </div>
      <div class="kpi">
        <div class="kpi__k">累计成交</div>
        <div class="kpi__v">¥{{ formatPrice(overview?.paidAmount ?? 0) }}</div>
        <div class="kpi__x">
          {{ overview?.paidOrderCount ?? 0 }} 单已付款
          <template v-if="overview && overview.paidOrderCount > 0">
            · 客单价 ¥{{ formatPrice(overview.paidAmount / overview.paidOrderCount) }}
          </template>
        </div>
      </div>
    </div>

    <!-- 筛选 -->
    <el-card class="om__filter" shadow="never">
      <div class="chips">
        <span
          v-for="chip in STATUS_CHIPS"
          :key="chip.label"
          class="chip"
          :class="{
            'is-on': query.status === chip.value,
            'chip--warn': chip.warn && query.status !== chip.value,
            'chip--mute': chip.mute,
          }"
          @click="selectStatus(chip.value)"
        >
          {{ chip.label }}
          <b>{{ countOf(chip) }}</b>
        </span>
      </div>

      <div class="om__row">
        <el-input
          v-model="query.keyword"
          class="om__search"
          placeholder="订单号 / 收货人 / 手机 / 商品名"
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
          start-placeholder="下单起始日"
          end-placeholder="结束日"
          :shortcuts="dateShortcuts"
          class="om__date"
          @change="onDateChange"
        />
        <el-select v-model="sortIndex" class="om__sort" @change="onSortChange">
          <el-option v-for="(opt, i) in SORT_OPTIONS" :key="opt.label" :label="opt.label" :value="i" />
        </el-select>
        <div class="om__spacer" />
        <el-button type="primary" @click="onSearch">查询</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <div v-if="selectedCount > 0" class="om__batch">
        <span>已选 <b>{{ selectedCount }}</b> 单 · 合计 <b>¥{{ formatPrice(selectedAmount) }}</b></span>
        <div class="om__spacer" />
        <el-button size="small" @click="clearSelection">取消选择</el-button>
        <el-button size="small" type="primary" @click="onBatchShip">批量发货</el-button>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="rows"
        row-key="orderNo"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" />

        <el-table-column label="订单" min-width="248">
          <template #default="{ row }">
            <div class="ocell">
              <img
                v-if="thumbOf(row)"
                :src="thumbOf(row)"
                class="ocell__thumb"
                alt=""
                @error="hideBrokenImage"
              />
              <div v-else class="ocell__thumb ocell__thumb--ph">无图</div>
              <div class="ocell__body">
                <div class="ocell__no">
                  {{ row.orderNo }}
                  <el-tag v-if="row.seckill" size="small" type="warning" effect="plain">秒杀</el-tag>
                </div>
                <div class="ocell__sub">下单 {{ formatTime(row.createdAt) }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="商品" min-width="210">
          <template #default="{ row }">
            <div class="ocell__name">{{ productNameOf(row) }}</div>
            <div class="ocell__spec">{{ specText(row) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="买家 / 收货" min-width="215">
          <template #default="{ row }">
            <div class="buyer__n">
              {{ row.receiver }}
              <span class="buyer__phone">{{ row.receiverPhone }}</span>
            </div>
            <div class="buyer__addr" :title="row.receiverAddr">{{ row.receiverAddr || '-' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="实付" width="118" align="right">
          <template #default="{ row }">
            <div class="om__amount">¥{{ formatPrice(row.payAmount) }}</div>
            <div class="om__qty">{{ row.totalQuantity }} 件</div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="146">
          <template #default="{ row }">
            <div class="st">
              <el-tag :type="statusTag(row.status)" size="small">{{ row.statusLabel }}</el-tag>
              <span
                v-if="row.wait"
                class="st__w"
                :class="{
                  'st__w--danger': row.wait.level === 'danger',
                  'st__w--warn': row.wait.level === 'warn',
                }"
              >{{ row.wait.text }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="128" align="right">
          <template #default="{ row }">
            <div class="om__ops">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.status === 1" link type="warning" @click="onShip(row)">发货</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="om__empty">
            <p>没有符合条件的订单</p>
            <p class="om__empty-tip">换个状态或关键字试试</p>
          </div>
        </template>
      </el-table>

      <el-pagination
        class="om__pager"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :current-page="query.page"
        :page-size="query.size"
        :page-sizes="[10, 20, 50]"
        @size-change="onSizeChange"
        @current-change="(p: number) => { query.page = p; load() }"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" size="760px" :with-header="false">
      <div v-loading="detailLoading" class="drawer">
        <template v-if="detail">
          <div class="drawer__head">
            <span class="drawer__no">{{ detail.orderNo }}</span>
            <el-tag :type="statusTag(detail.status)" size="small">{{ detail.statusLabel }}</el-tag>
            <el-tag v-if="detailSeckill" size="small" type="warning" effect="plain">秒杀</el-tag>
            <div class="om__spacer" />
            <el-button link @click="drawerVisible = false">关闭</el-button>
          </div>

          <div class="timeline">
            <div
              v-for="(node, i) in nodes"
              :key="i"
              class="timeline__node"
              :class="`is-${node.state}`"
            >
              <span class="timeline__dot" />
              <div class="timeline__label">{{ node.label }}</div>
              <div class="timeline__time">{{ node.time }}</div>
            </div>
          </div>

          <div v-if="detail.remark" class="remark">
            <b>买家备注</b>
            <span>{{ detail.remark }}</span>
          </div>

          <section class="sect">
            <header class="sect__head">
              收货信息
              <span class="sect__badge">下单快照，改地址不影响本单</span>
            </header>
            <div class="sect__body">
              <div class="kv">
                <span class="kv__k">收货人</span>
                <span class="kv__v">
                  {{ detail.receiver }}
                  <el-button link type="primary" size="small" @click="copy(detail.receiver, '收货人')">复制</el-button>
                </span>
              </div>
              <div class="kv">
                <span class="kv__k">手机号</span>
                <span class="kv__v">
                  {{ detail.receiverPhone }}
                  <el-button link type="primary" size="small" @click="copy(detail.receiverPhone, '手机号')">复制</el-button>
                </span>
              </div>
              <div class="kv">
                <span class="kv__k">地址</span>
                <span class="kv__v">
                  {{ detail.receiverAddr }}
                  <el-button link type="primary" size="small" @click="copy(detail.receiverAddr, '地址')">复制</el-button>
                </span>
              </div>
            </div>
          </section>

          <section class="sect">
            <header class="sect__head">
              商品明细
              <span class="sect__badge">{{ detail.items.length }} 种 · 共 {{ detailQuantity }} 件</span>
            </header>
            <div class="sect__body sect__body--tight">
              <div v-for="(item, i) in detail.items" :key="i" class="grow">
                <img
                  v-if="item.productImage"
                  :src="item.productImage"
                  class="grow__img"
                  alt=""
                  @error="hideBrokenImage"
                />
                <div v-else class="grow__img grow__img--ph">无图</div>
                <div class="grow__body">
                  <div class="grow__name">{{ item.productName }}</div>
                  <div class="grow__spec">
                    {{ item.specValue || '默认规格' }} · ¥{{ formatPrice(item.price) }}
                  </div>
                </div>
                <div class="grow__sum">
                  × {{ item.quantity }}
                  <span>¥{{ formatPrice(item.subtotal) }}</span>
                </div>
              </div>
              <div class="grow__total">
                <span>实付</span>
                <b>¥{{ formatPrice(detail.payAmount) }}</b>
              </div>
            </div>
          </section>

          <section class="sect">
            <header class="sect__head">
              支付信息
              <span class="sect__badge" :class="{ 'is-ok': payConclusion.ok }">
                {{ payConclusion.ok ? '已支付' : '未支付' }}
              </span>
            </header>
            <div class="sect__body">
              <div class="paybox">
                <div>
                  <span>支付结论</span>
                  <b :class="payConclusion.ok ? 'is-ok' : 'is-no'">{{ payConclusion.text }}</b>
                </div>
                <div><span>支付渠道</span><b>{{ channelText(detail.payChannel) }}</b></div>
                <div><span>支付时间</span>{{ formatTime(detail.payTime) }}</div>
                <div v-if="detail.payStatus === 1 && detail.payNo" class="paybox__wide">
                  <span>流水号</span><b class="mono">{{ detail.payNo }}</b>
                </div>
              </div>
              <p v-if="!payConclusion.ok" class="paybox__tip">
                未收到款，因此不展示流水号 —— 支付失败 / 用户取消 / 超时关闭同样会留下流水记录，
                拿流水号当「已付款」的证据是错的。
              </p>
            </div>
          </section>

          <!-- 退款信息：只要申请过就展示，被拒绝/撤销的记录也留着备查 -->
          <section v-if="detail.refund" class="sect">
            <header class="sect__head">
              退款信息
              <el-tag :type="refundStatusTag(detail.refund.status)" size="small" effect="plain">
                {{ detail.refund.statusLabel }}
              </el-tag>
            </header>
            <div class="sect__body">
              <div class="kv">
                <span class="kv__k">退款单号</span>
                <span class="kv__v mono">{{ detail.refund.refundNo }}</span>
              </div>
              <div class="kv">
                <span class="kv__k">退款金额</span>
                <span class="kv__v">¥{{ formatPrice(detail.refund.amount) }}</span>
              </div>
              <div class="kv">
                <span class="kv__k">退款原因</span>
                <span class="kv__v">{{ detail.refund.reasonLabel }}</span>
              </div>
              <div v-if="detail.refund.reason" class="kv">
                <span class="kv__k">补充说明</span>
                <span class="kv__v">{{ detail.refund.reason }}</span>
              </div>
              <div class="kv">
                <span class="kv__k">申请时间</span>
                <span class="kv__v">{{ formatTime(detail.refund.createdAt) }}</span>
              </div>
              <div v-if="detail.refund.auditRemark" class="kv">
                <span class="kv__k">审核意见</span>
                <span class="kv__v">{{ detail.refund.auditRemark }}</span>
              </div>
              <div v-if="detail.refund.refundTime" class="kv">
                <span class="kv__k">退款时间</span>
                <span class="kv__v">{{ formatTime(detail.refund.refundTime) }}</span>
              </div>
            </div>
          </section>
        </template>
      </div>

      <template #footer>
        <div class="drawer__foot">
          <el-button @click="drawerVisible = false">关闭</el-button>
          <div class="om__spacer" />
          <el-button v-if="detail && detail.status === 0" type="danger" plain @click="onCloseOrder">
            关闭订单
          </el-button>
          <el-button v-if="detail && detail.status === 1" type="primary" @click="onShipFromDrawer">
            发货
          </el-button>
          <template v-if="detail && detail.status === 6 && detail.refund && detail.refund.status === 0">
            <el-button type="danger" plain @click="onRejectRefund">拒绝退款</el-button>
            <el-button type="success" @click="onApproveRefund">同意退款</el-button>
          </template>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.om__kpis {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}
.kpi {
  position: relative;
  padding: 14px 18px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.kpi::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  content: '';
  background: #e4e7ed;
}
.kpi--danger::before {
  background: #f56c6c;
}
.kpi--primary::before {
  background: #409eff;
}
.kpi__k {
  font-size: 12.5px;
  color: #909399;
}
.kpi__v {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.25;
  letter-spacing: -0.5px;
  font-variant-numeric: tabular-nums;
}
.kpi__v--danger {
  color: #f56c6c;
}
.kpi__x {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
.kpi__x b {
  font-weight: 600;
  color: #f56c6c;
}

.om__filter {
  margin-bottom: 12px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
  cursor: pointer;
  background: #fafbfc;
  border: 1px solid #e4e7ed;
  border-radius: 15px;
  transition: 0.15s;
}
.chip:hover {
  color: #409eff;
  background: #f4f9ff;
  border-color: #c6e2ff;
}
.chip b {
  padding: 0 5px;
  font-size: 12px;
  font-weight: 600;
  line-height: 15px;
  color: #909399;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  font-variant-numeric: tabular-nums;
}
.chip.is-on {
  color: #fff;
  background: #409eff;
  border-color: #409eff;
}
.chip.is-on b {
  color: #fff;
  background: rgba(255, 255, 255, 0.22);
  border-color: transparent;
}
.chip--warn b {
  color: #f56c6c;
  background: #fef0f0;
  border-color: #fde2e2;
}
.chip--mute {
  color: #909399;
}

.om__row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px dashed #ebeef5;
}
.om__search {
  width: 258px;
}
.om__date {
  width: 250px;
}
.om__sort {
  width: 200px;
}
.om__spacer {
  flex: 1;
}

.om__batch {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 16px;
  margin: -20px -20px 12px;
  font-size: 13px;
  color: #606266;
  background: #ecf5ff;
  border-bottom: 1px solid #d9ecff;
}
.om__batch b {
  color: #409eff;
  font-variant-numeric: tabular-nums;
}

.ocell {
  display: flex;
  gap: 10px;
  align-items: center;
}
.ocell__thumb {
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  object-fit: cover;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.ocell__thumb--ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #c0c4cc;
}
.ocell__body {
  min-width: 0;
}
.ocell__no {
  display: flex;
  gap: 6px;
  align-items: center;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12.5px;
  color: #303133;
}
.ocell__sub {
  margin-top: 3px;
  font-size: 11.5px;
  color: #c0c4cc;
  font-variant-numeric: tabular-nums;
}
.ocell__name {
  max-width: 230px;
  overflow: hidden;
  font-size: 13px;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ocell__spec {
  margin-top: 3px;
  font-size: 11.5px;
  color: #909399;
}

.buyer__n {
  font-size: 13px;
  color: #303133;
}
.buyer__phone {
  margin-left: 6px;
  font-size: 11.5px;
  color: #909399;
  font-variant-numeric: tabular-nums;
}
.buyer__addr {
  max-width: 200px;
  margin-top: 2px;
  overflow: hidden;
  font-size: 11.5px;
  color: #c0c4cc;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.om__amount {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  font-variant-numeric: tabular-nums;
}
.om__qty {
  margin-top: 2px;
  font-size: 11.5px;
  font-weight: 400;
  color: #909399;
}

.st {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}
.st__w {
  font-size: 11.5px;
  color: #909399;
  font-variant-numeric: tabular-nums;
}
.st__w--danger {
  font-weight: 600;
  color: #f56c6c;
}
.st__w--warn {
  color: #e6a23c;
}

.om__ops {
  display: flex;
  gap: 2px;
  justify-content: flex-end;
}

.om__empty {
  padding: 20px 0;
  font-size: 14px;
  color: #909399;
}
.om__empty-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #c0c4cc;
}
.om__pager {
  justify-content: flex-end;
  margin-top: 16px;
}

/* ---------------- 抽屉 ---------------- */
.drawer {
  min-height: 320px;
}
.drawer__head {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-bottom: 14px;
  margin-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
}
.drawer__no {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 15px;
  font-weight: 600;
}
.drawer__foot {
  display: flex;
  gap: 10px;
  align-items: center;
}

.timeline {
  display: flex;
  padding: 18px 0 6px;
}
.timeline__node {
  position: relative;
  flex: 1;
  min-width: 0;
  padding-top: 24px;
  text-align: center;
}
.timeline__node::before {
  position: absolute;
  top: 7px;
  right: 50%;
  left: 0;
  height: 2px;
  content: '';
  background: #e4e7ed;
}
.timeline__node::after {
  position: absolute;
  top: 7px;
  right: 0;
  left: 50%;
  height: 2px;
  content: '';
  background: #e4e7ed;
}
.timeline__node:first-child::before,
.timeline__node:last-child::after {
  display: none;
}
.timeline__dot {
  position: absolute;
  top: 0;
  left: 50%;
  z-index: 1;
  width: 16px;
  height: 16px;
  background: #fff;
  border: 2px solid #dcdfe6;
  border-radius: 50%;
  transform: translateX(-50%);
}
.timeline__node.is-done::before,
.timeline__node.is-done::after {
  background: #67c23a;
}
.timeline__node.is-done .timeline__dot {
  background: #67c23a;
  border-color: #67c23a;
}
.timeline__node.is-current::before {
  background: #67c23a;
}
.timeline__node.is-current .timeline__dot {
  background: #fff;
  border-color: #f56c6c;
  box-shadow: 0 0 0 4px rgba(245, 108, 108, 0.14);
}
.timeline__label {
  font-size: 12.5px;
  font-weight: 500;
  color: #606266;
}
.timeline__node.is-current .timeline__label {
  font-weight: 600;
  color: #f56c6c;
}
.timeline__node.is-off .timeline__label {
  color: #c0c4cc;
}
/* 终止节点（已取消 / 已超时关闭）：灰实心，不画绿线，读作「流程到此为止」 */
.timeline__node.is-stop .timeline__dot {
  background: #909399;
  border-color: #909399;
}
.timeline__node.is-stop .timeline__label {
  font-weight: 600;
  color: #606266;
}
.timeline__time {
  margin-top: 3px;
  font-size: 11px;
  color: #c0c4cc;
  font-variant-numeric: tabular-nums;
}
.timeline__node.is-current .timeline__time {
  color: #f56c6c;
}

.remark {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  margin: 8px 0 14px;
  font-size: 12.5px;
  color: #b88230;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
}
.remark b {
  flex: 0 0 auto;
  font-weight: 600;
}

.sect {
  margin-bottom: 14px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
.sect__head {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 600;
  background: #fafbfc;
  border-bottom: 1px solid #ebeef5;
}
.sect__badge {
  padding: 0 6px;
  margin-left: auto;
  font-size: 11px;
  font-weight: 500;
  line-height: 16px;
  color: #909399;
  background: #f0f2f5;
  border-radius: 8px;
}
.sect__badge.is-ok {
  color: #529b2e;
  background: #f0f9eb;
}
.sect__body {
  padding: 12px 14px;
}
.sect__body--tight {
  padding: 6px 14px 12px;
}

.kv {
  display: flex;
  gap: 8px;
  align-items: baseline;
  font-size: 13px;
  line-height: 1.9;
}
.kv__k {
  flex: 0 0 64px;
  font-size: 12.5px;
  color: #909399;
}
.kv__v {
  flex: 1;
  min-width: 0;
  color: #303133;
}

.grow {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 9px 0;
  border-bottom: 1px solid #f5f7fa;
}
.grow:last-of-type {
  border-bottom: 0;
}
.grow__img {
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  object-fit: cover;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.grow__img--ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #c0c4cc;
}
.grow__body {
  flex: 1;
  min-width: 0;
}
.grow__name {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.grow__spec {
  margin-top: 3px;
  font-size: 11.5px;
  color: #909399;
}
.grow__sum {
  font-size: 13px;
  color: #606266;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.grow__sum span {
  display: block;
  margin-top: 3px;
  font-size: 11.5px;
  color: #909399;
}
.grow__total {
  display: flex;
  gap: 22px;
  align-items: baseline;
  justify-content: flex-end;
  padding-top: 10px;
  margin-top: 6px;
  font-size: 12.5px;
  color: #606266;
  border-top: 1px dashed #f0f2f5;
}
.grow__total b {
  font-size: 17px;
  color: #f56c6c;
  font-variant-numeric: tabular-nums;
}

.paybox {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 26px;
  font-size: 12.5px;
  color: #303133;
}
.paybox div span {
  margin-right: 6px;
  color: #909399;
}
.paybox__wide {
  flex-basis: 100%;
}
.paybox .mono {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
}
.paybox .is-ok {
  font-weight: 600;
  color: #67c23a;
}
.paybox .is-no {
  font-weight: 600;
  color: #f56c6c;
}
.paybox__tip {
  margin-top: 10px;
  font-size: 11.5px;
  line-height: 1.6;
  color: #c0c4cc;
}
</style>
