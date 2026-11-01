import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ProfileUpdateRequest, UserInfo } from '@tickcart/shared-types'
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

  async function login(payload: authApi.LoginPayload) {
    const res = await authApi.loginByPassword(payload)
    applyToken(res)
    await loadUserInfo()
  }

  async function loginWithSms(phone: string, code: string) {
    const res = await authApi.loginBySms(phone, code)
    applyToken(res)
    await loadUserInfo()
  }

  /** 注册（四项必填：用户名、手机号、密码、短信验证码），成功后直接进入登录态。 */
  async function register(payload: {
    username: string
    phone: string
    password: string
    code: string
  }) {
    const res = await authApi.registerByPassword(payload)
    applyToken(res)
    await loadUserInfo()
  }

  async function loadUserInfo() {
    if (!token.value) return
    userInfo.value = await authApi.fetchUserInfo()
  }

  /** 修改当前用户资料（用户名/性别）；后端返回最新信息，直接覆盖本地缓存。 */
  async function updateProfile(payload: ProfileUpdateRequest) {
    userInfo.value = await authApi.updateProfile(payload)
  }

  /** 上传头像；成功后用后端返回的最新信息覆盖本地缓存。 */
  async function uploadAvatar(file: File) {
    userInfo.value = await authApi.uploadAvatar(file)
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

  return {
    userInfo,
    token,
    isLoggedIn,
    isAdmin,
    login,
    loginWithSms,
    register,
    loadUserInfo,
    updateProfile,
    uploadAvatar,
    logout,
  }
})
