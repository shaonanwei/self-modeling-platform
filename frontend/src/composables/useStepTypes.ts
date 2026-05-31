/**
 * 步骤类型相关 composable
 * 提供类型标签查询、颜色获取等功能
 */
import { STEP_TYPE_TAG_MAP, STEP_TYPE_COLORS, type StepType } from '@/constants/stepTypes'

/** 根据步骤类型获取 Element Plus Tag 颜色 */
export function useStepTypeTag(type: string): string | undefined {
  const tagType = STEP_TYPE_TAG_MAP[type as StepType]
  return tagType || undefined
}

/** 根据步骤类型获取节点颜色配置 */
export function useStepTypeColor(type: string): { bg: string; border: string } {
  return STEP_TYPE_COLORS[type as StepType] || STEP_TYPE_COLORS.task
}
