<template>
  <div class="metadata-viewer">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-select
        v-model="currentDataSource"
        placeholder="选择数据源"
        style="width: 220px; margin-right: 12px;"
        @change="handleDataSourceChange"
      >
        <el-option
          v-for="ds in dataSources"
          :key="ds.dataSourceId"
          :label="ds.dataSourceName"
          :value="ds.dataSourceId"
        >
          <span style="display: flex; align-items: center; justify-content: space-between;">
            <span>{{ ds.dataSourceName }}</span>
            <el-tag
              :type="ds.connected ? 'success' : 'danger'"
              size="small"
              style="margin-left: 8px;"
            >
              {{ ds.connected ? '已连接' : '未连接' }}
            </el-tag>
          </span>
        </el-option>
      </el-select>

      <el-input
        v-model="searchKeyword"
        placeholder="搜索表名或字段..."
        clearable
        style="width: 300px; margin-right: 12px;"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select
        v-model="searchType"
        style="width: 120px; margin-right: 12px;"
      >
        <el-option label="搜索表" value="table" />
        <el-option label="搜索字段" value="column" />
        <el-option label="全部" value="all" />
      </el-select>

      <el-button type="primary" @click="handleSearch" :loading="loading">
        <el-icon><Search /></el-icon> 搜索
      </el-button>

      <el-button @click="handleRefresh" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 左侧：表列表 -->
      <div class="table-list-panel">
        <div class="panel-header">
          <h3>📋 表列表</h3>
          <span class="count">共 {{ tables.length }} 张表</span>
        </div>
        <div class="table-list" v-loading="loading">
          <div
            v-for="table in tables"
            :key="table.tableName"
            :class="['table-item', { active: selectedTable?.tableName === table.tableName }]"
            @click="selectTable(table)"
          >
            <div class="table-name">{{ table.tableName }}</div>
            <div class="table-comment" v-if="table.tableComment">
              {{ table.tableComment }}
            </div>
            <div class="table-meta">
              <el-tag size="small" type="info">{{ table.tableType || 'TABLE' }}</el-tag>
              <span v-if="table.rowCount" class="row-count">
                {{ formatNumber(table.rowCount) }} 行
              </span>
            </div>
          </div>

          <el-empty v-if="!loading && tables.length === 0" description="暂无数据表" />
        </div>
      </div>

      <!-- 右侧：表详情 -->
      <div class="detail-panel" v-loading="detailLoading">
        <template v-if="selectedTable">
          <!-- 表基本信息 -->
          <div class="panel-section">
            <div class="section-header">
              <h3>{{ selectedTable.tableName }}</h3>
              <el-tag :type="selectedTable.tableType === 'VIEW' ? 'warning' : ''">
                {{ selectedTable.tableType || 'TABLE' }}
              </el-tag>
            </div>
            <div class="table-info-grid" v-if="selectedTable.tableComment || selectedTable.rowCount">
              <div class="info-item" v-if="selectedTable.tableComment">
                <label>注释：</label>
                <span>{{ selectedTable.tableComment }}</span>
              </div>
              <div class="info-item" v-if="selectedTable.rowCount !== undefined">
                <label>行数：</label>
                <span>{{ formatNumber(selectedTable.rowCount) }} 行</span>
              </div>
              <div class="info-item" v-if="selectedTable.primaryKeys?.length">
                <label>主键：</label>
                <el-tag
                  v-for="pk in selectedTable.primaryKeys"
                  :key="pk"
                  size="small"
                  type="danger"
                  style="margin-right: 4px;"
                >
                  {{ pk }}
                </el-tag>
              </div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="action-bar">
            <el-button size="small" @click="showPreview = !showPreview">
              {{ showPreview ? '隐藏预览' : '数据预览' }}
            </el-button>
            <el-button size="small" type="primary" @click="copyCreateStatement">
              复制建表语句
            </el-button>
          </div>

          <!-- 数据预览 -->
          <div v-if="showPreview" class="panel-section preview-section">
            <div class="section-header">
              <h4>📊 数据预览</h4>
              <el-input-number
                v-model="previewLimit"
                :min="5"
                :max="100"
                :step="5"
                size="small"
                style="width: 120px;"
                @change="loadPreviewData"
              />
            </div>
            <el-table
              :data="previewData"
              border
              size="small"
              max-height="300"
              style="width: 100%;"
            >
              <el-table-column
                v-for="col in (selectedTable?.columns || [])"
                :key="col.columnName"
                :prop="col.columnName"
                :label="col.columnName"
                :width="Math.max(120, col.columnSize || 120)"
                show-overflow-tooltip
              />
            </el-table>
          </div>

          <!-- 字段列表 -->
          <div class="panel-section columns-section">
            <div class="section-header">
              <h4>📝 字段信息</h4>
              <span class="column-count">共 {{ selectedTable.columns?.length || 0 }} 个字段</span>
            </div>
            <el-table
              :data="selectedTable.columns || []"
              border
              size="small"
              style="width: 100%;"
              :default-sort="{ prop: 'ordinalPosition', order: 'ascending' }"
            >
              <el-table-column prop="columnName" label="字段名" width="150" fixed />
              <el-table-column prop="columnType" label="类型" width="120">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.columnType }}</el-tag>
                  <span v-if="row.columnSize" style="color: #909399; font-size: 12px;">
                    ({{ row.columnSize }})
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="columnComment" label="注释" min-width="150" show-overflow-tooltip />
              <el-table-column label="属性" width="200">
                <template #default="{ row }">
                  <el-tag v-if="row.primaryKey" size="small" type="danger">PK</el-tag>
                  <el-tag v-if="row.autoIncrement" size="small" type="warning">自增</el-tag>
                  <el-tag v-if="!row.nullable" size="small" type="success">NOT NULL</el-tag>
                  <el-tag v-if="row.indexed" size="small" type="info">索引</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="默认值" width="120" show-overflow-tooltip>
                <template #default="{ row }">
                  <code style="font-size: 12px;">{{ row.defaultValue || '-' }}</code>
                </template>
              </el-table-column>
              <el-table-column prop="ordinalPosition" label="顺序" width="70" align="center" />
            </el-table>
          </div>

          <!-- 索引信息 -->
          <div v-if="selectedTable.indexes?.length" class="panel-section indexes-section">
            <div class="section-header">
              <h4>🔗 索引信息</h4>
            </div>
            <div
              v-for="index in selectedTable.indexes"
              :key="index.indexName"
              class="index-item"
            >
              <el-tag :type="index.unique ? 'warning' : 'info'" size="small">
                {{ index.unique ? 'UNIQUE' : 'INDEX' }}
              </el-tag>
              <strong>{{ index.indexName }}</strong>
              <span>({{ index.columns.join(', ') }})</span>
            </div>
          </div>
        </template>

        <el-empty v-else description="请选择一张表查看详情" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { metadataApi } from '@/api/metadataApi'
import type {
  DataSourceInfo,
  TableMetaDTO,
  ColumnMetaDTO,
  SearchType
} from '@/types/metadata'

// ========== 状态 ==========
const loading = ref(false)
const detailLoading = ref(false)
const dataSources = ref<DataSourceInfo[]>([])
const currentDataSource = ref('sqlite')
const searchKeyword = ref('')
const searchType = ref<SearchType>('table')
const tables = ref<TableMetaDTO[]>([])
const selectedTable = ref<TableMetaDTO | null>(null)
const showPreview = ref(false)
const previewLimit = ref(10)
const previewData = ref<Record<string, any>[]>([])

// ========== 生命周期 ==========
onMounted(() => {
  loadDataSources()
  loadTables()
})

// ========== 方法 ==========
/** 加载数据源列表 */
const loadDataSources = async () => {
  try {
    const res = await metadataApi.getDataSources()
    dataSources.value = res.data || []
    // 默认选择第一个已连接的数据源
    const connected = dataSources.value.find(ds => ds.connected)
    if (connected) {
      currentDataSource.value = connected.dataSourceId
    }
  } catch (e: unknown) {
    console.error('加载数据源失败', e)
  }
}

/** 加载表列表 */
const loadTables = async () => {
  loading.value = true
  try {
    const res = await metadataApi.getTables({
      dataSourceId: currentDataSource.value
    })
    tables.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error('加载表列表失败')
    console.error(e)
  } finally {
    loading.value = false
  }
}

/** 选择表 */
const selectTable = async (table: TableMetaDTO) => {
  if (selectedTable.value?.tableName === table.tableName) return

  selectedTable.value = null
  detailLoading.value = true

  try {
    const res = await metadataApi.getTableInfo(currentDataSource.value, table.tableName)
    selectedTable.value = res.data
    previewData.value = []

    if (showPreview.value) {
      await loadPreviewData()
    }
  } catch (e: unknown) {
    ElMessage.error('加载表详情失败')
    console.error(e)
  } finally {
    detailLoading.value = false
  }
}

/** 搜索 */
const handleSearch = async () => {
  if (!searchKeyword.value.trim()) {
    await loadTables()
    return
  }

  loading.value = true
  try {
    const res = await metadataApi.searchMetadata({
      dataSourceId: currentDataSource.value,
      keyword: searchKeyword.value.trim(),
      searchType: searchType.value
    })
    tables.value = res.data || []
    ElMessage.success(`找到 ${tables.value.length} 个匹配项`)
  } catch (e: unknown) {
    ElMessage.error('搜索失败')
    console.error(e)
  } finally {
    loading.value = false
  }
}

/** 刷新 */
const handleRefresh = async () => {
  await Promise.all([loadDataSources(), loadTables()])
  ElMessage.success('刷新完成')
}

/** 切换数据源 */
const handleDataSourceChange = () => {
  selectedTable.value = null
  tables.value = []
  loadTables()
}

/** 加载预览数据 */
const loadPreviewData = async () => {
  if (!selectedTable.value) return

  try {
    const res = await metadataApi.previewTableData(
      currentDataSource.value,
      selectedTable.value.tableName,
      previewLimit.value
    )
    previewData.value = res.data || []
  } catch (e: unknown) {
    ElMessage.warning('加载预览数据失败')
    previewData.value = []
  }
}

// 监听预览开关
watch(showPreview, (val) => {
  if (val && selectedTable.value && previewData.value.length === 0) {
    loadPreviewData()
  }
})

/** 格式化数字 */
const formatNumber = (num: number): string => {
  if (!num) return '0'
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toLocaleString()
}

/** 复制建表语句（简化版） */
const copyCreateStatement = () => {
  if (!selectedTable.value?.columns) return

  let sql = `-- ${selectedTable.value.tableName}\n`
  sql += `CREATE TABLE ${selectedTable.value.tableName} (\n`

  const lines = selectedTable.value.columns.map((col: ColumnMetaDTO) => {
    let line = `  ${col.columnName} ${col.columnType}`
    if (col.columnSize && !col.columnType.includes('(')) {
      line += `(${col.columnSize})`
    }
    if (col.primaryKey) line += ' PRIMARY KEY'
    if (!col.nullable && !col.primaryKey) line += ' NOT NULL'
    if (col.defaultValue) line += ` DEFAULT ${col.defaultValue}`
    if (col.columnComment) line += ` -- ${col.columnComment}`
    return line
  })

  sql += lines.join(',\n') + '\n);'

  navigator.clipboard.writeText(sql).then(() => {
    ElMessage.success('建表语句已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}
</script>

<style scoped>
.metadata-viewer {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.toolbar {
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  background: #fafafa;
  border-radius: 8px 8px 0 0;
}

.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧表列表面板 */
.table-list-panel {
  width: 380px;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f5f7fa;
}

.panel-header h3 {
  margin: 0;
  font-size: 15px;
  color: #303133;
}

.count {
  font-size: 13px;
  color: #909399;
}

.table-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.table-item {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}

.table-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.table-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.table-name {
  font-weight: 500;
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
  font-family: 'Consolas', monospace;
}

.table-comment {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #606266;
}

.row-count {
  color: #67c23a;
  font-weight: 500;
}

/* 右侧详情面板 */
.detail-panel {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.panel-section {
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid #409eff;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.section-header h4 {
  margin: 0;
  font-size: 14px;
  color: #303133;
}

.column-count {
  font-size: 13px;
  color: #909399;
}

/* 表基本信息网格 */
.table-info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 12px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.info-item span {
  font-size: 14px;
  color: #303133;
}

/* 操作按钮栏 */
.action-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

/* 预览区域 */
.preview-section {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
}

/* 索引区域 */
.indexes-section .index-item {
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 8px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
