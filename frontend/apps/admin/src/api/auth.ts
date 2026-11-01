import request from '@/utils/request'
import type { TokenResponse, UserInfo } from '@tickcart/shared-types'

/** 后台登录入参：账号为手机号；captcha 两字段只在「需要人机验证」时出现。 */
export interface LoginPayload {
  phone: string
  password: string
  captchaId?: string
  captchaCode?: string
}

/** 图形验证码挑战：svg 可直接内联渲染。 */
export interface CaptchaChallenge {
  id: string
  svg: string
  expiresIn: number
}

/**
 * 后台登录：走后台专用接口，账号为手机号。
 *
 * 非管理员在登录阶段就被后端 403 拒绝，不会出现「先拿到令牌、再被前端吊销」的中间态。
 * 失败次数达到阈值后返回 40101，需带上图形码重试。
 */
export function login(payload: LoginPayload): Promise<TokenResponse> {
  // silent：失败文案由登录页按 code 分流渲染（图形码要停留，不能被 toast 一闪而过）
  return request.post('/auth/admin/login', payload, { silent: true })
}

/** 主动获取图形验证码（用户点图换一张时用）。 */
export function fetchCaptcha(): Promise<CaptchaChallenge> {
  return request.get('/auth/captcha')
}

/** 获取当前用户信息。 */
export function fetchUserInfo(): Promise<UserInfo> {
  return request.get('/auth/me')
}

/** 退出登录。 */
export function logout(): Promise<void> {
  return request.post('/auth/logout')
}
