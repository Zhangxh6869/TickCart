import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { UserInfo } from '@tickcart/shared-types'

/**
 * 用户 store：资料修改。
 *
 * <p>服务端是唯一事实来源：改资料/传头像后一律用后端返回值覆盖本地缓存，
 * 前端不做「先改本地再乐观回滚」那套 —— 用户名可能被占用、头像 URL 由服务端生成。</p>
 */

// 必须在 import store 之前 mock：store 在模块加载时就会 import 这两个模块
vi.mock('@/api/auth', () => ({
  loginByPassword: vi.fn(),
  loginBySms: vi.fn(),
  registerByPassword: vi.fn(),
  fetchUserInfo: vi.fn(),
  updateProfile: vi.fn(),
  uploadAvatar: vi.fn(),
  logout: vi.fn(),
}))

// store 初始化会读 getAccessToken()，node 环境没有 localStorage，这里整体打桩
vi.mock('@/utils/request', () => ({
  getAccessToken: vi.fn(() => null),
  setTokens: vi.fn(),
  clearTokens: vi.fn(),
}))

import * as authApi from '@/api/auth'
import { useUserStore } from '@/stores/user'

const mocked = vi.mocked(authApi)

function userInfo(overrides: Partial<UserInfo> = {}): UserInfo {
  return {
    id: 1,
    username: 'demo',
    phone: '138****0000',
    nickname: 'demo',
    avatar: null,
    gender: 0,
    role: 0,
    ...overrides,
  }
}

describe('useUserStore 资料修改', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('updateProfile 成功后用后端返回值刷新本地缓存', async () => {
    mocked.updateProfile.mockResolvedValue(userInfo({ username: 'newname', gender: 1 }))
    const store = useUserStore()

    await store.updateProfile({ username: 'newname', gender: 1 })

    expect(mocked.updateProfile).toHaveBeenCalledWith({ username: 'newname', gender: 1 })
    expect(store.userInfo?.username).toBe('newname')
    expect(store.userInfo?.gender).toBe(1)
  })

  it('uploadAvatar 成功后刷新头像 URL', async () => {
    mocked.uploadAvatar.mockResolvedValue(userInfo({ avatar: '/api/files/2026/11/01/a.jpg' }))
    const store = useUserStore()
    const file = { name: 'a.jpg' } as unknown as File

    await store.uploadAvatar(file)

    expect(mocked.uploadAvatar).toHaveBeenCalledWith(file)
    expect(store.userInfo?.avatar).toBe('/api/files/2026/11/01/a.jpg')
  })

  it('updateProfile 失败时保留原缓存（不把界面清空）', async () => {
    mocked.updateProfile.mockRejectedValue(new Error('用户名已存在'))
    const store = useUserStore()
    store.userInfo = userInfo()

    await expect(store.updateProfile({ username: 'taken', gender: 0 })).rejects.toThrow()

    expect(store.userInfo?.username).toBe('demo')
  })
})
