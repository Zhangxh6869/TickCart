import request from '@/utils/request'
import type { DashboardVO } from '@tickcart/shared-types'

/** 运营看板聚合数据：一次拉齐订单 / 秒杀 / 库存 / 近 N 天趋势。 */
export function fetchDashboard(days = 7): Promise<DashboardVO> {
  return request.get('/admin/dashboard', { params: { days } })
}
