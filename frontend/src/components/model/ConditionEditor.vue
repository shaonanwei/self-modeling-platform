<template>
  <el-dialog v-model="dialogVisible" title="配置查询条件" width="80%" :close-on-click-modal="false" top="10vh" append-to-body>
    <div class="condition-editor">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
        配置 WHERE 查询条件，支持多条件组合（AND/OR）
      </el-alert>

      <!-- 条件列表 -->
      <div class="condition-list">
        <el-empty v-if="conditions.length === 0" description="暂无查询条件，请在下方添加" :image-size="60" />

        <div
          v-for="(cond, index) in conditions"
          :key="index"
          class="condition-row"
        >
          <!-- 逻辑操作符 -->
          <div class="cond-logic">
            <el-select v-model="cond.logic" size="small" style="width: 70px" :disabled="index === 0">
              <el-option label="AND" value="AND" />
              <el-option label="OR" value="OR" />
            </el-select>
          </div>

          <!-- 括号 -->
          <el-select v-model="cond.leftParen" size="small" style="width: 50px" placeholder="">
            <el-option label="(" value="(" />
            <el-option label="" value="" />
          </el-select>

          <!-- 字段 -->
          <el-select v-model="cond.field" size="small" style="width: 180px" placeholder="选择字段" filterable>
            <el-option-group v-for="table in tables" :key="table.name" :label="table.alias || table.name">
              <el-option
                v-for="f in getTableFields(table.name)"
                :key="f"
                :label="`${table.alias || table.name}.${f}`"
                :value="`${table.name}.${f}`"
              />
            </el-option-group>
          </el-select>

          <!-- 操作符 -->
          <el-select v-model="cond.operator" size="small" style="width: 120px" placeholder="操作符">
            <el-option label="等于 =" value="=" />
            <el-option label="不等于 !=" value="!=" />
            <el-option label="大于 &gt;" value=">" />
            <el-option label="大于等于 &gt;=" value=">=" />
            <el-option label="小于 &lt;" value="<" />
            <el-option label="小于等于 &lt;=" value="<=" />
            <el-option label="包含 LIKE" value="LIKE" />
            <el-option label="不包含 NOT LIKE" value="NOT LIKE" />
            <el-option label="在范围内 IN" value="IN" />
            <el-option label="不在范围内 NOT IN" value="NOT IN" />
            <el-option label="为空 IS NULL" value="IS NULL" />
            <el-option label="不为空 IS NOT NULL" value="IS NOT NULL" />
            <el-option label="之间 BETWEEN" value="BETWEEN" />
          </el-select>

          <!-- 值 -->
          <div class="cond-value">
            <el-input
              v-if="needsValue(cond.operator)"
              v-model="cond.value"
              size="small"
              placeholder="条件值"
              style="width: 150px"
            />
            <span v-else class="no-value">-</span>
          </div>

          <!-- 右括号 -->
          <el-select v-model="cond.rightParen" size="small" style="width: 50px" placeholder="">
            <el-option label=")" value=")" />
            <el-option label="" value="" />
          </el-select>

          <!-- 删除 -->
          <el-button type="danger" size="small" text @click="removeCondition(index)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>

        <!-- 添加按钮 -->
        <div class="add-condition">
          <el-button type="primary" plain @click="addCondition">
            <el-icon><Plus /></el-icon> 添加条件
          </el-button>
        </div>
      </div>

      <!-- SQL 预览 -->
      <div class="sql-preview" v-if="conditions.length > 0">
        <h4>WHERE 子句预览：</h4>
        <code>{{ whereClausePreview }}</code>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

interface Condition {
  logic: 'AND' | 'OR'
  leftParen: string
  field: string
  operator: '=' | '!=' | '>' | '>=' | '<' | '<=' | 'LIKE' | 'NOT LIKE' | 'IN' | 'NOT IN' | 'IS NULL' | 'IS NOT NULL' | 'BETWEEN'
  value: string
  rightParen: string
}

const props = defineProps<{
  visible: boolean
  tables: Array<{ name: string; alias?: string }>
  conditions: Condition[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', conditions: Condition[]): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const mockFields: Record<string, string[]> = {
  user: ['id', 'username', 'email', 'phone', 'status', 'create_time'],
  order: ['id', 'user_id', 'product_id', 'quantity', 'total_amount', 'status', 'create_time'],
  product: ['id', 'name', 'category_id', 'price', 'stock'],
  category: ['id', 'name', 'parent_id']
}

const conditions = ref<Condition[]>([])

// 同步 props.conditions → 本地 conditions
watch(() => props.conditions, (val) => {
  conditions.value = val.length > 0
    ? val.map(c => ({ ...c }))
    : []
}, { immediate: true })

const getTableFields = (tableName: string) => {
  return mockFields[tableName] || ['id', 'name']
}

const needsValue = (operator: string) => {
  return !['IS NULL', 'IS NOT NULL'].includes(operator)
}

const addCondition = () => {
  conditions.value.push({
    logic: conditions.value.length === 0 ? 'AND' : 'AND',
    leftParen: '',
    field: '',
    operator: '=',
    value: '',
    rightParen: ''
  })
}

const removeCondition = (index: number) => {
  conditions.value.splice(index, 1)
  if (conditions.value.length > 0) {
    conditions.value[0].logic = 'AND'
  }
}

const whereClausePreview = computed(() => {
  return conditions.value.map((c, i) => {
    const logic = i === 0 ? '' : ` ${c.logic} `
    const leftP = c.leftParen || ''
    const rightP = c.rightParen || ''
    const val = needsValue(c.operator) && c.value ? `'${c.value}'` : ''
    return `${logic}${leftP}${c.field} ${c.operator}${val ? ' ' + val : ''}${rightP}`
  }).join('')
})

const confirm = () => {
  emit('confirm', conditions.value)
  dialogVisible.value = false
}
</script>

<style scoped>
.condition-editor {
  min-height: 300px;
}
.condition-list {
  margin-bottom: 16px;
}
.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 8px;
}
.cond-logic {
  flex-shrink: 0;
}
.cond-value {
  flex-shrink: 0;
}
.no-value {
  color: #c0c4cc;
  font-size: 13px;
}
.add-condition {
  text-align: center;
  padding: 8px 0;
}
.sql-preview {
  padding: 16px;
  background: #1e1e1e;
  border-radius: 8px;
  color: #d4d4d4;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}
.sql-preview h4 {
  margin: 0 0 8px 0;
  color: #fff;
  font-size: 13px;
}
.sql-preview code {
  display: block;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
