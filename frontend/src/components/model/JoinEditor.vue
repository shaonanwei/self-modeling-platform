<template>
  <el-dialog v-model="dialogVisible" title="配置表关联 (JOIN)" width="70%" :close-on-click-modal="false" top="10vh" append-to-body>
    <div class="join-editor">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
        配置表之间的关联关系，支持 INNER JOIN、LEFT JOIN、RIGHT JOIN
      </el-alert>

      <!-- 已有 JOIN 列表 -->
      <div v-if="joins.length > 0" class="join-list">
        <h4>已配置的关联</h4>
        <div
          v-for="(join, index) in joins"
          :key="index"
          class="join-item"
        >
          <div class="join-info">
            <el-tag :type="joinTypeTag(join.type)" size="small">{{ join.type }} JOIN</el-tag>
            <span class="join-detail">
              {{ getTableLabel(join.leftTable) }}.{{ join.leftField }}
              =
              {{ getTableLabel(join.rightTable) }}.{{ join.rightField }}
            </span>
          </div>
          <el-button type="danger" size="small" text @click="removeJoin(index)">删除</el-button>
        </div>
      </div>

      <el-empty v-else description="暂无关联配置，请在下方添加" :image-size="60" />

      <!-- 添加新 JOIN -->
      <el-divider>添加新关联</el-divider>
      <el-form :model="newJoin" label-width="80px" size="default">
        <el-form-item label="关联类型">
          <el-select v-model="newJoin.type" style="width: 150px">
            <el-option label="INNER JOIN" value="INNER" />
            <el-option label="LEFT JOIN" value="LEFT" />
            <el-option label="RIGHT JOIN" value="RIGHT" />
          </el-select>
        </el-form-item>

        <el-form-item label="左表">
          <el-select v-model="newJoin.leftTable" style="width: 180px" @change="onLeftTableChange">
            <el-option v-for="t in tables" :key="t.name" :label="t.alias || t.name" :value="t.name" />
          </el-select>
        </el-form-item>

        <el-form-item label="左字段">
          <el-select v-model="newJoin.leftField" style="width: 180px">
            <el-option v-for="f in leftFields" :key="f" :label="f" :value="f" />
          </el-select>
        </el-form-item>

        <el-form-item label="右表">
          <el-select v-model="newJoin.rightTable" style="width: 180px" @change="onRightTableChange">
            <el-option v-for="t in tables" :key="t.name" :label="t.alias || t.name" :value="t.name" />
          </el-select>
        </el-form-item>

        <el-form-item label="右字段">
          <el-select v-model="newJoin.rightField" style="width: 180px">
            <el-option v-for="f in rightFields" :key="f" :label="f" :value="f" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="addJoin" :disabled="!canAddJoin">
            <el-icon><Plus /></el-icon> 添加关联
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

interface Join {
  type: 'INNER' | 'LEFT' | 'RIGHT'
  leftTable: string
  leftField: string
  rightTable: string
  rightField: string
}

const props = defineProps<{
  visible: boolean
  tables: Array<{ name: string; alias?: string }>
  joins: Join[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', joins: Join[]): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

// 模拟字段
const mockFields: Record<string, string[]> = {
  user: ['id', 'username', 'email', 'phone', 'status', 'create_time'],
  order: ['id', 'user_id', 'product_id', 'quantity', 'total_amount', 'status', 'create_time'],
  product: ['id', 'name', 'category_id', 'price', 'stock'],
  category: ['id', 'name', 'parent_id']
}

const newJoin = ref<Partial<Join>>({ type: 'INNER' })
const leftFields = ref<string[]>([])
const rightFields = ref<string[]>([])

const canAddJoin = computed(() => {
  return !!(newJoin.value.leftTable && newJoin.value.leftField &&
         newJoin.value.rightTable && newJoin.value.rightField)
})

const getTableLabel = (tableName: string) => {
  const t = props.tables.find(t => t.name === tableName)
  return t?.alias || tableName
}

const getTableFields = (tableName: string) => {
  return mockFields[tableName] || ['id', 'name']
}

const onLeftTableChange = () => {
  if (newJoin.value.leftTable) {
    leftFields.value = getTableFields(newJoin.value.leftTable as string)
  }
}

const onRightTableChange = () => {
  if (newJoin.value.rightTable) {
    rightFields.value = getTableFields(newJoin.value.rightTable as string)
  }
}

const addJoin = () => {
  if (!canAddJoin.value) return
  const j = newJoin.value as Join
  // 检查重复
  const exists = props.joins.some(ej =>
    ej.leftTable === j.leftTable && ej.leftField === j.leftField &&
    ej.rightTable === j.rightTable && ej.rightField === j.rightField
  )
  if (exists) return

  const copy = { ...j }
  emit('confirm', [...props.joins, copy])
  newJoin.value = { type: 'INNER' }
  leftFields.value = []
  rightFields.value = []
}

const removeJoin = (index: number) => {
  const updated = [...props.joins]
  updated.splice(index, 1)
  emit('confirm', updated)
}

const joinTypeTag = (type: string) => {
  const map: Record<string, any> = { INNER: '', LEFT: 'warning', RIGHT: 'danger' }
  return map[type] || ''
}

const getTableJoins = (tableName: string) => {
  return props.joins.filter(j => j.leftTable === tableName || j.rightTable === tableName)
}

// 重置新 JOIN 表单
watch(dialogVisible, (val) => {
  if (val) {
    newJoin.value = { type: 'INNER' }
    leftFields.value = []
    rightFields.value = []
  }
})

const confirm = () => {
  emit('confirm', props.joins)
  dialogVisible.value = false
}
</script>

<style scoped>
.join-editor {
  min-height: 300px;
}
.join-list {
  margin-bottom: 16px;
}
.join-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 8px;
}
.join-detail {
  margin-left: 8px;
  font-family: monospace;
  font-size: 13px;
}
.join-visual {
  margin-top: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}
.visual-canvas {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.table-node {
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  min-width: 150px;
}
.table-header {
  background: #409EFF;
  color: #fff;
  padding: 8px 12px;
  font-weight: 500;
}
.table-body {
  padding: 8px;
}
.join-badge {
  font-size: 12px;
  color: #606266;
  padding: 2px 6px;
  background: #ecf5ff;
  border-radius: 4px;
  margin-bottom: 4px;
}
.join-arrows {
  width: 100%;
  margin-top: 16px;
}
.arrow {
  font-family: monospace;
  font-size: 12px;
  color: #909399;
  padding: 4px 0;
}
h4 {
  margin: 0 0 12px 0;
  color: #303133;
  font-size: 14px;
}
</style>
