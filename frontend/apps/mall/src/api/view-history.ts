import request from '@/utils/request'
import type { PageResult, ViewHistoryItem } from '@tickcart/shared-types'

export interface ViewHistoryQueryParams {
  page?: number
  size?: number
}

/** 浏览记录列表（需登录，按最近浏览时间倒序）。 */
export function fetchViewHistory(params: ViewHistoryQueryParams): Promise<PageResult<ViewHistoryItem>> {
  return request.get('/view-history', { params })
}

/**
 * 上报一次浏览（幂等：同一商品重复上报只刷新浏览时间）。
 *
 * ★ `silent: true` —— 埋点是旁路，失败不能弹「网络异常」打扰正在看商品的用户。
 * 调用方仍需自行 `catch`（拦截器静默后仍会 reject）。
 */
export function reportViewHistory(productId: number): Promise<void> {
  return request.post(`/view-history/${productId}`, null, { silent: true })
}

/** 删除单条浏览记录（幂等）。 */
export function removeViewHistory(productId: number): Promise<void> {
  return request.delete(`/view-history/${productId}`)
}

/** 清空全部浏览记录。 */
export function clearViewHistory(): Promise<void> {
  return request.delete('/view-history')
}
