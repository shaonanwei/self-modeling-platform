import router from '@/router'
import { clearTokens } from './auth'

let isRedirectingToLogin = false

/** 清理登录态并只跳转一次登录页，避免并发 401 造成重复导航。 */
export function handleUnauthorized(): void {
  if (isRedirectingToLogin) {
    return
  }

  isRedirectingToLogin = true
  const reset = () => {
    isRedirectingToLogin = false
  }
  const resetTimer = window.setTimeout(reset, 1000)
  const resetImmediately = () => {
    window.clearTimeout(resetTimer)
    reset()
  }

  try {
    clearTokens()
    const navigation = router.replace({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath }
    })
    void Promise.resolve(navigation).catch(resetImmediately)
  } catch {
    resetImmediately()
  }
}
