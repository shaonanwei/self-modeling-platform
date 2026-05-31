/**
 * 认证相关 API
 */
import request from '@/utils/request'
import type { LoginResponse, UserInfo } from '@/types/model'

export interface LoginRequest {
  username: string
  password: string
  rememberMe?: boolean
  captchaKey: string
  captchaCode: string
}

export interface CaptchaResponse {
  captchaKey: string
  captchaImage: string
}

export const authApi = {
  login(data: LoginRequest) {
    return request.post<LoginRequest, { code: number; message: string; data: LoginResponse }>('/api/v1/auth/login', data)
  },

  getCaptcha() {
    return request.get<never, { code: number; message: string; data: CaptchaResponse }>('/api/v1/auth/captcha')
  },

  logout() {
    return request.post<never, { code: number; message: string }>('/api/v1/auth/logout')
  },

  refresh(data: { refreshToken: string }) {
    return request.post<{ refreshToken: string }, { code: number; message: string; data: LoginResponse }>('/api/v1/auth/refresh', data)
  },

  getUserInfo() {
    return request.get<never, { code: number; message: string; data: UserInfo }>('/api/v1/auth/userinfo')
  }
}
