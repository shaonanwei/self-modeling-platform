<template>
  <div class="property-panel">
    <el-tabs v-model="activeTab" size="small" class="prop-tabs" :animated="false">
      <!-- 基本信息 Tab -->
      <el-tab-pane label="基本信息" name="basic">
        <div v-if="selectedTable" class="section">
          <div class="section-title">表: {{ selectedTable.tableName }}</div>
          <div class="form-row">
            <label>别名</label>
            <el-input :model-value="selectedTable.alias" size="small" @update:model-value="updateAlias($event)" />
          </div>
          <div class="form-row">
            <label>已选字段 ({{ selectedTable.selectedFields.length }})</label>
            <div class="field-tag-list">
              <el-tag
                v-for="f in selectedTable.selectedFields"
                :key="f"
                closable
                size="small"
                @close="removeField(f)"
              >
                {{ f }}<span v-if="getFieldType(f)" class="tag-type">{{ getFieldType(f) }}</span>
              </el-tag>
              <span v-if="!selectedTable.selectedFields.length" class="empty-hint">未选择字段</span>
            </div>
          </div>
          <div class="section">
            <div class="section-title" style="display:flex;justify-content:space-between;align-items:center;">
              <span>自定义字段</span>
              <el-button size="small" text type="primary" @click="handleAddCustomField(selectedTable!.alias)">+ 添加</el-button>
            </div>
            <div v-if="selectedTable.customFields && selectedTable.customFields.length > 0" class="custom-field-list">
              <div v-for="cf in selectedTable.customFields" :key="cf.id" class="custom-field-row">
                <el-input
                  :model-value="cf.expression"
                  size="small"
                  placeholder="如: COUNT(t.id) 或 substr(t.id,1,3)"
                  class="cf-expr-input"
                  @input="(v: string) => handleCustomFieldChange(selectedTable!.alias, cf.id, { expression: v })"
                />
                <span class="cf-as-label">AS</span>
                <el-input
                  :model-value="cf.alias"
                  size="small"
                  placeholder="别名"
                  :class="{ 'cf-alias-duplicate': cf.alias && isAliasDuplicate(cf.alias, cf.id) }"
                  style="width:80px"
                  @input="(v: string) => handleCustomFieldChange(selectedTable!.alias, cf.id, { alias: v })"
                />
                <el-button size="small" text type="danger" @click="store.removeCustomField(selectedTable!.alias, cf.id)">
                  <el-icon><Minus /></el-icon>
                </el-button>
              </div>
              <div v-if="hasDuplicateAliasInTable(selectedTable!.alias)" class="cf-duplicate-warning">别名重复，请修改</div>
            </div>
            <div v-else class="empty-hint">暂无自定义字段，点击添加</div>
          </div>
        </div>

        <!-- 未选中任何表或关联时显示所有表概览 -->
        <div v-if="!selectedTable && !selectedJoin" class="all-tables-overview">
          <div style=" margin-bottom: 0px; padding-bottom: 0px;" class="section-title">表和字段</div>
          <div v-if="allTablesOverview.length > 0">
            <div v-for="tableInfo in allTablesOverview" :key="tableInfo.alias" class="table-overview-item">
              <div class="overview-header">
                <span class="overview-table-name">{{ tableInfo.tableName }}</span>
                <span v-if="tableInfo.alias !== tableInfo.tableName" class="overview-alias">({{ tableInfo.alias }})</span>
              </div>
              <div class="overview-section">
                <div class="overview-label">已选字段 ({{ tableInfo.selectedFields.length }})</div>
                <div class="field-tag-list">
                  <el-tag
                    v-for="f in tableInfo.selectedFields"
                    :key="f"
                    size="small"
                  >
                    {{ f }}
                  </el-tag>
                  <span v-if="!tableInfo.selectedFields.length" class="empty-hint">未选择字段</span>
                </div>
              </div>
              <div class="overview-section">
                <div class="overview-label" style="display:flex;justify-content:space-between;align-items:center;">
                  <span>自定义字段</span>
                  <el-button size="small" text type="primary" @click="handleAddCustomField(tableInfo.alias)">+ 添加</el-button>
                </div>
                <div v-if="tableInfo.customFields && tableInfo.customFields.length > 0" class="custom-field-list">
                  <div v-for="cf in tableInfo.customFields" :key="cf.id" class="custom-field-row">
                    <el-input
                      :model-value="cf.expression"
                      size="small"
                      placeholder="如: COUNT(t.id) 或 substr(t.id,1,3)"
                      class="cf-expr-input"
                      @input="(v: string) => handleCustomFieldChange(tableInfo.alias, cf.id, { expression: v })"
                    />
                    <span class="cf-as-label">AS</span>
                    <el-input
                      :model-value="cf.alias"
                      size="small"
                      placeholder="别名"
                      :class="{ 'cf-alias-duplicate': cf.alias && isAliasDuplicate(cf.alias, cf.id) }"
                      style="width:80px"
                      @input="(v: string) => handleCustomFieldChange(tableInfo.alias, cf.id, { alias: v })"
                    />
                    <el-button size="small" text type="danger" @click="store.removeCustomField(tableInfo.alias, cf.id)">
                      <el-icon><Minus /></el-icon>
                    </el-button>
                  </div>
                  <div v-if="hasDuplicateAliasInTable(tableInfo.alias)" class="cf-duplicate-warning">别名重复，请修改</div>
                </div>
                <div v-else class="empty-hint">暂无自定义字段</div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            <p>从左侧拖拽表到画布开始配置查询</p>
          </div>
        </div>

        <!-- 关联条件 -->
        <div v-if="!selectedTable && tableNames.length > 1" class="section">
          <div class="section-title">关联条件</div>
          <!-- 关联方式选择器 -->
          <div v-if="canvasConfig.joins && canvasConfig.joins.length > 0" class="join-type-container">
            <span class="join-type-label">关联方式：</span>
            <button
              v-for="type in joinTypes"
              :key="type"
              class="join-type-btn"
              :class="{ active: firstJoinType === type }"
              @click="updateAllJoinTypes(type)"
            >
              {{ type }}
            </button>
            <el-button size="small" text type="primary" class="add-join-btn" @click="addNewJoin">+ 添加</el-button>
          </div>
          <div v-if="canvasConfig.joins && canvasConfig.joins.length > 0" class="join-list">
            <div v-for="join in canvasConfig.joins" :key="join.id" class="join-item">
              <div class="join-field-select">
                <select
                  :value="join.sourceField"
                  class="join-select"
                  @change="(e: any) => store.updateJoinField(join.id, 'sourceField', e.target.value)"
                >
                  <option value="">选择字段</option>
                  <option
                    v-for="field in getTableFields(join.sourceTable)"
                    :key="field"
                    :value="field"
                  >{{ join.sourceTable }}.{{ field }}</option>
                </select>
              </div>
              <span class="join-arrow">=</span>
              <div class="join-field-select">
                <select
                  :value="join.targetField"
                  class="join-select"
                  @change="(e: any) => store.updateJoinField(join.id, 'targetField', e.target.value)"
                >
                  <option value="">选择字段</option>
                  <option
                    v-for="field in getTableFields(join.targetTable)"
                    :key="field"
                    :value="field"
                  >{{ join.targetTable }}.{{ field }}</option>
                </select>
              </div>
              <button class="join-delete-btn" @click="emit('removeJoin', join.id)">×</button>
            </div>
          </div>
          <div v-if="!canvasConfig.joins || canvasConfig.joins.length === 0" class="empty-hint">暂无关联条件</div>
        </div>

        <!-- 全局设置 -->
        <div v-if="!selectedTable && !selectedJoin" class="section global-section">
          <div class="section-title">全局设置</div>
          <div class="form-row">
            <label>DISTINCT</label>
            <el-switch :model-value="canvasConfig.distinct" size="small" @change="emit('updateDistinct', $event)" />
          </div>
          <div class="form-row">
            <label>LIMIT</label>
            <el-input-number
              :model-value="canvasConfig.limit || 0"
              :min="0"
              :max="10000"
              size="small"
              controls-position="right"
              @change="(v: number | undefined) => emit('updateLimit', v || 0)"
            />
          </div>
          <div style="height: 12px;"></div>
        </div>
      </el-tab-pane>

      <!-- 查询条件 Tab -->
      <el-tab-pane label="查询条件" name="where">
        <div class="section">
          <div class="section-title">查询条件</div>
          <WhereConditionEditor
            :condition="canvasConfig.where"
            :canvas-config="canvasConfig"
            @change="emit('updateWhere', $event)"
          />
        </div>
      </el-tab-pane>

      <!-- 分组 Tab -->
      <el-tab-pane label="分组配置" name="groupBy">
        <GroupByPanel
          :fields="allSelectedFields"
          :group-by="canvasConfig.groupBy"
          :having="canvasConfig.having"
          @update:group-by="emit('updateGroupBy', $event)"
          @update:having="emit('updateHaving', $event)"
        />
      </el-tab-pane>

      <!-- 排序 Tab -->
      <el-tab-pane label="排序配置" name="orderBy">
        <OrderByPanel
          :fields="allSelectedFields"
          :order-by="canvasConfig.orderBy"
          @change="emit('updateOrderBy', $event)"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useQueryEditorStore } from '@/stores/queryEditorStore'
import { Minus } from '@element-plus/icons-vue'
import WhereConditionEditor from './WhereConditionEditor.vue'
import GroupByPanel from './GroupByPanel.vue'
import OrderByPanel from './OrderByPanel.vue'

const props = defineProps<{
  selectedTable: any
  selectedJoin: any
  canvasConfig: any
}>()

const emit = defineEmits<{
  (e: 'updateWhere', condition: any): void
  (e: 'updateGroupBy', fields: string[]): void
  (e: 'updateHaving', having: any): void
  (e: 'updateOrderBy', items: any[]): void
  (e: 'updateLimit', limit: number): void
  (e: 'updateDistinct', distinct: boolean): void
  (e: 'updateTableField', payload: any): void
  (e: 'updateJoinType', payload: any): void
  (e: 'removeJoin', id: string): void
  (e: 'addJoin'): void
}>()

const joinTypes = ['INNER', 'LEFT', 'RIGHT'] as const

const firstJoinType = computed(() => {
  if (!props.canvasConfig.joins || props.canvasConfig.joins.length === 0) return 'INNER'
  return props.canvasConfig.joins[0].joinType || 'INNER'
})

function updateAllJoinTypes(type: string) {
  if (!props.canvasConfig.joins) return
  props.canvasConfig.joins.forEach((join: any) => {
    join.joinType = type
  })
}

function addNewJoin() {
  emit('addJoin')
}

const store = useQueryEditorStore()
const activeTab = ref('basic')

watch(() => store.focusCustomFields, (val) => {
  if (val) {
    activeTab.value = 'basic'
  }
})

watch(() => store.activeTabName, (val) => {
  if (val !== null && activeTab.value !== val) {
    activeTab.value = val
  }
}, { immediate: true, deep: true })

watch([() => props.selectedTable, () => props.selectedJoin], () => {
  activeTab.value = 'basic'
})

const tableNames = computed(() => props.canvasConfig.tables?.map((t: any) => t.alias) || [])

const allSelectedFields = computed(() => {
  const fields: Array<{ tableAlias: string; tableName: string; fieldName: string; comment?: string }> = []
  props.canvasConfig.tables?.forEach((t: any) => {
    t.selectedFields?.forEach((f: string) => {
      // 尝试从表的字段元数据中获取注释
      let comment = ''
      if (t.fields && Array.isArray(t.fields)) {
        const fieldMeta = t.fields.find((field: any) => 
          field.columnName === f || field.name === f
        )
        comment = fieldMeta?.columnComment || fieldMeta?.comment || ''
      }
      fields.push({ tableAlias: t.alias, tableName: t.tableName, fieldName: f, comment })
    })
  })
  return fields
})

const allTablesOverview = computed(() => {
  return props.canvasConfig.tables?.slice(0, 2).map((t: any) => ({
    tableName: t.tableName,
    alias: t.alias,
    selectedFields: t.selectedFields || [],
    fieldAggregations: t.fieldAggregations || {},
    customFields: t.customFields || []
  })) || []
})

function updateAlias(newAlias: string) {
  if (!props.selectedTable || !newAlias) return
  store.updateTableAlias(props.selectedTable.alias, newAlias)
}

function removeField(field: string) {
  if (!props.selectedTable) return
  store.toggleFieldSelection(props.selectedTable.alias, field)
}

function getTableFields(tableAlias: string): string[] {
  const table = props.canvasConfig.tables?.find((t: any) => t.alias === tableAlias)
  if (!table) return []
  return table.fields?.map((f: any) => f.columnName || f.name) || []
}

function handleAddCustomField(tableAlias: string) {
  store.addCustomField(tableAlias)
}

function handleCustomFieldChange(tableAlias: string, cfId: string, updates: any) {
  store.updateCustomField(tableAlias, cfId, updates)
}

function isAliasDuplicate(alias: string, excludeCfId: string): boolean {
  const allCfAliases: string[] = []
  store.tables.forEach((t: any) => {
    if (t.customFields) {
      t.customFields.forEach((cf: any) => {
        if (cf.alias) allCfAliases.push(cf.alias)
      })
    }
  })
  const count = allCfAliases.filter(a => a === alias).length
  return count > 1
}

function hasDuplicateAliasInTable(tableAlias: string): boolean {
  const table = store.tables.find((t: any) => t.alias === tableAlias)
  if (!table || !table.customFields) return false
  const seen = new Set<string>()
  for (const cf of table.customFields) {
    if (!cf.alias) continue
    if (seen.has(cf.alias)) return true
    seen.add(cf.alias)
  }
  return false
}

function getFieldType(fieldName: string): string {
  if (!props.selectedTable?.fields) return ''
  const field = props.selectedTable.fields.find((f: any) => f.columnName === fieldName)
  if (!field) return ''
  return shortenType(field.dataType)
}

function shortenType(type: string): string {
  const map: Record<string, string> = { VARCHAR: 'str', TEXT: 'str', INT: 'int', BIGINT: 'int',
    DECIMAL: 'num', FLOAT: 'num', DOUBLE: 'num', DATE: 'date', TIMESTAMP: 'dtm',
    BOOLEAN: 'bool', JSON: 'json' }
  const upper = type.toUpperCase()
  for (const [k, v] of Object.entries(map)) {
    if (upper.includes(k)) return v
  }
  return type.length > 10 ? type.substring(0, 8) + '..' : type
}
</script>

<style scoped>
.property-panel { 
  height: 100%; 
  max-height: 100%; 
  display: flex; 
  flex-direction: column; 
  overflow: hidden; 
  position: relative;
}

.prop-tabs { 
  height: 100%; 
  max-height: 100%; 
  display: flex; 
  flex-direction: column; 
  min-height: 0; 
  flex: 1; 
  overflow: hidden;
}

.prop-tabs :deep(.el-tabs__header) { 
  background: #16213e; 
  margin: 0; 
  padding: 0 4px; 
  flex-shrink: 0;
}

.prop-tabs :deep(.el-tabs__item) { 
  color: #94a3b8; 
  font-size: 12px; 
  height: 32px; 
  line-height: 32px; 
}

.prop-tabs :deep(.el-tabs__item.is-active) { color: #3b82f6; }

.prop-tabs :deep(.el-tabs__content) { 
  flex: 1; 
  min-height: calc(100vh - 280px); 
  max-height: calc(100vh - 280px); 
  overflow-y: auto !important; 
  padding: 0 !important; 
  box-sizing: border-box !important; 
  position: relative;
}

.prop-tabs :deep(.el-tab-pane) { 
  height: auto !important; 
  max-height: none !important; 
  min-height: 0 !important; 
  padding: 4px 8px 8px !important;
  box-sizing: border-box !important; 
}

.section { margin-bottom: 12px; }
.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #60a5fa;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #1e3a5f;
}
.form-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.form-row label {
  font-size: 11px;
  color: #94a3b8;
  width: 56px;
  flex-shrink: 0;
}
.form-row :deep(.el-input),
.form-row :deep(.el-input-number) { width: calc(100% - 64px); }
.form-row :deep(.el-input__wrapper) { background: #0f3460; box-shadow: none; }
.form-row :deep(.el-input__inner) { color: #eee; font-size: 12px; }

.field-tag-list { display: flex; flex-wrap: wrap; gap: 4px; }
.tag-type {
  font-size: 9px;
  color: #94a3b8;
  margin-left: 4px;
  padding-left: 4px;
  border-left: 1px solid #475569;
}
.empty-hint { font-size: 11px; color: #555; }

.custom-field-list { display: flex; flex-direction: column; gap: 6px; }
.custom-field-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  background: rgba(15, 52, 96, 0.4);
  border: 1px solid #1e3a5f;
  border-radius: 4px;
}
.custom-field-row :deep(.el-input__wrapper) { background: #0f3460; box-shadow: none; }
.custom-field-row :deep(.el-input__inner) { color: #e2e8f0; font-size: 11px; }
.custom-field-row :deep(.el-select .el-input__wrapper) { background: #0f3460; box-shadow: none; }
.custom-field-row :deep(.el-select .el-input__inner) { color: #e2e8f0; font-size: 11px; }
.cf-expr-input { flex: 1; min-width: 0; }
.cf-expr-input :deep(.el-input__wrapper) { background: #0f3460; box-shadow: none; }
.cf-expr-input :deep(.el-input__inner) { color: #e2e8f0; font-size: 11px; font-family: 'Courier New', monospace; }
.cf-as-label { color: #94a3b8; font-size: 11px; flex-shrink: 0; margin: 0 2px; }
.cf-alias-duplicate :deep(.el-input__wrapper) { box-shadow: 0 0 0 1px #ef4444 inset !important; }
.cf-duplicate-warning {
  color: #ef4444;
  font-size: 10px;
  margin-top: 2px;
  padding: 0 4px;
}

.agg-list { width: 100%; }
.agg-row { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.agg-field { font-size: 11px; color: #cbd5e1; min-width: 50px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.agg-field-type {
  font-size: 9px;
  color: #64748b;
  background: rgba(100, 116, 139, 0.15);
  padding: 0 4px;
  border-radius: 3px;
  white-space: nowrap;
}

.join-detail {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 0;
  font-size: 11px;
}
.join-detail code {
  background: #0f3460;
  padding: 2px 6px;
  border-radius: 3px;
  color: #60a5fa;
  font-size: 10px;
}
.join-arrow { color: #fbbf24; font-weight: bold; }

.global-section { border-top: 1px dashed #1e3a5f; padding-top: 8px; margin-top: 8px; }

.empty-state { text-align: center; color: #555; padding: 30px 0; font-size: 12px; }

/* 关联方式选择器 */
.join-type-container {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.join-type-label {
  color: #94a3b8;
  font-size: 11px;
}
.join-type-btn {
  padding: 4px 12px;
  border: 1px solid #2d4a6f;
  background: rgba(15, 52, 96, 0.5);
  color: #94a3b8;
  font-size: 11px;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.2s;
}
.join-type-btn:hover {
  color: #60a5fa;
  border-color: #3b82f6;
}
.join-type-btn.active {
  color: #60a5fa;
  background: rgba(59, 130, 246, 0.2);
  border-color: #3b82f6;
  font-weight: 500;
}
.add-join-btn {
  margin-left: auto;
  margin-right: -4px;
}

/* 关联条件列表 */
.join-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.join-item {
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
.join-item:hover {
  background: rgba(59, 130, 246, 0.2);
}
.join-field-select {
  flex: 1;
}
.join-arrow {
  color: #fbbf24;
  font-weight: bold;
}
.join-delete-btn {
  width: 24px;
  height: 24px;
  border-radius: 3px;
  border: none;
  background: rgba(239, 68, 68, 0.1);
  color: #f87171;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  line-height: 1;
  flex-shrink: 0;
}
.join-delete-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.all-tables-overview { display: flex; flex-direction: column; gap: 8px; }
.table-overview-item {
  background: rgba(15, 52, 96, 0.3);
  border: 1px solid #1e3a5f;
  border-radius: 6px;
  padding: 10px;
}
.overview-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #1e3a5f;
}
.overview-table-name {
  font-size: 12px;
  font-weight: 600;
  color: #60a5fa;
}
.overview-alias {
  font-size: 11px;
  color: #94a3b8;
}
.overview-section { margin-bottom: 8px; }
.overview-label {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.field-tag-list :deep(.el-tag) {
  background: rgba(59, 130, 246, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.3);
  color: #60a5fa;
  font-size: 11px;
  padding: 2px 8px;
}

.field-tag-list :deep(.el-tag__close) {
  color: #94a3b8;
}

.field-tag-list :deep(.el-tag__close:hover) {
  color: #e2e8f0;
  background: rgba(59, 130, 246, 0.3);
}

.field-select :deep(.el-input__wrapper) {
  background: rgba(15, 23, 42, 0.7) !important;
  box-shadow: 0 0 0 1px #334155 inset !important;
  border-radius: 3px !important;
}

.field-select :deep(.el-input__inner) {
  color: #e2e8f0 !important;
  font-size: 11px !important;
}

.field-select :deep(.el-select__caret) {
  color: #64748b !important;
}

/* 关联条件下拉框样式 - 与查询条件一致 */
.join-select {
  flex: 1;
  padding: 3px 6px;
  border-radius: 3px;
  border: 1px solid #334155;
  background: rgba(15, 23, 42, 0.7);
  color: #e2e8f0;
  font-size: 11px;
  outline: none;
  width: 100%;
}
.join-select:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}



/* 统一 DISTINCT 开关样式 */
.form-row :deep(.el-switch .el-switch__core) {
  background: rgba(15, 52, 96, 0.8) !important;
  border: 1px solid #2d4a6f !important;
}

.form-row :deep(.el-switch.is-checked .el-switch__core) {
  background: rgba(59, 130, 246, 0.6) !important;
  border-color: #3b82f6 !important;
}

.form-row :deep(.el-switch__core::after) {
  background: #94a3b8 !important;
}

.form-row :deep(.el-switch.is-checked .el-switch__core::after) {
  background: #e2e8f0 !important;
}

/* 统一 LIMIT 数字输入框样式 */
.form-row :deep(.el-input-number .el-input__wrapper) {
  background: rgba(15, 23, 42, 0.7) !important;
  box-shadow: 0 0 0 1px #334155 inset !important;
  border-radius: 3px !important;
}

.form-row :deep(.el-input-number .el-input__inner) {
  color: #e2e8f0 !important;
  font-size: 11px !important;
}

.form-row :deep(.el-input-number .el-input-number__decrease),
.form-row :deep(.el-input-number .el-input-number__increase) {
  background: rgba(15, 52, 96, 0.8) !important;
  border-color: #2d4a6f !important;
  color: #64748b !important;
}

.form-row :deep(.el-input-number .el-input-number__decrease:hover),
.form-row :deep(.el-input-number .el-input-number__increase:hover) {
  color: #60a5fa !important;
  background: rgba(59, 130, 246, 0.2) !important;
}
</style>
