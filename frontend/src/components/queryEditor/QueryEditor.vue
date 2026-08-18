<template>
  <div class="query-editor">
    <!-- 顶部工具栏 -->
    <div class="qe-toolbar">
      <div class="qe-toolbar-left">
        <el-button size="small" type="primary" plain @click="aiDrawerVisible = true">
          <el-icon><MagicStick /></el-icon>
          AI 生成 SQL
        </el-button>
      </div>
      <div class="qe-toolbar-center">
        <span class="mode-badge">画布&SQL</span>
      </div>
      <div class="qe-toolbar-right">
        <el-tooltip content="SQL转画布" placement="bottom">
          <el-button size="small" @click="handleSqlToCanvas" :disabled="!store.sqlText.trim()">
            <el-icon><Top /></el-icon> 画布
          </el-button>
        </el-tooltip>
        <el-button size="small" @click="handleValidate" :loading="validating">校验</el-button>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="qe-body">
      <!-- 左侧：元数据面板 -->
      <div class="qe-left-panel" :style="{ width: leftPanelWidth + 'px' }">
        <div class="qe-left-header">
          <span class="panel-title">元数据面板</span>
        </div>
        <MetadataPanel
          :data-source-id="store.dataSourceId"
          @drag-table="handleDragTable"
        />
      </div>

      <!-- 左侧拖拽条 -->
      <div
        class="qe-resize-bar horizontal"
        @mousedown="startLeftPanelResize"
        title="拖拽调整宽度"
      >
        <span class="resize-icon">⋯</span>
      </div>

      <!-- 中间：画布 / SQL编辑器 -->
      <div class="qe-center">
        <!-- 画布模式 -->
        <div class="canvas-wrapper" style="flex: 1;">
          <CanvasArea
            ref="canvasRef"
            @node-click="handleNodeClick"
            @edge-click="handleEdgeClick"
            @nodes-change="handleNodesChange"
            @connect="handleConnect"
            @max-tables-reached="handleMaxTablesReached"
            @pane-click="handlePaneClick"
            @where-panel-click="handleWherePanelClick"
            @group-panel-click="handleGroupPanelClick"
            @order-panel-click="handleOrderPanelClick"
          />
        </div>

        <!-- 垂直拖拽条 -->
        <div
          class="qe-resize-bar vertical"
          @mousedown="startVerticalResize"
          title="拖拽调整大小"
        >
          <span class="resize-icon">⋯</span>
        </div>

        <!-- SQL 模式 -->
        <div v-show="!sqlPanelCollapsed" class="sql-wrapper" :style="{ height: sqlPanelHeight + 'px', flex: 'none' }">
          <div class="sql-header">
            <span class="panel-title">SQL 编辑器</span>
            <el-button
              size="small"
              @click="toggleSqlPanel"
              class="collapse-btn"
              title="收起面板"
            >
              <el-icon><Minus /></el-icon>
            </el-button>
          </div>
          <SqlEditor
            ref="sqlEditorRef"
            v-model:sql="store.sqlText"
            @change="handleSqlChange"
            @validate="handleValidate"
            @execute="handleExecute"
          />
        </div>

        <!-- SQL面板展开按钮 -->
        <div
          v-show="sqlPanelCollapsed"
          class="sql-expand"
          @click="toggleSqlPanel"
          title="展开SQL编辑器"
        >
          <el-icon><ArrowUp /></el-icon>
        </div>
      </div>

      <!-- 右侧拖拽条 -->
      <div
        v-show="!rightPanelCollapsed"
        class="qe-resize-bar horizontal"
        @mousedown="startHorizontalResize"
        title="拖拽调整宽度"
      >
        <span class="resize-icon">⋯</span>
      </div>

      <!-- 右侧：属性面板 -->
      <div
        class="qe-right-panel"
        :style="{ width: rightPanelWidth + 'px', display: rightPanelCollapsed ? 'none' : 'flex' }"
      >
        <div class="qe-right-header">
          <span class="panel-title">属性面板</span>
          <el-button
            size="small"
            @click="toggleRightPanel"
            class="collapse-btn"
            title="隐藏面板"
          >
            <el-icon><Minus /></el-icon>
          </el-button>
        </div>
        <div class="qe-right-content">
          <PropertyPanel
            :selected-table="store.selectedTable"
            :selected-join="store.selectedJoin"
            :canvas-config="store.canvasConfig"
            @update-where="store.setWhere($event)"
            @update-group-by="store.setGroupBy($event)"
            @update-having="store.setHaving($event)"
            @update-order-by="store.setOrderBy($event)"
            @update-limit="store.setLimit($event)"
            @update-distinct="store.setDistinct($event)"
            @update-table-field="handleUpdateTableField"
            @update-join-type="handleUpdateJoinType"
            @remove-join="store.removeJoin($event)"
            @add-join="handleAddJoin"
            @smart-recommend="handleSmartRecommend"
          />
        </div>
      </div>

      <!-- 右侧面板展开按钮 -->
      <div
        v-show="rightPanelCollapsed"
        class="qe-right-expand"
        @click="toggleRightPanel"
        title="展开属性面板"
      >
        <el-icon><ArrowLeft /></el-icon>
      </div>
    </div>

    <!-- 底部工具栏 -->
    <div class="qe-footer">
      <div class="qe-footer-left">
        <span class="footer-info">已加载 {{ store.tables.length }} 张表</span>
      </div>
      <div class="qe-footer-right">
        <span class="footer-info">SQL 行数: {{ store.sqlText.split('\n').length }}</span>
      </div>
    </div>

    <!-- 执行结果弹窗 -->
    <el-dialog v-model="showResultDialog" title="查询结果" width="80%" top="5vh">
      <SqlResultViewer :result="executeResult" />
    </el-dialog>

    <AiSqlDrawer
      v-model:visible="aiDrawerVisible"
      :data-source-id="store.dataSourceId"
      :current-sql="store.sqlText"
      :messages="aiMessages"
      @update:messages="emit('update:aiMessages', $event)"
      @apply-sql="applyAiSql"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useQueryEditorStore } from '@/stores/queryEditorStore'
import { sqlApi } from '@/api/sqlApi'
import type { AiSqlMessage } from '@/types/aiSql'
import type { SqlExecuteResult } from '@/types/queryEditor'
import MetadataPanel from './MetadataPanel.vue'
import CanvasArea from './CanvasArea.vue'
import SqlEditor from './SqlEditor.vue'
import PropertyPanel from './PropertyPanel.vue'
import SqlResultViewer from './SqlResultViewer.vue'
import AiSqlDrawer from './AiSqlDrawer.vue'
import { Bottom, Top, ArrowRight, ArrowLeft, Minus, ArrowUp, ArrowDown, MagicStick } from '@element-plus/icons-vue'

const props = defineProps<{
  initialSql?: string
  initialConfig?: any
  dataSourceId?: string
  aiMessages: AiSqlMessage[]
}>()

const emit = defineEmits<{
  (e: 'change', config: any): void
  (e: 'update:aiMessages', messages: AiSqlMessage[]): void
}>()

const store = useQueryEditorStore()
const canvasRef = ref()
const sqlEditorRef = ref<InstanceType<typeof SqlEditor>>()
const showResultDialog = ref(false)
const aiDrawerVisible = ref(false)
const executeResult = ref<SqlExecuteResult | null>(null)
const validating = ref(false)
const executing = ref(false)

// 拖拽相关状态
const sqlPanelHeight = ref(250) // 参考属性面板使用固定像素值
const rightPanelWidth = ref(380)
const rightPanelCollapsed = ref(false)
const leftPanelWidth = ref(240)
const sqlPanelCollapsed = ref(false)
const isResizing = ref(false)
const resizeType = ref<'vertical' | 'horizontal' | 'left'>('vertical')

// 缓存拖动时的初始值，避免频繁DOM测量
const initialResizeState = ref({
  startX: 0,
  startY: 0,
  startWidth: 0,
  startHeight: 0,
  containerWidth: 0,
  containerHeight: 0
})

// 拖拽方法
function toggleRightPanel() {
  rightPanelCollapsed.value = !rightPanelCollapsed.value
}

function toggleSqlPanel() {
  sqlPanelCollapsed.value = !sqlPanelCollapsed.value
}

function handleLeftPanelResize(width: number) {
  leftPanelWidth.value = width
}

function startLeftPanelResize(e: MouseEvent) {
  isResizing.value = true
  resizeType.value = 'left'
  
  // 缓存初始状态，避免拖动时频繁测量DOM
  const qeBody = document.querySelector('.qe-body') as HTMLElement
  if (qeBody) {
    const rect = qeBody.getBoundingClientRect()
    initialResizeState.value = {
      startX: e.clientX,
      startY: e.clientY,
      startWidth: leftPanelWidth.value,
      startHeight: 0,
      containerWidth: rect.width,
      containerHeight: rect.height
    }
  }
  
  document.addEventListener('mousemove', handleLeftPanelResizeDrag)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  e.preventDefault()
  e.stopPropagation()
}

function handleLeftPanelResizeDrag(e: MouseEvent) {
  if (!isResizing.value || resizeType.value !== 'left') return
  
  // 使用缓存的初始值计算，避免频繁DOM测量
  const deltaX = e.clientX - initialResizeState.value.startX
  const newWidth = initialResizeState.value.startWidth + deltaX
  
  leftPanelWidth.value = Math.max(150, Math.min(400, newWidth))
}

function startVerticalResize(e: MouseEvent) {
  isResizing.value = true
  resizeType.value = 'vertical'
  
  // 缓存初始状态，避免拖动时频繁测量DOM
  const qeCenter = document.querySelector('.qe-center') as HTMLElement
  if (qeCenter) {
    const rect = qeCenter.getBoundingClientRect()
    initialResizeState.value = {
      startX: e.clientX,
      startY: e.clientY,
      startWidth: 0,
      startHeight: sqlPanelHeight.value,
      containerWidth: rect.width,
      containerHeight: rect.height
    }
  }
  
  document.addEventListener('mousemove', handleVerticalResize)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'row-resize'
  document.body.style.userSelect = 'none'
  e.preventDefault()
  e.stopPropagation()
}

function handleVerticalResize(e: MouseEvent) {
  if (!isResizing.value || resizeType.value !== 'vertical') return
  
  // 使用缓存的初始值计算，避免频繁DOM测量（向上拖动减小高度，向下拖动增加高度）
  const deltaY = initialResizeState.value.startY - e.clientY
  const newHeight = initialResizeState.value.startHeight + deltaY
  
  // 限制高度范围
  sqlPanelHeight.value = Math.max(150, Math.min(500, newHeight))
}

function startHorizontalResize(e: MouseEvent) {
  isResizing.value = true
  resizeType.value = 'horizontal'
  
  // 缓存初始状态，避免拖动时频繁测量DOM
  const qeBody = document.querySelector('.qe-body') as HTMLElement
  if (qeBody) {
    const rect = qeBody.getBoundingClientRect()
    initialResizeState.value = {
      startX: e.clientX,
      startY: e.clientY,
      startWidth: rightPanelWidth.value,
      startHeight: 0,
      containerWidth: rect.width,
      containerHeight: rect.height
    }
  }
  
  document.addEventListener('mousemove', handleHorizontalResize)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  e.preventDefault()
  e.stopPropagation()
}

function handleHorizontalResize(e: MouseEvent) {
  if (!isResizing.value || resizeType.value !== 'horizontal') return
  
  // 使用缓存的初始值计算，避免频繁DOM测量
  const deltaX = e.clientX - initialResizeState.value.startX
  const newWidth = initialResizeState.value.startWidth - deltaX
  
  // 限制宽度范围
  rightPanelWidth.value = Math.max(200, Math.min(500, newWidth))
}

function stopResize() {
  isResizing.value = false
  document.removeEventListener('mousemove', handleVerticalResize)
  document.removeEventListener('mousemove', handleHorizontalResize)
  document.removeEventListener('mousemove', handleLeftPanelResizeDrag)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

let syncTimer: ReturnType<typeof setTimeout> | null = null
let autoSqlTimer: ReturnType<typeof setTimeout> | null = null

function cancelAutoSqlGeneration() {
  if (!autoSqlTimer) return
  clearTimeout(autoSqlTimer)
  autoSqlTimer = null
}

onMounted(() => {
  store.reset()
  if (props.dataSourceId) {
    store.dataSourceId = props.dataSourceId
  }
  if (props.initialSql) {
    store.setSql(props.initialSql)
  }
  if (props.initialConfig) {
    store.loadFromQueryConfig(props.initialConfig)
  }
})

watch(() => props.dataSourceId, (newVal) => {
  if (newVal) {
    store.dataSourceId = newVal
  }
})

watch(() => store.focusCustomFields, (val) => {
  if (val) {
    rightPanelCollapsed.value = false
  }
})

onUnmounted(() => {
  stopResize()
  if (syncTimer) clearTimeout(syncTimer)
  cancelAutoSqlGeneration()
})

// 监听画布变化，自动生成SQL
watch(() => [
  store.tables,
  store.joins,
  store.canvasConfig.where,
  store.canvasConfig.groupBy,
  store.canvasConfig.having,
  store.canvasConfig.orderBy,
  store.canvasConfig.limit,
  store.canvasConfig.distinct
], () => {
  if (autoSqlTimer) clearTimeout(autoSqlTimer)
  autoSqlTimer = setTimeout(() => {
    const sql = store.generateSql()
    store.setSql(sql)
  }, 200)
}, { deep: true })

watch(() => store.sqlText, () => {
  if (syncTimer) clearTimeout(syncTimer)
  syncTimer = setTimeout(() => {
    emit('change', store.exportToQueryConfig())
  }, 300)
}, { deep: true })

function handleSqlChange() {
  emit('change', store.exportToQueryConfig())
}

function applyAiSql(sql: string) {
  cancelAutoSqlGeneration()
  sqlEditorRef.value?.replaceAllSql(sql)
  store.setSql(sql)
  ElMessage.success('AI SQL 已应用，请确认后保存')
}

async function handleValidate() {
  if (!store.sqlText.trim()) {
    ElMessage.warning('请先输入 SQL 语句')
    return
  }
  validating.value = true
  try {
    const res = await sqlApi.validate(store.sqlText, store.dataSourceId)
    if (res.data.valid) {
      ElMessage.success('SQL 校验通过')
    } else {
      ElMessage.error('SQL 校验失败: ' + (res.data.message || ''))
    }
  } catch (e: any) {
    ElMessage.error('SQL 校验失败')
  } finally {
    validating.value = false
  }
}

async function handleExecute() {
  if (!store.sqlText.trim()) {
    ElMessage.warning('请先输入 SQL 语句')
    return
  }
  executing.value = true
  try {
    const res = await sqlApi.execute(store.sqlText, 50, store.dataSourceId)
    if (res.data.success) {
      executeResult.value = res.data
      showResultDialog.value = true
    } else {
      ElMessage.error('执行失败: ' + (res.data.message || ''))
    }
  } catch (e: any) {
    ElMessage.error('执行失败')
  } finally {
    executing.value = false
  }
}

function handleDragTable(tableName: string) {
  const added = store.addTable(tableName)
  if (added) {
    emit('change', store.exportToQueryConfig())
  }
}

function handleNodeClick(alias: string) {
  store.selectTable(alias)
  store.setActiveTab('basic')
}

function handleEdgeClick(id: string) {
  store.selectJoin(id)
}

function handleWherePanelClick() {
  // 确保右侧面板不会被收起
  rightPanelCollapsed.value = false
  // 强制设置为查询条件tab
  store.setActiveTab('where')
}

function handleGroupPanelClick() {
  // 确保右侧面板不会被收起
  rightPanelCollapsed.value = false
  // 强制设置为分组tab
  store.setActiveTab('groupBy')
}

function handleOrderPanelClick() {
  // 确保右侧面板不会被收起
  rightPanelCollapsed.value = false
  // 强制设置为排序tab
  store.setActiveTab('orderBy')
}

function handleNodesChange() {
  emit('change', store.exportToQueryConfig())
}

function handleConnect(conn: { source: string; target: string; sourceField: string; targetField: string }) {
  emit('change', store.exportToQueryConfig())
}

function handleMaxTablesReached() {
  ElMessage.warning('最多只能选择两张表进行关联查询')
}

function handlePaneClick() {
  store.selectTable(null)
  store.selectJoin(null)
  store.setActiveTab('basic')
}

function handleUpdateTableField({ alias, field, type, value }: { alias: string; field: string; type: string; value: any }) {
  const table = store.tables.find(t => t.alias === alias)
  if (!table) return
  if (type === 'alias') table.fieldAliases[field] = value
  else if (type === 'aggregation') table.fieldAggregations[field] = value
  emit('change', store.exportToQueryConfig())
}

function handleUpdateJoinType({ id, type }: { id: string; type: string }) {
  const join = store.joins.find(j => j.id === id)
  if (join) join.joinType = type as any
  emit('change', store.exportToQueryConfig())
}

function handleAddJoin() {
  if (store.joins.length === 0) {
    ElMessage.warning('请先在画布上连接两张表创建第一个关联')
    return
  }
  if (store.tables.length < 2) {
    ElMessage.warning('请先添加两张表')
    return
  }
  // 复制第一个join，清空字段
  const firstJoin = store.joins[0]
  const newJoin = store.addJoin({
    sourceTable: firstJoin.sourceTable,
    targetTable: firstJoin.targetTable,
    sourceField: '',
    targetField: '',
    joinType: firstJoin.joinType || 'INNER'
  })
  if (newJoin) {
    emit('change', store.exportToQueryConfig())
  }
}

async function handleSmartRecommend(tableName: string) {
  try {
    const res = await sqlApi.getSmartRecommend(tableName, store.allTableNames, store.dataSourceId)
    return res.data
  } catch {
    return null
  }
}

function handleCanvasToSql() {
  const sql = store.generateSql()
  store.setSql(sql)
  ElMessage.success('已转换为 SQL')
}

async function handleSqlToCanvas() {
  if (!store.sqlText.trim()) {
    ElMessage.warning('请先输入 SQL 语句')
    return
  }
  try {
    await store.parseSqlToCanvas(store.sqlText)
    ElMessage.success('已转换为画布')
  } catch (e: any) {
    ElMessage.error('SQL 解析失败: ' + (e.message || '格式不支持'))
  }
}

defineExpose({
  getConfig: () => store.exportToQueryConfig(),
  setConfig: (config: any) => store.loadFromQueryConfig(config),
  getSql: () => store.sqlText,
  setSql: (sql: string) => store.setSql(sql),
  validateConfig: () => store.validateConfig()
})
</script>

<style scoped>
.query-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #1a1a2e;
  color: #eee;
  border-radius: 8px;
  overflow: hidden;
}
.qe-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #16213e;
  border-bottom: 1px solid #0f3460;
  gap: 8px;
  height: 40px;
  box-sizing: border-box;
  flex-shrink: 0;
}
.qe-toolbar-left {
  display: flex;
  align-items: center;
}
.mode-badge {
  font-size: 12px;
  font-weight: 500;
  color: #93c5fd;
  background: rgba(59, 130, 246, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.3);
  padding: 4px 12px;
  border-radius: 4px;
  user-select: none;
}
.qe-toolbar-center {
  flex: 1;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

.qe-body {
  display: flex;
  flex: 1;
  overflow: hidden;
  position: relative;
  min-height: 0;
  height: 100%;
}
.qe-body.mode-sql .qe-left-panel,
.qe-body.mode-sql .qe-right-panel {
  display: none !important;
}

.qe-left-panel {
  flex-shrink: 0;
  border-right: 1px solid #0f3460;
  overflow: hidden;
  background: #16213e;
  display: flex;
  flex-direction: column;
}

.qe-left-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px;
  background: #1e3a5f;
  border-bottom: 1px solid #0f3460;
  flex-shrink: 0;
}

.qe-left-header .panel-title {
  font-size: 12px;
  font-weight: 500;
  color: #93c5fd;
  line-height: 1.2;
}

.qe-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  min-width: 0;
  min-height: 0;
  border-bottom: 1px solid #0f3460;
}

.canvas-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sql-wrapper {
  flex: 1;
  min-height: 150px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #16213e;
}

.sql-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px;
  background: #1e3a5f;
  border-bottom: 1px solid #0f3460;
  flex-shrink: 0;
}

.sql-header .panel-title {
  font-size: 12px;
  font-weight: 500;
  color: #93c5fd;
  line-height: 1.2;
}

.sql-header .collapse-btn {
  padding: 2px 6px;
  color: #93c5fd;
  background: transparent;
  border: none;
}

.sql-header .collapse-btn:hover {
  background: rgba(59, 130, 246, 0.2);
}

/* 拖拽条样式 */
.qe-resize-bar {
  background: #0f3460;
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s;
  z-index: 100;
  user-select: none;
  position: relative;
  flex-shrink: 0;
}

.qe-resize-bar:hover {
  background: #1e40af;
}

.qe-resize-bar.vertical {
  height: 6px;
  width: 100%;
  cursor: row-resize;
  margin: 0;
  padding: 0;
}

.qe-resize-bar.vertical:hover {
  background: #3b82f6;
}

.qe-resize-bar.horizontal {
  width: 6px;
}

.resize-icon {
  font-size: 12px;
  color: #60a5fa;
  opacity: 0.6;
  line-height: 1;
}

.qe-resize-bar:hover .resize-icon {
  opacity: 1;
}

/* 右侧面板样式 */
.qe-right-panel {
  width: 380px;
  flex-shrink: 0;
  border-left: 1px solid #0f3460;
  overflow: hidden;
  background: #16213e;
  display: flex;
  flex-direction: column;
}

.qe-right-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px;
  background: #1e3a5f;
  border-bottom: 1px solid #0f3460;
  flex-shrink: 0;
}

.qe-right-header .panel-title {
  font-size: 12px;
  font-weight: 500;
  color: #93c5fd;
  line-height: 1.2;
}

.qe-right-header .collapse-btn {
  padding: 2px 6px;
  color: #93c5fd;
  background: transparent;
  border: none;
}

.qe-right-header .collapse-btn:hover {
  background: rgba(59, 130, 246, 0.2);
}

/* 隐藏按钮样式 - 统一深色主题 */
.qe-right-header :deep(.el-button) {
  background: rgba(15, 52, 96, 0.6);
  border: 1px solid #2d4a6f;
  color: #93c5fd;
}
.qe-right-header :deep(.el-button:hover) {
  background: rgba(59, 130, 246, 0.2);
  border-color: #3b82f6;
  color: #60a5fa;
}

.qe-right-content {
  flex: 1;
  min-height: calc(100vh - 280px);
  max-height: calc(100vh - 280px);
  overflow-y: auto;
  padding: 0px;
  box-sizing: border-box;
}

/* 右侧面板展开按钮 */
.qe-right-expand {
  width: 24px;
  background: #16213e;
  border: 1px solid #0f3460;
  border-right: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #60a5fa;
  transition: all 0.2s;
  border-radius: 4px 0 0 4px;
}

.qe-right-expand:hover {
  background: #1e3a5f;
  color: #93c5fd;
}

/* SQL面板展开按钮 */
.sql-expand {
  height: 24px;
  background: #16213e;
  border: 1px solid #0f3460;
  border-bottom: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #60a5fa;
  transition: all 0.2s;
  border-radius: 4px 4px 0 0;
}

.sql-expand:hover {
  background: #1e3a5f;
  color: #93c5fd;
}

/* 底部工具栏 */
.qe-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #16213e;
  border-top: 1px solid #0f3460;
  gap: 8px;
  height: 28px;
  box-sizing: border-box;
  flex-shrink: 0;
}

.qe-footer-left {
  display: flex;
  align-items: center;
}

.qe-footer-right {
  display: flex;
  align-items: center;
}

.footer-info {
  font-size: 12px;
  color: #94a3b8;
}

/* 顶部工具栏按钮样式 - 统一深色主题 */
.qe-toolbar-right :deep(.el-button) {
  background: rgba(15, 52, 96, 0.8);
  border: 1px solid #2d4a6f;
  color: #94a3b8;
}
.qe-toolbar-right :deep(.el-button:hover) {
  background: rgba(59, 130, 246, 0.2);
  border-color: #3b82f6;
  color: #60a5fa;
}
.qe-toolbar-right :deep(.el-button--primary) {
  background: rgba(59, 130, 246, 0.6);
  border-color: #3b82f6;
  color: #e2e8f0;
}
.qe-toolbar-right :deep(.el-button--primary:hover) {
  background: rgba(59, 130, 246, 0.8);
}
</style>
