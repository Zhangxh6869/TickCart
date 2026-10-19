import request from '@/utils/request'
import type { SeckillActivity, SeckillBuyResult, SeckillResult } from '@tickcart/shared-types'

/** 进行中与即将开始的秒杀活动（按开始时间升序）。 */
export function fetchSeckillActivities(): Promise<SeckillActivity[]> {
  return request.get('/seckill/activities')
}

/** 秒杀活动详情（已下线的按不存在处理）。 */
export function fetchSeckillActivity(id: number): Promise<SeckillActivity> {
  return request.get(`/seckill/activities/${id}`)
}

/**
 * 抢购。
 *
 * <p>必须带用户选定的收货地址：建单是异步的，地址无效会让「抢到了却建不了单」。
 * 返回的 requestId 用于轮询结果。</p>
 */
export function buySeckill(
  activityId: number,
  payload: { addressId: number; quantity?: number },
): Promise<SeckillBuyResult> {
  return request.post(`/seckill/activities/${activityId}/order`, payload)
}

/**
 * 查询抢购结果。
 *
 * <p>结果还没写出来时后端返回 404，axios 拦截器会 reject ——
 * 调用方把 reject 当作「继续轮询」，不要当错误提示。</p>
 */
export function fetchSeckillResult(requestId: string): Promise<SeckillResult> {
  return request.get(`/seckill/result/${requestId}`)
}
