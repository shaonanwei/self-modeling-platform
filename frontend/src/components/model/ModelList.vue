<template>
  <div class="model-list">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-info">
          <h2>模型列表</h2>
        </div>
      </div>
    </div>

    <!-- Content Wrapper -->
    <div class="content-wrapper">
      <!-- Search + Actions Bar -->
      <div class="content-card">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-form :inline="true" :model="searchForm">
              <el-form-item label="模型名称">
                <el-input
                  v-model="searchForm.modelName"
                  placeholder="请输入模型名称"
                  clearable
                  style="width: 180px"
                  @keyup.enter="handleSearch"
                />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
                  <el-option label="启用" :value="1" />
                  <el-option label="禁用" :value="0" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSearch">
                  <el-icon><Search /></el-icon> 搜索
                </el-button>
                <el-button @click="handleReset">重置</el-button>
              </el-form-item>
            </el-form>
          </div>
          <div class="toolbar-right">
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon> 新建模型
            </el-button>
          </div>
        </div>
      </div>

      <!-- Table -->
      <div class="table-wrapper">
        <el-table :data="models" v-loading="loading" stripe height="100%">
          <el-table-column prop="modelCode" label="模型编码" width="180" resizable />
          <el-table-column prop="modelName" label="模型名称" min-width="200" show-overflow-tooltip resizable />
          <el-table-column prop="modelType" label="模型分类" width="120" resizable>
            <template #default="{ row }">
              {{ modelTypeLabel(row.modelType) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="70" align="center" />
          <el-table-column prop="creator" label="创建人" width="100" resizable />
          <el-table-column prop="createTime" label="创建时间" width="180" />
          <el-table-column label="操作" width="400" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button size="small" type="primary" @click="handleConfig(row)">配置</el-button>
              <el-button size="small" type="success" @click="handlePublish(row)">
                {{ row.status === 1 ? '禁用' : '发布' }}
              </el-button>
              <el-button size="small" @click="handleCopy(row)">复制</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Pagination -->
      <div class="pagination">
        <el-pagination
          :current-page="pagination.pageNum"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- Create/Edit Dialog: append-to-body 挂载到 body，destroy-on-close 确保每次打开都是干净状态 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      :close-on-click-modal="false"
      append-to-body
      destroy-on-close
      @close="onDialogClose"
    >
      <el-form :model="form" :rules="formRules" ref="dialogFormRef" label-width="100px">
        <el-form-item label="模型编码" prop="modelCode">
          <el-input
            v-model="form.modelCode"
            :placeholder="'留空自动生成'"
          />
          <span class="input-hint">只允许英文字母和数字</span>
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="form.modelType" placeholder="请选择类型">
            <el-option label="业务模型" value="BUSINESS" />
            <el-option label="技术模型" value="TECHNICAL" />
            <el-option label="数据模型" value="DATA" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源" prop="dataSource">
          <el-select v-model="form.dataSource" placeholder="请选择数据源">
            <el-option label="SQLite" value="sqlite" />
            <el-option label="PostgreSQL" value="postgres" />
            <el-option label="Hive" value="hive" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型描述" prop="modelDesc">
          <el-input v-model="form.modelDesc" type="textarea" :rows="3" placeholder="请输入模型描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelApi } from '@/api/modelApi'
import { modelTypeLabel } from '@/utils/formatters'
import type { ModelInfo } from '@/types/model'

const router = useRouter()

// ========== 列表状态（与弹窗完全独立） ==========
const loading = ref(false)
const models = ref<ModelInfo[]>([])
const searchForm = reactive({
  modelName: '',
  status: undefined as number | undefined
})
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// ========== 弹窗状态（独立命名空间） ==========
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | undefined>()
const submitLoading = ref(false)
const dialogFormRef = ref()

const form = reactive({
  modelCode: '',
  modelName: '',
  modelType: 'BUSINESS',
  dataSource: 'postgres',
  modelDesc: '',
  status: 1
})

const formRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelCode: [
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (!value && dialogMode.value === 'create') {
          callback() // 新建模式允许空
        } else if (value && !/^[A-Za-z0-9]+$/.test(value)) {
          callback(new Error('模型编码只允许英文字母和数字'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// ========== 列表操作 ==========
const loadModels = async () => {
  loading.value = true
  try {
    const res = await modelApi.pageModels({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      ...searchForm
    })
    models.value = res.data?.list ?? []
    pagination.total = res.data?.total ?? 0
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '加载失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadModels()
}

const handleReset = () => {
  searchForm.modelName = ''
  searchForm.status = undefined
  pagination.pageNum = 1
  loadModels()
}

// ========== 弹窗操作 ==========
/** 新建模型 - 先重置表单再打开弹窗 */
const handleCreate = () => {
  dialogMode.value = 'create'
  editingId.value = undefined
  dialogTitle.value = '新建模型'
  // 1. 先重置表单
  resetForm()
  // 2. 再打开弹窗
  dialogVisible.value = true
}

/** 编辑模型 - 先填充表单再打开弹窗（关键：时序不能反！） */
const handleEdit = (row: ModelInfo & { dataSource?: string }) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  dialogTitle.value = '编辑模型信息'
  // 1. 先填充表单数据
  form.modelCode = row.modelCode
  form.modelName = row.modelName
  form.modelType = row.modelType
  form.dataSource = row.dataSource || 'postgres'
  form.modelDesc = row.modelDesc || ''
  form.status = row.status
  // 2. 再打开弹窗
  dialogVisible.value = true
  // 3. 清除验证状态
  nextTick(() => dialogFormRef.value?.clearValidate())
}

/** 配置步骤 */
const handleConfig = (row: ModelInfo) => {
  router.push(`/models/${row.id}/edit`)
}

/** 复制模型 */
const handleCopy = async (row: ModelInfo) => {
  try {
    await modelApi.copyModel(row.id)
    ElMessage.success('复制成功')
    await loadModels()
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '复制失败'
    ElMessage.error(message)
  }
}

/** 发布/禁用模型 */
const handlePublish = async (row: ModelInfo) => {
  try {
    const targetStatus = row.status === 1 ? 0 : 1
    await modelApi.updateStatus(row.id, targetStatus)
    ElMessage.success(targetStatus === 1 ? '发布成功' : '禁用成功')
    await loadModels()
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '操作失败'
    ElMessage.error(message)
  }
}

/** 删除模型 */
const handleDelete = async (row: ModelInfo) => {
  try {
    await ElMessageBox.confirm(
      `确定删除模型"${row.modelName}"吗？删除后无法恢复。`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await modelApi.deleteModel(row.id)
    ElMessage.success('删除成功')
    await loadModels()
  } catch (e: unknown) {
    if (e !== 'cancel' && e instanceof Error) {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

// ========== 弹窗表单 ==========
const resetForm = () => {
  form.modelCode = ''
  form.modelName = ''
  form.modelType = 'BUSINESS'
  form.dataSource = 'postgres'
  form.modelDesc = ''
  form.status = 1
}

const onDialogClose = () => {
  // 清空表单引用，防止残留
  dialogFormRef.value = null
}

const handleCancel = (event: MouseEvent) => {
  event.stopPropagation()
  event.preventDefault()
  dialogVisible.value = false
}

const handleSubmit = async () => {
  if (submitLoading.value) return
  try {
    await dialogFormRef.value?.validate()
  } catch {
    return
  }

  submitLoading.value = true
  try {
    const { modelCode, modelName, modelType, dataSource, modelDesc, status } = form
    const submitData: Record<string, any> = {
      modelName,
      modelType,
      dataSource,
      modelDesc,
      status
    }
    if (modelCode) {
      submitData.modelCode = modelCode
    }
    if (dialogMode.value === 'create') {
      await modelApi.createModel(submitData)
      ElMessage.success('创建成功')
    } else if (editingId.value !== undefined) {
      await modelApi.updateModel(editingId.value, submitData)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await loadModels()
  } catch (e: unknown) {
    if (e instanceof Error && e.message) {
      ElMessage.error(e.message)
    }
  } finally {
    submitLoading.value = false
  }
}

// ========== 分页 ==========
const onPageChange = (val: number) => {
  pagination.pageNum = val
  loadModels()
}

const onSizeChange = (val: number) => {
  pagination.pageSize = val
  pagination.pageNum = 1
  loadModels()
}

onMounted(() => {
  loadModels()
})
</script>

<style scoped>
.model-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0;
  gap: 0;
  background: #f5f7fa;
}

.page-header {
  flex-shrink: 0;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: none;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-info h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 0;
}

.header-subtitle {
  margin: 4px 0 0;
  font-size: 14px;
  color: #909399;
}

.content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px 24px;
  gap: 20px;
  min-height: 0;
  overflow: hidden;
}

.content-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  flex-shrink: 0;
  border-bottom: 1px solid #f0f0f0;
}

.toolbar-left {
  flex: 1;
  min-width: 0;
}

.toolbar-right {
  flex-shrink: 0;
  margin-left: 16px;
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
  padding: 16px 20px;
  background: #fff;
  border-radius: 12px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.input-hint {
  font-size: 12px;
  color: #909399;
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

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: #fafbfc;
}

:deep(.el-table__body tr:hover > td.el-table__cell) {
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

:deep(.el-button--success) {
  background: #67c23a;
  border: none;
  box-shadow: none;
}

:deep(.el-button--danger) {
  background: #f56c6c;
  border: none;
  box-shadow: none;
}

:deep(.el-button--default) {
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  color: #606266;
  transition: all 0.3s ease;
}

:deep(.el-button--default:hover) {
  border-color: #667eea;
  color: #667eea;
  background: #fff;
}

/* Input styles */
:deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  transition: all 0.3s ease;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c0c4cc inset;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #667eea inset;
}

/* Select styles */
:deep(.el-select .el-input__wrapper) {
  border-radius: 8px;
}

/* Tag styles */
:deep(.el-tag--success) {
  background: #f0f9eb;
  border: none;
  color: #67c23a;
}

:deep(.el-tag--danger) {
  background: #fef0f0;
  border: none;
  color: #f56c6c;
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

/* Form styles */
:deep(.el-form-item__label) {
  color: #606266;
  font-weight: 500;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

@media (max-width: 768px) {
  .model-list {
    padding: 0;
    gap: 0;
  }

  .content-wrapper {
    padding: 16px;
    gap: 16px;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
    padding: 16px;
  }

  .toolbar-right {
    margin-left: 0;
    align-self: flex-end;
  }

  .pagination {
    padding: 16px;
    justify-content: center;
  }

  :deep(.el-table) {
    font-size: 13px;
  }
}
</style>
