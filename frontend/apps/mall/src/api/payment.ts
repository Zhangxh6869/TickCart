import request from '@/utils/request'
import type { PaymentChannel, PaymentResult } from '@tickcart/shared-types'

/** 发起支付（仅待付款订单）。 */
export function payOrder(orderNo: string, channel: PaymentChannel): Promise<PaymentResult> {
  return request.post(`/payments/${orderNo}`, { channel })
}
