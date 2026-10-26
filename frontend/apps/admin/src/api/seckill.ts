import request from '@/utils/request'
import type {
  PageResult,
  SeckillActivity,
  SeckillActivityQuery,
  SeckillActivitySaveRequest,
  SeckillOverview,
} from '@tickcart/shared-types'

/** 活动概览：页头 KPI 与状态 chips 的计数。 */
export function fetchSeckillOverview(): Promise<SeckillOverview> {
  return request.get('/admin/seckill/overview')
}

/** 秒杀活动分页列表（后台）。status 为推导状态，指定后忽略 enabled 与 activeOnly。 */
export function fetchSeckillActivities(
  query: SeckillActivityQuery = {},
): Promise<PageResult<SeckillActivity>> {
  return request.get('/admin/seckill/activities', { params: query })
}

/** 秒杀活动详情（后台，不过滤上下线）。 */
export function fetchSeckillActivity(id: number): Promise<SeckillActivity> {
  return request.get(`/admin/seckill/activities/${id}`)
}

/** 新建活动：一律建成草稿（enabled=0），需要再调 changeSeckillEnabled 上线。 */
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

/** 上线 / 下线。上线会二次校验商品在售与同规格时间重叠。 */
export function changeSeckillEnabled(id: number, enabled: number): Promise<void> {
  return request.put(`/admin/seckill/activities/${id}/enabled`, null, { params: { enabled } })
}

/** 批量上线 / 下线：整批成功或整批失败，上限 100。 */
export function batchChangeSeckillEnabled(ids: number[], enabled: number): Promise<number> {
  return request.put('/admin/seckill/activities/batch/enabled', ids, { params: { enabled } })
}

/** 复制为新活动：草稿、已售归零、时间平移到原结束次日同时刻。 */
export function copySeckillActivity(id: number): Promise<number> {
  return request.post(`/admin/seckill/activities/${id}/copy`)
}

/** 删除活动；有成交记录时后端会拒绝。 */
export function deleteSeckillActivity(id: number): Promise<void> {
  return request.delete(`/admin/seckill/activities/${id}`)
}
