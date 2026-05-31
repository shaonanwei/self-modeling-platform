<template>
  <div class="metadata-panel">
    <el-input v-model="searchText" placeholder="搜索表名..." size="small" clearable class="search-input" />
    <div class="table-list">
      <div
        v-for="table in filteredTables"
        :key="table.tableName"
        class="table-item"
        draggable="true"
        @dragstart="handleDragStart($event, table)"
        @click="$emit('dragTable', table.tableName)"
      >
        <span class="table-icon">📋</span>
        <span class="table-name">{{ table.tableName }}</span>
        <span v-if="table.tableComment" class="table-comment">{{ table.tableComment }}</span>
      </div>
      <div v-if="loading" class="loading">加载中...</div>
      <div v-if="!loading && filteredTables.length === 0" class="empty">无匹配表</div>
      <div v-if="!loading && remainingCount > 0" class="remaining-hint">
        还有 {{ remainingCount }} 张表未显示，请使用搜索过滤
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { metadataApi } from '@/api/metadataApi'

const props = defineProps<{
  dataSourceId: string
}>()

const emit = defineEmits<{
  (e: 'dragTable', tableName: string): void
}>()

const searchText = ref('')
const tables = ref<Array<{ tableName: string; tableComment?: string }>>([])
const loading = ref(false)

const filteredTables = computed(() => {
  const maxDisplay = 15
  let result = tables.value
  
  if (searchText.value) {
    const kw = searchText.value.toLowerCase()
    result = tables.value.filter(t =>
      t.tableName.toLowerCase().includes(kw) ||
      (t.tableComment || '').toLowerCase().includes(kw)
    )
  }
  
  // 限制最多显示15条
  return result.slice(0, maxDisplay)
})

const remainingCount = computed(() => {
  const filtered = searchText.value 
    ? tables.value.filter(t => 
        t.tableName.toLowerCase().includes(searchText.value.toLowerCase()) ||
        (t.tableComment || '').toLowerCase().includes(searchText.value.toLowerCase())
      )
    : tables.value
  return Math.max(0, filtered.length - 15)
})

async function loadTables() {
  loading.value = true
  try {
    const res = await metadataApi.getTables({ dataSourceId: props.dataSourceId })
    tables.value = res.data || []
  } catch (e) {
    console.error('获取表列表失败', e)
    tables.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTables()
})

watch(() => props.dataSourceId, () => {
  loadTables()
})

function handleDragStart(e: DragEvent, table: { tableName: string }) {
  if (e.dataTransfer) {
    e.dataTransfer.setData('tableName', table.tableName)
    e.dataTransfer.effectAllowed = 'copy'
  }
}
</script>

<style scoped>
.metadata-panel { 
  padding: 8px; 
  height: 100%; 
  display: flex; 
  flex-direction: column; 
  background: #16213e;
  position: relative;
  overflow: hidden;
  box-sizing: border-box;
}

.search-input { margin-bottom: 8px; }
.search-input :deep(.el-input__wrapper) { background: #0f3460; box-shadow: none; }
.search-input :deep(.el-input__inner) { color: #eee; }

/* 表格列表容器 */
.table-list { 
  flex: 1; 
  overflow-y: auto; 
  overflow-x: hidden;
  padding-right: 4px;
}

/* 自定义滚动条 */
.table-list::-webkit-scrollbar {
  width: 6px;
}

.table-list::-webkit-scrollbar-track {
  background: #0f3460;
  border-radius: 3px;
}

.table-list::-webkit-scrollbar-thumb {
  background: #2d4a6f;
  border-radius: 3px;
}

.table-list::-webkit-scrollbar-thumb:hover {
  background: #3d5a8f;
}

.table-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  margin-bottom: 2px;
  border-radius: 4px;
  cursor: grab;
  font-size: 13px;
  transition: background 0.15s;
  overflow: hidden;
}

.table-item:hover { background: #0f3460; }
.table-item:active { cursor: grabbing; }
.table-icon { font-size: 14px; flex-shrink: 0; }
.table-name { 
  overflow: hidden; 
  text-overflow: ellipsis; 
  white-space: nowrap; 
  flex: 1;
}

/* 表注释 */
.table-comment {
  font-size: 11px;
  color: #64748b;
  flex-shrink: 0;
  margin-left: auto;
}

.loading, .empty { text-align: center; color: #888; padding: 20px 0; font-size: 13px; }

.remaining-hint {
  text-align: center;
  color: #64748b;
  padding: 8px 0;
  font-size: 12px;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 4px;
  margin-top: 4px;
}
</style>
