<template>
  <el-dialog
    v-model="visible"
    width="100vw"
    height="100vh"
    :close-on-click-modal="false"
    top="0"
    append-to-body
    destroy-on-close
    class="step-edit-fullscreen-dialog"
    wrapper-class="step-edit-fullscreen-wrapper"
  >
    <template #header>
      <div class="custom-dialog-header">
        <div class="title-badge">{{ dialogTitle }}</div>
      </div>
    </template>
    <div class="wizard-container">
      <div class="steps-wrapper">
        <div class="step-navigator">
          <div 
            class="step-item" 
            :class="{ active: currentStep === 0, completed: currentStep > 0 }"
            @click="currentStep = 0"
          >
            <div class="step-number">
              <span v-if="currentStep <= 0">1</span>
              <el-icon v-else><Check /></el-icon>
            </div>
            <div class="step-info">
              <div class="step-title">基本信息</div>
              <div class="step-desc">配置步骤信息</div>
            </div>
          </div>
          <div class="step-line" :class="{ active: currentStep > 0 }"></div>
          <div 
            class="step-item" 
            :class="{ active: currentStep === 1, completed: currentStep > 1 }"
            @click="currentStep = 1"
          >
            <div class="step-number">
              <span v-if="currentStep <= 1">2</span>
              <el-icon v-else><Check /></el-icon>
            </div>
            <div class="step-info">
              <div class="step-title">SQL配置</div>
              <div class="step-desc">数据源：{{ modelDataSource }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="wizard-content">
        <transition name="fade-slide" mode="out-in">
          <div key="step-0" v-if="currentStep === 0" class="step-panel">
            <div class="basic-config-container">
                  
              <div class="form-card">
                <el-form 
                  :model="form" 
                  :rules="rules" 
                  ref="formRef" 
                  label-width="100px"
                  class="step-form"
                >
                  <el-form-item label="步骤编码" prop="stepCode">
                    <el-input 
                      v-model="form.stepCode" 
                      placeholder="留空自动生成" 
                      disabled
                      class="custom-input"
                    >
                      <template #prefix>
                        <el-icon><Key /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>
                  
                  <el-form-item label="步骤名称" prop="stepName">
                    <el-input 
                      v-model="form.stepName" 
                      placeholder="请输入步骤名称" 
                      maxlength="100" 
                      show-word-limit
                      class="custom-input"
                    >
                      <template #prefix>
                        <el-icon><Edit /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>
                  
                  <el-form-item label="步骤描述">
                    <el-input 
                      v-model="form.stepDesc" 
                      type="textarea" 
                      :rows="6" 
                      placeholder="请输入步骤描述（可选）" 
                      maxlength="500" 
                      show-word-limit
                      class="custom-textarea"
                    />
                  </el-form-item>
                </el-form>
              </div>
            </div>
          </div>

          <div key="step-1" v-else class="step-panel">
            <div class="sql-config-container">
              
              <div class="query-editor-card">
                <QueryEditor
                  ref="queryEditorRef"
                  :initial-sql="sqlConfig.sqlStatement"
                  :initial-config="currentQueryConfig"
                  :data-source-id="modelDataSource"
                  @change="handleQueryConfigChange"
                />
              </div>
            </div>
          </div>
        </transition>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <div class="footer-actions">
          <el-button 
            v-if="currentStep === 1" 
            @click="handlePrev"
            class="btn-secondary"
          >
            <el-icon><ArrowLeft /></el-icon>
            上一步
          </el-button>
          <el-button 
            @click="handleCancel"
            class="btn-cancel"
          >
            取消
          </el-button>
          <el-button
            v-if="currentStep === 0"
            :loading="saveCloseLoading"
            @click="handleSaveAndClose"
            :disabled="!form.stepName.trim()"
            class="btn-save-close"
          >
            保存&关闭
           </el-button>
          <el-button
            v-if="currentStep === 0"
            type="primary"
            @click="handleNext"
            :disabled="!form.stepName.trim()"
            class="btn-primary"
          >
            下一步
            <el-icon><ArrowRight /></el-icon>
          </el-button>
          <el-button 
            v-else 
            type="primary" 
            :loading="submitLoading" 
            @click="handleSubmit"
            class="btn-primary"
          >
            保存
            <el-icon><CircleCheck /></el-icon>
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Document, 
  DataLine, 
  Check, 
  ArrowRight, 
  ArrowLeft, 
  CircleCheck, 
  Edit, 
  Key 
} from '@element-plus/icons-vue'
import type { ModelStep, InsertStepRequest } from '@/types/model'
import { modelApi } from '@/api/modelApi'
import { sqlApi } from '@/api/sqlApi'
import QueryEditor from '@/components/queryEditor/QueryEditor.vue'

const props = defineProps<{
  modelId: number
  visible: boolean
  editStep?: ModelStep | null
  insertAfterStepId?: number | null
  insertAfterStepName?: string
  isAppendMode?: boolean
  modelDataSource?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const formRef = ref()
const submitLoading = ref(false)
const saveCloseLoading = ref(false)
const queryEditorRef = ref<InstanceType<typeof QueryEditor>>()

const currentStep = ref(0)

const form = reactive({
  stepCode: '',
  stepName: '',
  stepDesc: ''
})

const rules = {
  stepName: [{ required: true, message: '请输入步骤名称', trigger: 'blur' }]
}

const sqlConfig = reactive({
  sqlStatement: ''
})

let currentQueryConfig: any = null

const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const isInsert = computed(() => !!props.insertAfterStepId)
const afterStepName = computed(() => props.insertAfterStepName || '')
const modelDataSource = computed(() => props.modelDataSource || 'sqlite')

const dialogTitle = computed(() => {
  if (props.isAppendMode) return '添加步骤'
  if (isInsert.value) return `在「${afterStepName.value}」之后插入步骤`
  return '编辑步骤'
})

function clearFormData() {
  form.stepCode = ''
  form.stepName = ''
  form.stepDesc = ''
  sqlConfig.sqlStatement = ''
  currentQueryConfig = null
  currentStep.value = 0
  nextTick(() => formRef.value?.clearValidate())
}

function fillForm(step: ModelStep) {
  form.stepCode = step.stepCode
  form.stepName = step.stepName
  form.stepDesc = step.stepDesc || ''

  try {
    const config = JSON.parse(step.stepConfig || '{}')
    sqlConfig.sqlStatement = step.sqlStatement || config.sqlStatement || ''
    currentQueryConfig = config.queryConfig || null
  } catch {
    sqlConfig.sqlStatement = ''
    currentQueryConfig = null
  }
}

watch(visible, async (val) => {
  if (val) {
    currentStep.value = 0
    if (props.editStep) {
      await nextTick()
      fillForm(props.editStep)
      await nextTick()
      if (currentQueryConfig && queryEditorRef.value) {
        queryEditorRef.value.setConfig(currentQueryConfig)
      }
    } else {
      clearFormData()
    }
  } else {
    clearFormData()
  }
})

function handleQueryConfigChange(config: any) {
  currentQueryConfig = config
  if (config?.sql) {
    sqlConfig.sqlStatement = config.sql
  }
}

const handleCancel = () => {
  visible.value = false
}

const handleNext = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  currentStep.value = 1
}

const handleSaveAndClose = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  if (saveCloseLoading.value) return
  saveCloseLoading.value = true

  try {
    const stepConfigObj = {
      configType: 'SQL' as const,
      sqlStatement: '',
      queryConfig: undefined
    }
    const stepConfigStr = JSON.stringify(stepConfigObj)

    if (props.isAppendMode) {
      const addData: Partial<ModelStep> = {
        stepName: form.stepName,
        stepType: 'task',
        stepDesc: form.stepDesc,
        stepConfig: stepConfigStr
      }
      await modelApi.addStep(props.modelId, addData)
      ElMessage.success('添加成功')
    } else if (props.insertAfterStepId) {
      const insertData: InsertStepRequest = {
        afterStepId: props.insertAfterStepId,
        stepName: form.stepName,
        stepType: 'task',
        stepDesc: form.stepDesc,
        stepConfig: stepConfigObj
      }
      await modelApi.insertStep(props.modelId, insertData)
      ElMessage.success('插入成功')
    } else if (props.editStep) {
      const updateData: Partial<ModelStep> = {
        stepName: form.stepName,
        stepType: 'task',
        stepDesc: form.stepDesc,
        stepConfig: stepConfigStr
      }
      await modelApi.updateStep(props.modelId, props.editStep.id, updateData)
      ElMessage.success('更新成功')
    } else {
      ElMessage.error('未知的操作步骤')
      return
    }

    visible.value = false
    nextTick(() => emit('success'))
  } catch (e: unknown) {
    if (e instanceof Error) {
      const axiosErr = e as { response?: { data?: { message?: string } } }
      const message = axiosErr.response?.data?.message || e.message || '操作失败'
      ElMessage.error(message)
    } else {
      ElMessage.error('操作失败')
    }
  } finally {
    saveCloseLoading.value = false
  }
}

const handlePrev = () => {
  currentStep.value = 0
}

const handleSubmit = async () => {
  if (submitLoading.value) return

  const validation = queryEditorRef.value?.validateConfig?.()
  if (validation && !validation.valid) {
    ElMessageBox.alert(
      validation.errors.map((e: string) => `• ${e}`).join('\n'),
      '配置校验失败',
      { type: 'warning', dangerouslyUseHTMLString: false, confirmButtonText: '知道了' }
    )
    submitLoading.value = false
    return
  }

  submitLoading.value = true
  try {
    const finalSql = queryEditorRef.value?.getSql() || sqlConfig.sqlStatement
    const finalConfig = queryEditorRef.value?.getConfig() || currentQueryConfig

    const sqlValidation = await sqlApi.validate(finalSql, modelDataSource.value)
    if (!sqlValidation.data.valid) {
      ElMessage.error('SQL 校验失败: ' + (sqlValidation.data.message || '仅支持单条只读 SELECT'))
      return
    }

    const stepConfigObj = {
      configType: 'SQL' as const,
      sqlStatement: finalSql,
      queryConfig: finalConfig || undefined
    }

    const stepConfigStr = JSON.stringify(stepConfigObj)

    if (props.isAppendMode) {
      const addData: Partial<ModelStep> = {
        stepName: form.stepName,
        stepType: 'task',
        stepDesc: form.stepDesc,
        stepConfig: stepConfigStr
      }
      await modelApi.addStep(props.modelId, addData)
      ElMessage.success('添加成功')
    } else if (props.insertAfterStepId) {
      const insertData: InsertStepRequest = {
        afterStepId: props.insertAfterStepId,
        stepName: form.stepName,
        stepType: 'task',
        stepDesc: form.stepDesc,
        stepConfig: stepConfigObj
      }
      await modelApi.insertStep(props.modelId, insertData)
      ElMessage.success('插入成功')
    } else if (props.editStep) {
      const updateData: Partial<ModelStep> = {
        stepName: form.stepName,
        stepType: 'task',
        stepDesc: form.stepDesc,
        stepConfig: stepConfigStr
      }
      await modelApi.updateStep(props.modelId, props.editStep.id, updateData)
      ElMessage.success('更新成功')
    } else {
      ElMessage.error('未知的操作步骤')
      return
    }

    visible.value = false
    nextTick(() => emit('success'))
  } catch (e: unknown) {
    if (e instanceof Error) {
      const axiosErr = e as { response?: { data?: { message?: string } } }
      const message = axiosErr.response?.data?.message || e.message || '操作失败'
      ElMessage.error(message)
    } else {
      ElMessage.error('操作失败')
    }
  } finally {
    submitLoading.value = false
  }
}
</script>

<style scoped>
.wizard-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  width: 100%;
  background: #f5f7fa;
}

.steps-wrapper {
  padding: 8px 20px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.step-navigator {
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 500px;
  margin: 0 auto;
  gap: 10px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
  background: #f8fafc;
  border: 2px solid transparent;
}

.step-item:hover {
  background: #f1f5f9;
}

.step-item.active {
  background: #667eea;
  border-color: #667eea;
}

.step-item.completed {
  background: #67c23a;
  border-color: #67c23a;
}

.step-number {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 12px;
  background: #e2e8f0;
  color: #64748b;
  transition: all 0.3s ease;
}

.step-item.active .step-number,
.step-item.completed .step-number {
  background: rgba(255, 255, 255, 0.25);
  color: #ffffff;
}

.step-number .el-icon {
  font-size: 12px;
}

.step-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.step-title {
  font-weight: 600;
  font-size: 12px;
  color: #1e293b;
  transition: color 0.3s ease;
}

.step-item.active .step-title,
.step-item.completed .step-title {
  color: #ffffff;
}

.step-desc {
  font-size: 10px;
  color: #94a3b8;
  transition: color 0.3s ease;
}

.step-item.active .step-desc,
.step-item.completed .step-desc {
  color: rgba(255, 255, 255, 0.8);
}

.step-line {
  width: 30px;
  height: 2px;
  background: #e2e8f0;
  border-radius: 2px;
  transition: background 0.3s ease;
}

.step-line.active {
  background: #667eea;
}

.wizard-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
  width: 100%;
  padding: 8px;
  box-sizing: border-box;
}

.step-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  min-height: 0;
  width: 100%;
}

.step-form-card {
  width: 100%;
  max-width: 720px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.card-header {
  background: #667eea;
  padding: 20px 28px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.header-icon {
  font-size: 28px;
  color: #ffffff;
  margin-bottom: 4px;
}

.header-title {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
}

.header-subtitle {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
}

.step-form {
  padding: 28px 32px;
}

.step-form :deep(.el-form-item) {
  margin-bottom: 24px;
}

.step-form :deep(.el-form-item__label) {
  font-weight: 600;
  font-size: 14px;
  color: #374151;
  padding-right: 16px;
}

.custom-input,
.custom-textarea {
  width: 100%;
}

.custom-input :deep(.el-input__wrapper) {
  border-radius: 8px;
  padding: 4px 12px;
  box-shadow: 0 0 0 1px #d1d5db inset;
  transition: all 0.3s ease;
}

.custom-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #909399 inset;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #667eea inset;
}

.custom-input :deep(.el-input__inner) {
  padding: 8px 4px;
  font-size: 14px;
}

.custom-textarea :deep(.el-textarea__inner) {
  border-radius: 8px;
  border-color: #d1d5db;
  padding: 14px 16px;
  font-size: 14px;
  transition: all 0.3s ease;
}

.custom-textarea :deep(.el-textarea__inner:hover) {
  border-color: #909399;
}

.custom-textarea :deep(.el-textarea__inner:focus) {
  border-color: #667eea;
}

.basic-config-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-card {
  flex: none;
  height: 70vh;
  min-height: 300px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.form-card .step-form {
  padding: 28px 32px;
  height: 100%;
  box-sizing: border-box;
}

.sql-config-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 0;
}

.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  padding: 16px 20px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left .header-icon {
  font-size: 32px;
  color: #667eea;
  margin-bottom: 0;
}

.header-texts {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-texts .header-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.header-texts .header-subtitle {
  font-size: 13px;
  color: #64748b;
}

.data-source-selector {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ds-label {
  font-weight: 600;
  font-size: 14px;
  color: #374151;
}

.custom-select {
  width: 220px;
}

.custom-select :deep(.el-select__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #d1d5db inset;
  transition: all 0.3s ease;
}

.custom-select :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #909399 inset;
}

.custom-select :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px #667eea inset;
}

.option-content {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #374151;
}

.option-content .el-icon {
  color: #667eea;
}

.query-editor-card {
  flex: 1;
  min-height: 0;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.query-editor-card :deep(.query-editor) {
  height: 100%;
  width: 100%;
}

.dialog-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 0;
  z-index: 100;
}

.footer-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  padding: 8px 16px;
  background: #ffffff;
  border-top: 1px solid #e5e7eb;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.btn-cancel {
  padding: 6px 14px;
  font-size: 12px;
  border-radius: 5px;
  border-color: #d1d5db;
  color: #4b5563;
  font-weight: 500;
  transition: all 0.3s ease;
}

.btn-cancel:hover {
  border-color: #909399;
  color: #374151;
  background: #f9fafb;
}

.btn-secondary {
  padding: 6px 14px;
  font-size: 12px;
  border-radius: 5px;
  border-color: #667eea;
  color: #667eea;
  font-weight: 500;
  transition: all 0.3s ease;
  background: #f5f7fa;
}

.btn-secondary:hover {
  border-color: #764ba2;
  color: #764ba2;
  background: #f5f7fa;
}

.btn-primary {
  padding: 6px 14px;
  font-size: 12px;
  border-radius: 5px;
  font-weight: 600;
  background: #667eea;
  border: none;
  transition: all 0.3s ease;
}

.btn-primary:hover {
  background: #764ba2;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-save-close {
  padding: 6px 14px;
  font-size: 12px;
  border-radius: 5px;
  font-weight: 600;
  background: #fff;
  border: 1px solid #667eea;
  color: #667eea;
  transition: all 0.3s ease;
}

.btn-save-close:hover {
  background: #667eea;
  color: #fff;
}

.btn-save-close:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

:deep(.step-edit-fullscreen-dialog) {
  width: 100vw !important;
  height: 100vh !important;
  min-height: 100vh !important;
  margin: 0 !important;
  padding: 0 !important;
  max-width: 100vw !important;
  max-height: 100vh !important;
  display: flex !important;
  flex-direction: column !important;
  border-radius: 0 !important;
  top: 0 !important;
  left: 0 !important;
  bottom: 0 !important;
  right: 0 !important;
  overflow: hidden !important;
  border: none !important;
}

:deep(.step-edit-fullscreen-wrapper) {
  width: 100vw !important;
  height: 100vh !important;
  min-height: 100vh !important;
  margin: 0 !important;
  padding: 0 !important;
  max-width: 100vw !important;
  max-height: 100vh !important;
  position: fixed !important;
  top: 0 !important;
  left: 0 !important;
  bottom: 0 !important;
  right: 0 !important;
  display: flex !important;
  align-items: stretch !important;
  justify-content: stretch !important;
  overflow: hidden !important;
}

:deep(.step-edit-fullscreen-wrapper::before) {
  display: none !important;
}

.custom-dialog-header {
  padding: 6px 16px;
  margin: 0;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
  display: flex;
  align-items: center;
}

.title-badge {
  font-size: 12px;
  font-weight: 600;
  color: #2f5454;
  background: #e6f7f5;
  padding: 4px 12px;
  border-radius: 4px;
}

:deep(.step-edit-fullscreen-dialog .el-dialog__body) {
  flex: 1 !important;
  overflow: hidden !important;
  padding: 0 !important;
  margin: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  min-height: 0 !important;
  background: transparent !important;
  width: 100% !important;
}

:deep(.step-edit-fullscreen-dialog .el-dialog__footer) {
  padding: 0 !important;
  border-top: none !important;
  flex-shrink: 0 !important;
  background: transparent !important;
  margin: 0 !important;
}
</style>
