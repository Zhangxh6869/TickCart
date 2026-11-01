import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { UserInfo } from '@tickcart/shared-types'
import * as authApi from '@/api/auth'
import { clearTokens, getAccessToken, setTokens } from '@/utils/request'

/** 后台管理员状态。 */
export const useAdminStore = defineStore('admin', () => {
  const userInfo = ref<UserInfo | null>(null)
  const token = ref<string | null>(getAccessToken())

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 1)

  /** 后台登录：账号为手机号，登录后立即校验管理员角色。 */
  async function login(phone: string, password: string) {
    const res = await authApi.login(phone, password)
    setTokens(res.accessToken, res.refreshToken)
    token.value = res.accessToken
    await loadUserInfo()
    // 后端 /auth/admin/login 已经拒绝了非管理员，这里保留一次兜底：
    // 万一将来接口被改回通用登录，也不至于静默放行。
    if (!isAdmin.value) {
      await logout()
      throw new Error('该账号不是管理员，无法登录后台')
    }
  }

  async function loadUserInfo() {
    if (!token.value) return
    userInfo.value = await authApi.fetchUserInfo()
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 忽略
    }
    clearTokens()
    token.value = null
    userInfo.value = null
  }

  return { userInfo, token, isLoggedIn, isAdmin, login, loadUserInfo, logout }
})
