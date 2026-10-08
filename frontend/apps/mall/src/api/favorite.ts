import request from '@/utils/request'
import type { PageResult, ProductListItem } from '@tickcart/shared-types'

export interface FavoriteQueryParams {
  page?: number
  size?: number
}

/** 收藏列表（需登录，按收藏时间倒序）。 */
export function fetchFavorites(params: FavoriteQueryParams): Promise<PageResult<ProductListItem>> {
  return request.get('/favorites', { params })
}

/** 收藏商品（幂等）。 */
export function addFavorite(productId: number): Promise<void> {
  return request.post(`/favorites/${productId}`)
}

/** 取消收藏（幂等）。 */
export function removeFavorite(productId: number): Promise<void> {
  return request.delete(`/favorites/${productId}`)
}

/** 是否已收藏（供详情页回显按钮状态）。 */
export function fetchFavoriteStatus(productId: number): Promise<boolean> {
  return request.get(`/favorites/${productId}/status`)
}
