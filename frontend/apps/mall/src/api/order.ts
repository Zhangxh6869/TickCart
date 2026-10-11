import request from '@/utils/request'
import type {
  OrderBuyNowRequest,
  OrderCreateRequest,
  OrderCreateResult,
  OrderDetail,
  OrderQuery,
  OrderSummary,
  PageResult,
} from '@tickcart/shared-types'

/** 创建订单（结算购物车中已勾选且有效的商品）。 */
export function createOrder(payload: OrderCreateRequest): Promise<OrderCreateResult> {
  return request.post('/orders', payload)
}

/** 立即购买（商品详情页直达下单，不经过购物车）。 */
export function createBuyNowOrder(payload: OrderBuyNowRequest): Promise<OrderCreateResult> {
  return request.post('/orders/buy-now', payload)
}

/** 订单列表，可按状态筛选。 */
export function fetchOrders(query: OrderQuery = {}): Promise<PageResult<OrderSummary>> {
  return request.get('/orders', { params: query })
}

/** 订单详情。 */
export function fetchOrderDetail(orderNo: string): Promise<OrderDetail> {
  return request.get(`/orders/${orderNo}`)
}

/** 取消订单（仅待付款）。 */
export function cancelOrder(orderNo: string): Promise<void> {
  return request.put(`/orders/${orderNo}/cancel`)
}

/** 确认收货（仅待收货）。 */
export function confirmOrder(orderNo: string): Promise<void> {
  return request.put(`/orders/${orderNo}/confirm`)
}
