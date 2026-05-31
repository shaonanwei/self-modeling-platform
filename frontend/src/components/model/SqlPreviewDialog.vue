<template>
  <el-dialog v-model="dialogVisible" title="SQL 预览" width="70%" :close-on-click-modal="false" top="10vh" append-to-body>
    <div class="sql-preview-dialog">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
        根据当前配置自动生成的 SQL 语句
      </el-alert>

      <div class="sql-block">
        <div class="sql-header">
          <span>生成的 SQL</span>
          <el-button size="small" @click="copySql">
            <el-icon><CopyDocument /></el-icon> 复制
          </el-button>
        </div>
        <div class="sql-content">
          <code>{{ generatedSql }}</code>
        </div>
      </div>

      <div class="sql-block" v-if="configType === 'VISUAL'">
        <div class="sql-header">
          <span>配置摘要</span>
        </div>
        <div class="summary-content">
          <p>📋 <strong>表：</strong>{{ tables.map(t => t.alias || t.name).join(', ') }}</p>
          <p>📊 <strong>字段：</strong>{{ selectedFields.length }} 个</p>
          <p>🔗 <strong>JOIN：</strong>{{ joins.length }} 个</p>
          <p>🔍 <strong>条件：</strong>{{ conditions.length }} 个</p>
        </div>
      </div>

      <div class="sql-block">
        <div class="sql-header">
          <span>测试执行（前 10 条）</span>
        </div>
        <div class="test-result">
          <el-empty description="测试功能需要后端 API 支持，将在后续版本实现" :image-size="80" />
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { visualToSql } from '@/utils/sqlConverter'

interface Join {
  type: 'INNER' | 'LEFT' | 'RIGHT'
  leftTable: string
  leftField: string
  rightTable: string
  rightField: string
}

interface Condition {
  field: string
  operator: string
  value: string
  logic: 'AND' | 'OR'
  leftParen: string
  rightParen: string
}

const props = defineProps<{
  visible: boolean
  configType: 'SQL' | 'VISUAL'
  sqlStatement?: string
  dataSource?: string
  tables: Array<{ name: string; alias?: string }>
  selectedFields: string[]
  joins: Join[]
  conditions: Condition[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const generatedSql = computed(() => {
  if (props.configType === 'SQL') {
    return props.sqlStatement || '-- 暂无 SQL 语句'
  }

  // VISUAL 模式：从可视化配置生成 SQL
  if (props.tables.length === 0) return '-- 请先选择表'

  try {
    return visualToSql({
      tables: props.tables,
      selectedFields: props.selectedFields,
      joins: props.joins,
      conditions: props.conditions,
      groupBy: [],
      orderBy: []
    })
  } catch (e) {
    return '-- SQL 生成失败，请检查配置'
  }
})

const copySql = () => {
  if (!generatedSql.value || generatedSql.value.startsWith('--')) {
    ElMessage.warning('没有可复制的 SQL')
    return
  }
  navigator.clipboard.writeText(generatedSql.value).then(() => {
    ElMessage.success('SQL 已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}
</script>

<style scoped>
.sql-preview-dialog {
  min-height: 300px;
}
.sql-block {
  margin-bottom: 16px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
}
.sql-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}
.sql-content {
  padding: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
}
.summary-content {
  padding: 12px;
  font-size: 13px;
  color: #606266;
}
.summary-content p {
  margin: 4px 0;
}
.test-result {
  padding: 20px;
}
</style>
