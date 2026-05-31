/**
 * 通用格式化工具函数
 */

/** 模型类型 → 中文标签 */
export function modelTypeLabel(type: string): string {
  const map: Record<string, string> = {
    BUSINESS: '业务模型',
    TECHNICAL: '技术模型',
    DATA: '数据模型'
  }
  return map[type] || type
}

/** JOIN 类型 → Element Plus Tag 颜色 */
export function joinTypeTag(type: string): string {
  const map: Record<string, string> = { INNER: '', LEFT: 'warning', RIGHT: 'danger' }
  return map[type] || ''
}

/** 判断条件操作符是否需要值输入框 */
export function needsConditionValue(operator: string): boolean {
  return !['IS NULL', 'IS NOT NULL'].includes(operator)
}

/** 生成 stepCode */
export function generateStepCode(modelId: number): string {
  return `STEP_${modelId}_${Date.now() % 10000}`
}
