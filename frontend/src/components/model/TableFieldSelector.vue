<template>
  <el-dialog v-model="dialogVisible" title="选择字段" width="80%" :close-on-click-modal="false" top="10vh" append-to-body>
    <div class="field-selector">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
        为每个已选表勾选需要查询的字段，可设置字段别名
      </el-alert>

      <el-tabs v-model="activeTable" v-if="tableFields.length > 0">
        <el-tab-pane
          v-for="table in tableFields"
          :key="table.name"
          :label="table.alias || table.name"
          :name="table.name"
        >
          <div class="field-table">
            <el-table :data="table.fields" height="400" border>
              <el-table-column width="50">
                <template #header>
                  <el-checkbox
                    :model-value="areAllSelected(table.name)"
                    :indeterminate="areSomeSelected(table.name)"
                    @change="toggleAll(table.name, $event)"
                  />
                </template>
                <template #default="{ row }">
                  <el-checkbox v-model="row.selected" @change="onFieldChange" />
                </template>
              </el-table-column>
              <el-table-column prop="name" label="字段名" width="150" />
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column label="别名" min-width="150">
                <template #default="{ row }">
                  <el-input
                    v-if="row.selected"
                    v-model="row.alias"
                    size="small"
                    placeholder="可选别名"
                    @blur="onAliasChange(row)"
                  />
                  <span v-else style="color: #c0c4cc;">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="comment" label="注释" min-width="120" />
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-empty v-else description="请先选择数据表" :image-size="100" />

      <div class="selected-summary" v-if="selectedCount > 0">
        已选 <strong>{{ selectedCount }}</strong> 个字段：
        <el-tag v-for="f in selectedList" :key="f.fullPath" size="small" style="margin-right: 4px;">
          {{ f.alias || f.fullPath }}
        </el-tag>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'

interface Field {
  name: string
  type: string
  comment: string
  selected: boolean
  alias: string
}

interface TableDef {
  name: string
  alias?: string
  fields: Field[]
}

const props = defineProps<{
  visible: boolean
  tables: Array<{ name: string; alias?: string }>
  selectedFields: string[] // "table.field" 或 "table.field AS alias"
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', fields: string[]): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const activeTable = ref('')

// 模拟字段元数据
const mockFields: Record<string, Field[]> = {
  user: [
    { name: 'id', type: 'BIGINT', comment: '主键', selected: false, alias: '' },
    { name: 'username', type: 'VARCHAR', comment: '用户名', selected: false, alias: '' },
    { name: 'email', type: 'VARCHAR', comment: '邮箱', selected: false, alias: '' },
    { name: 'phone', type: 'VARCHAR', comment: '手机号', selected: false, alias: '' },
    { name: 'status', type: 'INT', comment: '状态', selected: false, alias: '' },
    { name: 'create_time', type: 'TIMESTAMP', comment: '创建时间', selected: false, alias: '' }
  ],
  order: [
    { name: 'id', type: 'BIGINT', comment: '主键', selected: false, alias: '' },
    { name: 'user_id', type: 'BIGINT', comment: '用户ID', selected: false, alias: '' },
    { name: 'product_id', type: 'BIGINT', comment: '商品ID', selected: false, alias: '' },
    { name: 'quantity', type: 'INT', comment: '数量', selected: false, alias: '' },
    { name: 'total_amount', type: 'DECIMAL', comment: '总金额', selected: false, alias: '' },
    { name: 'status', type: 'VARCHAR', comment: '订单状态', selected: false, alias: '' },
    { name: 'create_time', type: 'TIMESTAMP', comment: '下单时间', selected: false, alias: '' }
  ],
  product: [
    { name: 'id', type: 'BIGINT', comment: '主键', selected: false, alias: '' },
    { name: 'name', type: 'VARCHAR', comment: '商品名称', selected: false, alias: '' },
    { name: 'category_id', type: 'BIGINT', comment: '分类ID', selected: false, alias: '' },
    { name: 'price', type: 'DECIMAL', comment: '价格', selected: false, alias: '' },
    { name: 'stock', type: 'INT', comment: '库存', selected: false, alias: '' }
  ],
  category: [
    { name: 'id', type: 'BIGINT', comment: '主键', selected: false, alias: '' },
    { name: 'name', type: 'VARCHAR', comment: '分类名称', selected: false, alias: '' },
    { name: 'parent_id', type: 'BIGINT', comment: '父分类ID', selected: false, alias: '' }
  ]
}

// 字段数据（不会在 tables 变化时重置，只增量更新）
const tableFields = ref<TableDef[]>([])

/**
 * 同步 props.tables → tableFields
 * - 新增表：添加字段
 * - 删除表：移除
 * - 已选状态：根据 selectedFields 恢复
 * - 已有字段的选择状态：保留（不会被重置）
 */
watch(() => props.tables, (newTables) => {
  const currentNames = new Set(newTables.map(t => t.name))
  const existingNames = new Set(tableFields.value.map(t => t.name))

  // 移除已经不存在的表
  tableFields.value = tableFields.value.filter(t => currentNames.has(t.name))

  // 添加新表
  for (const t of newTables) {
    if (!existingNames.has(t.name)) {
      // 新表：初始化字段
      const fields = (mockFields[t.name] || [
        { name: 'id', type: 'BIGINT', comment: '主键', selected: false, alias: '' },
        { name: t.name, type: 'VARCHAR', comment: '名称', selected: false, alias: '' }
      ]).map(f => {
        const field = { ...f }
        // 恢复已选状态
        props.selectedFields.forEach(sf => {
          const parts = sf.split(' AS ')
          const fieldName = parts[0]
          const alias = parts.length > 1 ? parts[1] : ''
          const [tableName, colName] = fieldName.split('.')
          if (tableName === t.name && colName === field.name) {
            field.selected = true
            field.alias = alias
          }
        })
        return field
      })
      tableFields.value.push({ ...t, fields })
    } else {
      // 已存在的表：更新别名
      const existing = tableFields.value.find(tf => tf.name === t.name)
      if (existing) {
        existing.alias = t.alias
      }
    }
  }

  // 设置默认激活 tab
  if (newTables.length > 0 && (!activeTable.value || !currentNames.has(activeTable.value))) {
    activeTable.value = newTables[0].name
  }
}, { immediate: true })

const getFieldsForTable = (tableName: string): Field[] => {
  return tableFields.value.find(t => t.name === tableName)?.fields || []
}

const areAllSelected = (tableName: string): boolean => {
  const fields = getFieldsForTable(tableName)
  return fields.length > 0 && fields.every(f => f.selected)
}

const areSomeSelected = (tableName: string): boolean => {
  const fields = getFieldsForTable(tableName)
  return fields.some(f => f.selected) && !fields.every(f => f.selected)
}

const toggleAll = (tableName: string, checked: boolean | string | number) => {
  const fields = getFieldsForTable(tableName)
  fields.forEach(f => f.selected = !!checked)
}

const onFieldChange = () => {}
const onAliasChange = (_field: Field) => {}

const selectedCount = computed(() => {
  return tableFields.value.reduce((sum, t) => sum + t.fields.filter(f => f.selected).length, 0)
})

const selectedList = computed(() => {
  const result: Array<{ fullPath: string; alias: string }> = []
  tableFields.value.forEach(t => {
    t.fields.forEach(f => {
      if (f.selected) {
        result.push({
          fullPath: `${t.name}.${f.name}`,
          alias: f.alias
        })
      }
    })
  })
  return result
})

const confirm = () => {
  const fields = selectedList.value.map(f => f.alias ? `${f.fullPath} AS ${f.alias}` : f.fullPath)
  emit('confirm', fields)
  dialogVisible.value = false
}
</script>

<style scoped>
.field-selector {
  min-height: 300px;
}
.field-table {
  margin-bottom: 16px;
}
.selected-summary {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
}
.selected-summary strong {
  color: #409EFF;
}
</style>
