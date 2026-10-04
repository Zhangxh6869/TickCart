import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { showToast } from 'vant'
import type { R } from '@tickcart/shared-types'

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
      return body.data
    }
    showToast(body.message || '请求失败')
    return Promise.reject(new Error(body.message))
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      clearTokens()
      showToast('登录已过期，请重新登录')
    } else if (status === 403) {
      showToast('没有权限访问')
    } else {
      showToast(message || '网络异常，请稍后再试')
    }
    return Promise.reject(error)
  }
)

export default request
