import request from '@/utils/request'
import type { TokenResponse, UserInfo } from '@tickcart/shared-types'

/** 后台登录（复用账密登录接口，账号为手机号；角色校验在前端登录动作里做）。 */
export function login(phone: string, password: string): Promise<TokenResponse> {
  return request.post('/auth/login', { phone, password })
}

/** 获取当前用户信息。 */
export function fetchUserInfo(): Promise<UserInfo> {
  return request.get('/auth/me')
}

/** 退出登录。 */
export function logout(): Promise<void> {
  return request.post('/auth/logout')
}
