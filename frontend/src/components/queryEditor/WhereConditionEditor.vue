<template>
  <div class="where-editor">
    <!-- 无条件时：直接显示添加按钮 -->
    <div v-if="!rootCondition || !rootCondition.conditions || rootCondition.conditions.length === 0" class="no-condition">
      <div class="quick-add-grid">
        <button class="add-card field-card" @click="addFieldAndInit">
          <span class="card-icon">📋</span>
          <span class="card-title">字段条件</span>
          <span class="card-desc">字段 = 值</span>
        </button>
        <button class="add-card sql-card" @click="addCustomAndInit">
          <span class="card-icon">💻</span>
          <span class="card-title">自定义SQL</span>
          <span class="card-desc">EXISTS / IN / ...</span>
        </button>
      </div>
    </div>

    <!-- 有条件时：显示条件列表 -->
    <template v-else>
      <!-- 条件列表 -->
      <div class="conditions-list">
        <div
          v-for="(c, idx) in rootCondition.conditions"
          :key="c.id"
          class="condition-row"
        >
          <!-- 左侧：逻辑符 + 左括号 -->
          <div class="row-left">
            <span
              v-if="idx > 0"
              class="logic-tag"
              :class="{ 'is-or': c.logic === 'OR' }"
              @click="toggleLogic(c.id)"
            >
              {{ c.logic || 'AND' }}
            </span>
            <button
              class="bracket-btn"
              :class="{ active: c.leftBracket }"
              title="点击切换左括号"
              @click="toggleLeftBracket(c.id)"
            >{{ c.leftBracket ? '(' : '' }}</button>
          </div>

          <!-- 中间：条件内容 -->
          <div class="row-content">
            <!-- 字段条件 -->
            <div v-if="c.type === 'field'" class="field-row">
              <select
                :value="c.field"
                class="field-select"
                @change="(e: any) => updateItem(c.id, { field: e.target.value })"
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

              <select
                :value="c.operator"
                class="operator-select"
                @change="(e: any) => updateItem(c.id, { operator: e.target.value })"
              >
                <option value="=">=</option>
                <option value="!=">!=</option>
                <option value=">">></option>
                <option value=">=">>=</option>
                <option value="<"><</option>
                <option value="<="><=</option>
                <option value="LIKE">LIKE</option>
                <option value="NOT LIKE">NOT LIKE</option>
                <option value="IN">IN</option>
                <option value="NOT IN">NOT IN</option>
                <option value="IS NULL">IS NULL</option>
                <option value="IS NOT NULL">IS NOT NULL</option>
              </select>

              <input
                v-if="!['IS NULL', 'IS NOT NULL'].includes(c.operator || '=')"
                :value="c.value"
                class="value-input"
                placeholder="值"
                @input="(e: any) => updateItem(c.id, { value: e.target.value })"
              />
            </div>

            <!-- 自定义SQL -->
            <div v-else-if="c.type === 'custom'" class="custom-row">
              <textarea
                :value="c.customSql || ''"
                class="custom-sql"
                placeholder="自定义SQL片段 (如: EXISTS(...))"
                rows="1"
                @input="(e: any) => updateItem(c.id, { customSql: e.target.value })"
              ></textarea>
            </div>
          </div>

          <!-- 右侧：右括号 + 操作按钮 -->
          <div class="row-right">
            <button
              class="bracket-btn"
              :class="{ active: c.rightBracket }"
              title="点击切换右括号"
              @click="toggleRightBracket(c.id)"
            >{{ c.rightBracket ? ')' : '' }}</button>
            <button class="del-btn" @click="removeItem(c.id)">×</button>
          </div>
        </div>
      </div>

      <!-- 快速添加栏 -->
      <div class="quick-add-bar">
        <button class="add-btn" @click="addFieldCondition">+ 字段</button>
        <button class="add-btn" @click="addCustomCondition">+ SQL</button>
      </div>
      <div style="height: 12px;"></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { WhereCondition, ConditionItem } from '@/types/queryEditor'

const props = defineProps<{
  condition: WhereCondition | null
  canvasConfig: any
}>()

const emit = defineEmits<{
  (e: 'change', condition: WhereCondition | null): void
}>()

const availableFields = computed(() => {
  const fields: Array<{ value: string; label: string; group?: string }> = []
  const tables = props.canvasConfig?.tables || []
  
  tables.forEach((t: any) => {
    const alias = t.alias || t.tableName
    
    // 只添加具体字段（不添加 表名.* 选项）
    if (t.fields && Array.isArray(t.fields)) {
      t.fields.forEach((f: any) => {
        const fieldName = f.columnName || f.name
        if (fieldName) {
          const comment = f.columnComment || f.comment || ''
          const displayLabel = comment ? `${fieldName} (${comment})` : fieldName
          fields.push({
            value: `${alias}.${fieldName}`,
            label: displayLabel,
            group: alias
          })
        }
      })
    }
    
    // 如果没有字段信息，也添加已选字段
    if (t.selectedFields && Array.isArray(t.selectedFields)) {
      t.selectedFields.forEach((fieldName: string) => {
        if (fieldName !== '*' && !fields.find(f => f.value === `${alias}.${fieldName}`)) {
          fields.push({
            value: `${alias}.${fieldName}`,
            label: fieldName,
            group: alias
          })
        }
      })
    }
  })
  
  return fields
})

const groupedFields = computed(() => {
  const groups: Array<{ name: string; fields: Array<{ value: string; label: string }> }> = []
  const fieldMap = new Map<string, Array<{ value: string; label: string }>>()
  
  availableFields.value.forEach(f => {
    const groupName = f.group || '其他'
    if (!fieldMap.has(groupName)) {
      fieldMap.set(groupName, [])
    }
    fieldMap.get(groupName)!.push({ value: f.value, label: f.label })
  })
  
  fieldMap.forEach((fields, name) => {
    groups.push({ name, fields })
  })
  
  return groups
})

const rootCondition = computed(() => props.condition)

function generateId(): string {
  return 'c_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5)
}

function addRootGroup() {
  emit('change', { logic: 'AND', conditions: [], groups: [] })
}

function addFieldAndInit() {
  const newItem: ConditionItem = {
    id: generateId(),
    type: 'field',
    field: '',
    operator: '=',
    value: ''
  }
  emit('change', { logic: 'AND', conditions: [newItem], groups: [] })
}

function addCustomAndInit() {
  const newItem: ConditionItem = {
    id: generateId(),
    type: 'custom',
    customSql: ''
  }
  emit('change', { logic: 'AND', conditions: [newItem], groups: [] })
}

function addFieldCondition() {
  if (!props.condition) return
  const isNotFirst = props.condition.conditions.length > 0
  const newItem: ConditionItem = {
    id: generateId(),
    type: 'field',
    field: '',
    operator: '=',
    value: '',
    logic: isNotFirst ? 'AND' : undefined
  }
  emit('change', {
    ...props.condition,
    conditions: [...props.condition.conditions, newItem]
  })
}

function addCustomCondition() {
  if (!props.condition) return
  const isNotFirst = props.condition.conditions.length > 0
  const newItem: ConditionItem = {
    id: generateId(),
    type: 'custom',
    customSql: '',
    logic: isNotFirst ? 'AND' : undefined
  }
  emit('change', {
    ...props.condition,
    conditions: [...props.condition.conditions, newItem]
  })
}

function removeItem(id: string) {
  if (!props.condition) return
  emit('change', {
    ...props.condition,
    conditions: props.condition.conditions.filter(c => c.id !== id)
  })
}

function updateItem(id: string, updates: Partial<ConditionItem>) {
  if (!props.condition) return
  emit('change', {
    ...props.condition,
    conditions: props.condition.conditions.map(c =>
      c.id === id ? { ...c, ...updates } : c
    )
  })
}

function toggleLeftBracket(id: string) {
  if (!props.condition) return
  emit('change', {
    ...props.condition,
    conditions: props.condition.conditions.map(c =>
      c.id === id ? { ...c, leftBracket: !c.leftBracket } : c
    )
  })
}

function toggleRightBracket(id: string) {
  if (!props.condition) return
  emit('change', {
    ...props.condition,
    conditions: props.condition.conditions.map(c =>
      c.id === id ? { ...c, rightBracket: !c.rightBracket } : c
    )
  })
}

function changeItemType(id: string, type: 'field' | 'custom') {
  if (!props.condition) return
  const newConditions = props.condition.conditions.map(c => {
    if (c.id !== id) return c
    if (type === 'field') {
      return { id, type: 'field' as const, field: '', operator: '=', value: '' }
    } else {
      return { id, type: 'custom' as const, customSql: '' }
    }
  })
  emit('change', { ...props.condition, conditions: newConditions })
}

function toggleLogic(id: string) {
  if (!props.condition) return
  const item = props.condition.conditions.find(c => c.id === id)
  if (!item) return
  updateItem(id, { logic: item.logic === 'OR' ? 'AND' : 'OR' })
}

function clearAll() {
  emit('change', null)
}

function formatValue(val: any): string {
  if (val === null || val === undefined) return ''
  if (typeof val === 'string') return val.replace(/^'|'$/g, '')
  return String(val)
}
</script>

<style scoped>
.where-editor { 
  padding: 2px 0; 
}

/* 无条件时的快速添加界面 */
.no-condition {
  text-align: center;
  padding: 8px 0;
}

.quick-add-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.add-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: rgba(15, 52, 96, 0.4);
  border: 2px dashed;
}

.add-card:hover {
  transform: translateY(-1px);
}

.field-card { border-color: #3b82f6; }
.field-card:hover { background: rgba(59, 130, 246, 0.15); border-color: #60a5fa; box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3); }
.sql-card { border-color: #a78bfa; }
.sql-card:hover { background: rgba(167, 138, 250, 0.15); border-color: #c4b5fd; box-shadow: 0 4px 12px rgba(167, 138, 250, 0.3); }

.card-icon { font-size: 20px; line-height: 1; }
.card-title { font-size: 12px; font-weight: 600; color: #e2e8f0; }
.card-desc { font-size: 10px; color: #94a3b8; }

/* 条件列表 */
.conditions-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.condition-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.3;
  transition: all 0.2s;
}
.condition-row:hover { background: rgba(59, 130, 246, 0.2); }

.row-left {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.logic-tag {
  display: inline-block;
  padding: 1px 6px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 3px;
  cursor: pointer;
  background: rgba(15, 23, 42, 0.8);
  color: #94a3b8;
  user-select: none;
  transition: all 0.2s;
}
.logic-tag:hover { background: rgba(59, 130, 246, 0.3); color: #60a5fa; }
.logic-tag.is-or { background: rgba(245, 158, 11, 0.15); color: #fbbf24; }
.logic-tag.is-or:hover { background: rgba(245, 158, 11, 0.3); }

.bracket-btn {
  width: 20px;
  height: 20px;
  border-radius: 3px;
  border: 1px dashed #475569;
  background: transparent;
  color: #64748b;
  font-size: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.bracket-btn:hover { border-color: #60a5fa; color: #60a5fa; background: rgba(59, 130, 246, 0.1); }
.bracket-btn.active { border-color: #22c55e; background: rgba(34, 197, 94, 0.1); color: #22c55e; }

.row-content {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
}

.field-row { 
  display: flex; 
  align-items: center; 
  gap: 6px; 
  width: 100%;
  flex: 1;
}
.field-select {
  flex: 1;
  padding: 3px 6px;
  border-radius: 3px;
  border: 1px solid #334155;
  background: rgba(15, 23, 42, 0.7);
  color: #e2e8f0;
  font-size: 11px;
  outline: none;
  min-width: 100px;
  max-width: 180px;
}
.field-select:focus { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2); }

.operator-select {
  padding: 3px 5px;
  border-radius: 3px;
  border: 1px solid #334155;
  background: rgba(15, 23, 42, 0.7);
  color: #e2e8f0;
  font-size: 11px;
  outline: none;
  width: 60px;
  flex-shrink: 0;
  text-align: center;
}
.operator-select:focus { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2); }

.value-input {
  flex: 1;
  padding: 3px 6px;
  border-radius: 3px;
  border: 1px solid #334155;
  background: rgba(15, 23, 42, 0.7);
  color: #e2e8f0;
  font-size: 11px;
  outline: none;
  min-width: 70px;
}
.value-input:focus { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2); }

.custom-row { 
  width: 100%;
  flex: 1;
}
.custom-sql {
  width: 100%;
  padding: 3px 6px;
  border-radius: 3px;
  border: 1px solid #334155;
  background: rgba(15, 23, 42, 0.7);
  color: #e2e8f0;
  font-size: 11px;
  outline: none;
  resize: vertical;
  min-height: 26px;
}
.custom-sql:focus { border-color: #a78bfa; box-shadow: 0 0 0 2px rgba(167, 138, 250, 0.2); }

.row-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.type-btn {
  padding: 2px 8px;
  border-radius: 3px;
  border: 1px solid #334155;
  background: rgba(15, 23, 42, 0.5);
  color: #94a3b8;
  font-size: 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.type-btn:hover { border-color: #60a5fa; background: rgba(59, 130, 246, 0.2); color: #60a5fa; }

.del-btn {
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
.del-btn:hover { background: rgba(239, 68, 68, 0.2); color: #ef4444; }

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
.add-btn:hover { border-color: #60a5fa; background: rgba(59, 130, 246, 0.1); color: #60a5fa; }

.clear-btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: none;
  background: rgba(239, 68, 68, 0.1);
  color: #f87171;
  font-size: 13px;
  cursor: pointer;
  margin-left: auto;
  transition: all 0.2s;
}
.clear-btn:hover { background: rgba(239, 68, 68, 0.2); color: #ef4444; }

/* 调试信息 */
.debug-info {
  font-family: monospace;
  font-size: 11px;
  color: #64748b;
  background: rgba(15, 23, 42, 0.5);
  padding: 8px;
  border-radius: 4px;
  white-space: pre-wrap;
  margin-top: 8px;
  text-align: left;
}
</style>
