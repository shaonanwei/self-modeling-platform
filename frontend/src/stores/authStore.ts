/**
 * 认证状态管理
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/authApi'
import { setTokens, clearTokens } from '@/utils/auth'
import type { UserInfo } from '@/types/model'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)

  async function login(username: string, password: string, captchaKey: string, captchaCode: string, _rememberMe: boolean) {
    const res = await authApi.login({ username, password, captchaKey, captchaCode, rememberMe: false })
    setTokens(res.data.accessToken, res.data.refreshToken)
    await fetchUserInfo()
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 忽略注销接口失败（无状态 JWT，token 失效是客户端行为）
    } finally {
      clearTokens()
      user.value = null
    }
  }

  async function fetchUserInfo() {
    try {
      const res = await authApi.getUserInfo()
      user.value = res.data
    } catch {
      user.value = null
    }
  }

  return { user, login, logout, fetchUserInfo }
})
