<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { Category, ProductListItem } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import {
  changeProductStatus,
  createProduct,
  deleteProduct,
  fetchCategories,
  fetchProductDetail,
  fetchProducts,
  updateProduct,
  type ProductPayload,
} from '@/api/product'
import { ACCEPT_IMAGE_TYPES, MAX_IMAGE_SIZE_MB, uploadImage } from '@/api/upload'

const loading = ref(false)
const list = ref<ProductListItem[]>([])
const total = ref(0)
const categories = ref<Category[]>([])

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  categoryId: null as number | null,
  status: null as number | null,
})

// ---------------- 列表 ----------------
async function load() {
  loading.value = true
  try {
    const res = await fetchProducts(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  categories.value = await fetchCategories()
  await load()
})

function onSearch() {
  query.page = 1
  load()
}

// ---------------- 弹窗表单 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('新增商品')
const submitting = ref(false)

const emptyForm = (): ProductPayload => ({
  categoryId: 1,
  name: '',
  subtitle: '',
  mainImage: '',
  detail: '',
  price: 0,
  status: 0,
  isSeckill: 0,
  skus: [{ specName: '', specValue: '', price: 0, stock: 0 }],
  images: [],
})

const form = ref<ProductPayload>(emptyForm())

function openCreate() {
  dialogTitle.value = '新增商品'
  form.value = emptyForm()
  dialogVisible.value = true
}

async function openEdit(row: ProductListItem) {
  dialogTitle.value = '编辑商品'
  const detail = await fetchProductDetail(row.id)
  form.value = {
    id: detail.id,
    categoryId: detail.categoryId,
    name: detail.name,
    subtitle: detail.subtitle ?? '',
    mainImage: detail.mainImage ?? '',
    detail: detail.detail ?? '',
    price: detail.price,
    status: detail.status,
    isSeckill: detail.isSeckill,
    skus: detail.skus.map((s) => ({
      id: s.id,
      specName: s.specName ?? '',
      specValue: s.specValue ?? '',
      skuImage: s.skuImage ?? '',
      price: s.price,
      stock: s.stock,
    })),
    images: detail.images.map((i) => i.url),
  }
  dialogVisible.value = true
}

function addSku() {
  form.value.skus?.push({ specName: '', specValue: '', price: 0, stock: 0 })
}

function removeSku(index: number) {
  form.value.skus?.splice(index, 1)
}

// ---------------- 主图：本地选图 → 上传 → 回填 URL ----------------
const uploading = ref(false)

/** 选择本地图片后立即上传，成功后把后端返回的相对 URL 写入主图字段。 */
async function onMainImageChange(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  if (!raw.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (raw.size > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
    ElMessage.warning(`图片大小不能超过 ${MAX_IMAGE_SIZE_MB}MB`)
    return
  }
  uploading.value = true
  try {
    form.value.mainImage = await uploadImage(raw)
    ElMessage.success('图片上传成功')
  } finally {
    uploading.value = false
  }
}

function clearMainImage() {
  form.value.mainImage = ''
}

async function onSubmit() {
  if (!form.value.name) {
    ElMessage.warning('请填写商品名称')
    return
  }
  submitting.value = true
  try {
    if (form.value.id) {
      await updateProduct(form.value.id, form.value)
      ElMessage.success('已保存')
    } else {
      await createProduct(form.value)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

// ---------------- 上下架 / 删除 ----------------
async function onToggleStatus(row: ProductListItem) {
  const next = row.status === 1 ? 0 : 1
  await changeProductStatus(row.id, next)
  ElMessage.success(next === 1 ? '已上架' : '已下架')
  await load()
}

async function onDelete(row: ProductListItem) {
  await ElMessageBox.confirm(`确认删除商品「${row.name}」？`, '提示', { type: 'warning' })
  await deleteProduct(row.id)
  ElMessage.success('已删除')
  await load()
}

function categoryName(id: number): string {
  return categories.value.find((c) => c.id === id)?.name ?? '-'
}
</script>

<template>
  <div>
    <!-- 查询条件 -->
    <el-card class="toolbar" shadow="never">
      <el-form :inline="true">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="商品名称" clearable @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.categoryId" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="openCreate">新增商品</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="主图" width="80">
          <template #default="{ row }">
            <el-image :src="row.mainImage" fit="cover" style="width: 48px; height: 48px" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="价格" width="110">
          <template #default="{ row }">¥{{ formatPrice(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="sales" label="销量" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="onToggleStatus(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px">
      <el-form label-width="90px">
        <el-form-item label="商品名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" style="width: 200px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" />
        </el-form-item>
        <el-form-item label="商品主图">
          <div class="image-picker">
            <el-upload
              class="image-picker__upload"
              :show-file-list="false"
              :auto-upload="false"
              :accept="ACCEPT_IMAGE_TYPES"
              :disabled="uploading"
              :on-change="onMainImageChange"
            >
              <div v-loading="uploading" class="image-picker__box">
                <el-image v-if="form.mainImage" :src="form.mainImage" fit="cover" class="image-picker__img" />
                <template v-else>
                  <el-icon class="image-picker__icon"><Plus /></el-icon>
                  <span class="image-picker__tip">选择图片</span>
                </template>
              </div>
            </el-upload>
            <div class="image-picker__side">
              <span v-if="form.mainImage" class="image-picker__path">{{ form.mainImage }}</span>
              <span v-else class="image-picker__tip">
                支持 JPG / PNG / GIF / WEBP，不超过 {{ MAX_IMAGE_SIZE_MB }}MB
              </span>
              <el-button
                v-if="form.mainImage"
                link
                type="danger"
                :disabled="uploading"
                @click="clearMainImage"
              >
                移除
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="展示价格">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="商品详情">
          <el-input v-model="form.detail" type="textarea" :rows="3" />
        </el-form-item>

        <el-form-item label="SKU">
          <div class="sku-editor">
            <div v-for="(sku, index) in form.skus" :key="index" class="sku-editor__row">
              <el-input v-model="sku.specName" placeholder="规格名" style="width: 110px" />
              <el-input v-model="sku.specValue" placeholder="规格值" style="width: 140px" />
              <el-input-number v-model="sku.price" :min="0" :precision="2" placeholder="价格" />
              <el-input-number v-model="sku.stock" :min="0" placeholder="库存" />
              <el-button link type="danger" @click="removeSku(index)">删除</el-button>
            </div>
            <el-button link type="primary" @click="addSku">+ 添加 SKU</el-button>
          </div>
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
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.sku-editor {
  width: 100%;
}
.sku-editor__row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

/* ---- 主图选择器 ---- */
.image-picker {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.image-picker__box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100px;
  height: 100px;
  overflow: hidden;
  color: #8c939d;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;
}
.image-picker__box:hover {
  border-color: var(--el-color-primary);
}
.image-picker__img {
  width: 100%;
  height: 100%;
}
.image-picker__icon {
  font-size: 22px;
}
.image-picker__tip {
  font-size: 12px;
  line-height: 18px;
}
.image-picker__side {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding-top: 4px;
  font-size: 12px;
  line-height: 18px;
  color: #909399;
}
.image-picker__path {
  max-width: 400px;
  word-break: break-all;
  color: #606266;
}
</style>
