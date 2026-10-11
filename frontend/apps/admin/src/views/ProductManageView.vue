<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { ArrowLeft, ArrowRight, Close, Plus } from '@element-plus/icons-vue'
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
  skus: [{ specName: '', specValue: '', skuImage: '', price: 0, stock: 0 }],
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
    images: (detail.images ?? []).map((i) => i.url),
  }
  dialogVisible.value = true
}

function addSku() {
  form.value.skus?.push({ specName: '', specValue: '', skuImage: '', price: 0, stock: 0 })
}

function removeSku(index: number) {
  form.value.skus?.splice(index, 1)
}

// ---------------- 图片：本地选图 → 上传 → 回填 URL ----------------
const uploading = ref(false)

/** 图集在传数量：多选时同时会有多张在传，用计数而非布尔表示「有上传进行中」。 */
const galleryPending = ref(0)
const galleryUploading = computed(() => galleryPending.value > 0)

/**
 * 图集上传队列。
 *
 * <p>必须串行：多选时若并发上传，各请求的返回顺序不可控，而数组顺序就是落库的
 * {@code sort}（也是详情页图集的展示顺序），并发会让用户选的顺序被打乱。</p>
 */
let galleryQueue: Promise<void> = Promise.resolve()

/** 校验本地图片，返回错误文案；通过则返回 null（主图与图集共用）。 */
function validateImage(raw: File): string | null {
  if (!raw.type.startsWith('image/')) {
    return '请选择图片文件'
  }
  if (raw.size > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
    return `图片大小不能超过 ${MAX_IMAGE_SIZE_MB}MB`
  }
  return null
}

/** 选择本地图片后立即上传，成功后把后端返回的相对 URL 写入主图字段。 */
async function onMainImageChange(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  const error = validateImage(raw)
  if (error) {
    ElMessage.warning(error)
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

/**
 * 追加一张图集图片（入队，不等待）。
 *
 * <p>选图即上传，成功后按选择顺序追加到数组尾部；上传中的进度由缩略图出现与
 * loading 表达，因此不再逐张弹「上传成功」提示，避免多选时刷屏。</p>
 */
function onGalleryChange(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  const error = validateImage(raw)
  if (error) {
    ElMessage.warning(error)
    return
  }
  galleryQueue = galleryQueue.then(() => appendGalleryImage(raw))
}

async function appendGalleryImage(raw: File) {
  galleryPending.value += 1
  try {
    form.value.images.push(await uploadImage(raw))
  } catch {
    // 失败文案由请求拦截器统一提示；这里吞掉异常，否则队列会因 rejected 而卡死
  } finally {
    galleryPending.value -= 1
  }
}

function removeGalleryImage(index: number) {
  form.value.images.splice(index, 1)
}

/** 与前/后相邻的一张交换位置；越界不动。 */
function moveGalleryImage(index: number, offset: number) {
  const images = form.value.images
  const target = index + offset
  if (target < 0 || target >= images.length) return
  const current = images[index]
  images[index] = images[target]
  images[target] = current
}

/** SKU 图片上传中状态，按行下标跟踪（一行只会有一张图在传）。 */
const skuUploading = ref<Record<number, boolean>>({})

/**
 * 给某一行 SKU 上传图片。
 *
 * <p>先按对象引用抓住这一行的 sku 再 await：上传期间用户若删掉/移动了行，
 * 结果仍写回原来那一行，不会因为下标漂移而串到别的规格上。</p>
 */
async function onSkuImageChange(index: number, uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  const error = validateImage(raw)
  if (error) {
    ElMessage.warning(error)
    return
  }
  const sku = form.value.skus?.[index]
  if (!sku) return
  skuUploading.value = { ...skuUploading.value, [index]: true }
  try {
    sku.skuImage = await uploadImage(raw)
  } finally {
    const next = { ...skuUploading.value }
    delete next[index]
    skuUploading.value = next
  }
}

function clearSkuImage(index: number) {
  const sku = form.value.skus?.[index]
  if (sku) {
    sku.skuImage = ''
  }
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
        <el-form-item label="商品图集">
          <div class="gallery-editor">
            <div
              v-for="(url, index) in form.images"
              :key="url"
              class="gallery-editor__item"
            >
              <div class="gallery-editor__thumb">
                <el-image :src="url" fit="cover" class="gallery-editor__img" />
                <el-button
                  class="gallery-editor__remove"
                  type="danger"
                  size="small"
                  circle
                  :icon="Close"
                  title="移除这张图"
                  @click="removeGalleryImage(index)"
                />
              </div>
              <div class="gallery-editor__actions">
                <el-button
                  link
                  type="primary"
                  :icon="ArrowLeft"
                  title="前移"
                  :disabled="index === 0"
                  @click="moveGalleryImage(index, -1)"
                />
                <span class="gallery-editor__index">{{ index + 1 }}</span>
                <el-button
                  link
                  type="primary"
                  :icon="ArrowRight"
                  title="后移"
                  :disabled="index === form.images.length - 1"
                  @click="moveGalleryImage(index, 1)"
                />
              </div>
            </div>
            <el-upload
              class="gallery-editor__upload"
              :show-file-list="false"
              :auto-upload="false"
              multiple
              :accept="ACCEPT_IMAGE_TYPES"
              :disabled="galleryUploading"
              :on-change="onGalleryChange"
            >
              <div v-loading="galleryUploading" class="gallery-editor__add">
                <el-icon class="image-picker__icon"><Plus /></el-icon>
                <span class="image-picker__tip">添加图片</span>
              </div>
            </el-upload>
          </div>
          <div class="gallery-editor__hint">
            按顺序展示，第一张为详情页图集首图；不添加时详情页退回主图。单张不超过
            {{ MAX_IMAGE_SIZE_MB }}MB。
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
              <div class="sku-editor__image-wrap">
                <el-upload
                  class="sku-editor__upload"
                  :show-file-list="false"
                  :auto-upload="false"
                  :accept="ACCEPT_IMAGE_TYPES"
                  :disabled="!!skuUploading[index]"
                  :on-change="(file: UploadFile) => onSkuImageChange(index, file)"
                >
                  <div v-loading="!!skuUploading[index]" class="sku-editor__image">
                    <img
                      v-if="sku.skuImage"
                      :src="sku.skuImage"
                      alt=""
                      class="sku-editor__image-img"
                    />
                    <el-icon v-else class="sku-editor__image-icon"><Plus /></el-icon>
                  </div>
                </el-upload>
                <button
                  v-if="sku.skuImage"
                  class="sku-editor__image-clear"
                  type="button"
                  title="清除图片"
                  @click="clearSkuImage(index)"
                >
                  <el-icon><Close /></el-icon>
                </button>
              </div>
              <el-input v-model="sku.specName" placeholder="规格名" style="width: 100px" />
              <el-input v-model="sku.specValue" placeholder="规格值" style="width: 130px" />
              <el-input-number
                v-model="sku.price"
                :min="0"
                :precision="2"
                placeholder="价格"
                style="width: 110px"
              />
              <el-input-number
                v-model="sku.stock"
                :min="0"
                placeholder="库存"
                style="width: 100px"
              />
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
  gap: 6px;
  margin-bottom: 8px;
  align-items: center;
}

/* ---- SKU 图片选择器 ---- */
.sku-editor__image-wrap {
  position: relative;
  flex: none;
  width: 40px;
  height: 40px;
}
.sku-editor__image {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  overflow: hidden;
  color: #8c939d;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;
}
.sku-editor__image:hover {
  border-color: var(--el-color-primary);
}
.sku-editor__image-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.sku-editor__image-icon {
  font-size: 16px;
}
.sku-editor__image-clear {
  position: absolute;
  top: -6px;
  right: -6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  color: #fff;
  background: var(--el-color-danger);
  border: none;
  border-radius: 50%;
  cursor: pointer;
}
.sku-editor__image-clear :deep(svg) {
  width: 10px;
  height: 10px;
}
.sku-editor__image-clear:hover {
  background: var(--el-color-danger-dark-2, #c45656);
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

/* ---- 图集编辑器 ---- */
.gallery-editor {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  width: 100%;
}
.gallery-editor__item {
  width: 88px;
}
.gallery-editor__thumb {
  position: relative;
  width: 88px;
  height: 88px;
}
.gallery-editor__img {
  width: 100%;
  height: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}
.gallery-editor__remove {
  position: absolute;
  top: -6px;
  right: -6px;
}
.gallery-editor__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 24px;
}
/* Element Plus 默认给相邻按钮加 12px 左间距，会挤坏 88px 的三栏布局 */
.gallery-editor__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.gallery-editor__index {
  font-size: 12px;
  color: #909399;
}
.gallery-editor__add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 88px;
  height: 88px;
  color: #8c939d;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;
}
.gallery-editor__add:hover {
  border-color: var(--el-color-primary);
}
.gallery-editor__hint {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 18px;
  color: #909399;
}
</style>
