<template>
  <div class="order-by-panel">
    <div class="sort-list">
      <div v-for="(item, index) in orderBy" :key="index" class="sort-item">
        <!-- 左侧：序号 -->
        <div class="sort-item-left">
          <span class="sort-index">{{ index + 1 }}</span>
        </div>
        
        <!-- 中间：内容区域 -->
        <div class="sort-item-content">
          <!-- 字段排序 -->
          <template v-if="isFieldType(item)">
            <select
              :value="getFieldValue(item.field)"
              size="small"
              class="field-sel"
              @change="(e: any) => updateField(index, e.target.value)"
            >
              <option value="">选择字段</option>
              <template v-for="group in groupedFields" :key="group.name">
                <optgroup :label="group.name">
                  <option
                    v-for="f in group.fields"
                    :key="f.value"
                    :value="f.value"
                  >{{ f.label }}</option>
                </optgroup>
              </template>
            </select>
          </template>
          
          <!-- SQL表达式排序 -->
          <template v-else>
            <input
              :value="getSqlValue(item)"
              class="custom-sql-input"
              placeholder="输入排序表达式 (如: COUNT(id))"
              @input="(e: any) => updateCustomSql(index, e.target.value)"
            />
          </template>
          
          <!-- 排序方向 -->
          <el-radio-group
            :model-value="item.direction"
            size="small"
            @change="(v: string) => updateDirection(index, v as 'ASC' | 'DESC')"
          >
            <el-radio-button value="ASC">升序</el-radio-button>
            <el-radio-button value="DESC">降序</el-radio-button>
          </el-radio-group>
        </div>
        
        <!-- 右侧：类型切换和删除按钮 -->
        <div class="sort-item-right">
          <button class="remove-btn" @click="removeSort(index)">×</button>
        </div>
      </div>
    </div>
    
    <!-- 添加排序按钮 -->
    <div class="quick-add-bar">
      <button class="add-btn field-add-btn" @click="addFieldSort">+ 排序字段</button>
      <button class="add-btn sql-add-btn" @click="addCustomSort">+ SQL</button>
    </div>
  </div>
   <div style="height: 12px;"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { OrderByItem } from '@/types/queryEditor'

const props = defineProps<{
  fields: Array<{ tableAlias: string; tableName: string; fieldName: string; comment?: string }>
  orderBy: OrderByItem[]
}>()

const emit = defineEmits<{
  (e: 'change', orderBy: OrderByItem[]): void
}>()

const groupedFields = computed(() => {
  const groups: Record<string, Array<{ value: string; label: string }>> = {}
  
  props.fields.forEach(f => {
    const groupName = f.tableAlias
    if (!groups[groupName]) {
      groups[groupName] = []
    }
    
    const value = `${f.tableAlias}.${f.fieldName}`
    const comment = f.comment || ''
    const label = comment ? `${f.fieldName} (${comment})` : f.fieldName
    
    if (!groups[groupName].find(item => item.value === value)) {
      groups[groupName].push({ value, label })
    }
  })
  
  return Object.entries(groups).map(([name, fields]) => ({
    name,
    fields
  }))
})

function getFieldValue(field: string): string {
  if (field.includes('.')) {
    return field
  }
  const matchedField = props.fields.find(f => f.fieldName === field)
  if (matchedField) {
    return `${matchedField.tableAlias}.${matchedField.fieldName}`
  }
  return field
}

function isFieldType(item: OrderByItem): boolean {
  // 如果明确指定了类型，直接返回
  if (item.type === 'field') return true
  if (item.type === 'custom') return false
  
  // 如果没有指定类型，检查字段是否能匹配到选项
  const fieldValue = item.field || ''
  if (!fieldValue) return true // 空值默认为字段类型
  
  // 检查是否能匹配到字段选项
  const matchedField = props.fields.find(f => 
    `${f.tableAlias}.${f.fieldName}` === fieldValue || f.fieldName === fieldValue
  )
  return !!matchedField
}

function getSqlValue(item: OrderByItem): string {
  // 如果有customSql，返回customSql
  if (item.customSql) {
    return item.customSql
  }
  // 否则返回field（用于从SQL解析回来的情况）
  return item.field || ''
}

function addFieldSort() {
  const newItem: OrderByItem = {
    field: '',
    direction: 'ASC',
    type: 'field'
  }
  emit('change', [...props.orderBy, newItem])
}

function addCustomSort() {
  const newItem: OrderByItem = {
    field: '',
    direction: 'ASC',
    type: 'custom',
    customSql: ''
  }
  emit('change', [...props.orderBy, newItem])
}

function removeSort(index: number) {
  const newOrderBy = [...props.orderBy]
  newOrderBy.splice(index, 1)
  emit('change', newOrderBy)
}

function updateField(index: number, field: string) {
  const newOrderBy = [...props.orderBy]
  const updatedItem: OrderByItem = {
    ...newOrderBy[index],
    type: 'field',
    field
  }
  newOrderBy[index] = updatedItem
  emit('change', newOrderBy)
}

function updateCustomSql(index: number, customSql: string) {
  const newOrderBy = [...props.orderBy]
  const updatedItem: OrderByItem = {
    ...newOrderBy[index],
    type: 'custom',
    customSql
  }
  newOrderBy[index] = updatedItem
  emit('change', newOrderBy)
}

function updateDirection(index: number, direction: 'ASC' | 'DESC') {
  const newOrderBy = [...props.orderBy]
  const updatedItem: OrderByItem = {
    ...newOrderBy[index],
    direction
  }
  newOrderBy[index] = updatedItem
  emit('change', newOrderBy)
}

function toggleType(index: number) {
  const newOrderBy = [...props.orderBy]
  const current = newOrderBy[index]
  let updatedItem: OrderByItem
  if (current.type === 'custom') {
    updatedItem = { ...current, type: 'field' }
  } else {
    updatedItem = { ...current, type: 'custom', customSql: '' }
  }
  newOrderBy[index] = updatedItem
  emit('change', newOrderBy)
}
</script>

<style scoped>
.order-by-panel { padding: 4px 0; }
.sort-list { overflow-y: auto; }
.sort-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  padding: 5px 8px;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 4px;
  transition: all 0.2s;
}
.sort-item:hover {
  background: rgba(59, 130, 246, 0.15);
}

/* 左侧：序号 */
.sort-item-left {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.sort-index {
  font-size: 10px;
  color: #60a5fa;
  width: 18px;
  text-align: center;
  padding: 2px 0;
}

/* 中间：内容区域（字段选择和排序方向） */
.sort-item-content {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.field-sel {
  flex: 1;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid #334155;
  color: #e2e8f0;
  border-radius: 3px;
  padding: 3px 6px;
  font-size: 11px;
  outline: none;
  min-width: 100px;
}
.field-sel:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}

/* 右侧：类型切换和删除按钮 */
.sort-item-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

/* 类型切换按钮 */
.type-toggle-btn {
  padding: 2px 6px;
  border-radius: 3px;
  border: 1px solid #334155;
  background: rgba(15, 23, 42, 0.5);
  color: #94a3b8;
  font-size: 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.type-toggle-btn:hover {
  border-color: #60a5fa;
  background: rgba(59, 130, 246, 0.2);
  color: #60a5fa;
}
.type-toggle-btn.is-custom {
  border-color: #a78bfa;
  color: #a78bfa;
}
.type-toggle-btn.is-custom:hover {
  background: rgba(167, 138, 250, 0.2);
}

.remove-btn {
  width: 20px;
  height: 20px;
  border-radius: 3px;
  border: none;
  background: rgba(239, 68, 68, 0.1);
  color: #f87171;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  line-height: 1;
}
.remove-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

/* SQL表达式输入框 */
.custom-sql-input {
  flex: 1;
  padding: 3px 6px;
  border-radius: 3px;
  border: 1px solid #334155;
  background: rgba(15, 23, 42, 0.7);
  color: #e2e8f0;
  font-size: 11px;
  outline: none;
  min-width: 100px;
  font-family: monospace;
}
.custom-sql-input:focus {
  border-color: #a78bfa;
  box-shadow: 0 0 0 2px rgba(167, 138, 250, 0.2);
}
.custom-sql-input::placeholder {
  color: #64748b;
}

/* 快速添加栏 */
.quick-add-bar {
  display: flex;
  gap: 6px;
  align-items: center;
  padding-top: 6px;
  border-top: 1px solid #334155;
}

.add-btn {
  padding: 4px 10px;
  border-radius: 4px;
  border: 1px dashed #475569;
  background: transparent;
  color: #94a3b8;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}
.add-btn:hover {
  border-color: #60a5fa;
  background: rgba(59, 130, 246, 0.1);
  color: #60a5fa;
}

.field-add-btn:hover {
  border-color: #3b82f6;
}

.sql-add-btn:hover {
  border-color: #a78bfa;
  background: rgba(167, 138, 250, 0.1);
  color: #a78bfa;
}

/* 添加排序按钮样式 - 参考JOIN模块设计 */
.order-by-panel :deep(.el-button) {
  padding: 4px 12px;
  border: 1px solid #2d4a6f;
  background: rgba(15, 52, 96, 0.6);
  color: #94a3b8;
  font-size: 11px;
  border-radius: 3px;
  transition: all 0.2s;
}
.order-by-panel :deep(.el-button:hover) {
  background: rgba(59, 130, 246, 0.2);
  border-color: #3b82f6;
  color: #60a5fa;
}

/* 升序/降序单选按钮样式 - 参考JOIN模块设计 */
.order-by-panel :deep(.el-radio-button__inner) {
  padding: 3px 10px !important;
  border: 1px solid #2d4a6f !important;
  background: rgba(15, 52, 96, 0.5) !important;
  color: #94a3b8 !important;
  font-size: 11px !important;
  border-radius: 3px !important;
  transition: all 0.2s;
}
.order-by-panel :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-radius: 3px 0 0 3px !important;
  border-right-width: 0 !important;
}
.order-by-panel :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 0 3px 3px 0 !important;
}
.order-by-panel :deep(.el-radio-button:first-child:last-child .el-radio-button__inner) {
  border-radius: 3px !important;
}
.order-by-panel :deep(.el-radio-button__inner:hover) {
  color: #60a5fa !important;
  border-color: #3b82f6 !important;
}
.order-by-panel :deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: rgba(59, 130, 246, 0.2) !important;
  border-color: #3b82f6 !important;
  color: #60a5fa !important;
  font-weight: 500;
}
.order-by-panel :deep(.el-radio-button__orig-radio:checked + .el-radio-button__inner) {
  background: rgba(59, 130, 246, 0.2) !important;
  border-color: #3b82f6 !important;
}
</style>
