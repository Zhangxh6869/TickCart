import request from '@/utils/request'
import type {
  MockChannelResult,
  PaymentChannel,
  PaymentResult,
  PaymentStatusResult,
} from '@tickcart/shared-types'

/**
 * 发起支付：为订单创建一条「待支付」流水。
 *
 * 注意返回 `status` 恒为 0（待支付）—— 订单此时**尚未变更**，
 * 要等渠道异步回调到达才会变成已支付，前端需跳转渠道页并随后轮询。
 */
export function createPayment(orderNo: string, channel: PaymentChannel): Promise<PaymentResult> {
  return request.post(`/payments/${orderNo}`, { channel })
}

/** 查询支付状态（收银台轮询用）：同时返回订单状态与最新一笔流水状态。 */
export function fetchPaymentStatus(orderNo: string): Promise<PaymentStatusResult> {
  return request.get(`/payments/${orderNo}/status`)
}

/**
 * 向模拟渠道提交支付结果。
 *
 * 渠道会延迟若干秒才回调商城，因此本接口返回成功**不代表订单已变更**，
 * 调用方必须回收银台轮询确认。
 */
export function submitMockChannelResult(
  payNo: string,
  result: MockChannelResult
): Promise<void> {
  return request.post(`/mock-channel/${payNo}/notify`, { result })
}
