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

/** 图形验证码挑战：svg 可直接 v-html 内联渲染。 */
export interface CaptchaChallenge {
  id: string
  svg: string
  expiresIn: number
}

/**
 * 发送短信验证码的结果。
 *
 * `devCode` 只在后端 Mock 模式（tickcart.sms.mock-enabled=true）下才有值，
 * 真实网关模式下该字段整个不出现 —— 前端只能把它当「可选的自测便利」，不能依赖。
 */
export interface SmsCodeSendResult {
  expireSeconds: number
  devCode?: string
}

/** 账密登录入参：captcha 两字段只在「需要安全验证」时出现。 */
export interface LoginPayload {
  username: string
  password: string
  captchaId?: string
  captchaCode?: string
}

/**
 * 发送短信验证码。
 *
 * 返回值带 `devCode`（仅 Mock 模式）：验证码每次随机，联调时靠它拿到码。
 */
export function sendSmsCode(phone: string, scene: number): Promise<SmsCodeSendResult> {
  return request.post('/auth/sms/send', { phone, scene })
}

/**
 * 账密登录。
 *
 * 传 silent：失败提示由登录页自己渲染（字段红字 / 锁定倒计时 / 图形码），
 * 走全局 toast 会把「需要验证」这种需要停留的提示一闪而过。
 */
export function loginByPassword(payload: LoginPayload): Promise<TokenResponse> {
  return request.post('/auth/login', payload, { silent: true })
}

/** 主动获取图形验证码（刷新页面后验证态仍在时用）。 */
export function fetchCaptcha(): Promise<CaptchaChallenge> {
  return request.get('/auth/captcha')
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
