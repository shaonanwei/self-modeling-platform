/**
 * 剪贴板复制 composable
 * 带降级处理：支持 navigator.clipboard 和 execCommand('copy') 降级方案
 */
import { ElMessage } from 'element-plus'

export function useCopy() {
  async function copyToClipboard(text: string, successMsg = '已复制到剪贴板') {
    if (!text) {
      ElMessage.warning('没有可复制的内容')
      return false
    }

    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text)
        ElMessage.success(successMsg)
        return true
      }
      // 降级方案：使用 textarea + execCommand
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.left = '-9999px'
      document.body.appendChild(textarea)
      textarea.select()
      const success = document.execCommand('copy')
      document.body.removeChild(textarea)
      if (success) {
        ElMessage.success(successMsg)
        return true
      }
      ElMessage.error('复制失败')
      return false
    } catch {
      ElMessage.error('复制失败')
      return false
    }
  }

  return { copyToClipboard }
}
