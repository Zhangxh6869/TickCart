import request from '@/utils/request'
import type {
  AdminOrderDetail,
  AdminOrderSummary,
  BatchShipResult,
  OrderOverview,
  PageResult,
} from '@tickcart/shared-types'

export interface AdminOrderQueryParams {
  page?: number
  size?: number
  /** 状态筛选，不传表示全部 */
  status?: number | null
  /** 订单号 / 收货人 / 手机号 / 商品名 */
  keyword?: string
  /** 排序字段：createdAt（默认）/ payTime */
  sortBy?: 'createdAt' | 'payTime'
  /** 排序方向 */
  order?: 'asc' | 'desc'
  /** 下单日期起（含），yyyy-MM-dd */
  beginDate?: string | null
  /** 下单日期止（含），yyyy-MM-dd */
  endDate?: string | null
}

/** 订单概览：各状态计数 + 待发货最早付款时间 + 成交额。 */
export function fetchOrderOverview(): Promise<OrderOverview> {
  return request.get('/admin/orders/overview')
}

/** 订单分页（全部用户）。 */
export function fetchOrders(params: AdminOrderQueryParams): Promise<PageResult<AdminOrderSummary>> {
  return request.get('/admin/orders', { params })
}

/** 订单详情（含支付语义）。 */
export function fetchOrderDetail(orderNo: string): Promise<AdminOrderDetail> {
  return request.get(`/admin/orders/${orderNo}`)
}

/** 发货：待发货 → 待收货。 */
export function shipOrder(orderNo: string): Promise<void> {
  return request.put(`/admin/orders/${orderNo}/ship`)
}

/**
 * 批量发货：允许部分成功。
 *
 * 返回里 `failed` 非空说明有订单没发出去（状态已变 / 不存在），
 * 调用方必须把失败原因展示出来，不能只提示「成功」。
 */
export function batchShipOrders(orderNos: string[]): Promise<BatchShipResult> {
  return request.put('/admin/orders/batch/ship', orderNos)
}

/** 关闭订单：仅待付款可关闭，关闭时回补库存。 */
export function closeOrder(orderNo: string): Promise<void> {
  return request.put(`/admin/orders/${orderNo}/close`)
}
