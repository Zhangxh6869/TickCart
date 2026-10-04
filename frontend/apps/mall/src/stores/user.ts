import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@tickcart/shared-types'
import * as authApi from '@/api/auth'
import { setTokens, clearTokens, getAccessToken } from '@/utils/request'

/** 用户状态：登录态与用户信息。 */
export const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo | null>(null)
  const token = ref<string | null>(getAccessToken())

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 1)

  function applyToken(res: { accessToken: string; refreshToken: string }) {
    setTokens(res.accessToken, res.refreshToken)
    token.value = res.accessToken
  }

  async function login(username: string, password: string) {
    const res = await authApi.loginByPassword(username, password)
    applyToken(res)
    await loadUserInfo()
  }

  async function loginWithSms(phone: string, code: string) {
    const res = await authApi.loginBySms(phone, code)
    applyToken(res)
    await loadUserInfo()
  }

  async function register(payload: { username: string; password: string; phone?: string; nickname?: string }) {
    const res = await authApi.registerByPassword(payload)
    applyToken(res)
    await loadUserInfo()
  }

  async function loadUserInfo() {
    if (!token.value) return
    userInfo.value = await authApi.fetchUserInfo()
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 忽略退出接口异常，本地状态照常清理
    }
    clearTokens()
    token.value = null
    userInfo.value = null
  }

  return { userInfo, token, isLoggedIn, isAdmin, login, loginWithSms, register, loadUserInfo, logout }
})
