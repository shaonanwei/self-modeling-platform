<template>
  <div class="flow-chart-container">
    <VueFlow
      :nodes="flowNodes"
      :edges="flowEdges"
      :node-types="nodeTypes"
      ref="vueFlowRef"
      :default-viewport="{ x: 0, y: 0, zoom: 1 }"
      :min-zoom="0.2"
      :max-zoom="2"
      fit-view-on-init
      fit-view-options="{ padding: 0.3 }"
    >
      <Background pattern-color="#ddd" :gap="16" />
      <Controls position="bottom-right" />
    </VueFlow>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, h, nextTick, onMounted, markRaw } from 'vue'
import { VueFlow, type Node, type Edge, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import { modelApi } from '@/api/modelApi'
import { ElMessage } from 'element-plus'
import type { ModelStep } from '@/types/model'

const props = defineProps<{
  modelId: number
}>()

const rawSteps = ref<ModelStep[]>([])
const vueFlowRef = ref()

const loadSteps = async () => {
  try {
    const res = await modelApi.getSteps(props.modelId)
    rawSteps.value = res.data ?? []
    await nextTick()
    setTimeout(() => {
      if (vueFlowRef.value) {
        vueFlowRef.value.fitView({ padding: 0.3 })
      }
    }, 100)
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '加载步骤失败'
    ElMessage.error(message)
  }
}

const createNodeType = (color: string, isRound: boolean = false) => {
  return markRaw({
    props: ['data'] as const,
    render(this: { data: { label: string } }) {
      return h('div', {
        style: {
          padding: '14px 28px',
          borderRadius: isRound ? '50%' : '8px',
          border: `2px solid ${color}`,
          background: `${color}20`,
          minWidth: '140px',
          textAlign: 'center',
          fontSize: '14px',
          fontWeight: '500',
          color: color,
        }
      }, this.data?.label ?? '')
    }
  })
}

const nodeTypes = {
  start: createNodeType('#67c23a', true),
  end: createNodeType('#f56c6c', true),
  task: createNodeType('#409EFF'),
  gateway: createNodeType('#e6a23c'),
  subprocess: createNodeType('#909399'),
}

const flowNodes = computed<Node[]>(() => {
  const colCount = 4
  const nodeWidth = 200
  const nodeHeight = 160
  const xGap = 80
  const yGap = 80

  const totalWidth = colCount * (nodeWidth + xGap) - xGap
  const startX = Math.max(50, (800 - totalWidth) / 2)

  return rawSteps.value.map((step, index) => {
    const col = index % colCount
    const row = Math.floor(index / colCount)

    return {
      id: String(step.id),
      type: step.stepType,
      position: {
        x: startX + col * (nodeWidth + xGap),
        y: 50 + row * (nodeHeight + yGap),
      },
      data: { label: step.stepName },
    }
  })
})

const flowEdges = computed<Edge[]>(() => {
  const edges: Edge[] = []
  for (let i = 0; i < rawSteps.value.length - 1; i++) {
    edges.push({
      id: `e-${i}`,
      source: String(rawSteps.value[i].id),
      target: String(rawSteps.value[i + 1].id),
      type: 'smoothstep',
      animated: true,
      style: { stroke: '#409EFF', strokeWidth: 2 },
      label: rawSteps.value[i].conditionExpr || '下一步',
      labelStyle: { fill: '#606266', fontSize: '13px', fontWeight: '500' },
      labelBgPadding: [8, 4],
      labelBgStyle: { fill: '#fff', opacity: 0.9 },
    })
  }
  return edges
})

watch(() => props.modelId, loadSteps, { immediate: true })
</script>

<style scoped>
.flow-chart-container {
  width: 100%;
  height: 100%;
  min-height: 550px;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
}
</style>
