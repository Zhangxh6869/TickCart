import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import type { R } from '@tickcart/shared-types'

/**
 * 请求配置扩展：静默请求。
 *
 * 登录接口用 `silent: true` —— 失败文案（密码错 / 非管理员 / 需要图形码 / 锁定倒计时）
 * 由登录页按 code 分流后自行渲染。走全局 toast 的话，「需要图形码」这种要停留的提示
 * 会一闪而过，用户根本来不及输入。
 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

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
    if (!response.config.silent) {
      ElMessage.error(body.message || '请求失败')
    }
    return Promise.reject(new Error(body.message))
  },
  (error) => {
    const status = error.response?.status
    const code = error.response?.data?.code
    const message = error.response?.data?.message
    const silent = error.config?.silent === true
    const url: string = error.config?.url || ''
    // 登录接口的 401 是「凭据错误」，不是「登录态过期」—— 按过期处理会清 token 并提示
    // 「登录已过期，请重新登录」，驴唇不对马嘴，用户还会以为会话出了问题
    const isLoginAttempt = url.includes('/auth/login') || url.includes('/auth/admin/login')

    // 40101 = 需要人机验证：这是登录流程的一部分，不是会话过期。
    // 必须抢在下面的 401 分支之前返回，否则挑战会被当成过期吞掉，
    // 登录页也就永远拿不到图形码 —— 后台账号会卡在这一步直到 15 分钟计数窗口过期。
    if (code === 40101) {
      return Promise.reject(error)
    }

    if (status === 401 && !isLoginAttempt) {
      // 令牌确实失效时必须清（与是否静默无关），只有提示文案受 silent 控制
      clearTokens()
      if (!silent) ElMessage.error('登录已过期，请重新登录')
      // 避免在登录页反复跳转
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
      return Promise.reject(error)
    }

    // 登录尝试与静默请求：文案交给调用方，这里不弹
    if (silent || isLoginAttempt) {
      return Promise.reject(error)
    }

    if (status === 403) {
      ElMessage.error(message || '没有权限访问（需要管理员账号）')
    } else {
      ElMessage.error(message || '网络异常，请稍后再试')
    }
    return Promise.reject(error)
  }
)

export default request
