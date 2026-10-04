import request from '@/utils/request'
import type { TokenResponse, UserInfo } from '@tickcart/shared-types'

/** 发送短信验证码。 */
export function sendSmsCode(phone: string, scene: number): Promise<void> {
  return request.post('/auth/sms/send', { phone, scene })
}

/** 账密登录。 */
export function loginByPassword(username: string, password: string): Promise<TokenResponse> {
  return request.post('/auth/login', { username, password })
}

/** 账密注册。 */
export function registerByPassword(payload: {
  username: string
  password: string
  phone?: string
  nickname?: string
}): Promise<TokenResponse> {
  return request.post('/auth/register', payload)
}

/** 手机验证码登录。 */
export function loginBySms(phone: string, code: string): Promise<TokenResponse> {
  return request.post('/auth/login/sms', { phone, code })
}

/** 手机验证码注册。 */
export function registerBySms(payload: {
  phone: string
  code: string
  username?: string
  password?: string
  nickname?: string
}): Promise<TokenResponse> {
  return request.post('/auth/register/sms', payload)
}

/** 获取当前用户信息。 */
export function fetchUserInfo(): Promise<UserInfo> {
  return request.get('/auth/me')
}

/** 退出登录。 */
export function logout(): Promise<void> {
  return request.post('/auth/logout')
}
