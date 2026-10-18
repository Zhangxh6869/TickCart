import request from '@/utils/request'
import type {
  PageResult,
  SeckillActivity,
  SeckillActivityQuery,
  SeckillActivitySaveRequest,
} from '@tickcart/shared-types'

/** 秒杀活动分页列表（后台）。status 为推导状态，指定后忽略 enabled。 */
export function fetchSeckillActivities(
  query: SeckillActivityQuery = {},
): Promise<PageResult<SeckillActivity>> {
  return request.get('/admin/seckill/activities', { params: query })
}

/** 秒杀活动详情（后台，不过滤上下线）。 */
export function fetchSeckillActivity(id: number): Promise<SeckillActivity> {
  return request.get(`/admin/seckill/activities/${id}`)
}

/** 新建活动；后端会校验秒杀价必须低于原价、开始时间早于结束时间。 */
export function createSeckillActivity(payload: SeckillActivitySaveRequest): Promise<number> {
  return request.post('/admin/seckill/activities', payload)
}

/** 编辑活动；活动已开始时后端会拒绝改价与改库存。 */
export function updateSeckillActivity(
  id: number,
  payload: SeckillActivitySaveRequest,
): Promise<void> {
  return request.put(`/admin/seckill/activities/${id}`, payload)
}

/** 上线 / 下线。 */
export function changeSeckillEnabled(id: number, enabled: number): Promise<void> {
  return request.put(`/admin/seckill/activities/${id}/enabled`, null, { params: { enabled } })
}

/** 删除活动；有成交记录时后端会拒绝。 */
export function deleteSeckillActivity(id: number): Promise<void> {
  return request.delete(`/admin/seckill/activities/${id}`)
}
