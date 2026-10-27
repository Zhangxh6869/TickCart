<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { ArrowDown, ArrowLeft, ArrowRight, Close, Plus } from '@element-plus/icons-vue'
import type { Category, LowStockItem, LowStockReport, ProductListItem } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import {
  batchChangeProductCategory,
  batchChangeProductStatus,
  batchDeleteProducts,
  changeProductStatus,
  createProduct,
  deleteProduct,
  fetchCategories,
  fetchLowStock,
  fetchProductDetail,
  fetchProducts,
  updateProduct,
  updateSkuStock,
  type ProductPayload,
} from '@/api/product'
import { ACCEPT_IMAGE_TYPES, MAX_IMAGE_SIZE_MB, uploadImage } from '@/api/upload'

/**
 * 商城前台地址：只用于「预览详情页」跳转。
 *
 * <p>后台和商城是两个独立 dev server（5174 / 5173），没有共享路由，
 * 所以这里直接开新标签页，不做跨应用路由跳转。</p>
 */
const MALL_URL = import.meta.env.VITE_MALL_URL ?? 'http://localhost:5173'

const loading = ref(false)
const list = ref<ProductListItem[]>([])
const total = ref(0)
const categories = ref<Category[]>([])
const route = useRoute()

/** 排序项：sortBy / order 白名单与后端 ProductService#applySort 一致（price / sales / createdAt） */
const SORT_OPTIONS = [
  { label: '最近更新', sortBy: 'createdAt', order: 'desc' },
  { label: '价格从低到高', sortBy: 'price', order: 'asc' },
  { label: '价格从高到低', sortBy: 'price', order: 'desc' },
  { label: '销量从高到低', sortBy: 'sales', order: 'desc' },
] as const

const sortIndex = ref(0)

const query = reactive({
  page: 1,
  /** 每页 12：一屏能看完且不用频繁翻页，与设计稿一致 */
  size: 12,
  keyword: '',
  categoryId: null as number | null,
  status: null as number | null,
  /** 只看库存告急（商品级总库存口径，SKU 级见补货清单） */
  stockLow: false,
})

// ---------------- 列表 ----------------
async function load() {
  loading.value = true
  try {
    const sort = SORT_OPTIONS[sortIndex.value]
    const res = await fetchProducts({
      ...query,
      sortBy: sort.sortBy,
      order: sort.order,
    })
    list.value = res.records
    total.value = res.total
    // 列表换了就清空勾选：否则「已选 3 项」里可能混着上一页的商品
    clearSelection()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 从「搜索分析 · 建商品」跳过来时带着关键词，直接预填筛选条件
  const keywordFromQuery = route.query.keyword
  if (typeof keywordFromQuery === 'string' && keywordFromQuery.trim()) {
    query.keyword = keywordFromQuery.trim()
  }
  // 从「数据看板 · 库存告急」跳过来时，直接只看库存告急（商品级口径，与看板数字一致）
  if (route.query.lowStock === '1') {
    query.stockLow = true
  }
  categories.value = await fetchCategories()
  await Promise.all([load(), loadLowStock()])
})

/**
 * 列表 + 告急横幅一起刷新。
 *
 * <p>上下架、删除、批量操作都会改变「哪些商品该被扫描」（补货待办只算在售），
 * 所以横幅数字必须跟着变，否则会出现「商品已下架、横幅还说它告急」。</p>
 */
async function reloadAfterMutation() {
  await load()
  await loadLowStock()
}

function onSearch() {
  query.page = 1
  load()
}

/** 分类 chips：点当前项可取消（回到全部） */
function onCategoryChip(id: number | null) {
  query.categoryId = query.categoryId === id ? null : id
  onSearch()
}

function onStatusSeg(value: number | null) {
  query.status = value
  onSearch()
}

function onSortChange() {
  query.page = 1
  load()
}

/**
 * 切换「只看库存告急」。
 *
 * <p>这是**商品级**口径（总库存 < 分类阈值），和补货清单的 SKU 级不是一回事：
 * 这里用来在完整列表上下文里处理告急商品（能编辑、能批量），
 * 补货清单用来回答「今天补哪个规格」。</p>
 */
function toggleStockLow() {
  query.stockLow = !query.stockLow
  query.page = 1
  load()
}

function categoryName(id: number): string {
  return categories.value.find((c) => c.id === id)?.name ?? '-'
}

/** 当前页库存告急条数：只用于提示，不做筛选（全库筛选见设计文档 §11） */
const lowStockCount = computed(() => list.value.filter((it) => it.stockLow).length)

function rowClassName({ row }: { row: ProductListItem }): string {
  return row.stockLow ? 'is-low' : ''
}

// ---------------- 勾选与批量 ----------------
const tableRef = ref()
const selectedIds = ref<number[]>([])

const selectedCount = computed(() => selectedIds.value.length)

function onSelectionChange(rows: ProductListItem[]) {
  selectedIds.value = rows.map((r) => r.id)
}

function clearSelection() {
  selectedIds.value = []
  tableRef.value?.clearSelection()
}

// ---------------- 上下架（乐观更新，不重查列表） ----------------
const statusPending = ref<number[]>([])

/**
 * 切换上下架。
 *
 * <p>先改本地状态再发请求：这个动作频率高，等接口回来再刷新会让开关「粘手」。
 * 失败则回滚并提示。刻意**不调用 load()** —— 那会丢掉滚动位置和当前筛选。</p>
 */
async function onToggleStatus(row: ProductListItem) {
  if (statusPending.value.includes(row.id)) return
  const previous = row.status
  const next = previous === 1 ? 0 : 1
  row.status = next
  statusPending.value = [...statusPending.value, row.id]
  try {
    await changeProductStatus(row.id, next)
    ElMessage.success(next === 1 ? '已上架' : '已下架')
    // 只静默刷新横幅：补货待办只算在售商品，下架后它不该再出现。
    // 列表不重查 —— 那会丢掉滚动位置和当前筛选
    void loadLowStock()
  } catch {
    row.status = previous
  } finally {
    statusPending.value = statusPending.value.filter((id) => id !== row.id)
  }
}

// ---------------- 单条删除 / 预览 ----------------
async function onDelete(row: ProductListItem) {
  await ElMessageBox.confirm(`确认删除商品「${row.name}」？`, '删除后不可恢复', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger',
  })
  await deleteProduct(row.id)
  ElMessage.success('已删除')
  await reloadAfterMutation()
}

function onPreview(row: ProductListItem) {
  window.open(`${MALL_URL}/product/${row.id}`, '_blank')
}

function onRowCommand(cmd: string, row: ProductListItem) {
  if (cmd === 'delete') {
    onDelete(row)
    return
  }
  onPreview(row)
}

// ---------------- 批量动作 ----------------
async function onBatchStatus(status: number) {
  const ids = selectedIds.value
  if (!ids.length) return
  const action = status === 1 ? '上架' : '下架'
  await ElMessageBox.confirm(`确认${action}选中的 ${ids.length} 个商品？`, '批量操作', {
    type: 'warning',
  })
  await batchChangeProductStatus(ids, status)
  ElMessage.success(`已${action} ${ids.length} 个商品`)
  await reloadAfterMutation()
}

const batchCategoryVisible = ref(false)
const batchCategoryId = ref<number | null>(null)
const batchSubmitting = ref(false)

function openBatchCategory() {
  batchCategoryId.value = null
  batchCategoryVisible.value = true
}

async function submitBatchCategory() {
  if (!batchCategoryId.value) {
    ElMessage.warning('请选择目标分类')
    return
  }
  batchSubmitting.value = true
  try {
    await batchChangeProductCategory(selectedIds.value, batchCategoryId.value)
    ElMessage.success(`已改分类 ${selectedIds.value.length} 个商品`)
    batchCategoryVisible.value = false
    await reloadAfterMutation()
  } finally {
    batchSubmitting.value = false
  }
}

async function onBatchDelete() {
  const ids = selectedIds.value
  if (!ids.length) return
  await ElMessageBox.confirm(
    `确认删除选中的 ${ids.length} 个商品？其 SKU 与图集会一并删除，且不可恢复。`,
    '批量删除',
    { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' },
  )
  await batchDeleteProducts(ids)
  ElMessage.success(`已删除 ${ids.length} 个商品`)
  await reloadAfterMutation()
}

// ---------------- 补货待办（SKU 粒度） ----------------
const lowStock = ref<LowStockReport | null>(null)
const lowStockVisible = ref(false)
const lowStockLoading = ref(false)
/** 抽屉里改过库存 → 关抽屉时要刷新列表，否则行内红标还是旧的 */
const stockTouched = ref(false)
/** 每行库存基线，保存失败时回滚 */
const lowStockBaseline = ref<Record<number, number>>({})

async function loadLowStock() {
  try {
    lowStock.value = await fetchLowStock(100)
  } catch {
    // 横幅只是辅助信息，拉失败不该打断主流程
    lowStock.value = null
  }
}

async function openLowStock() {
  lowStockLoading.value = true
  try {
    lowStock.value = await fetchLowStock(100)
    lowStockBaseline.value = Object.fromEntries(
      (lowStock.value.items ?? []).map((it) => [it.skuId, it.stock]),
    )
    lowStockVisible.value = true
  } finally {
    lowStockLoading.value = false
  }
}

function onLowStockClosed() {
  if (!stockTouched.value) return
  stockTouched.value = false
  void reloadAfterMutation()
}

function specText(it: LowStockItem): string {
  const value = it.specValue?.trim()
  if (!value) return '默认规格'
  const name = it.specName?.trim()
  return name ? `${name} ${value}` : value
}

/**
 * 抽屉里改库存：改完即落库，补够了的行自动从清单消失。
 *
 * <p>不做「标记已处理」—— 库存补到线上以后它本来就不该留在待办里，
 * 多一个状态反而要维护「处理了但没补货」的中间态。</p>
 */
async function onLowStockStockChange(it: LowStockItem) {
  const baseline = lowStockBaseline.value[it.skuId]
  if (baseline === undefined || baseline === it.stock) return
  try {
    await updateSkuStock(it.skuId, it.stock)
    stockTouched.value = true
    lowStockBaseline.value = { ...lowStockBaseline.value, [it.skuId]: it.stock }
    if (it.stock >= it.threshold) {
      const report = lowStock.value
      if (report) {
        report.items = report.items.filter((x) => x.skuId !== it.skuId)
        report.total = Math.max(0, report.total - 1)
      }
      ElMessage.success({ message: '库存已更新，已从清单移除', duration: 1600 })
    } else {
      it.shortage = it.threshold - it.stock
      it.remainRatio = Math.round((it.stock * 100) / it.threshold) / 100
      ElMessage.success({ message: '库存已更新', duration: 1200 })
    }
  } catch {
    it.stock = baseline
  }
}

// ---------------- 抽屉表单 ----------------
const drawerVisible = ref(false)
const drawerTitle = ref('新增商品')
const drawerTab = ref<'basic' | 'image' | 'sku'>('basic')
const submitting = ref(false)

const emptyForm = (): ProductPayload => ({
  categoryId: 1,
  name: '',
  subtitle: '',
  searchKeywords: '',
  mainImage: '',
  detail: '',
  price: 0,
  sales: 0,
  status: 0,
  isSeckill: 0,
  skus: [{ specName: '', specValue: '', skuImage: '', price: 0, stock: 0 }],
  images: [],
})

const form = ref<ProductPayload>(emptyForm())
/** 抽屉头部展示用：编辑时是商品 ID */
const editingId = ref<number | null>(null)

function openCreate() {
  drawerTitle.value = '新增商品'
  editingId.value = null
  form.value = emptyForm()
  drawerTab.value = 'basic'
  skuStockSnapshot.value = {}
  drawerVisible.value = true
}

async function openEdit(row: ProductListItem) {
  drawerTitle.value = '编辑商品'
  editingId.value = row.id
  const detail = await fetchProductDetail(row.id)
  form.value = {
    id: detail.id,
    categoryId: detail.categoryId,
    name: detail.name,
    subtitle: detail.subtitle ?? '',
    searchKeywords: detail.searchKeywords ?? '',
    mainImage: detail.mainImage ?? '',
    detail: detail.detail ?? '',
    price: detail.price,
    sales: detail.sales,
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
  // 记录 SKU 库存基线：抽屉里的库存是「改完即落库」，靠它判断是否真的变了
  skuStockSnapshot.value = Object.fromEntries((detail.skus ?? []).map((s) => [s.id, s.stock]))
  drawerTab.value = 'basic'
  drawerVisible.value = true
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

// ---------------- SKU 库存即时落库 ----------------
/**
 * 各 SKU 已落库的库存基线。
 *
 * <p>抽屉里改 SKU 库存是**即时生效**的（走 {@code PUT /admin/skus/{id}/stock}），
 * 不等「保存」—— 改库存不该触发整单 update，否则会重建 SKU 把 id 换掉，
 * 秒杀活动里存的 sku_id 就失联了。所以界面上要明确提示「失焦即保存」。</p>
 */
const skuStockSnapshot = ref<Record<number, number>>({})

type FormSku = NonNullable<ProductPayload['skus']>[number]

async function onSkuStockBlur(sku: FormSku) {
  if (!sku.id) return // 新增的 SKU 还没落库，等整体保存
  const baseline = skuStockSnapshot.value[sku.id]
  if (baseline === sku.stock) return
  try {
    await updateSkuStock(sku.id, sku.stock)
    skuStockSnapshot.value = { ...skuStockSnapshot.value, [sku.id]: sku.stock }
    ElMessage.success({ message: '库存已保存', duration: 1200 })
  } catch {
    sku.stock = baseline ?? sku.stock
  }
}

// ---------------- 保存 ----------------
async function onSubmit() {
  if (!form.value.name) {
    ElMessage.warning('请填写商品名称')
    drawerTab.value = 'basic'
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
    drawerVisible.value = false
    await reloadAfterMutation()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="pm">
    <!-- 页头：标题由顶栏提供，这里只放统计与主操作 -->
    <div class="pm__head">
      <div class="pm__summary">
        <template v-if="query.stockLow">
          共 <b>{{ total }}</b> 个库存告急商品
          <el-button link type="primary" class="pm__clear" @click="toggleStockLow">取消筛选</el-button>
        </template>
        <template v-else>
          共 <b>{{ total }}</b> 个商品
          <template v-if="lowStockCount > 0">
            · <span class="pm__danger">本页 {{ lowStockCount }} 个库存告急</span>
          </template>
        </template>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增商品</el-button>
    </div>

    <!-- 补货待办横幅：只在有告急时出现，不弹窗不阻塞 -->
    <div v-if="lowStock && lowStock.total > 0" class="pm__alert">
      <span class="pm__alert-icon">!</span>
      <div class="pm__alert-text">
        <b>{{ lowStock.total }}</b> 个 SKU 库存告急<template v-if="lowStock.outOfStock > 0"
          >，其中 <b>{{ lowStock.outOfStock }}</b> 个已断货</template
        >
      </div>
      <el-button size="small" type="danger" plain :loading="lowStockLoading" @click="openLowStock">
        查看补货清单
      </el-button>
    </div>

    <!-- 筛选 -->
    <el-card class="pm__filter" shadow="never">
      <div class="pm__row">
        <el-input
          v-model="query.keyword"
          class="pm__search"
          placeholder="商品名称 / 关键词"
          clearable
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-segmented
          :model-value="query.status"
          :options="[
            { label: '全部', value: null },
            { label: '在售', value: 1 },
            { label: '已下架', value: 0 },
          ]"
          @change="(v: any) => onStatusSeg(v as number | null)"
        />
        <el-check-tag
          :checked="query.stockLow"
          class="pm__chip pm__warn-chip"
          @change="toggleStockLow"
        >
          只看库存告急
        </el-check-tag>
        <div class="pm__spacer" />
        <el-select v-model="sortIndex" class="pm__sort" @change="onSortChange">
          <el-option
            v-for="(opt, i) in SORT_OPTIONS"
            :key="opt.label"
            :label="opt.label"
            :value="i"
          />
        </el-select>
        <el-button type="primary" plain @click="onSearch">查询</el-button>
      </div>
      <div class="pm__row pm__row--chips">
        <span class="pm__label">分类</span>
        <el-check-tag
          :checked="query.categoryId === null"
          class="pm__chip"
          @change="onCategoryChip(null)"
        >
          全部
        </el-check-tag>
        <el-check-tag
          v-for="c in categories"
          :key="c.id"
          :checked="query.categoryId === c.id"
          class="pm__chip"
          @change="onCategoryChip(c.id)"
        >
          {{ c.name }}
        </el-check-tag>
      </div>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <!-- 批量操作条：勾选后浮出 -->
      <transition name="el-fade-in">
        <div v-if="selectedCount > 0" class="pm__batch">
          <span>已选 <b>{{ selectedCount }}</b> 项</span>
          <div class="pm__spacer" />
          <el-button size="small" @click="onBatchStatus(1)">批量上架</el-button>
          <el-button size="small" @click="onBatchStatus(0)">批量下架</el-button>
          <el-button size="small" @click="openBatchCategory">改分类</el-button>
          <el-button size="small" type="danger" plain @click="onBatchDelete">删除</el-button>
          <el-button size="small" link @click="clearSelection">取消选择</el-button>
        </div>
      </transition>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="list"
        :row-class-name="rowClassName"
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column label="商品" min-width="330">
          <template #default="{ row }">
            <div class="goods">
              <div class="goods__img" :class="{ 'is-off': row.status !== 1 }">
                <el-image :src="row.mainImage" fit="cover" class="goods__pic" />
              </div>
              <div class="goods__body">
                <div class="goods__name" :class="{ 'is-off': row.status !== 1 }">
                  {{ row.name }}
                </div>
                <div v-if="row.subtitle" class="goods__sub">{{ row.subtitle }}</div>
                <div class="goods__meta">
                  <el-tag size="small" type="info" effect="plain">
                    {{ categoryName(row.categoryId) }}
                  </el-tag>
                  <span>ID {{ row.id }}</span>
                  <el-tag v-if="row.isSeckill === 1" size="small" type="danger" effect="plain">
                    秒杀中
                  </el-tag>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="118">
          <template #default="{ row }">
            <div class="pm__price">¥{{ formatPrice(row.price) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="104">
          <template #default="{ row }">
            <div class="pm__stock" :class="{ 'is-low': row.stockLow }">
              <span class="pm__stock-num">{{ row.stock }}</span>
              <el-tag v-if="row.stockLow" size="small" type="danger" effect="dark">告急</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="销量" width="88" align="right">
          <template #default="{ row }">
            <span class="pm__num">{{ row.sales }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="112">
          <template #default="{ row }">
            <div class="pm__status">
              <el-switch
                :model-value="row.status === 1"
                :loading="statusPending.includes(row.id)"
                @change="onToggleStatus(row)"
              />
              <span :class="{ 'is-off': row.status !== 1 }">
                {{ row.status === 1 ? '在售' : '已下架' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="132" align="right">
          <template #default="{ row }">
            <div class="pm__ops">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-dropdown trigger="click" @command="(cmd: string) => onRowCommand(cmd, row)">
                <el-button link type="primary">
                  更多<el-icon class="pm__caret"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="preview">预览详情页</el-dropdown-item>
                    <el-dropdown-item command="delete" divided class="pm__danger-item">
                      删除商品
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="pm__empty">
            <p>没有符合条件的商品</p>
            <p class="pm__empty-tip">换个关键词或分类试试，也可以直接新增商品</p>
          </div>
        </template>
      </el-table>

      <el-pagination
        class="pm__pager"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.page"
        :page-size="query.size"
        @current-change="(p: number) => { query.page = p; load() }"
      />
    </el-card>

    <!-- 批量改分类 -->
    <el-dialog v-model="batchCategoryVisible" title="批量改分类" width="380px">
      <el-select v-model="batchCategoryId" placeholder="选择目标分类" style="width: 100%">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <p class="pm__hint">已选 {{ selectedCount }} 个商品，改分类不会影响它们的规格与库存。</p>
      <template #footer>
        <el-button @click="batchCategoryVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatchCategory">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 补货清单 -->
    <el-drawer
      v-model="lowStockVisible"
      title="补货清单"
      size="640px"
      class="ls-drawer"
      @closed="onLowStockClosed"
    >
      <div class="ls__note">
        清单按 <b>SKU</b> 统计：商品总库存够、但某个规格断货时，商品列表不会标红，这里会出现。
        改完库存这一行会自动消失。
      </div>

      <div v-if="lowStock && lowStock.items.length" class="ls__list">
        <div v-for="it in lowStock.items" :key="it.skuId" class="ls__row">
          <img class="ls__img" :src="it.skuImage || it.mainImage || ''" alt="" />
          <div class="ls__body">
            <div class="ls__name">{{ it.productName }}</div>
            <div class="ls__meta">
              <el-tag size="small" type="info" effect="plain">{{ it.categoryName || '-' }}</el-tag>
              <span>{{ specText(it) }}</span>
            </div>
          </div>
          <div class="ls__stat">
            <div class="ls__num" :class="{ 'is-zero': it.stock === 0 }">
              {{ it.stock }}<i>/{{ it.threshold }}</i>
            </div>
            <div class="ls__gap">缺 {{ it.shortage }}</div>
          </div>
          <el-input-number
            v-model="it.stock"
            :min="0"
            :controls="false"
            size="small"
            class="ls__input"
            @change="onLowStockStockChange(it)"
          />
        </div>
      </div>

      <el-empty v-else description="当前没有库存告急的 SKU" :image-size="80" />

      <template #footer>
        <span class="ls__foot">
          {{ lowStock?.truncated ? '清单已截断，仅显示前 100 条' : '库存补到预警线以上，该行自动移出清单' }}
        </span>
        <el-button @click="lowStockVisible = false">关闭</el-button>
      </template>
    </el-drawer>

    <!-- 新增 / 编辑抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="760px"
      class="pm-drawer"
      destroy-on-close
    >
      <template #header>
        <div class="dr-head">
          <div class="dr-head__title">{{ drawerTitle }}</div>
          <div v-if="editingId" class="dr-head__meta">
            ID {{ editingId }} · {{ categoryName(form.categoryId) }}
          </div>
          <div v-else class="dr-head__meta">填写基础信息后保存，图片与规格可随后补</div>
        </div>
      </template>

      <el-tabs v-model="drawerTab" class="dr-tabs">
        <!-- 基础信息 -->
        <el-tab-pane label="基础信息" name="basic">
          <el-form label-width="88px" label-position="right">
            <el-form-item label="商品名称" required>
              <el-input v-model="form.name" placeholder="如：5G 旗舰智能手机 12+256G 曜夜黑" />
            </el-form-item>
            <el-form-item label="副标题">
              <el-input v-model="form.subtitle" placeholder="卖点短句，列表与详情页都会展示" />
            </el-form-item>
            <el-form-item label="分类" required>
              <el-select v-model="form.categoryId" style="width: 220px">
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="搜索关键词">
              <el-input
                v-model="form.searchKeywords"
                placeholder="用户会搜、但商品名里没有的品类词，英文逗号分隔，如：手机,智能手机,苹果手机"
              />
              <div class="dr-hint">与名称、副标题一起参与搜索匹配</div>
            </el-form-item>
            <el-form-item label="展示价格">
              <div class="dr-inline">
                <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" />
                <span class="dr-hint">元</span>
              </div>
            </el-form-item>
            <el-form-item label="累计销量">
              <div class="dr-inline">
                <el-input-number v-model="form.sales" :min="0" :step="1" />
                <span class="dr-hint">支付成功后自动累加，这里只做人工校正</span>
              </div>
            </el-form-item>
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="商品详情">
              <el-input v-model="form.detail" type="textarea" :rows="4" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 图片 -->
        <el-tab-pane name="image">
          <template #label>
            图片
            <span class="dr-badge">{{ form.images.length + (form.mainImage ? 1 : 0) }}</span>
          </template>
          <el-form label-width="88px">
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
                    <img
                      v-if="form.mainImage"
                      :src="form.mainImage"
                      alt=""
                      class="image-picker__img"
                    />
                    <template v-else>
                      <el-icon class="image-picker__icon"><Plus /></el-icon>
                      <span class="image-picker__tip">选择图片</span>
                    </template>
                  </div>
                </el-upload>
                <div class="image-picker__side">
                  <span v-if="form.mainImage" class="image-picker__path">{{ form.mainImage }}</span>
                  <span v-else class="image-picker__tip">
                    JPG / PNG / GIF / WEBP，不超过 {{ MAX_IMAGE_SIZE_MB }}MB
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
                <div v-for="(url, index) in form.images" :key="url" class="gallery-editor__item">
                  <div class="gallery-editor__thumb">
                    <el-image :src="url" fit="cover" class="gallery-editor__img" />
                    <el-tag v-if="index === 0" size="small" class="gallery-editor__first">首图</el-tag>
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
              <div class="dr-hint">
                按顺序展示，第一张为详情页图集首图；不添加时详情页退回主图。单张不超过
                {{ MAX_IMAGE_SIZE_MB }}MB。
              </div>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- SKU -->
        <el-tab-pane name="sku">
          <template #label>
            SKU
            <span class="dr-badge">{{ form.skus?.length ?? 0 }}</span>
          </template>
          <div class="sku-toolbar">
            <span class="dr-hint">库存失焦即保存；价格、规格随「保存」一起提交</span>
            <el-button size="small" :icon="Plus" @click="addSku">添加 SKU</el-button>
          </div>
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
              <el-input v-model="sku.specName" placeholder="规格名" style="width: 96px" />
              <el-input v-model="sku.specValue" placeholder="规格值" style="width: 128px" />
              <el-input-number
                v-model="sku.price"
                :min="0"
                :precision="2"
                :controls="false"
                placeholder="价格"
                style="width: 104px"
              />
              <el-input-number
                v-model="sku.stock"
                :min="0"
                :controls="false"
                placeholder="库存"
                style="width: 84px"
                @blur="onSkuStockBlur(sku)"
              />
              <el-button link type="danger" @click="removeSku(index)">删除</el-button>
            </div>
            <p v-if="!form.skus?.length" class="dr-hint">
              还没有 SKU。无规格商品保留一行即可（规格名与规格值留空），价格会继承商品价。
            </p>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <div class="dr-foot">
          <span class="dr-foot__note">SKU 库存是即时保存的，其余改动需点「保存」</span>
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
/* ---------------- 页头 ---------------- */
.pm__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.pm__summary {
  font-size: 13.5px;
  color: var(--el-text-color-regular);
}
.pm__summary b {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.pm__danger {
  color: var(--el-color-danger);
}

/* ---------------- 筛选 ---------------- */
.pm__filter {
  margin-bottom: 12px;
}
.pm__row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.pm__row--chips {
  margin-top: 12px;
  gap: 8px;
}
.pm__search {
  width: 280px;
}
.pm__sort {
  width: 148px;
}
.pm__spacer {
  flex: 1;
}
.pm__label {
  flex: 0 0 34px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.pm__chip {
  margin: 0;
}
/* 告急筛选用警示色，和分类 chips 区分开 */
.pm__warn-chip.is-checked {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-5);
}
.pm__clear {
  margin-left: 8px;
  font-size: 12px;
}

/* ---------------- 批量条 ---------------- */
.pm__batch {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 6px;
}
.pm__batch b {
  color: var(--el-color-primary);
}

/* ---------------- 表格：商品复合列 ---------------- */
.goods {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}
.goods__img {
  flex: 0 0 56px;
  width: 56px;
  height: 56px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.goods__img.is-off {
  opacity: 0.55;
}
.goods__pic {
  width: 100%;
  height: 100%;
}
.goods__body {
  min-width: 0;
}
.goods__name {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 500;
  line-height: 1.35;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.goods__name.is-off {
  color: var(--el-text-color-secondary);
}
.goods__sub {
  margin-top: 3px;
  overflow: hidden;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.goods__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
  font-size: 11.5px;
  color: var(--el-text-color-placeholder);
}

.pm__price {
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.pm__num {
  font-variant-numeric: tabular-nums;
}
.pm__stock {
  display: flex;
  align-items: center;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}
.pm__stock.is-low .pm__stock-num {
  font-weight: 600;
  color: var(--el-color-danger);
}
.pm__status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--el-text-color-regular);
}
.pm__status .is-off {
  color: var(--el-text-color-placeholder);
}
.pm__ops {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}
.pm__caret {
  margin-left: 2px;
  font-size: 10px;
}
.pm__danger-item {
  color: var(--el-color-danger);
}
.pm__pager {
  justify-content: flex-end;
  margin-top: 16px;
}
.pm__empty {
  padding: 26px 0;
  color: var(--el-text-color-secondary);
}
.pm__empty-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.pm__hint {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

/* 低库存行：左侧红条（用 box-shadow 而不是 border，避免整行位移 1px） */
:deep(.el-table__row.is-low) td:first-child {
  box-shadow: inset 3px 0 0 var(--el-color-danger);
}

/* ---------------- 补货待办横幅 ---------------- */
.pm__alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 6px;
}
.pm__alert-icon {
  display: flex;
  flex: 0 0 18px;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: var(--el-color-danger);
  border-radius: 50%;
}
.pm__alert-text {
  flex: 1;
}
.pm__alert-text b {
  font-weight: 600;
}

/* ---------------- 补货清单抽屉 ---------------- */
.ls__note {
  padding: 10px 12px;
  margin-bottom: 10px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
.ls__list {
  display: flex;
  flex-direction: column;
}
.ls__row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.ls__row:last-child {
  border-bottom: 0;
}
.ls__img {
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
  object-fit: cover;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.ls__body {
  flex: 1;
  min-width: 0;
}
.ls__name {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ls__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ls__stat {
  flex: 0 0 78px;
  text-align: right;
}
.ls__num {
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.ls__num i {
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
  color: var(--el-text-color-placeholder);
}
.ls__num.is-zero {
  color: var(--el-color-danger);
}
.ls__gap {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--el-color-danger);
}
.ls__input {
  flex: 0 0 84px;
  width: 84px;
}
.ls__foot {
  flex: 1;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  text-align: left;
}
:deep(.el-drawer__footer) {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ---------------- 抽屉 ---------------- */
.dr-head__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.dr-head__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.dr-tabs {
  margin-top: -6px;
}
.dr-badge {
  display: inline-block;
  min-width: 16px;
  padding: 0 5px;
  margin-left: 4px;
  font-size: 11px;
  line-height: 16px;
  color: var(--el-text-color-secondary);
  text-align: center;
  background: var(--el-fill-color);
  border-radius: 8px;
}
.dr-inline {
  display: flex;
  align-items: center;
  gap: 10px;
}
/* el-input-number 默认 150px，但在 flex 里会被 hint 文字挤扁，把数字裁掉 */
.dr-inline :deep(.el-input-number) {
  flex: 0 0 160px;
  width: 160px;
}
.dr-hint {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.dr-foot {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dr-foot__note {
  flex: 1;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  text-align: left;
}

/* ---------------- SKU 编辑器 ---------------- */
.sku-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
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

/* ---------------- 图片选择器 ---------------- */
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
/* 用 img 而不是 el-image：el-image 在 flex 容器里不会撑满，预览只显示 72px 见方 */
.image-picker__img {
  display: block;
  width: 100px;
  height: 100px;
  object-fit: cover;
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
  max-width: 380px;
  word-break: break-all;
  color: #606266;
}

/* ---------------- 图集编辑器 ---------------- */
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
.gallery-editor__first {
  position: absolute;
  top: 4px;
  left: 4px;
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
</style>
