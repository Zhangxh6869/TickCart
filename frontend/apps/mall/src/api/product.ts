import request from '@/utils/request'
import type { ProductListItem, ProductDetail, ProductQuery, Category, PageResult } from '@tickcart/shared-types'

/** 商品列表（公开）。 */
export function fetchProducts(query: ProductQuery): Promise<PageResult<ProductListItem>> {
  return request.get('/products', { params: query })
}

/** 商品详情（公开）。 */
export function fetchProductDetail(id: number): Promise<ProductDetail> {
  return request.get(`/products/${id}`)
}

/** 分类列表（公开，仅启用）。 */
export function fetchCategories(): Promise<Category[]> {
  return request.get('/categories')
}
