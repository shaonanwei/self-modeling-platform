<template>
  <div class="group-by-panel">
    <!-- 分组字段部分 -->
    <div class="section">
      <div class="section-header">
        <span class="section-title">分组字段</span>
        <div class="header-actions">
          <button class="action-btn add-all-btn" @click="addAllFields" v-if="!groupBy || groupBy.length === 0">
            + 添加全部字段
          </button>
          <button class="action-btn clear-all-btn" @click="removeAllFields" v-if="groupBy && groupBy.length > 0">
            清空
          </button>
        </div>
      </div>

      <!-- 显示所有字段 -->
      <div v-if="groupBy && groupBy.length > 0" class="field-tags">
        <div
          v-for="field in groupBy"
          :key="field"
          class="field-tag"
        >
          {{ field }}
        </div>
      </div>
      <div v-else class="empty-hint">
        点击"添加全部字段"将所有字段添加到分组
      </div>
    </div>

    <!-- 分组条件（HAVING）部分 -->
    <div class="section">
      <div class="section-header">
        <span class="section-title">分组条件</span>
      </div>

      <!-- 没有条件时 -->
      <div v-if="!having || !having.conditions || having.conditions.length === 0" class="no-condition">
        <div class="quick-add-grid">
          <button class="add-card field-card" @click="addFieldAndInit">
            <span class="card-icon">📋</span>
            <span class="card-title">字段条件</span>
            <span class="card-desc">聚合函数条件</span>
          </button>
          <button class="add-card sql-card" @click="addCustomAndInit">
            <span class="card-icon">💻</span>
            <span class="card-title">自定义SQL</span>
            <span class="card-desc">HAVING SQL片段</span>
          </button>
        </div>
      </div>

      <!-- 有条件时 -->
      <template v-else>
        <div class="conditions-list">
          <div
            v-for="(c, idx) in having.conditions"
            :key="c.id"
            class="condition-row"
          >
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

            <div class="row-content">
              <div v-if="c.type === 'field'" class="field-row">
                <select
                  :value="c.field"
                  class="field-select"
                  @change="(e: any) => updateHavingItem(c.id, { field: e.target.value })"
                >
                  <option value="">选择聚合字段</option>
                  <option
                    v-for="f in aggregateFields"
                    :key="f.value"
                    :value="f.value"
                  >{{ f.label }}</option>
                </select>

                <select
                  :value="c.operator"
                  class="operator-select"
                  @change="(e: any) => updateHavingItem(c.id, { operator: e.target.value })"
                >
                  <option value="=">=</option>
                  <option value="!=">!=</option>
                  <option value=">">></option>
                  <option value=">=">>=</option>
                  <option value="<"><</option>
                  <option value="<="><=</option>
                </select>

                <input
                  :value="c.value"
                  class="value-input"
                  placeholder="值"
                  @input="(e: any) => updateHavingItem(c.id, { value: e.target.value })"
                />
              </div>

              <div v-else-if="c.type === 'custom'" class="custom-row">
                <textarea
                  :value="c.customSql || ''"
                  class="custom-sql"
                  placeholder="HAVING SQL片段 (如: COUNT(*) > 10)"
                  rows="1"
                  @input="(e: any) => updateHavingItem(c.id, { customSql: e.target.value })"
                />
              </div>
            </div>

            <div class="row-right">
              <button
                class="bracket-btn"
                :class="{ active: c.rightBracket }"
                title="点击切换右括号"
                @click="toggleRightBracket(c.id)"
              >{{ c.rightBracket ? ')' : '' }}</button>
              <button class="del-btn" @click="removeHavingCondition(c.id)">×</button>
            </div>
          </div>
        </div>

        <div class="quick-add-bar">
          <button class="add-btn" @click="addFieldCondition">+ 字段</button>
          <button class="add-btn" @click="addCustomCondition">+ SQL</button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { WhereCondition, ConditionItem } from '../../types/queryEditor'

const props = defineProps<{
  fields: Array<{ tableAlias: string; tableName: string; fieldName: string }>
  groupBy: string[]
  having: WhereCondition | null
}>()

const emit = defineEmits<{
  (e: 'update:groupBy', groupBy: string[]): void
  (e: 'update:having', having: WhereCondition | null): void
}>()

const availableFields = computed(() => {
  return props.fields.map(f => ({
    value: `${f.tableAlias}.${f.fieldName}`,
    label: `${f.tableAlias}.${f.fieldName}`
  }))
})

const aggregateFields = computed(() => {
  const aggs = ['COUNT', 'SUM', 'AVG', 'MIN', 'MAX']
  const result: Array<{ value: string; label: string }> = []

  props.fields.forEach(f => {
    const base = `${f.tableAlias}.${f.fieldName}`
    aggs.forEach(agg => {
      result.push({
        value: `${agg}(${base})`,
        label: `${agg}(${base})`
      })
    })
  })
  return result
})

function generateId(): string {
  return 'c_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5)
}

// 分组字段相关函数
function addAllFields() {
  if (availableFields.value.length === 0) return
  const allFieldValues = availableFields.value.map(f => f.value)
  emit('update:groupBy', allFieldValues)
}

function removeAllFields() {
  emit('update:groupBy', [])
  emit('update:having', null)
}

// HAVING条件相关函数
function checkGroupBy(): boolean {
  if (!props.groupBy || props.groupBy.length === 0) {
    ElMessage.warning('请先选择分组字段，才能添加分组条件')
    return false
  }
  return true
}

function addFieldAndInit() {
  if (!checkGroupBy()) return
  const newItem: ConditionItem = {
    id: generateId(),
    type: 'field',
    field: '',
    operator: '=',
    value: ''
  }
  emit('update:having', { logic: 'AND', conditions: [newItem], groups: [] })
}

function addCustomAndInit() {
  if (!checkGroupBy()) return
  const newItem: ConditionItem = {
    id: generateId(),
    type: 'custom',
    customSql: ''
  }
  emit('update:having', { logic: 'AND', conditions: [newItem], groups: [] })
}

function addFieldCondition() {
  if (!checkGroupBy()) return
  if (!props.having) return
  const isNotFirst = props.having.conditions.length > 0
  const newItem: ConditionItem = {
    id: generateId(),
    type: 'field',
    field: '',
    operator: '=',
    value: '',
    logic: isNotFirst ? 'AND' : undefined
  }
  emit('update:having', {
    ...props.having,
    conditions: [...props.having.conditions, newItem]
  })
}

function addCustomCondition() {
  if (!checkGroupBy()) return
  if (!props.having) return
  const isNotFirst = props.having.conditions.length > 0
  const newItem: ConditionItem = {
    id: generateId(),
    type: 'custom',
    customSql: '',
    logic: isNotFirst ? 'AND' : undefined
  }
  emit('update:having', {
    ...props.having,
    conditions: [...props.having.conditions, newItem]
  })
}

function removeHavingCondition(id: string) {
  if (!props.having) return
  const newConditions = props.having.conditions.filter(c => c.id !== id)
  if (newConditions.length === 0) {
    emit('update:having', null)
  } else {
    emit('update:having', {
      ...props.having,
      conditions: newConditions
    })
  }
}

function updateHavingItem(id: string, updates: Partial<ConditionItem>) {
  if (!props.having) return
  emit('update:having', {
    ...props.having,
    conditions: props.having.conditions.map(c =>
      c.id === id ? { ...c, ...updates } : c
    )
  })
}

function toggleLeftBracket(id: string) {
  if (!props.having) return
  emit('update:having', {
    ...props.having,
    conditions: props.having.conditions.map(c =>
      c.id === id ? { ...c, leftBracket: !c.leftBracket } : c
    )
  })
}

function toggleRightBracket(id: string) {
  if (!props.having) return
  emit('update:having', {
    ...props.having,
    conditions: props.having.conditions.map(c =>
      c.id === id ? { ...c, rightBracket: !c.rightBracket } : c
    )
  })
}

function toggleLogic(id: string) {
  if (!props.having) return
  const item = props.having.conditions.find(c => c.id === id)
  if (!item) return
  updateHavingItem(id, { logic: item.logic === 'OR' ? 'AND' : 'OR' })
}
</script>

<style scoped>
.group-by-panel {
  padding: 2px 0;
}

.section {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 4px 12px;
  border-radius: 4px;
  border: none;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-all-btn {
  background: rgba(34, 197, 94, 0.15);
  color: #4ade80;
  border: 1px dashed #22c55e;
}
.add-all-btn:hover {
  background: rgba(34, 197, 94, 0.25);
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.3);
}

.clear-all-btn {
  background: rgba(239, 68, 68, 0.1);
  color: #f87171;
  border: 1px dashed #ef4444;
}
.clear-all-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
}

/* 字段标签展示 */
.field-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.field-tag {
  padding: 4px 10px;
  background: rgba(59, 130, 246, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 4px;
  font-size: 11px;
  color: #60a5fa;
}

.empty-hint {
  padding: 12px;
  text-align: center;
  font-size: 11px;
  color: #64748b;
  background: rgba(15, 52, 96, 0.3);
  border-radius: 6px;
}

/* 无内容时的快速添加界面 */
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
  background: rgba(167, 138, 250, 0.1);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.3;
  transition: all 0.2s;
}
.condition-row:hover { background: rgba(167, 138, 250, 0.2); }

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
.operator-select:focus { border-color: #a78bfa; box-shadow: 0 0 0 2px rgba(167, 138, 250, 0.2); }

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
</style>
