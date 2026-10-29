import request from '@/utils/request'
import type {
  OrderBuyNowRequest,
  OrderCreateRequest,
  OrderCreateResult,
  OrderDetail,
  OrderQuery,
  OrderStatusCount,
  OrderSummary,
  PageResult,
  RefundSummary,
} from '@tickcart/shared-types'

/**
 * 下单幂等键的请求头名。
 *
 * <p>服务端按 {@code (scene, user_id, 幂等键)} 去重：同一个键只会成一次单，
 * 后续请求直接回放首次的订单号。前端必须在「进入结算页」时生成一次、失败重试时保持不变，
 * 否则等于没有幂等保护。</p>
 */
const IDEM_HEADER = 'Idempotency-Key'

function idemConfig(idemKey?: string) {
  return idemKey ? { headers: { [IDEM_HEADER]: idemKey } } : undefined
}

/** 创建订单（结算购物车中已勾选且有效的商品）。 */
export function createOrder(
  payload: OrderCreateRequest,
  idemKey?: string,
): Promise<OrderCreateResult> {
  return request.post('/orders', payload, idemConfig(idemKey))
}

/** 立即购买（商品详情页直达下单，不经过购物车）。 */
export function createBuyNowOrder(
  payload: OrderBuyNowRequest,
  idemKey?: string,
): Promise<OrderCreateResult> {
  return request.post('/orders/buy-now', payload, idemConfig(idemKey))
}

/** 订单列表，可按状态筛选。 */
export function fetchOrders(query: OrderQuery = {}): Promise<PageResult<OrderSummary>> {
  const { statuses, ...rest } = query
  // ★ 数组要自己拼成逗号分隔：axios 默认发 `statuses[]=6&statuses[]=7`，Spring 绑不上
  const params = statuses && statuses.length > 0 ? { ...rest, statuses: statuses.join(',') } : rest
  return request.get('/orders', { params })
}

/**
 * 各状态订单数量，「我的」页订单宫格用它渲染角标。
 *
 * 路径是字面量 `/count`，不会与 `fetchOrderDetail` 的 `/orders/{orderNo}` 冲突
 * （后端那边字面量路由优先于路径变量）。
 */
export function fetchOrderCount(): Promise<OrderStatusCount> {
  return request.get('/orders/count')
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

/**
 * 申请退款（仅待发货，P0 只支持未发货退款）。
 *
 * ★ 不提交金额：退多少由后端按「支付成功那条流水」算，前端传金额等于把退款口子敞开。
 */
export function applyRefund(
  orderNo: string,
  payload: { reasonCode: number; reason?: string },
): Promise<RefundSummary> {
  return request.post(`/orders/${orderNo}/refund`, payload)
}

/** 撤销退款申请（仅「待审核」可撤销）。 */
export function cancelRefund(orderNo: string): Promise<void> {
  return request.put(`/orders/${orderNo}/refund/cancel`)
}
