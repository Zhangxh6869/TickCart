import request from '@/utils/request'
import type { Category, PageResult, ProductDetail, ProductListItem } from '@tickcart/shared-types'

export interface ProductPayload {
  id?: number
  categoryId: number
  name: string
  subtitle?: string
  /**
   * 搜索补充关键词，英文逗号分隔（如 `手机,智能手机,苹果手机`）。
   *
   * <p>用户会搜、但商品名/副标题里没有的品类词与别名。搜索时与商品名、副标题一起参与匹配。</p>
   */
  searchKeywords?: string
  mainImage?: string
  detail?: string
  price: number
  status?: number
  isSeckill?: number
  /**
   * 累计销量。
   *
   * <p>新增时是初始值（不传后端按 0）；编辑时是绝对值覆盖，**不传则保持原值**。
   * 成交后由后端在支付成功时按购买数量自动累加，这里只是人工校正入口。</p>
   */
  sales?: number
  skus?: Array<{
    id?: number
    specName?: string
    specValue?: string
    skuImage?: string
    price: number
    stock: number
  }>
  /**
   * 图集 URL 列表，数组顺序即落库的 sort 顺序。
   *
   * <p>必填而非可选：后端 {@code ProductService.update} 对图集是全量替换语义
   * （{@code req.getImages() != null} 就先删后插），漏传与传空数组都等于清空，
   * 所以这里强制调用方显式给出，避免「忘记传」被静默当成「要清空」。</p>
   */
  images: string[]
}

export interface ProductQueryParams {
  page?: number
  size?: number
  keyword?: string
  categoryId?: number | null
  status?: number | null
  sortBy?: string
  order?: string
}

/** 商品分页（含下架）。 */
export function fetchProducts(params: ProductQueryParams): Promise<PageResult<ProductListItem>> {
  return request.get('/admin/products', { params })
}

/** 商品详情。 */
export function fetchProductDetail(id: number): Promise<ProductDetail> {
  return request.get(`/admin/products/${id}`)
}

/** 新增商品。 */
export function createProduct(payload: ProductPayload): Promise<number> {
  return request.post('/admin/products', payload)
}

/** 编辑商品。 */
export function updateProduct(id: number, payload: ProductPayload): Promise<void> {
  return request.put(`/admin/products/${id}`, payload)
}

/** 上下架。 */
export function changeProductStatus(id: number, status: number): Promise<void> {
  return request.put(`/admin/products/${id}/status`, null, { params: { status } })
}

/** 删除商品。 */
export function deleteProduct(id: number): Promise<void> {
  return request.delete(`/admin/products/${id}`)
}

// ---------------- 批量（后端单事务，全部成功或全部不做） ----------------

/**
 * 批量上架/下架，返回实际更新行数。
 *
 * <p>三个批量动作刻意都做成独立接口而不是前端循环：循环是 N 个独立事务，
 * 第 N 条失败时前面的已经提交，用户只会看到「部分失败」却不知道哪几条生效了。</p>
 */
export function batchChangeProductStatus(ids: number[], status: number): Promise<number> {
  return request.put('/admin/products/batch/status', ids, { params: { status } })
}

/** 批量改分类（只改 category_id，不会重建 SKU）。 */
export function batchChangeProductCategory(ids: number[], categoryId: number): Promise<number> {
  return request.put('/admin/products/batch/category', { ids, categoryId })
}

/** 批量删除（连同 SKU 与图集，单事务）。 */
export function batchDeleteProducts(ids: number[]): Promise<number> {
  return request.post('/admin/products/batch/delete', ids)
}

/** 分类列表（含禁用）。 */
export function fetchCategories(): Promise<Category[]> {
  return request.get('/admin/categories')
}

/**
 * 直接设置某个 SKU 的库存（抽屉里改完即落库）。
 *
 * <p>这是独立于「保存商品」的窄接口：改库存不该触发整单 update —— 那会重建 SKU，
 * 把 `product_sku.id` 换掉，秒杀活动里存的 `sku_id` 就失联了。</p>
 */
export function updateSkuStock(skuId: number, stock: number): Promise<void> {
  return request.put(`/admin/skus/${skuId}/stock`, null, { params: { stock } })
}

/** 新增分类。 */
export function createCategory(payload: Partial<Category>): Promise<number> {
  return request.post('/admin/categories', payload)
}

/** 编辑分类。 */
export function updateCategory(id: number, payload: Partial<Category>): Promise<void> {
  return request.put(`/admin/categories/${id}`, payload)
}

/** 删除分类。 */
export function deleteCategory(id: number): Promise<void> {
  return request.delete(`/admin/categories/${id}`)
}
