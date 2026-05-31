/**
 * 步骤类型常量与映射表
 * 集中管理 stepType 相关配置，避免多组件重复定义
 */

export const STEP_TYPES = {
  START: 'start',
  END: 'end',
  TASK: 'task',
  GATEWAY: 'gateway',
  SUBPROCESS: 'subprocess'
} as const

export type StepType = typeof STEP_TYPES[keyof typeof STEP_TYPES]

/** Element Plus Tag 类型映射 */
export const STEP_TYPE_TAG_MAP: Record<StepType, string> = {
  start: 'success',
  end: 'danger',
  task: '',
  gateway: 'warning',
  subprocess: 'info'
}

/** 步骤类型中文标签 */
export const STEP_TYPE_LABEL_MAP: Record<StepType, string> = {
  start: '开始',
  end: '结束',
  task: '任务',
  gateway: '网关',
  subprocess: '子流程'
}

/** 节点颜色配置 */
export const STEP_TYPE_COLORS: Record<StepType, { bg: string; border: string }> = {
  start: { bg: '#f0f9eb', border: '#67C23A' },
  end: { bg: '#fef0f0', border: '#F56C6C' },
  task: { bg: '#ecf5ff', border: '#409EFF' },
  gateway: { bg: '#fdf6ec', border: '#E6A23C' },
  subprocess: { bg: '#f4f4f5', border: '#909399' }
}
