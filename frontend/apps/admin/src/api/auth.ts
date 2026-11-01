import request from '@/utils/request'
import type { TokenResponse, UserInfo } from '@tickcart/shared-types'

/**
 * 后台登录：走后台专用接口，账号为手机号。
 *
 * 非管理员在登录阶段就被后端 403 拒绝，不会出现「先拿到令牌、再被前端吊销」的中间态。
 */
export function login(phone: string, password: string): Promise<TokenResponse> {
  return request.post('/auth/admin/login', { phone, password })
}

/** 获取当前用户信息。 */
export function fetchUserInfo(): Promise<UserInfo> {
  return request.get('/auth/me')
}

/** 退出登录。 */
export function logout(): Promise<void> {
  return request.post('/auth/logout')
}
