<template>
  <div class="table-node" :class="{ selected: isSelected }">
    <div class="table-header" @click="$emit('headerClick')">
      <span class="table-icon">📋</span>
      <span class="table-title">{{ data.tableName }}</span>
      <span v-if="data.alias !== data.tableName" class="alias-badge">{{ data.alias }}</span>
    </div>

    <div v-show="data.expanded" class="field-list">
      <div
        v-for="field in fields"
        :key="field.columnName"
        class="field-row nodrag"
        :class="{ selected: isFieldSelected(field.columnName) }"
      >
        <Handle
          :id="`${field.columnName}-target-left`"
          type="target"
          :position="Position.Left"
          class="field-handle-invisible"
        />
        <Handle
          :id="`${field.columnName}-target-right`"
          type="target"
          :position="Position.Right"
          class="field-handle-invisible"
        />
        <Handle
          :id="`${field.columnName}-source-left`"
          type="source"
          :position="Position.Left"
          class="field-handle-invisible"
        />
        <Handle
          :id="`${field.columnName}-source-right`"
          type="source"
          :position="Position.Right"
          class="field-handle-invisible"
        />
        <Handle
          :id="`${field.columnName}-source-row`"
          type="source"
          :position="Position.Right"
          class="field-source-dot-handle"
        />
        <Handle
          :id="`${field.columnName}-target-row`"
          type="target"
          :position="Position.Left"
          class="field-target-dot-handle"
        />
        <label class="field-checkbox">
          <input
            type="checkbox"
            :checked="isFieldSelected(field.columnName)"
            @change.stop="toggleField(field.columnName)"
          />
        </label>
        <span
          class="field-info"
          :title="getFieldTitle(field)"
          @dblclick.stop="handleFieldDblClick(field)"
        >
          <span class="field-name">{{ field.columnName }}</span>
          <template v-if="getFieldAgg(field.columnName)">
            <span class="field-sep"> - </span>
            <span class="agg-text">{{ getFieldAgg(field.columnName) }}</span>
          </template>
          <template v-else-if="field.columnComment">
            <span class="field-sep"> - </span>
            <span class="field-comment">{{ truncateComment(field.columnComment) }}</span>
          </template>
        </span>
        <span class="field-type-badge">{{ field.dataType }}</span>
      </div>
      <div v-if="fieldsLoading" class="fields-loading">加载字段中...</div>

      <div v-if="customFields.length > 0" class="custom-field-section">
        <div class="custom-field-divider"></div>
        <div
          v-for="cf in customFields"
          :key="cf.id"
          class="custom-field-row nodrag"
        >
          <span class="cf-expr">{{ cf.expression }}</span>
          <template v-if="cf.alias">
            <span class="cf-arrow">→</span>
            <span class="cf-alias">{{ cf.alias }}</span>
          </template>
        </div>
      </div>
    </div>

    <div v-show="!data.expanded && data.selectedFields?.length > 0" class="collapsed-summary">
      已选 {{ data.selectedFields.length }} 个字段
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Handle, Position, useNode } from '@vue-flow/core'
import { useQueryEditorStore } from '@/stores/queryEditorStore'
import { metadataApi } from '@/api/metadataApi'

const { node } = useNode()
const store = useQueryEditorStore()

interface FieldInfo {
  columnName: string
  dataType: string
  columnComment?: string
}

const props = defineProps<{
  data: {
    tableName: string
    alias: string
    expanded: boolean
    selectedFields: string[]
    fieldAliases: Record<string, string>
    fieldAggregations: Record<string, string>
    dataSourceId: string
  }
}>()

const emit = defineEmits<{
  (e: 'headerClick'): void
}>()

const fields = ref<FieldInfo[]>([])
const fieldsLoading = ref(false)

const isSelected = computed(() => store.selectedTableAlias === props.data.alias)

const customFields = computed(() => {
  const table = store.tables.find(t => t.alias === props.data.alias)
  return table?.customFields || []
})

onMounted(async () => {
  if (props.data.tableName) {
    await loadFields()
  }
})

async function loadFields() {
  fieldsLoading.value = true
  try {
    const res = await metadataApi.getTableColumns(props.data.dataSourceId, props.data.tableName)
    fields.value = (res.data || []).map(col => ({
      columnName: col.columnName,
      dataType: col.columnType || col.dataType || 'UNKNOWN',
      columnComment: col.columnComment
    }))
    store.updateTableFields(props.data.alias, fields.value.map(f => ({
      columnName: f.columnName,
      dataType: f.dataType,
      columnComment: f.columnComment
    })))
  } catch (e) {
    console.error('加载字段失败:', e)
  } finally {
    fieldsLoading.value = false
  }
}

function isFieldSelected(fieldName: string): boolean {
  if (props.data.selectedFields.includes('*')) {
    return true
  }
  return props.data.selectedFields.includes(fieldName)
}

function toggleField(fieldName: string) {
  if (props.data.selectedFields.includes('*')) {
    store.toggleFieldSelection(props.data.alias, '*')
    store.toggleFieldSelection(props.data.alias, fieldName)
  } else {
    store.toggleFieldSelection(props.data.alias, fieldName)
  }
}

function getFieldAgg(fieldName: string): string | undefined {
  return props.data.fieldAggregations[fieldName]
}

function handleFieldDblClick(field: FieldInfo) {
  if (!isFieldSelected(field.columnName)) {
    toggleField(field.columnName)
  }
}

function truncateComment(comment: string): string {
  if (!comment) return ''
  return comment.length > 8 ? comment.substring(0, 8) + '..' : comment
}

function getFieldTitle(field: FieldInfo): string {
  const parts = [field.columnName]
  const agg = getFieldAgg(field.columnName)
  if (agg) {
    parts.push(agg)
  } else if (field.columnComment) {
    parts.push(field.columnComment)
  }
  parts.push(field.dataType)
  return parts.join(' | ')
}
</script>

<style scoped>
.table-node {
  background: #1e3a5f;
  border: 1.5px solid #2d4a6f;
  border-radius: 6px;
  min-width: 220px;
  font-size: 11px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  transition: border-color 0.15s;
}
.table-node.selected { border-color: #3b82f6; }
.table-header {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  background: #0f3460;
  border-radius: 5px 5px 0 0;
  cursor: pointer;
  user-select: none;
}
.table-icon { font-size: 12px; }
.table-title { font-weight: 600; color: #e2e8f0; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.alias-badge {
  font-size: 9px;
  color: #60a5fa;
  background: rgba(59, 130, 246, 0.2);
  padding: 0 4px;
  border-radius: 3px;
  margin-left: auto;
}
.field-list { padding: 2px 0; }
.field-row {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px 2px 8px;
  position: relative;
  transition: background 0.1s;
}
.field-row:hover { background: rgba(59,130,246,0.15); }
.field-row.selected .field-info .field-name { color: #60a5fa; font-weight: 500; }

.field-checkbox input[type="checkbox"] {
  accent-color: #3b82f6;
  width: 11px;
  height: 11px;
  cursor: pointer;
  position: relative;
  z-index: 6;
  flex-shrink: 0;
}
.field-info {
  flex: 1;
  display: inline-flex;
  align-items: center;
  min-width: 0;
  cursor: default;
  overflow: hidden;
  position: relative;
  z-index: 6;
}
.field-name {
  color: #cbd5e1;
  font-size: 11px;
  white-space: nowrap;
  flex-shrink: 0;
}
.field-sep {
  color: #64748b;
  margin: 0 2px;
  white-space: nowrap;
  flex-shrink: 0;
}
.field-comment {
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agg-text {
  color: #d97706;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.field-type-badge {
  font-size: 10px;
  color: #818cf8;
  background: rgba(129, 140, 248, 0.15);
  padding: 0 4px;
  border-radius: 3px;
  white-space: nowrap;
  position: relative;
  z-index: 6;
  flex-shrink: 0;
}

.collapsed-summary {
  padding: 6px 10px;
  text-align: center;
  color: #94a3b8;
  font-size: 11px;
}
.fields-loading { padding: 10px; text-align: center; color: #94a3b8; font-size: 11px; }

.custom-field-section { padding: 0; }
.custom-field-divider {
  height: 1px;
  background: #2d4a6f;
  margin: 4px 8px;
}
.custom-field-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  font-size: 10px;
}
.cf-expr {
  color: #d97706;
  font-size: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}
.cf-arrow {
  color: #64748b;
  font-size: 9px;
}
.cf-alias {
  color: #60a5fa;
  font-weight: 500;
  white-space: nowrap;
}

:deep(.field-handle-invisible) {
  width: 1px !important;
  height: 1px !important;
  background: transparent !important;
  border: none !important;
  top: 50% !important;
  transform: translateY(-50%) !important;
  z-index: 1 !important;
  opacity: 0 !important;
  pointer-events: none !important;
}

:deep(.field-source-dot-handle) {
  width: 4px !important;
  height: 4px !important;
  background: transparent !important;
  border: none !important;
  border-radius: 50% !important;
  right: -2px !important;
  top: 50% !important;
  transform: translateY(-50%) !important;
  z-index: 10 !important;
  cursor: crosshair !important;
  transition: all 0.2s !important;
  opacity: 0 !important;
  pointer-events: none !important;
}
.field-row:hover :deep(.field-source-dot-handle) {
  width: 14px !important;
  height: 14px !important;
  background: #3b82f6 !important;
  border: 2px solid #2563eb !important;
  right: -7px !important;
  box-shadow: 0 0 8px rgba(59,130,246,0.5) !important;
  transform: translateY(-50%) !important;
  opacity: 1 !important;
  pointer-events: all !important;
}

:deep(.field-target-dot-handle) {
  width: 4px !important;
  height: 4px !important;
  background: transparent !important;
  border: none !important;
  border-radius: 50% !important;
  left: -2px !important;
  top: 50% !important;
  transform: translateY(-50%) !important;
  z-index: 10 !important;
  cursor: crosshair !important;
  transition: all 0.2s !important;
  opacity: 0 !important;
  pointer-events: none !important;
}
.field-row:hover :deep(.field-target-dot-handle) {
  width: 14px !important;
  height: 14px !important;
  background: #22c55e !important;
  border: 2px solid #16a34a !important;
  left: -7px !important;
  box-shadow: 0 0 8px rgba(34,197,94,0.5) !important;
  transform: translateY(-50%) !important;
  opacity: 1 !important;
  pointer-events: all !important;
}

</style>
