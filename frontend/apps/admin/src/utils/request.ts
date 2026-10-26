import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import type { R } from '@tickcart/shared-types'

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

const ACCESS_KEY = 'tickcart_admin_access_token'
const REFRESH_KEY = 'tickcart_admin_refresh_token'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY)
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_KEY, accessToken)
  localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data as R
    if (body.code === 0) {
      // 拦截器把 R<T> 解包成业务数据，调用方拿到的就是 body.data。
      // 但 axios 的类型签名要求响应拦截器返回 AxiosResponse，与「解包」这一用法天然冲突，
      // 故只在类型层桥接一次，运行时行为不变（api/*.ts 均已按解包后的类型声明返回值）。
      return body.data as unknown as AxiosResponse
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message))
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      clearTokens()
      ElMessage.error('登录已过期，请重新登录')
      // 避免在登录页反复跳转
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    } else if (status === 403) {
      ElMessage.error('没有权限访问（需要管理员账号）')
    } else {
      ElMessage.error(message || '网络异常，请稍后再试')
    }
    return Promise.reject(error)
  }
)

export default request
