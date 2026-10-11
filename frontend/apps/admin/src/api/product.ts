import request from '@/utils/request'
import type { Category, PageResult, ProductDetail, ProductListItem } from '@tickcart/shared-types'

export interface ProductPayload {
  id?: number
  categoryId: number
  name: string
  subtitle?: string
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

/** 分类列表（含禁用）。 */
export function fetchCategories(): Promise<Category[]> {
  return request.get('/admin/categories')
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
