<template>
  <div class="model-editor">
    <div class="editor-header">
      <div class="header-left">
        <el-button @click="goBack" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="header-info">
          <h2>{{ modelName }}</h2>
          <p class="header-subtitle">模型步骤管理</p>
        </div>
      </div>
    </div>

    <div class="editor-content">
      <div class="content-tabs">
        <div class="tabs-nav">
          <button 
            v-for="tab in tabs" 
            :key="tab.name"
            :class="['tab-item', { active: activeTab === tab.name }]"
            @click="activeTab = tab.name"
          >
            <el-icon><component :is="tab.icon" /></el-icon>
            {{ tab.label }}
          </button>
        </div>
        
        <div class="tabs-content">
          <!-- 步骤配置 -->
          <div v-show="activeTab === 'steps'" class="steps-panel">
            <!-- 搜索 + Actions Bar -->
            <div class="content-card">
              <div class="panel-toolbar">
                <div class="toolbar-left">
                  <el-button type="primary" size="small" @click="showAppendDialog">
                    添加
                  </el-button>
                  <el-button type="warning" size="small" :disabled="!selectedStep" @click="handleInsertAfterSelected">
                    插入
                  </el-button>
                  <el-button type="primary" size="small" :disabled="!selectedStep" @click="handleEditSelectedStep" class="edit-btn">
                    编辑
                  </el-button>
                  <el-button type="danger" size="small" :disabled="!selectedStep" @click="handleDeleteSelectedStep"> 
                    删除</el-button>
                </div>
              </div>
            </div>

            <!-- Table -->
            <div class="table-wrapper">
              <el-table
                :data="paginatedSteps"
                style="width: 100%"
                border
                stripe
                highlight-current-row
                @current-change="handleSelectRow"
                :row-class-name="tableRowClassName"
                height="100%"
              >
                <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
                <el-table-column label="类型" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="useStepTypeTag(row.stepType)">
                      {{ row.stepType }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="stepName" label="步骤名称" min-width="100" />
                <el-table-column prop="stepDesc" label="描述" min-width="150" show-overflow-tooltip />
                <el-table-column prop="resultTableName" label="结果表名" min-width="100" show-overflow-tooltip />
                <el-table-column label="执行状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag
                      v-if="row.executeStatus"
                      size="small"
                      :type="getExecuteStatusType(row.executeStatus)"
                      :class="row.executeStatus === 'failed' ? 'clickable-tag' : ''"
                      @click="row.executeStatus === 'failed' && handleViewExecuteLog(row)"
                    >
                      {{ getExecuteStatusLabel(row.executeStatus) }}
                    </el-tag>
                    <span v-else style="color: #909399;">-</span>
                  </template>
                </el-table-column>
                <el-table-column label="执行开始时间" width="160" align="center">
                  <template #default="{ row }">
                    <span v-if="row.executeStartTime">{{ formatDateTime(row.executeStartTime) }}</span>
                    <span v-else style="color: #909399;">-</span>
                  </template>
                </el-table-column>
                <el-table-column label="执行结束时间" width="160" align="center">
                  <template #default="{ row }">
                    <span v-if="row.executeEndTime">{{ formatDateTime(row.executeEndTime) }}</span>
                    <span v-else style="color: #909399;">-</span>
                  </template>
                </el-table-column>
                <el-table-column label="修改时间" width="160" align="center">
                  <template #default="{ row }">
                    <span>{{ formatDateTime(row.updateTime) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" type="success" @click="handleExecute(row)">执行</el-button>
                    <el-button size="small" type="warning" @click="handleViewResult(row)">查看结果</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <!-- Pagination -->
            <div class="pagination">
              <el-button @click="handleRefreshSteps" :loading="stepsLoading" class="refresh-btn" text>
                <el-icon v-if="!stepsLoading"><Refresh /></el-icon>
              </el-button>
              <el-pagination
                v-model:current-page="currentPage"
                v-model:page-size="pageSize"
                :page-sizes="[10, 20, 50]"
                :total="middleSteps.length"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="handlePageChange"
                @current-change="handlePageChange"
              />
            </div>
          </div>

          <!-- 流程图 -->
          <div v-if="activeTab === 'flow'" class="tab-panel">
            <div class="flow-panel">
              <FlowChart :model-id="modelId" :key="stepsUpdatedAt" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Step Edit Dialog: 移到 el-tabs 外面，配合 append-to-body 彻底隔离 -->
    <StepEditDialog
      v-model:visible="editDialogVisible"
      :model-id="modelId"
      :edit-step="editingStep"
      :insert-after-step-id="insertAfterStepId"
      :insert-after-step-name="insertAfterStepName"
      :is-append-mode="isAppendMode"
      :model-data-source="modelDataSource"
      @success="loadSteps"
    />

    <!-- Result Dialog -->
    <el-dialog
      v-model="resultDialogVisible"
      :title="resultDialogTitle"
      width="80%"
      top="5vh"
      append-to-body
    >
      <div v-if="resultTableData.length > 0" style="max-height: 60vh; overflow: auto;">
        <el-table :data="resultTableData" border stripe max-height="500">
          <el-table-column
            v-for="column in resultTableColumns"
            :key="column"
            :prop="column"
            :label="column"
            min-width="150"
            show-overflow-tooltip
          />
        </el-table>
      </div>
      <div v-else style="text-align: center; padding: 40px; color: #909399;">
        暂无数据
      </div>
      <template #footer>
        <el-pagination
          v-if="resultTotal > 0"
          v-model:current-page="resultCurrentPage"
          :page-size="resultPageSize"
          :total="resultTotal"
          layout="total, prev, pager, next"
          @current-change="handleResultPageChange"
        />
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelApi } from '@/api/modelApi'
import { useStepTypeTag } from '@/composables/useStepTypes'
import type { ModelStep } from '@/types/model'
import StepEditDialog from './StepEditDialog.vue'
import FlowChart from './FlowChart.vue'
import { List, Share, VideoPlay, Refresh } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const modelId = computed(() => Number(route.params.id))

const steps = ref<ModelStep[]>([])
const activeTab = ref('steps')
const selectedStep = ref<ModelStep | null>(null)
const modelName = ref('')
const modelDataSource = ref('sqlite')
const stepsUpdatedAt = ref(Date.now())

const tabs = [
  { name: 'steps', label: '步骤列表', icon: List },
  { name: 'flow', label: '流程图', icon: Share }
]

// Dialog
const editDialogVisible = ref(false)
const editingStep = ref<ModelStep | null>(null)
const insertAfterStepId = ref<number | null>(null)
const insertAfterStepName = ref('')
const isAppendMode = ref(false)

// Result Dialog
const resultDialogVisible = ref(false)
const resultDialogTitle = ref('')
const resultTableData = ref<any[]>([])
const resultTableColumns = ref<string[]>([])
const resultTotal = ref(0)
const resultCurrentPage = ref(1)
const resultPageSize = ref(10)
const currentViewStepId = ref<number | null>(null)

// Auto refresh
const stepsLoading = ref(false)
let autoRefreshTimer: number | null = null
const AUTO_REFRESH_INTERVAL = 30000 // 30秒

// Computed
const middleSteps = computed(() =>
  steps.value.filter(s => s.stepType !== 'start' && s.stepType !== 'end')
)

// 分页
const currentPage = ref(1)
const pageSize = ref(10)

const paginatedSteps = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return middleSteps.value.slice(start, start + pageSize.value)
})

const handlePageChange = () => {
  selectedStep.value = null
}

const tableRowClassName = ({ row }: { row: ModelStep }) => {
  if (selectedStep.value && row.id === selectedStep.value.id) {
    return 'selected-row'
  }
  return ''
}

const handleSelectRow = (row: ModelStep | null) => {
  selectedStep.value = row
}

const getExecuteStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  switch (status) {
    case 'success':
      return 'success'
    case 'failed':
      return 'danger'
    case 'running':
      return 'warning'
    case 'pending':
      return 'info'
    default:
      return 'info'
  }
}

const getExecuteStatusLabel = (status: string): string => {
  switch (status) {
    case 'success':
      return '成功'
    case 'failed':
      return '失败'
    case 'running':
      return '执行中'
    case 'pending':
      return '待执行'
    default:
      return status
  }
}

const handleViewExecuteLog = (row: ModelStep) => {
  if (row.executeLog) {
    ElMessageBox.alert(row.executeLog, `步骤「${row.stepName}」执行日志`, {
      confirmButtonText: '关闭',
      customClass: 'execute-log-dialog'
    })
  }
}

const formatDateTime = (datetime: string): string => {
  if (!datetime) return '-'
  try {
    const date = new Date(datetime)
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  } catch {
    return datetime
  }
}

const loadModelInfo = async () => {
  try {
    const res = await modelApi.getModel(modelId.value)
    if (res.data) {
      modelName.value = res.data.modelName || '未知模型'
      modelDataSource.value = res.data.dataSource || 'master'
    }
  } catch {
    modelName.value = '未知模型'
    modelDataSource.value = 'master'
  }
}

const loadSteps = async () => {
  try {
    const res = await modelApi.getSteps(modelId.value)
    if (res.data) {
      steps.value = res.data
      stepsUpdatedAt.value = Date.now()
      currentPage.value = 1
      if (selectedStep.value) {
        const found = steps.value.find(s => s.id === selectedStep.value!.id)
        if (!found) selectedStep.value = null
      }
    }
  } catch {
    // 静默处理
  }
}

const showEditDialog = (step: ModelStep) => {
  editingStep.value = step
  insertAfterStepId.value = null
  insertAfterStepName.value = ''
  isAppendMode.value = false
  editDialogVisible.value = true
}

const handleEditSelectedStep = () => {
  if (!selectedStep.value) {
    ElMessage.warning('请先选择要编辑的步骤')
    return
  }
  editingStep.value = selectedStep.value
  insertAfterStepId.value = null
  insertAfterStepName.value = ''
  isAppendMode.value = false
  editDialogVisible.value = true
}

const handleInsertAfterSelected = () => {
  if (!selectedStep.value) return
  editingStep.value = null
  insertAfterStepId.value = selectedStep.value.id
  insertAfterStepName.value = selectedStep.value.stepName
  isAppendMode.value = false
  editDialogVisible.value = true
}

const showAppendDialog = () => {
  editingStep.value = null
  insertAfterStepId.value = null
  insertAfterStepName.value = ''
  isAppendMode.value = true
  editDialogVisible.value = true
}

const handleDeleteSelectedStep = async () => {
  if (!selectedStep.value) {
    ElMessage.warning('请先选择要删除的步骤')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除步骤「${selectedStep.value.stepName}」吗？删除后无法恢复。`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning', center: true }
    )
    await modelApi.deleteStep(modelId.value, selectedStep.value.id)
    ElMessage.success('删除成功')
    selectedStep.value = null
    await loadSteps()
  } catch (e: unknown) {
    if (e !== 'cancel' && e instanceof Error) {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

const handleExecute = async (row: ModelStep) => {
  try {
    const res = await modelApi.executeStep(modelId.value, row.id)
    if (res.code === 0 || res.code === 200) {
      ElMessage.success(`正在执行步骤「${row.stepName}」...`)
      await loadSteps()
    } else {
      ElMessage.error(res.message || '执行失败')
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '执行失败')
  }
}

const handleViewResult = async (row: ModelStep, pageNum: number = 1) => {
  try {
    currentViewStepId.value = row.id
    resultCurrentPage.value = pageNum
    const res = await modelApi.getStepResult(modelId.value, row.id, pageNum, resultPageSize.value)
    if (res.data) {
      const resultData = res.data.list

      if (Array.isArray(resultData) && resultData.length > 0) {
        resultDialogTitle.value = `步骤「${row.stepName}」执行结果`
        resultTableData.value = resultData
        resultTableColumns.value = Object.keys(resultData[0])
        resultTotal.value = res.data.total
        resultDialogVisible.value = true
      } else if (Array.isArray(resultData) && resultData.length === 0) {
        ElMessage.warning('执行结果为空')
      } else {
        ElMessage.warning('执行结果格式不正确')
      }
    } else {
      ElMessage.warning('暂无执行结果，请先执行该步骤')
    }
  } catch {
    ElMessage.warning('暂无执行结果，请先执行该步骤')
  }
}

const handleResultPageChange = (page: number) => {
  if (currentViewStepId.value) {
    const step = steps.value.find(s => s.id === currentViewStepId.value)
    if (step) {
      handleViewResult(step, page)
    }
  }
}

const goBack = () => {
  router.push('/models')
}

onMounted(async () => {
  await loadModelInfo()
  await loadSteps()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})

const startAutoRefresh = () => {
  if (autoRefreshTimer !== null) {
    return
  }
  autoRefreshTimer = window.setInterval(async () => {
    if (!resultDialogVisible.value && activeTab.value === 'steps') {
      await loadSteps()
    }
  }, AUTO_REFRESH_INTERVAL)
  console.info('启动步骤列表自动刷新，定时间隔：' + AUTO_REFRESH_INTERVAL + 'ms')
}

const stopAutoRefresh = () => {
  if (autoRefreshTimer !== null) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
    console.info('停止步骤列表自动刷新')
  }
}

const handleRefreshSteps = async () => {
  stepsLoading.value = true
  try {
    await loadSteps()
    ElMessage.success('刷新成功')
  } finally {
    stepsLoading.value = false
  }
}
</script>

<style scoped>
.model-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: none;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border: none;
  color: #606266;
  transition: all 0.3s ease;
  cursor: pointer;
}

.back-btn:hover {
  background: #e8ecf1;
  color: #303133;
  transform: none;
}

.header-info h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 0;
}

.header-subtitle {
  margin: 4px 0 0;
  font-size: 14px;
  color: #909399;
}

.editor-content {
  flex: 1;
  padding: 20px 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.content-tabs {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.tabs-nav {
  display: flex;
  border-bottom: 1px solid #f0f0f0;
  background: #fafbfc;
  padding: 0 8px;
  flex-shrink: 0;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 20px;
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 500;
  color: #909399;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.tab-item:hover {
  color: #667eea;
  background: transparent;
}

.tab-item.active {
  color: #667eea;
  background: #fff;
  border-bottom-color: #667eea;
}

.tabs-content {
  padding: 16px;
  flex: 1;
  overflow: hidden;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.tab-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.steps-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 12px;
}

.content-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  flex-shrink: 0;
}

.toolbar-left {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 12px;
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.pagination {
  padding: 10px 16px;
  background: #fff;
  border-radius: 12px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.pagination .refresh-btn {
  margin-right: 8px;
  font-size: 18px;
}

.clickable-tag {
  cursor: pointer;
  text-decoration: underline;
}

.clickable-tag:hover {
  opacity: 0.8;
}

:deep(.execute-log-dialog) {
  width: 60% !important;
  max-width: 60vw !important;
}

:deep(.execute-log-dialog .el-message-box__content) {
  white-space: pre-wrap;
  word-break: break-all;
}

.flow-panel {
  height: calc(100vh - 260px);
  min-height: 500px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafbfc;
  border-radius: 8px;
  padding: 20px;
}

/* Table styles */
:deep(.el-table) {
  border-radius: 0;
}

:deep(.el-table th.el-table__cell) {
  background: #fafbfc;
  color: #606266;
  font-weight: 600;
  font-size: 14px;
  padding: 14px 0;
}

:deep(.el-table td.el-table__cell) {
  padding: 14px 0;
  color: #303133;
}

:deep(.el-table__row.selected-row) {
  background: #f0f7ff !important;
}

:deep(.el-table__row:hover) {
  cursor: pointer;
}

:deep(.el-table__row:hover > td.el-table__cell) {
  background: #f0f7ff;
}

/* Button styles */
:deep(.el-button--small) {
  padding: 8px 14px;
  border-radius: 8px;
  font-weight: 500;
}

:deep(.el-button--primary) {
  background: #667eea;
  border: none;
  box-shadow: none;
}

:deep(.el-button--primary:hover) {
  transform: none;
  box-shadow: none;
  background: #764ba2;
}

:deep(.el-button--warning) {
  border: none;
  box-shadow: none;
}

:deep(.el-button--success) {
  background: #67c23a;
  border: none;
  box-shadow: none;
}

:deep(.el-button--success:hover) {
  transform: none;
  box-shadow: none;
  background: #85ce61;
}

:deep(.el-button--default) {
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  color: #606266;
  transition: all 0.3s ease;
}

:deep(.edit-btn) {
  background: #9b59b6 !important;
  border-color: #9b59b6 !important;
}

:deep(.edit-btn:hover) {
  background: #8e44ad !important;
  border-color: #8e44ad !important;
}

:deep(.edit-btn.is-disabled) {
  background: #9b59b6 !important;
  border-color: #9b59b6 !important;
  opacity: 0.5 !important;
  cursor: not-allowed !important;
}

:deep(.el-button--default:hover) {
  border-color: #667eea;
  color: #667eea;
  background: #fff;
}

/* Tag styles */
:deep(.el-tag--small) {
  border-radius: 6px;
  padding: 4px 10px;
  font-weight: 500;
}

/* Dialog styles */
:deep(.el-dialog) {
  border-radius: 12px;
}

:deep(.el-dialog__header) {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

:deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

:deep(.el-dialog__body) {
  padding: 24px;
}

:deep(.el-dialog__footer) {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
}
</style>
