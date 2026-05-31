/**
 * Axios 请求封装
 * 统一处理请求拦截、响应拦截、Token 刷新与错误处理
 */
import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { getAccessToken, clearTokens } from './auth'
import router from '../router'

// 防止并发 401 时多次跳转登录页
let isRedirectingToLogin = false

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000
})

// Request interceptor: 自动附加 Token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getAccessToken()
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// Response interceptor: 统一响应处理
service.interceptors.response.use(
  response => {
    const res = response.data
    // 业务码非 200 视为失败
    if (res.code !== 200) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  async (error: AxiosError<{ message?: string }>) => {
    // 处理 401 未授权
    if (error.response?.status === 401 && !isRedirectingToLogin) {
      isRedirectingToLogin = true
      clearTokens()
      // 使用 replace 避免用户点击浏览器后退回到原页面
      router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      // 延迟重置标志位，避免同批次请求重复跳转
      setTimeout(() => {
        isRedirectingToLogin = false
      }, 1000)
    }

    // 提取后端返回的错误信息
    const message = error.response?.data?.message || error.message || '网络异常'
    return Promise.reject(new Error(message))
  }
)

export default service
