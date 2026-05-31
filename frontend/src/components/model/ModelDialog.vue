<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="500px"
    :close-on-click-modal="false"
    append-to-body
    destroy-on-close
    @closed="onClosed"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="模型编码" prop="modelCode">
        <el-input
          v-model="form.modelCode"
          :placeholder="isEdit ? '' : '留空自动生成'"
          :disabled="isEdit"
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
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, computed } from 'vue'
import type { ModelInfo } from '@/types/model'

const props = defineProps<{
  modelId?: number        // 编辑时传入 ID
  title: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
  (e: 'create', data: Partial<ModelInfo>): void
  (e: 'update', id: number, data: Partial<ModelInfo>): void
}>()

const visible = computed({
  get: () => false, // 不使用 computed，由父组件通过 v-model 控制
  set: () => {}
})

// 用独立的 ref 控制，不依赖 props
const dialogVisible = ref(false)
const formRef = ref()
const loading = ref(false)
const isEdit = computed(() => props.modelId !== undefined)

const form = reactive<Partial<ModelInfo>>({
  modelCode: '',
  modelName: '',
  modelType: 'BUSINESS',
  modelDesc: '',
  status: 1
})

const rules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelCode: [
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (!value && !isEdit.value) {
          callback()
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

/** 由父组件调用此方法填充表单 */
function fillForm(data: Partial<ModelInfo>) {
  form.modelCode = data.modelCode || ''
  form.modelName = data.modelName || ''
  form.modelType = data.modelType || 'BUSINESS'
  form.modelDesc = data.modelDesc || ''
  form.status = data.status ?? 1
}

/** 重置表单 */
function resetForm() {
  form.modelCode = ''
  form.modelName = ''
  form.modelType = 'BUSINESS'
  form.modelDesc = ''
  form.status = 1
  nextTick(() => formRef.value?.clearValidate())
}

const onClosed = () => {
  resetForm()
  loading.value = false
}

const handleConfirm = async () => {
  if (loading.value) return
  try { await formRef.value?.validate() } catch { return }

  loading.value = true
  try {
    if (isEdit.value && props.modelId) {
      emit('update', props.modelId, { ...form })
    } else {
      const data = { ...form }
      if (!data.modelCode) delete data.modelCode
      emit('create', data)
    }
  } finally {
    loading.value = false
  }
}

// 暴露方法给父组件
defineExpose({ fillForm, resetForm, dialogVisible, loading })
</script>

<style scoped>
.input-hint {
  font-size: 12px;
  color: #909399;
}
</style>
