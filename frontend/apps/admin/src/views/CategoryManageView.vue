<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Rank } from '@element-plus/icons-vue'
import type { Category, CategoryOverview } from '@tickcart/shared-types'
import {
  createCategory,
  deleteCategory,
  fetchCategoryOverview,
  reorderCategories,
  updateCategory,
  updateCategoryStatus,
  updateCategoryThreshold,
} from '@/api/product'

const loading = ref(false)
const list = ref<CategoryOverview[]>([])
const keyword = ref('')

/**
 * 搜索时禁用拖拽。
 *
 * <p>拖拽要提交**全部分类**的新顺序（后端会校验数量），
 * 过滤后的列表只是子集，拖它会把未显示的分类顺序弄乱。</p>
 */
const canDrag = computed(() => keyword.value.trim() === '')

const filtered = computed(() => {
  const k = keyword.value.trim()
  return k ? list.value.filter((c) => c.name.includes(k)) : list.value
})

const summary = computed(() => ({
  total: list.value.length,
  disabled: list.value.filter((c) => c.status !== 1).length,
  products: list.value.reduce((sum, c) => sum + c.productCount, 0),
  low: list.value.reduce((sum, c) => sum + c.lowStockCount, 0),
}))

async function load() {
  loading.value = true
  try {
    list.value = await fetchCategoryOverview()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function parentLabel(row: CategoryOverview): string {
  if (!row.parentId) return '顶级分类'
  return list.value.find((c) => c.id === row.parentId)?.name ?? '未知父分类'
}

// ---------------- 拖拽排序 ----------------
const dragFrom = ref<number | null>(null)
const dragOver = ref<number | null>(null)

function onDragStart(event: DragEvent, index: number) {
  if (!canDrag.value) {
    event.preventDefault()
    return
  }
  dragFrom.value = index
  event.dataTransfer?.setData('text/plain', String(index))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onDragOver(event: DragEvent, index: number) {
  if (dragFrom.value === null) return
  // 不 preventDefault 的话浏览器不认这是一个可放置区域，drop 永远不触发
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
  dragOver.value = index
}

function onDragLeave(index: number) {
  if (dragOver.value === index) {
    dragOver.value = null
  }
}

async function onDrop(index: number) {
  const from = dragFrom.value
  resetDrag()
  if (from === null || from === index) return

  const previous = list.value
  const next = [...list.value]
  const [moved] = next.splice(from, 1)
  next.splice(index, 0, moved)
  // 先动本地顺序，接口失败再回滚 —— 拖完等 200ms 才动会显得很粘手
  list.value = next
  try {
    await reorderCategories(next.map((c) => c.id))
    ElMessage.success({ message: '顺序已保存', duration: 1200 })
  } catch {
    list.value = previous
  }
}

function resetDrag() {
  dragFrom.value = null
  dragOver.value = null
}

// ---------------- 行内编辑 ----------------
async function onToggleStatus(row: CategoryOverview) {
  const previous = row.status
  const next = previous === 1 ? 0 : 1
  row.status = next
  try {
    await updateCategoryStatus(row.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已禁用')
  } catch {
    row.status = previous
  }
}

/** 预警线失焦/回车时提交；空值 = 改回继承 */
async function onThresholdCommit(row: CategoryOverview, event: Event) {
  const input = event.target as HTMLInputElement
  const raw = input.value.trim()
  const previous = row.stockWarnThreshold ?? null
  const value = raw === '' ? null : Number(raw)

  if (value !== null && (!Number.isInteger(value) || value < 0)) {
    ElMessage.warning('预警线要填非负整数，留空表示继承')
    input.value = previous === null ? '' : String(previous)
    return
  }
  if (value === previous) return

  try {
    await updateCategoryThreshold(row.id, value)
    ElMessage.success(value === null ? '已改回继承' : '预警线已保存')
    // 生效值可能随继承变化，重新拉一次比在前端猜更稳
    await load()
  } catch {
    input.value = previous === null ? '' : String(previous)
  }
}

// ---------------- 删除 ----------------
function deleteHint(row: CategoryOverview): string {
  return row.productCount > 0
    ? `该分类下有 ${row.productCount} 个商品，需先移走或删除商品`
    : '删除分类'
}

async function onDelete(row: CategoryOverview) {
  // 有商品就删不掉，按钮已置灰；这里再挡一次，防止键盘/脚本绕过
  if (row.productCount > 0) return
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '提示', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger',
  })
  await deleteCategory(row.id)
  ElMessage.success('已删除')
  await load()
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('新增分类')
const submitting = ref(false)
const form = reactive<{
  id?: number
  name: string
  parentId: number
  stockWarnThreshold: number | null
}>({ name: '', parentId: 0, stockWarnThreshold: null })

function openCreate() {
  dialogTitle.value = '新增分类'
  form.id = undefined
  form.name = ''
  form.parentId = 0
  form.stockWarnThreshold = null
  dialogVisible.value = true
}

function openEdit(row: CategoryOverview) {
  dialogTitle.value = '编辑分类'
  form.id = row.id
  form.name = row.name
  form.parentId = row.parentId ?? 0
  form.stockWarnThreshold = row.stockWarnThreshold ?? null
  dialogVisible.value = true
}

async function onSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写分类名称')
    return
  }
  submitting.value = true
  try {
    const payload: Partial<Category> = {
      parentId: form.parentId,
      name: form.name.trim(),
      stockWarnThreshold: form.stockWarnThreshold,
    }
    if (form.id) {
      await updateCategory(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await createCategory(payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="cm">
    <!-- 页头 -->
    <div class="cm__head">
      <div class="cm__summary">
        共 <b>{{ summary.total }}</b> 个分类
        <template v-if="summary.disabled > 0">· {{ summary.disabled }} 个禁用</template>
        · {{ summary.products }} 个商品
        <template v-if="summary.low > 0">
          · <span class="cm__danger">{{ summary.low }} 个库存告急</span>
        </template>
        <span class="cm__tip">｜拖动左侧手柄调整商城首页的分类顺序</span>
      </div>
      <div class="cm__acts">
        <el-input
          v-model="keyword"
          class="cm__search"
          placeholder="搜索分类名"
          clearable
          :prefix-icon="undefined"
        />
        <el-button type="primary" :icon="Plus" @click="openCreate">新增分类</el-button>
      </div>
    </div>

    <!-- 列表 -->
    <el-card v-loading="loading" shadow="never" :body-style="{ padding: '0' }">
      <div class="cm__row cm__row--head">
        <span />
        <span>分类名称</span>
        <span>商品</span>
        <span>库存预警线</span>
        <span>状态</span>
        <span class="cm__ops-head">操作</span>
      </div>

      <div
        v-for="(row, index) in filtered"
        :key="row.id"
        class="cm__row"
        :class="{
          'is-off': row.status !== 1,
          'is-dragging': dragFrom === index,
          'is-over': dragOver === index && dragFrom !== index,
        }"
        @dragover="onDragOver($event, index)"
        @dragleave="onDragLeave(index)"
        @drop.prevent="onDrop(index)"
      >
        <!-- 拖拽手柄 -->
        <span
          class="cm__handle"
          :class="{ 'is-disabled': !canDrag }"
          :draggable="canDrag"
          :title="canDrag ? '拖动调整顺序' : '搜索状态下不能拖动'"
          @dragstart="onDragStart($event, index)"
          @dragend="resetDrag"
        >
          <el-icon><Rank /></el-icon>
        </span>

        <div class="cm__name">
          <div class="cm__name-text">{{ row.name }}</div>
          <div class="cm__name-sub">ID {{ row.id }} · {{ parentLabel(row) }}</div>
        </div>

        <div class="cm__usage">
          {{ row.productCount }} 个商品
          <span v-if="row.lowStockCount > 0" class="cm__danger">· {{ row.lowStockCount }} 告急</span>
        </div>

        <div class="cm__th">
          <input
            class="cm__th-input"
            type="text"
            inputmode="numeric"
            :value="row.stockWarnThreshold ?? ''"
            :placeholder="`继承 ${row.effectiveThreshold ?? ''}`"
            @change="onThresholdCommit(row, $event)"
            @keyup.enter="($event.target as HTMLInputElement).blur()"
          />
        </div>

        <div class="cm__status">
          <el-switch :model-value="row.status === 1" @change="onToggleStatus(row)" />
        </div>

        <div class="cm__ops">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-tooltip :content="deleteHint(row)" placement="top" :disabled="row.productCount === 0">
            <span>
              <el-button
                link
                type="danger"
                :disabled="row.productCount > 0"
                @click="onDelete(row)"
              >
                删除
              </el-button>
            </span>
          </el-tooltip>
        </div>
      </div>

      <el-empty v-if="!loading && filtered.length === 0" description="没有匹配的分类" :image-size="70" />
    </el-card>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form label-width="96px">
        <el-form-item label="分类名称" required>
          <el-input v-model="form.name" placeholder="如：手机数码" />
        </el-form-item>
        <el-form-item label="父分类">
          <el-select v-model="form.parentId" style="width: 100%">
            <el-option label="顶级分类" :value="0" />
            <el-option
              v-for="c in list.filter((x) => x.id !== form.id)"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
          <div class="cm__hint">
            只影响「库存预警线」的继承，不影响商城展示 —— 商城首页只列分类名，不区分层级。
          </div>
        </el-form-item>
        <el-form-item label="库存预警线">
          <el-input-number
            v-model="form.stockWarnThreshold"
            :min="0"
            :step="1"
            :controls="false"
            :value-on-clear="null"
            placeholder="留空继承"
            class="cm__th-dialog"
          />
          <span class="cm__hint cm__hint--inline">留空 = 继承父分类 / 全局默认</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cm__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.cm__summary {
  font-size: 13.5px;
  color: var(--el-text-color-regular);
}
.cm__summary b {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.cm__danger {
  color: var(--el-color-danger);
}
.cm__tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.cm__acts {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cm__search {
  width: 200px;
}

/* ---------------- 列表行 ---------------- */
.cm__row {
  display: grid;
  grid-template-columns: 34px minmax(180px, 1fr) 160px 150px 72px 118px;
  align-items: center;
  gap: 10px;
  padding: 9px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
  transition: background 0.12s;
}
.cm__row:last-of-type {
  border-bottom: 0;
}
.cm__row--head {
  padding-top: 10px;
  padding-bottom: 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  background: #fafafa;
  border-bottom: 1px solid var(--el-border-color-light);
}
.cm__row:not(.cm__row--head):hover {
  background: #f7fbff;
}
.cm__row.is-off .cm__name-text {
  color: var(--el-text-color-secondary);
}
/* 拖动中的行半透明，落点行加一条上边线 */
.cm__row.is-dragging {
  opacity: 0.45;
}
.cm__row.is-over {
  box-shadow: inset 0 2px 0 var(--el-color-primary);
}
.cm__ops-head {
  text-align: right;
}

.cm__handle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: var(--el-text-color-placeholder);
  cursor: grab;
  border-radius: 4px;
}
.cm__handle:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.cm__handle.is-disabled {
  cursor: not-allowed;
  opacity: 0.4;
}
.cm__handle:active {
  cursor: grabbing;
}

.cm__name {
  min-width: 0;
}
.cm__name-text {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cm__name-sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--el-text-color-placeholder);
}

.cm__usage {
  font-size: 12.5px;
  color: var(--el-text-color-regular);
  font-variant-numeric: tabular-nums;
}

.cm__th-input {
  width: 100px;
  height: 28px;
  padding: 0 8px;
  font-size: 12.5px;
  font-family: inherit;
  color: var(--el-text-color-primary);
  text-align: right;
  font-variant-numeric: tabular-nums;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}
.cm__th-input:focus {
  border-color: var(--el-color-primary);
  outline: none;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.12);
}
.cm__th-input::placeholder {
  color: var(--el-text-color-placeholder);
}

.cm__status {
  display: flex;
  align-items: center;
}
.cm__ops {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.cm__hint {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.cm__hint--inline {
  width: auto;
  margin-top: 0;
  margin-left: 10px;
}
.cm__th-dialog {
  width: 110px;
}
</style>
