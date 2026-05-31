<template>
  <div class="model-flow">
    <div class="flow-header">
      <el-button @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <h3>{{ model?.modelName || '流程图' }}</h3>
      <div class="header-actions">
        <el-button @click="handleFullscreen">
          <el-icon><FullScreen /></el-icon> 全屏
        </el-button>
        <el-button type="primary" @click="handleExport">
          <el-icon><Download /></el-icon> 导出图片
        </el-button>
      </div>
    </div>

    <!-- Flow Chart -->
    <FlowChart :model-id="modelId" ref="flowChartRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { modelApi } from '@/api/modelApi'
import FlowChart from './FlowChart.vue'
import type { ModelInfo } from '@/types/model'

const router = useRouter()
const route = useRoute()
const modelId = computed(() => Number(route.params.id))

const model = ref<ModelInfo | null>(null)
const flowChartRef = ref()

const loadModel = async () => {
  try {
    const res = await modelApi.getModel(modelId.value)
    model.value = res.data
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  }
}

const handleFullscreen = () => {
  // Simple fullscreen toggle
  document.documentElement.requestFullscreen?.()
}

const handleExport = () => {
  ElMessage.info('导出功能需要后端支持，可在后续版本中添加')
}

const goBack = () => {
  router.push('/models')
}

onMounted(() => {
  loadModel()
})
</script>

<style scoped>
.model-flow {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.flow-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.flow-header h3 {
  flex: 1;
  font-size: 16px;
  color: #303133;
}
</style>
