<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Category } from '@tickcart/shared-types'
import {
  createCategory,
  deleteCategory,
  fetchCategories,
  updateCategory,
} from '@/api/product'

const loading = ref(false)
const list = ref<Category[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增分类')
const submitting = ref(false)

const emptyForm = (): Partial<Category> => ({
  parentId: 0,
  name: '',
  icon: '',
  sort: 0,
  status: 1,
  // null 表示「继承父分类 / 全局默认」，不是「0」
  stockWarnThreshold: null,
})

const form = ref<Partial<Category>>(emptyForm())

async function load() {
  loading.value = true
  try {
    list.value = await fetchCategories()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  dialogTitle.value = '新增分类'
  form.value = emptyForm()
  dialogVisible.value = true
}

function openEdit(row: Category) {
  dialogTitle.value = '编辑分类'
  form.value = { ...row }
  dialogVisible.value = true
}

async function onSubmit() {
  if (!form.value.name) {
    ElMessage.warning('请填写分类名称')
    return
  }
  submitting.value = true
  try {
    if (form.value.id) {
      await updateCategory(form.value.id, form.value)
      ElMessage.success('已保存')
    } else {
      await createCategory(form.value)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function onDelete(row: Category) {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '提示', { type: 'warning' })
  await deleteCategory(row.id)
  ElMessage.success('已删除')
  await load()
}

function parentName(id: number): string {
  if (!id) return '顶级'
  return list.value.find((c) => c.id === id)?.name ?? '-'
}
</script>

<template>
  <div>
    <el-card class="toolbar" shadow="never">
      <el-button type="primary" @click="openCreate">新增分类</el-button>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border row-key="id">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" min-width="160" />
        <el-table-column label="父分类" width="140">
          <template #default="{ row }">{{ parentName(row.parentId) }}</template>
        </el-table-column>
        <el-table-column prop="level" label="层级" width="80" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="库存预警线" width="140">
          <template #default="{ row }">
            <span v-if="row.stockWarnThreshold === null || row.stockWarnThreshold === undefined" class="inherit">
              继承
            </span>
            <span v-else class="threshold">{{ row.stockWarnThreshold }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form label-width="90px">
        <el-form-item label="分类名称">
          <el-input v-model="form.name" />
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
        </el-form-item>
        <el-form-item label="图标URL">
          <el-input v-model="form.icon" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="库存预警线">
          <div class="threshold-field">
            <el-input-number
              v-model="form.stockWarnThreshold"
              :min="0"
              :step="1"
              :controls="false"
              :value-on-clear="null"
              placeholder="留空继承"
              class="threshold-field__input"
            />
            <span class="threshold-field__hint">
              库存低于该值在商品列表标「告急」；留空 = 继承父分类，最终继承全局默认 10。
            </span>
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
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
.toolbar {
  margin-bottom: 12px;
}
.inherit {
  font-size: 12.5px;
  color: var(--el-text-color-placeholder);
}
.threshold {
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.threshold-field {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.threshold-field__input {
  flex: 0 0 110px;
  width: 110px;
}
.threshold-field__hint {
  flex: 1;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
