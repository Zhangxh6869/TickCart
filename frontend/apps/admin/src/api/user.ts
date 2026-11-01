import request from '@/utils/request'
import type { AdminUserDetail, AdminUserSummary, PageResult } from '@tickcart/shared-types'

export interface AdminUserQueryParams {
  page?: number
  size?: number
  /** 用户名 / 昵称 / 手机号模糊匹配；纯数字额外按用户ID精确匹配 */
  keyword?: string
  /** 状态 0正常 1禁用，null 表示全部 */
  status?: number | null
  /** 注册日期起（含），yyyy-MM-dd */
  beginDate?: string | null
  /** 注册日期止（含），yyyy-MM-dd */
  endDate?: string | null
  sortBy?: 'createdAt' | 'id'
  order?: 'asc' | 'desc'
}

/** 用户分页。列表里的手机号是脱敏的。 */
export function fetchUsers(params: AdminUserQueryParams): Promise<PageResult<AdminUserSummary>> {
  return request.get('/admin/users', { params })
}

/** 用户详情：明文手机号 + 订单统计 + 最近订单 + 收货地址。 */
export function fetchUserDetail(id: number): Promise<AdminUserDetail> {
  return request.get(`/admin/users/${id}`)
}

/**
 * 启用 / 禁用账号。
 *
 * 禁用是**立即生效**的：后端会吊销该用户全部会话，手上正在用的 access token 也会作废。
 * 因此调用前必须让管理员二次确认 —— 误点一下就是把人踢下线。
 */
export function changeUserStatus(id: number, status: 0 | 1): Promise<void> {
  return request.put(`/admin/users/${id}/status`, null, { params: { status } })
}
