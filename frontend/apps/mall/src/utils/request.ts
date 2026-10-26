import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { showToast } from 'vant'
import type { R } from '@tickcart/shared-types'

/**
 * 请求配置扩展：静默请求。
 *
 * 埋点这类**旁路请求**失败时不应该打扰用户 —— 用户在商品详情页看东西，
 * 却因为一次浏览上报的网络抖动弹「网络异常，请稍后再试」，是纯噪音。
 * 带 `silent: true` 的请求失败时照样 reject（调用方自行 catch），只是不弹 toast。
 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

/** 请求实例：统一携带 JWT、统一错误提示。 */
const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// ---- 令牌存储（内存 + localStorage，避免刷新页面丢登录态） ----
const ACCESS_KEY = 'tickcart_access_token'
const REFRESH_KEY = 'tickcart_refresh_token'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_KEY, accessToken)
  localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

// ---- 请求拦截器：附加 Authorization ----
request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

// ---- 响应拦截器：解包 R<T>，统一处理业务错误 ----
request.interceptors.response.use(
  (response) => {
    const body = response.data as R
    if (body.code === 0) {
      // 拦截器把 R<T> 解包成业务数据，调用方拿到的就是 body.data。
      // 但 axios 的类型签名要求响应拦截器返回 AxiosResponse，与「解包」这一用法天然冲突，
      // 故只在类型层桥接一次，运行时行为不变（api/*.ts 均已按解包后的类型声明返回值）。
      return body.data as unknown as AxiosResponse
    }
    if (!response.config.silent) {
      showToast(body.message || '请求失败')
    }
    return Promise.reject(new Error(body.message))
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    // 静默请求：不弹提示，但仍然 reject 给调用方
    const silent = error.config?.silent === true
    // 登录接口的 401 是「凭据错误/需要验证」，不是「登录态过期」：
    // 照常走清 token + 过期提示的话，用户会看到「登录已过期」这种驴唇不对马嘴的文案
    const url: string = error.config?.url || ''
    const isLoginAttempt = url.includes('/auth/login')
    if (status === 401 && !isLoginAttempt) {
      // 令牌确实失效时必须清（与是否静默无关），只有提示文案受 silent 控制
      clearTokens()
      if (!silent) showToast('登录已过期，请重新登录')
    } else if (!silent && !isLoginAttempt) {
      if (status === 403) {
        showToast('没有权限访问')
      } else {
        showToast(message || '网络异常，请稍后再试')
      }
    }
    return Promise.reject(error)
  }
)

export default request
