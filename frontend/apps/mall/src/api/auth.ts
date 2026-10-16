import request from '@/utils/request'
import type { TokenResponse, UserInfo } from '@tickcart/shared-types'

/**
 * 短信验证码场景，与后端 {@code AuthService} 的 SMS_SCENE_* 常量一一对应。
 * 发送与校验必须用同一个场景值，否则「登录的码」能拿去重置密码。
 */
export const SMS_SCENE = {
  REGISTER: 1,
  LOGIN: 2,
  RESET_PASSWORD: 3,
} as const

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

/**
 * 重置密码（忘记密码）：凭手机号 + 短信验证码（场景 3）重设密码。
 * 无需登录态；成功后后端会吊销该用户全部刷新令牌，需重新登录。
 */
export function resetPassword(payload: {
  phone: string
  code: string
  newPassword: string
}): Promise<void> {
  return request.post('/auth/password/reset', payload)
}

/** 获取当前用户信息。 */
export function fetchUserInfo(): Promise<UserInfo> {
  return request.get('/auth/me')
}

/** 退出登录。 */
export function logout(): Promise<void> {
  return request.post('/auth/logout')
}
