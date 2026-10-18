<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ProductDetail,
  ProductListItem,
  SeckillActivity,
  SeckillActivityQuery,
  SeckillActivitySaveRequest,
  SeckillStatus,
} from '@tickcart/shared-types'
import { fetchProductDetail, fetchProducts } from '@/api/product'
import {
  changeSeckillEnabled,
  createSeckillActivity,
  deleteSeckillActivity,
  fetchSeckillActivities,
  updateSeckillActivity,
} from '@/api/seckill'

const loading = ref(false)
const list = ref<SeckillActivity[]>([])
const total = ref(0)

const query = ref<SeckillActivityQuery>({ page: 1, size: 10, status: undefined, keyword: '' })

/** 状态下拉：与后端 SeckillActivityStatus 的 code 一一对应 */
const STATUS_OPTIONS: Array<{ label: string; value: SeckillStatus }> = [
  { label: '即将开始', value: 1 },
  { label: '进行中', value: 2 },
  { label: '已结束', value: 3 },
  { label: '已下线', value: 0 },
]

const STATUS_TAG: Record<number, 'success' | 'warning' | 'info' | 'danger'> = {
  0: 'info',
  1: 'warning',
  2: 'danger',
  3: 'info',
}

const dialogVisible = ref(false)
const dialogTitle = ref('新建秒杀活动')
const submitting = ref(false)
const editingId = ref<number | null>(null)

/** 商品下拉：一次加载前 200 个在售商品，够用且不必做远程搜索 */
const products = ref<ProductListItem[]>([])
const productLoading = ref(false)
const skuOptions = ref<ProductDetail['skus']>([])

interface FormState extends SeckillActivitySaveRequest {
  timeRange: [string, string] | null
}

const emptyForm = (): FormState => ({
  title: '',
  productId: 0,
  skuId: 0,
  seckillPrice: 0,
  seckillStock: 10,
  limitPerUser: 1,
  startTime: '',
  endTime: '',
  timeRange: null,
})

const form = ref<FormState>(emptyForm())

const isEditing = computed(() => editingId.value !== null)
/** 已开始的活动（进行中/已结束）不允许改价改库存，与后端规则一致 */
const priceLocked = computed(() => {
  const row = list.value.find((item) => item.id === editingId.value)
  return row ? row.status === 2 || row.status === 3 : false
})

async function load() {
  loading.value = true
  try {
    const result = await fetchSeckillActivities(query.value)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function loadProducts() {
  if (products.value.length > 0) return
  productLoading.value = true
  try {
    const result = await fetchProducts({ page: 1, size: 200, status: 1 })
    products.value = result.records
  } finally {
    productLoading.value = false
  }
}

onMounted(load)

watch(
  () => query.value.status,
  () => {
    query.value.page = 1
    void load()
  },
)

/** 切换商品时拉一次详情，拿到 SKU 列表供选择 */
async function onProductChange(productId: number) {
  form.value.skuId = 0
  skuOptions.value = []
  if (!productId) return
  const detail = await fetchProductDetail(productId)
  skuOptions.value = detail.skus ?? []
}

function openCreate() {
  editingId.value = null
  dialogTitle.value = '新建秒杀活动'
  form.value = emptyForm()
  skuOptions.value = []
  void loadProducts()
  dialogVisible.value = true
}

function openEdit(row: SeckillActivity) {
  editingId.value = row.id
  dialogTitle.value = '编辑秒杀活动'
  form.value = {
    title: row.title,
    productId: row.productId,
    skuId: row.skuId,
    seckillPrice: row.seckillPrice,
    seckillStock: row.seckillStock,
    limitPerUser: row.limitPerUser,
    startTime: row.startTime,
    endTime: row.endTime,
    timeRange: [row.startTime, row.endTime],
  }
  void loadProducts()
  void onProductChange(row.productId).then(() => {
    form.value.skuId = row.skuId
  })
  dialogVisible.value = true
}

/** 时间范围 → startTime / endTime（后端要 ISO 风格的 LocalDateTime 字符串） */
watch(
  () => form.value.timeRange,
  (range) => {
    form.value.startTime = range?.[0] ?? ''
    form.value.endTime = range?.[1] ?? ''
  },
)

async function onSubmit() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请填写活动标题')
    return
  }
  if (!form.value.productId) {
    ElMessage.warning('请选择商品')
    return
  }
  if (!form.value.timeRange) {
    ElMessage.warning('请选择活动时间')
    return
  }
  const payload: SeckillActivitySaveRequest = {
    title: form.value.title.trim(),
    productId: form.value.productId,
    skuId: form.value.skuId || 0,
    seckillPrice: form.value.seckillPrice,
    seckillStock: form.value.seckillStock,
    limitPerUser: form.value.limitPerUser,
    startTime: form.value.startTime,
    endTime: form.value.endTime,
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createSeckillActivity(payload)
      ElMessage.success('已创建')
    } else {
      await updateSeckillActivity(editingId.value, payload)
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function onToggleEnabled(row: SeckillActivity) {
  const next = row.enabled === 1 ? 0 : 1
  const action = next === 1 ? '上线' : '下线'
  try {
    await ElMessageBox.confirm(
      next === 1 ? '上线后活动会出现在前台，确定继续？' : '下线后前台不再展示，已成交订单不受影响。',
      `${action}确认`,
      { type: 'warning' },
    )
  } catch {
    return
  }
  await changeSeckillEnabled(row.id, next)
  ElMessage.success(`已${action}`)
  await load()
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
  await load()
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

function formatPrice(value?: number | null) {
  return value === null || value === undefined ? '-' : `¥${Number(value).toFixed(2)}`
}
</script>

<template>
  <div>
    <el-card class="toolbar" shadow="never">
      <div class="toolbar__row">
        <el-button type="primary" @click="openCreate">新建秒杀活动</el-button>
        <div class="toolbar__filters">
          <el-input
            v-model="query.keyword"
            placeholder="活动标题"
            clearable
            style="width: 180px"
            @keyup.enter="load"
            @clear="load"
          />
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            style="width: 140px"
            @clear="load"
          >
            <el-option
              v-for="opt in STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="活动标题" min-width="150">
          <template #default="{ row }">
            <div class="cell-title">{{ row.title }}</div>
            <div class="cell-sub">{{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="190">
          <template #default="{ row }">
            <div class="cell-product">
              <el-image
                v-if="row.productImage"
                :src="row.productImage"
                fit="cover"
                class="cell-product__img"
              />
              <div>
                <div class="cell-title">{{ row.productName || '商品已删除' }}</div>
                <div class="cell-sub">{{ row.specValue || '无规格' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="130">
          <template #default="{ row }">
            <div class="cell-price">{{ formatPrice(row.seckillPrice) }}</div>
            <div class="cell-sub cell-sub--strike">{{ formatPrice(row.originalPrice) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="库存（已售/总）" width="130">
          <template #default="{ row }">
            {{ row.soldCount }} / {{ row.seckillStock }}
            <div class="cell-sub">剩 {{ row.remainingStock }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="limitPerUser" label="限购" width="70" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG[row.status] ?? 'info'" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="onToggleEnabled(row)">
              {{ row.enabled === 1 ? '下线' : '上线' }}
            </el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px">
      <el-form label-width="110px">
        <el-form-item label="活动标题" required>
          <el-input v-model="form.title" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="商品" required>
          <el-select
            v-model="form.productId"
            filterable
            :loading="productLoading"
            placeholder="选择在售商品"
            style="width: 100%"
            @change="onProductChange"
          >
            <el-option
              v-for="p in products"
              :key="p.id"
              :label="`#${p.id} ${p.name}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="skuOptions.length > 0" label="规格">
          <el-select v-model="form.skuId" placeholder="不选表示按商品级" style="width: 100%">
            <el-option
              v-for="s in skuOptions"
              :key="s.id"
              :label="`${s.specValue || '默认'}（原价 ¥${Number(s.price).toFixed(2)}）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="秒杀价" required>
          <el-input-number
            v-model="form.seckillPrice"
            :min="0.01"
            :precision="2"
            :step="1"
            :disabled="priceLocked"
          />
          <span class="hint">必须低于商品原价</span>
        </el-form-item>
        <el-form-item label="秒杀库存" required>
          <el-input-number v-model="form.seckillStock" :min="1" :step="10" :disabled="priceLocked" />
          <span class="hint">独立于商品库存，不参与普通售卖</span>
        </el-form-item>
        <el-form-item label="每人限购" required>
          <el-input-number v-model="form.limitPerUser" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="活动时间" required>
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-alert
          v-if="isEditing && priceLocked"
          type="warning"
          :closable="false"
          show-icon
          title="活动已开始，秒杀价与库存已锁定"
          description="改价会让已下单用户的价格与展示不符；改库存会让 Redis 与数据库的账对不上。标题与限购仍可修改。"
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.toolbar__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toolbar__filters {
  display: flex;
  gap: 8px;
}

.cell-title {
  font-size: 13px;
  color: #303133;
  line-height: 1.4;
}

.cell-sub {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.cell-sub--strike {
  text-decoration: line-through;
}

.cell-price {
  font-size: 13px;
  font-weight: 600;
  color: #e6a23c;
}

.cell-product {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cell-product__img {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  flex: none;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
