import request from '@/utils/request'
import type { AdminOrderSummary, OrderDetail, PageResult } from '@tickcart/shared-types'

export interface AdminOrderQueryParams {
  page?: number
  size?: number
  /** 状态筛选，不传表示全部 */
  status?: number | null
  /** 订单号 / 收货人 / 手机号 */
  keyword?: string
}

/** 订单分页（全部用户）。 */
export function fetchOrders(params: AdminOrderQueryParams): Promise<PageResult<AdminOrderSummary>> {
  return request.get('/admin/orders', { params })
}

/** 订单详情。 */
export function fetchOrderDetail(orderNo: string): Promise<OrderDetail> {
  return request.get(`/admin/orders/${orderNo}`)
}

/** 发货：待发货 → 待收货。 */
export function shipOrder(orderNo: string): Promise<void> {
  return request.put(`/admin/orders/${orderNo}/ship`)
}
