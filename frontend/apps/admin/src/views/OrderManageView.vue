<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AdminOrderSummary, OrderDetail } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { fetchOrderDetail, fetchOrders, shipOrder } from '@/api/order'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 与后端 OrderStatus 一一对应；「待发货」用 danger 提示管理员需要处理 */
const STATUS_OPTIONS = [
  { label: '待付款', value: 0, tag: 'warning' },
  { label: '待发货', value: 1, tag: 'danger' },
  { label: '待收货', value: 2, tag: 'primary' },
  { label: '已完成', value: 3, tag: 'success' },
  { label: '已取消', value: 4, tag: 'info' },
  { label: '已超时关闭', value: 5, tag: 'info' },
] as const

const loading = ref(false)
const list = ref<AdminOrderSummary[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  status: null as number | null,
  keyword: '',
})

// ---------------- 列表 ----------------
async function load() {
  loading.value = true
  try {
    const res = await fetchOrders(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onSearch() {
  query.page = 1
  load()
}

function statusTag(status: number): TagType {
  return STATUS_OPTIONS.find((s) => s.value === status)?.tag ?? 'info'
}

/** 后端返回的是 ISO 字符串（2026-10-08T12:00:00），这里只取到分钟 */
function formatTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

/** 列表里的商品列：只展示首个商品名，多商品时补「等 N 种」 */
function itemSummary(row: AdminOrderSummary): string {
  if (row.items.length === 0) return '-'
  const first = row.items[0].productName
  return row.items.length > 1 ? `${first} 等 ${row.items.length} 种` : first
}

/** 支付渠道文案：0模拟 1微信 2支付宝；未发起过支付时为 - */
function channelLabel(channel: number | null | undefined): string {
  if (channel === 1) return '微信支付'
  if (channel === 2) return '支付宝'
  if (channel === 0) return '模拟'
  return '-'
}

// ---------------- 详情弹窗 ----------------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<OrderDetail | null>(null)

async function openDetail(row: AdminOrderSummary) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await fetchOrderDetail(row.orderNo)
  } finally {
    detailLoading.value = false
  }
}

// ---------------- 发货 ----------------
async function ship(orderNo: string) {
  await ElMessageBox.confirm(`确认对订单「${orderNo}」发货？`, '提示', { type: 'warning' })
  await shipOrder(orderNo)
  ElMessage.success('已发货')
  await load()
}

async function onShip(row: AdminOrderSummary) {
  await ship(row.orderNo)
}

async function onShipFromDetail() {
  if (!detail.value) return
  await ship(detail.value.orderNo)
  detailVisible.value = false
}
</script>

<template>
  <div>
    <!-- 查询条件 -->
    <el-card class="toolbar" shadow="never">
      <el-form :inline="true">
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="订单号 / 收货人 / 手机号"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
        <el-table-column label="买家" width="150">
          <template #default="{ row }">
            <div>{{ row.receiver }}</div>
            <div class="sub">{{ row.receiverPhone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ itemSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="件数" width="70">
          <template #default="{ row }">{{ row.totalQuantity }}</template>
        </el-table-column>
        <el-table-column label="实付" width="110">
          <template #default="{ row }">¥{{ formatPrice(row.payAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付流水号" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.payNo">{{ row.payNo }}</span>
            <span v-else class="sub">未发起支付</span>
          </template>
        </el-table-column>
        <el-table-column label="支付渠道" width="100">
          <template #default="{ row }">{{ channelLabel(row.channel) }}</template>
        </el-table-column>
        <el-table-column label="下单时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 1" link type="warning" @click="onShip(row)">发货</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.page"
        :page-size="query.size"
        @current-change="(p: number) => { query.page = p; load() }"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="700px">
      <div v-loading="detailLoading" class="detail">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTag(detail.status)">{{ detail.statusLabel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="收货人">{{ detail.receiver }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detail.receiverPhone }}</el-descriptions-item>
            <el-descriptions-item label="收货地址" :span="2">{{ detail.receiverAddr }}</el-descriptions-item>
            <el-descriptions-item label="买家备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
            <el-descriptions-item label="下单时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="支付时间">{{ formatTime(detail.payTime) }}</el-descriptions-item>
            <el-descriptions-item label="发货时间">{{ formatTime(detail.shipTime) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ formatTime(detail.finishTime) }}</el-descriptions-item>
          </el-descriptions>

          <el-table :data="detail.items" border size="small" class="detail__items">
            <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
            <el-table-column label="规格" width="110">
              <template #default="{ row }">{{ row.specValue || '-' }}</template>
            </el-table-column>
            <el-table-column label="单价" width="100">
              <template #default="{ row }">¥{{ formatPrice(row.price) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="70" />
            <el-table-column label="小计" width="110">
              <template #default="{ row }">¥{{ formatPrice(row.subtotal) }}</template>
            </el-table-column>
          </el-table>

          <div class="detail__total">
            实付金额：<span class="detail__amount">¥{{ formatPrice(detail.payAmount) }}</span>
          </div>
        </template>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail && detail.status === 1" type="primary" @click="onShipFromDetail">
          发货
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.sub {
  font-size: 12px;
  color: #909399;
}
.detail__items {
  margin-top: 16px;
}
.detail__total {
  margin-top: 12px;
  text-align: right;
  font-size: 14px;
}
.detail__amount {
  font-size: 18px;
  font-weight: 600;
  color: #f56c6c;
}
</style>
