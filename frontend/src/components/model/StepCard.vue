<template>
  <div class="step-card" :class="{ 'is-start': step.stepType === 'start', 'is-end': step.stepType === 'end' }">
    <div class="step-card-header">
      <span class="step-icon">
        <el-icon v-if="step.stepType === 'start'" color="#67C23A"><VideoPlay /></el-icon>
        <el-icon v-else-if="step.stepType === 'end'" color="#F56C6C"><VideoPause /></el-icon>
        <el-icon v-else-if="step.stepType === 'gateway'" color="#E6A23C"><Connection /></el-icon>
        <el-icon v-else color="#409EFF"><Edit /></el-icon>
      </span>
      <span class="step-name">{{ step.stepName }}</span>
      <el-tag size="small" :type="stepTypeTag">{{ step.stepType }}</el-tag>
    </div>
    <div class="step-card-body">
      <span class="sort-order">排序: {{ step.sortOrder }}</span>
      <span v-if="step.stepDesc" class="step-desc">{{ step.stepDesc }}</span>
    </div>
    <div class="step-card-actions">
      <el-button size="small" @click.stop="$emit('edit', step)">编辑</el-button>
      <el-button size="small" @click.stop="$emit('moveUp', step)" :disabled="isFirst">上移</el-button>
      <el-button size="small" @click.stop="$emit('moveDown', step)" :disabled="isLast">下移</el-button>
      <el-button size="small" type="danger" @click.stop="$emit('delete', step)">删除</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ModelStep } from '@/types/model'

const props = defineProps<{
  step: ModelStep
  isFirst?: boolean
  isLast?: boolean
}>()

defineEmits<{
  (e: 'edit', step: ModelStep): void
  (e: 'delete', step: ModelStep): void
  (e: 'moveUp', step: ModelStep): void
  (e: 'moveDown', step: ModelStep): void
}>()

const stepTypeTag = computed(() => {
  const map: Record<string, string> = {
    start: 'success',
    end: 'danger',
    task: '',
    gateway: 'warning',
    subprocess: 'info'
  }
  return map[props.step.stepType] || ''
})
</script>

<style scoped>
.step-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 16px;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
}

.step-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.step-card.is-start {
  border-color: #67C23A;
  background: #f0f9eb;
}

.step-card.is-end {
  border-color: #F56C6C;
  background: #fef0f0;
}

.step-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.step-icon {
  font-size: 18px;
}

.step-name {
  font-weight: 500;
  font-size: 14px;
  color: #303133;
}

.step-card-body {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.sort-order {
  font-size: 12px;
  color: #909399;
}

.step-desc {
  font-size: 12px;
  color: #606266;
}

.step-card-actions {
  display: flex;
  gap: 4px;
  justify-content: flex-end;
}
</style>
