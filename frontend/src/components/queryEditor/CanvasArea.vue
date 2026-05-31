<template>
  <div
    class="canvas-area"
    @drop="handleDrop"
    @dragover.prevent
    @contextmenu.prevent
  >
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      class="vue-flow-container"
      :default-edge-options="defaultEdgeOptions"
      :node-types="nodeTypes"
      :edge-types="edgeTypes"
      :connection-line-style="{ strokeDasharray: '5 5', stroke: '#60a5fa', strokeWidth: 1.5 }"
      :connection-mode="('loose' as any)"
      :snap-to-grid="true"
      :snap-grid="[15, 15]"
      :nodes-draggable="!store.layoutLocked"
      :fit-view-options="{ padding: 0.15, includeHiddenNodes: false }"
      :min-zoom="0.1"
      :max-zoom="2"
      @connect="onConnect"
      @node-click="onNodeClick"
      @edge-click="onEdgeClick"
      @node-context-menu="onNodeContextMenu"
      @edge-context-menu="onEdgeContextMenu"
      @pane-click="onPaneClick"
      @node-drag-stop="onNodeDragStop"
      @init="onFlowInit"
    >
      <template #node-tableNode="nodeProps">
        <TableNode v-bind="nodeProps" />
      </template>
    </VueFlow>

    <!-- 布局锁定小锁按钮 -->
    <div
      class="layout-lock-btn"
      :class="{ locked: store.layoutLocked }"
      :title="store.layoutLocked ? '点击解锁布局' : '点击锁定布局'"
      @click="store.layoutLocked = !store.layoutLocked"
    >
      <svg v-if="store.layoutLocked" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
        <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
      </svg>
      <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
        <path d="M7 11V7a5 5 0 0 1 9.9-1"/>
      </svg>
    </div>

    <!-- WHERE 条件面板 - 支持拖拽移动 -->
    <div 
      v-if="store.canvasConfig.where && store.canvasConfig.where.conditions && store.canvasConfig.where.conditions.length > 0"
      class="where-panel"
      :class="{ 'is-dragging': isDraggingWhere }"
      :style="wherePanelStyle"
      @mousedown.stop="startDragWherePanel($event)"
      @click="handleWherePanelClick"
    >
        <div class="where-header drag-handle" title="拖拽移动">
          <span class="drag-icon">⠿</span>
          查询条件
        </div>
      <div class="where-list">
        <div 
          v-for="(w, idx) in store.canvasConfig.where.conditions" 
          :key="w.id || idx" 
          class="where-item"
          :class="{ 'has-left-bracket': w.leftBracket, 'has-right-bracket': w.rightBracket }"
          :title="getWhereItemTitle(w, idx)"
        >
          <!-- 显示逻辑符 -->
          <span 
            v-if="idx > 0" 
            class="where-logic"
            :class="{ 'is-or': w.logic === 'OR' }"
          >
            {{ w.logic || 'AND' }}
          </span>
          <!-- 显示左括号（如果有） -->
          <span v-if="w.leftBracket" class="where-bracket">(</span>
          
          <!-- 条件内容 -->
          <template v-if="w.type === 'field' && w.field">
            <span class="where-field">{{ w.field }}</span>
            <span class="where-op">{{ w.operator }}</span>
            <span class="where-value">{{ formatValue(w.value) }}</span>
          </template>
          <template v-else-if="w.type === 'custom' && w.customSql">
            <span class="where-custom">{{ truncateSql(w.customSql, 30) }}</span>
          </template>
          <template v-else>
            <span class="where-empty-inline">未配置</span>
          </template>
          
          <!-- 显示右括号（如果有） -->
          <span v-if="w.rightBracket" class="where-bracket">)</span>
        </div>
      </div>
    </div>

    <!-- 排序面板 - 支持拖拽移动 -->
    <div 
      v-if="store.canvasConfig.orderBy && store.canvasConfig.orderBy.length > 0"
      class="order-panel"
      :class="{ 'is-dragging': isDraggingOrder }"
      :style="orderPanelStyle"
      @mousedown="startDragOrderPanel($event)"
      @click="handleOrderPanelClick"
      @contextmenu.prevent="showOrderPanelContextMenu($event)"
    >
        <div class="order-header drag-handle" title="拖拽移动">
          <span class="drag-icon">⠿</span>
          排序配置
        </div>
      <div class="order-list">
        <div 
          v-for="(item, index) in store.canvasConfig.orderBy" 
          :key="index" 
          class="order-item"
          :title="getOrderItemTitle(item, index)"
          @contextmenu.prevent.stop="showOrderContextMenu($event, index)"
        >
          <span class="order-index">{{ index + 1 }}</span>
          <template v-if="item.type === 'field' || !item.type">
            <span class="order-field">{{ item.field }}</span>
          </template>
          <template v-else>
            <span class="order-custom">{{ truncateSql(item.customSql || item.field || '', 25) }}</span>
          </template>
          <span v-if="item.direction === 'DESC'" class="order-direction desc">
            DESC
          </span>
        </div>
      </div>
    </div>

    <!-- 分组面板 - 支持拖拽移动 -->
    <div 
      v-if="store.canvasConfig.groupBy && store.canvasConfig.groupBy.length > 0"
      class="group-panel"
      :class="{ 'is-dragging': isDraggingGroup }"
      :style="groupPanelStyle"
      @mousedown="startDragGroupPanel($event)"
      @click="handleGroupPanelClick"
      @contextmenu.prevent="showGroupPanelContextMenu($event)"
    >
        <div class="group-header drag-handle" title="拖拽移动">
          <span class="drag-icon">⠿</span>
          分组配置
        </div>
      
      <!-- 分组字段 -->
      <div class="group-section">
        <div class="group-section-title">分组字段</div>
        <div class="group-fields">
          <span 
            v-for="(field, idx) in store.canvasConfig.groupBy" 
            :key="field"
            class="group-field-tag"
            @contextmenu.prevent.stop="showGroupContextMenu($event, 'field', idx)"
          >
            {{ field }}
          </span>
        </div>
      </div>

      <!-- 分组条件 -->
      <div class="group-section">
        <div v-if="store.canvasConfig.having && store.canvasConfig.having.conditions && store.canvasConfig.having.conditions.length > 0" class="group-conditions">
          <div class="group-section-title">分组条件</div>
          <div 
            v-for="(c, idx) in store.canvasConfig.having.conditions" 
            :key="c.id" 
            class="group-condition-item"
            :title="getHavingItemTitle(c, idx)"
            @contextmenu.prevent.stop="showGroupContextMenu($event, 'condition', idx)"
          >
            <!-- 显示逻辑符 -->
            <span 
              v-if="idx > 0" 
              class="group-logic"
              :class="{ 'is-or': c.logic === 'OR' }"
            >
              {{ c.logic || 'AND' }}
            </span>
            
            <!-- 左括号 -->
            <span v-if="c.leftBracket" class="group-bracket">(</span>
            
            <!-- 条件内容 -->
            <template v-if="c.type === 'field' && c.field">
              <span class="group-field">{{ c.field }}</span>
              <span class="group-op">{{ c.operator }}</span>
              <span class="group-value">{{ formatValue(c.value) }}</span>
            </template>
            <template v-else-if="c.type === 'custom' && c.customSql">
              <span class="group-custom">{{ truncateSql(c.customSql, 30) }}</span>
            </template>
            <template v-else>
              <span class="group-empty-inline">未配置</span>
            </template>
            
            <!-- 右括号 -->
            <span v-if="c.rightBracket" class="group-bracket">)</span>
          </div>
        </div>

      </div>
    </div>

    <!-- 右键菜单 - 表节点 -->
    <div
      v-if="contextMenu.visible && contextMenu.type === 'node'"
      class="context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
    >
      <div class="context-menu-item" @click="handleSelectAllFields">
        <span class="menu-icon">✅</span> 全选字段
      </div>
      <div class="context-menu-item" @click="handleDeselectAllFields">
        <span class="menu-icon">⬜</span> 取消全选
      </div>
      <div class="context-menu-item" @click="handleToggleExpand">
        <span class="menu-icon">{{ contextMenuTargetExpanded ? '📥' : '📤' }}</span>
        {{ contextMenuTargetExpanded ? '收起字段' : '展开字段' }}
      </div>
      <div class="context-menu-divider"></div>
      <div class="context-menu-item" @click="handleAddCustomField">
        <span class="menu-icon">➕</span> 添加自定义字段
      </div>
      <div class="context-menu-item" @click="handleAddWhereCondition">
        <span class="menu-icon">🔍</span> 添加查询条件
      </div>
      <div class="context-menu-item" @click="handleAddGroupByField">
        <span class="menu-icon">📊</span> 添加分组配置
      </div>
      <div class="context-menu-divider"></div>
      <div class="context-menu-item danger" @click="handleDeleteTable">
        <span class="menu-icon">🗑️</span> 删除表
      </div>
    </div>

    <!-- 右键菜单 - 连接线 -->
    <div
      v-if="contextMenu.visible && contextMenu.type === 'edge'"
      class="context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
    >
      <div class="context-menu-item" @click="handleDeleteEdge">
        <span class="menu-icon">🗑️</span> 删除连接线
      </div>
      <div class="context-menu-divider"></div>
      <div class="context-menu-item" @click="handleSwitchJoinType('INNER')">
        <span class="menu-icon" style="color:#22c55e">●</span> INNER JOIN
      </div>
      <div class="context-menu-item" @click="handleSwitchJoinType('LEFT')">
        <span class="menu-icon" style="color:#3b82f6">●</span> LEFT JOIN
      </div>
      <div class="context-menu-item" @click="handleSwitchJoinType('RIGHT')">
        <span class="menu-icon" style="color:#f97316">●</span> RIGHT JOIN
      </div>
    </div>

    <!-- 右键菜单 - 分组配置面板 -->
    <div
      v-if="contextMenu.visible && contextMenu.type === 'groupPanel'"
      class="context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
    >
      <!-- 分组字段右键 -->
      <template v-if="contextMenu.groupTarget === 'field'">
        <div class="context-menu-item" @click="handleAddGroupBy">
          <span class="menu-icon">➕</span> 添加分组配置
        </div>
        <div class="context-menu-divider"></div>
        <div class="context-menu-item" @click="handleClearGroupBy">
          <span class="menu-icon">🗑️</span> 删除所有字段
        </div>
      </template>
      
      <!-- 分组条件右键 -->
      <template v-else-if="contextMenu.groupTarget === 'condition'">
        <div class="context-menu-item" @click="handleAddHavingCondition">
          <span class="menu-icon">➕</span> 添加分组条件
        </div>
          <div class="context-menu-divider"></div>
        <div class="context-menu-item" @click="handleRemoveHavingCondition">
          <span class="menu-icon">🗑️</span> 删除分组条件
        </div>
      </template>
      
      <!-- 面板空白区域右键 -->
      <template v-else>
        <div class="context-menu-item" @click="handleAddGroupBy">
          <span class="menu-icon">➕</span> 添加分组配置
        </div>
        <div class="context-menu-divider"></div>
        <div class="context-menu-item danger" @click="handleClearGroupBy">
          <span class="menu-icon">🗑️</span> 删除分组配置
        </div>
      </template>      
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick, markRaw } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { useQueryEditorStore } from '@/stores/queryEditorStore'
import TableNode from './TableNode.vue'

const props = defineProps<{
  initialConfig?: any
  flex?: number | string
}>()

const emit = defineEmits<{
  (e: 'nodeClick', alias: string): void
  (e: 'edgeClick', id: string): void
  (e: 'nodesChange'): void
  (e: 'connect', conn: { source: string; target: string; sourceField: string; targetField: string }): void
  (e: 'maxTablesReached'): void
  (e: 'paneClick'): void
  (e: 'layoutChange'): void
  (e: 'wherePanelClick'): void
  (e: 'groupPanelClick'): void
  (e: 'orderPanelClick'): void
}>()

const store = useQueryEditorStore()
const { project, viewport, fitView } = useVueFlow()

const nodes = ref<any[]>([])
const edges = ref<any[]>([])

// 标记是否已经初始化过视口（防止拖拽等操作时重新调整）
const isViewportInitialized = ref(false)

const contextMenu = ref({ 
  visible: false, 
  x: 0, 
  y: 0, 
  type: 'node' as 'node' | 'edge' | 'groupPanel' | 'orderPanel', 
  nodeAlias: '', 
  edgeId: '',
  groupTarget: '' as '' | 'field' | 'condition' | 'panel',
  groupIndex: -1
})

const contextMenuTargetExpanded = computed(() => {
  if (!contextMenu.value.nodeAlias) return false
  const table = store.tables.find(t => t.alias === contextMenu.value.nodeAlias)
  return table?.expanded ?? false
})

// 格式化条件为 SQL
function formatCondition(condition: any): string {
  if (condition.type === 'custom') {
    return condition.customSql || ''
  }
  if (!condition.field || !condition.operator) {
    return ''
  }
  let value = formatValue(condition.value)
  let leftBracket = condition.leftBracket ? '(' : ''
  let rightBracket = condition.rightBracket ? ')' : ''
  let logic = ''
  if (condition.logic) {
    logic = condition.logic.toUpperCase() + ' '
  }
  return `${leftBracket}${condition.field} ${condition.operator} ${value}${rightBracket}`
}

// 生成单个查询条件的完整文本
function getWhereItemTitle(condition: any, index: number): string {
  const parts: string[] = []
  if (index > 0) {
    parts.push(condition.logic?.toUpperCase() || 'AND')
  }
  if (condition.leftBracket) {
    parts.push('(')
  }
  if (condition.type === 'custom') {
    parts.push(condition.customSql || '')
  } else if (condition.field && condition.operator) {
    parts.push(`${condition.field} ${condition.operator} ${formatValue(condition.value)}`)
  }
  if (condition.rightBracket) {
    parts.push(')')
  }
  return parts.join(' ')
}

// 生成单个分组条件的完整文本
function getHavingItemTitle(condition: any, index: number): string {
  const parts: string[] = []
  if (index > 0) {
    parts.push(condition.logic?.toUpperCase() || 'AND')
  }
  if (condition.leftBracket) {
    parts.push('(')
  }
  if (condition.type === 'custom') {
    parts.push(condition.customSql || '')
  } else if (condition.field && condition.operator) {
    parts.push(`${condition.field} ${condition.operator} ${formatValue(condition.value)}`)
  }
  if (condition.rightBracket) {
    parts.push(')')
  }
  return parts.join(' ')
}

// 生成 WHERE SQL
const whereSql = computed(() => {
  if (!store.canvasConfig.where || !store.canvasConfig.where.conditions || store.canvasConfig.where.conditions.length === 0) {
    return '暂无查询条件'
  }
  let sql = 'WHERE '
  store.canvasConfig.where.conditions.forEach((cond: any, index: number) => {
    if (index > 0) {
      sql += ' ' + (cond.logic?.toUpperCase() || 'AND') + ' '
    }
    if (cond.leftBracket) sql += '('
    if (cond.type === 'custom') {
      sql += cond.customSql || ''
    } else {
      sql += `${cond.field} ${cond.operator} ${formatValue(cond.value)}`
    }
    if (cond.rightBracket) sql += ')'
  })
  return sql
})

// 生成 ORDER BY SQL
const orderSql = computed(() => {
  if (!store.canvasConfig.orderBy || store.canvasConfig.orderBy.length === 0) {
    return '暂无排序配置'
  }
  let sql = 'ORDER BY '
  store.canvasConfig.orderBy.forEach((item: any, index: number) => {
    if (index > 0) {
      sql += ', '
    }
    const orderField = item.type === 'custom' ? item.customSql || item.field : item.field
    sql += `${orderField} ${item.direction}`
  })
  return sql
})

// 生成排序项的完整文本
function getOrderItemTitle(item: any, index: number): string {
  const orderField = item.type === 'custom' ? item.customSql || item.field : item.field
  return `${index + 1}. ${orderField} ${item.direction}`
}

// 生成 GROUP BY 和 HAVING SQL
const groupSql = computed(() => {
  let sql = ''
  if (store.canvasConfig.groupBy && store.canvasConfig.groupBy.length > 0) {
    sql += 'GROUP BY ' + store.canvasConfig.groupBy.join(', ') + '\n'
  }
  if (store.canvasConfig.having && store.canvasConfig.having.conditions && store.canvasConfig.having.conditions.length > 0) {
    sql += 'HAVING '
    store.canvasConfig.having.conditions.forEach((cond: any, index: number) => {
      if (index > 0) {
        sql += ' ' + (cond.logic?.toUpperCase() || 'AND') + ' '
      }
      if (cond.leftBracket) sql += '('
      if (cond.type === 'custom') {
        sql += cond.customSql || ''
      } else {
        sql += `${cond.field} ${cond.operator} ${formatValue(cond.value)}`
      }
      if (cond.rightBracket) sql += ')'
    })
  }
  return sql || '暂无分组配置'
})

const defaultEdgeOptions = {
  type: 'smoothstep',
  animated: true,
  label: 'INNER JOIN',
  labelStyle: { fill: '#e2e8f0', fontSize: '10px', fontWeight: '500' },
  labelBgStyle: { fill: '#1e3a5f', fillOpacity: 0.9 },
  labelBgPadding: [4, 6] as [number, number],
  labelBgBorderRadius: 4,
  style: { stroke: '#3b82f6', strokeWidth: 1.5 },
  markerEnd: { type: 'arrowclosed', color: '#3b82f6', width: 12, height: 12 }
} as any

const nodeTypes = { tableNode: markRaw(TableNode) } as any
const edgeTypes = {} as any

// 初始化时完整同步
function syncNodesFromStore() {
  const storeNodes = store.tables.map(t => ({
    id: t.alias,
    type: 'tableNode',
    position: { x: t.x || 0, y: t.y || 0 },
    data: { ...t }
  })) as any[]
  
  nodes.value = [...storeNodes]
}

// 只更新节点数据，不改变位置
function updateNodeData() {
  for (const node of nodes.value) {
    const storeTable = store.tables.find(t => t.alias === node.id)
    if (storeTable) {
      node.data = { ...storeTable }
    }
  }
}

function syncEdgesFromStore() {
  const storeEdges = store.joins
    .filter(j => {
      const sourceNode = nodes.value.find(n => n.id === j.sourceTable)
      const targetNode = nodes.value.find(n => n.id === j.targetTable)
      if (!sourceNode || !targetNode) {
        console.warn(`[CanvasArea] 跳过无效边: source=${j.sourceTable}, target=${j.targetTable}`)
        return false
      }
      return true
    })
    .map(j => {
      const color = getEdgeColor(j.joinType)
      const sourceNode = nodes.value.find(n => n.id === j.sourceTable)!
      const targetNode = nodes.value.find(n => n.id === j.targetTable)!
      const sourceOnLeft = sourceNode.position.x <= targetNode.position.x

      const sourceHandle = sourceOnLeft
        ? `${j.sourceField}-source-right`
        : `${j.sourceField}-source-left`
      const targetHandle = sourceOnLeft
        ? `${j.targetField}-target-left`
        : `${j.targetField}-target-right`

      return {
        id: j.id,
        type: 'smoothstep',
        source: j.sourceTable,
        target: j.targetTable,
        sourceHandle,
        targetHandle,
        data: { ...j },
        label: (j.joinType || 'INNER') + ' JOIN',
        labelStyle: { fill: '#e2e8f0', fontSize: '10px', fontWeight: '500' },
        labelBgStyle: { fill: '#1e3a5f', fillOpacity: 0.9 },
        labelBgPadding: [4, 6] as [number, number],
        labelBgBorderRadius: 4,
        style: { stroke: color, strokeWidth: 1.5 },
        markerEnd: { type: 'arrowclosed', color, width: 12, height: 12 }
      }
    }) as any[]
  edges.value = [...storeEdges]
}

function getEdgeColor(joinType: string | undefined): string {
  const colors: Record<string, string> = {
    INNER: '#22c55e',
    LEFT: '#3b82f6',
    RIGHT: '#f97316',
    CROSS: '#a855f7'
  }
  return colors[joinType || 'INNER'] || '#64748b'
}

function onConnect(params: any) {
  const sourceField = params.sourceHandle?.replace(/-source-row$|-source-left$|-source-right$|-source$|-target-row$|-target-left$|-target-right$|-target$/, '') || ''
  const targetField = params.targetHandle?.replace(/-source-row$|-source-left$|-source-right$|-source$|-target-row$|-target-left$|-target-right$|-target$/, '') || ''

  if (!sourceField || !targetField) {
    console.warn('连线必须从字段到字段，不允许连到表本身')
    return
  }

  if (params.source === params.target) {
    console.warn('不允许连接同一表的字段')
    return
  }

  const existing = store.joins.find(j =>
    j.sourceTable === params.source &&
    j.sourceField === sourceField &&
    j.targetTable === params.target &&
    j.targetField === targetField
  )
  if (existing) {
    console.warn('该关联已存在')
    return
  }

  store.addJoin({
    sourceTable: params.source,
    sourceField,
    targetTable: params.target,
    targetField,
    joinType: 'INNER'
  })

  emit('connect', {
    source: params.source,
    target: params.target,
    sourceField,
    targetField
  })
}

function onNodeClick(e: any) {
  emit('nodeClick', e.node.id)
}

function onEdgeClick(e: any) {
  emit('edgeClick', e.edge.id)
}

function handleWherePanelClick() {
  // 只有在不是拖拽的情况下才触发点击事件
  if (isClick.value) {
    emit('wherePanelClick')
  }
}

function onNodeContextMenu(e: any) {
  e.event?.preventDefault()
  const alias = e.node.id
  const rect = (e.event?.target as HTMLElement)?.closest('.canvas-area')?.getBoundingClientRect()
  const x = rect ? e.event.clientX - rect.left : e.event.clientX
  const y = rect ? e.event.clientY - rect.top : e.event.clientY
  contextMenu.value = { visible: true, x, y, type: 'node', nodeAlias: alias, edgeId: '', groupTarget: '', groupIndex: -1 }
}

function onEdgeContextMenu(e: any) {
  e.event?.preventDefault()
  const edgeId = e.edge.id
  const rect = (e.event?.target as HTMLElement)?.closest('.canvas-area')?.getBoundingClientRect()
  const x = rect ? e.event.clientX - rect.left : e.event.clientX
  const y = rect ? e.event.clientY - rect.top : e.event.clientY
  contextMenu.value = { visible: true, x, y, type: 'edge', nodeAlias: '', edgeId, groupTarget: '', groupIndex: -1 }
}

function closeContextMenu() {
  contextMenu.value.visible = false
}

function handleSelectAllFields() {
  const alias = contextMenu.value.nodeAlias
  const table = store.tables.find(t => t.alias === alias)
  if (table) {
    table.selectedFields = ['*']
  }
  closeContextMenu()
}

function handleDeselectAllFields() {
  const alias = contextMenu.value.nodeAlias
  const table = store.tables.find(t => t.alias === alias)
  if (table) {
    table.selectedFields = []
  }
  closeContextMenu()
}

function handleToggleExpand() {
  const alias = contextMenu.value.nodeAlias
  const table = store.tables.find(t => t.alias === alias)
  if (table) {
    table.expanded = !table.expanded
  }
  closeContextMenu()
}

function handleDeleteTable() {
  const alias = contextMenu.value.nodeAlias
  store.removeTable(alias)
  closeContextMenu()
}

function handleAddCustomField() {
  const alias = contextMenu.value.nodeAlias
  store.addCustomField(alias)
  closeContextMenu()
  store.selectTable(alias)
  store.triggerFocusCustomFields()
}

function handleAddWhereCondition() {
  const alias = contextMenu.value.nodeAlias
  closeContextMenu()
  store.selectTable(alias)
  store.setActiveTab('where')
  emit('wherePanelClick')
}

function handleAddGroupByField() {
  const alias = contextMenu.value.nodeAlias
  closeContextMenu()
  store.selectTable(alias)
  store.setActiveTab('groupBy')
  emit('groupPanelClick')
}

function handleAddGroupBy() {
  closeContextMenu()
  const alias = contextMenu.value.nodeAlias
  if (alias) {
    store.selectTable(alias)
  }
  store.setActiveTab('groupBy')
  emit('groupPanelClick')
}

function handleDeleteEdge() {
  const edgeId = contextMenu.value.edgeId
  store.removeJoin(edgeId)
  closeContextMenu()
}

function handleSwitchJoinType(joinType: 'INNER' | 'LEFT' | 'RIGHT') {
  // 统一修改所有关联条件的关联方式
  if (store.joins.length > 0) {
    store.joins.forEach(join => {
      join.joinType = joinType
    })
  }
  closeContextMenu()
}

function showGroupContextMenu(e: MouseEvent, target: 'field' | 'condition', index: number) {
  e.preventDefault()
  e.stopPropagation()
  const rect = (e.currentTarget as HTMLElement)?.closest('.canvas-area')?.getBoundingClientRect()
  const x = rect ? e.clientX - rect.left : e.clientX
  const y = rect ? e.clientY - rect.top : e.clientY
  contextMenu.value = { visible: true, x, y, type: 'groupPanel', nodeAlias: '', edgeId: '', groupTarget: target, groupIndex: index }
}

function showGroupPanelContextMenu(e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  const rect = (e.currentTarget as HTMLElement)?.closest('.canvas-area')?.getBoundingClientRect()
  const x = rect ? e.clientX - rect.left : e.clientX
  const y = rect ? e.clientY - rect.top : e.clientY
  contextMenu.value = { visible: true, x, y, type: 'groupPanel', nodeAlias: '', edgeId: '', groupTarget: 'panel', groupIndex: -1 }
}

function handleAddGroupField() {
  closeContextMenu()
  store.setActiveTab('groupBy')
  emit('groupPanelClick')
}
 

function handleRemoveGroupField() {
  if (contextMenu.value.groupIndex >= 0) {
    store.removeGroupByField(contextMenu.value.groupIndex)
  }
  closeContextMenu()
}

function handleRemoveHavingCondition() {
  if (contextMenu.value.groupIndex >= 0) {
    store.removeHavingCondition(contextMenu.value.groupIndex)
  }
  closeContextMenu()
}

function handleAddHavingCondition() {
  closeContextMenu()
  // 创建一个默认的字段类型条件
  const newCondition = {
    id: `h${Date.now()}`,
    type: 'field' as const,
    field: '',
    operator: '=',
    value: '',
    logic: 'AND' as const
  }
  store.addHavingCondition(newCondition)
  store.setActiveTab('groupBy')
  emit('groupPanelClick')
}

function handleClearGroupBy() {
  store.clearGroupBy()
  closeContextMenu()
}

// Vue Flow 初始化时，设置初始视口
// 画布默认定位在中心位置，节点自适应宽度和高度
function onFlowInit() {
  // 监听 nodes 变化，当有节点时设置初始视口
  watch(nodes, (newNodes) => {
    // 只在首次有节点且未初始化过时执行
    if (newNodes.length > 0 && !isViewportInitialized.value) {
      isViewportInitialized.value = true
      
      // 使用 nextTick 确保 DOM 已更新
      nextTick(() => {
        // 适应视图，让节点自适应显示在画布中心
        fitView({ 
          padding: 0.2,
          includeHiddenNodes: false,
          duration: 0 // 无动画，立即完成
        })
      })
    }
  }, { immediate: true }) // 立即执行一次检查
}

// 监听表数量变化（添加/删除表时才完整同步）
watch(() => store.tables.length, (newLen, oldLen) => {
  if (newLen !== oldLen) {
    syncNodesFromStore()
  }
})

// 监听表内容变化（只更新数据，不改变位置）
watch(() => store.tables, () => {
  if (store.tables.length === nodes.value.length) {
    updateNodeData()
  } else {
    syncNodesFromStore()
  }
}, { deep: true })

watch(() => store.joins, syncEdgesFromStore, { deep: true, immediate: true })

// ========== 查询条件面板拖拽功能 ==========
const isDraggingWhere = ref(false)
const wherePanelPos = ref({ x: 0, y: 0 })
const dragStart = ref({ x: 0, y: 0 })
const dragOffset = ref({ x: 0, y: 0 })
const isClick = ref(false) // 区分点击和拖拽

// ========== 分组面板拖拽功能 ==========
const isDraggingGroup = ref(false)
const groupPanelPos = ref({ x: 0, y: 0 })
const groupDragStart = ref({ x: 0, y: 0 })
const groupDragOffset = ref({ x: 0, y: 0 })
const groupIsClick = ref(false)

// ========== 排序面板拖拽功能 ==========
const isDraggingOrder = ref(false)
const orderPanelPos = ref({ x: 0, y: 0 })
const orderDragStart = ref({ x: 0, y: 0 })
const orderDragOffset = ref({ x: 0, y: 0 })
const orderIsClick = ref(false)

// 计算面板样式（支持拖拽定位和缩放）
// 面板位置存储在画布坐标系中，需要转换为屏幕坐标
const wherePanelStyle = computed(() => {
  const currentViewport = viewport.value
  const zoom = currentViewport?.zoom ?? 1
  const viewX = currentViewport?.x ?? 0
  const viewY = currentViewport?.y ?? 0
  
  if (wherePanelPos.value.x !== 0 || wherePanelPos.value.y !== 0) {
    // 屏幕位置 = 画布坐标 * zoom + 视口偏移
    const screenX = wherePanelPos.value.x * zoom + viewX
    const screenY = wherePanelPos.value.y * zoom + viewY
    return {
      bottom: 'auto',
      left: `${screenX}px`,
      top: `${screenY}px`,
      transform: `scale(${zoom})`,
      transformOrigin: 'top left',
      // 确保面板在缩放时不会超出画布边界太多
      maxWidth: 'none',
      maxHeight: 'none'
    }
  }
  return {}
})

// 计算分组面板样式
const groupPanelStyle = computed(() => {
  const currentViewport = viewport.value
  const zoom = currentViewport?.zoom ?? 1
  const viewX = currentViewport?.x ?? 0
  const viewY = currentViewport?.y ?? 0
  
  if (groupPanelPos.value.x !== 0 || groupPanelPos.value.y !== 0) {
    const screenX = groupPanelPos.value.x * zoom + viewX
    const screenY = groupPanelPos.value.y * zoom + viewY
    return {
      bottom: 'auto',
      left: `${screenX}px`,
      top: `${screenY}px`,
      transform: `scale(${zoom})`,
      transformOrigin: 'top left',
      maxWidth: 'none',
      maxHeight: 'none'
    }
  }
  return {}
})

// 计算排序面板样式
const orderPanelStyle = computed(() => {
  const currentViewport = viewport.value
  const zoom = currentViewport?.zoom ?? 1
  const viewX = currentViewport?.x ?? 0
  const viewY = currentViewport?.y ?? 0
  
  if (orderPanelPos.value.x !== 0 || orderPanelPos.value.y !== 0) {
    const screenX = orderPanelPos.value.x * zoom + viewX
    const screenY = orderPanelPos.value.y * zoom + viewY
    return {
      bottom: 'auto',
      left: `${screenX}px`,
      top: `${screenY}px`,
      transform: `scale(${zoom})`,
      transformOrigin: 'top left',
      maxWidth: 'none',
      maxHeight: 'none'
    }
  }
  return {}
})

function startDragWherePanel(e: MouseEvent) {
  // 只响应左键点击
  if (e.button !== 0) return
  // 如果布局已锁定，禁止拖拽
  if (store.layoutLocked) return
  
  const panel = (e.currentTarget as HTMLElement)
  const rect = panel.getBoundingClientRect()
  const currentViewport = viewport.value
  const zoom = currentViewport?.zoom ?? 1
  const viewX = currentViewport?.x ?? 0
  const viewY = currentViewport?.y ?? 0
  
  isDraggingWhere.value = false // 先标记为非拖拽
  isClick.value = true // 先假设是点击
  dragStart.value = { x: e.clientX, y: e.clientY }
  
  // 如果位置尚未设置（首次拖拽），从当前位置计算画布坐标
  if (wherePanelPos.value.x === 0 && wherePanelPos.value.y === 0) {
    const canvasRect = document.querySelector('.vue-flow')?.getBoundingClientRect()
    if (canvasRect) {
      // 画布坐标 = (屏幕位置 - 视口偏移) / zoom
      wherePanelPos.value = {
        x: (rect.left - canvasRect.left - viewX) / zoom,
        y: (rect.top - canvasRect.top - viewY) / zoom
      }
    }
  }
  
  // 计算鼠标相对于面板的偏移（屏幕坐标）
  dragOffset.value = {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top
  }
  
  // 添加全局事件监听
  window.addEventListener('mousemove', onDragWherePanel)
  window.addEventListener('mouseup', stopDragWherePanel)
  // 防止文本选中
  document.body.style.userSelect = 'none'
}

function onDragWherePanel(e: MouseEvent) {
  // 计算移动距离，超过阈值才认为是拖拽
  const dx = Math.abs(e.clientX - dragStart.value.x)
  const dy = Math.abs(e.clientY - dragStart.value.y)
  
  // 如果移动超过3像素，认为是拖拽
  if (dx > 3 || dy > 3) {
    isDraggingWhere.value = true
    isClick.value = false
  }
  
  if (!isDraggingWhere.value) return
  
  const canvasRect = document.querySelector('.vue-flow')?.getBoundingClientRect()
  if (!canvasRect) return
  
  const currentViewport = viewport.value
  const zoom = currentViewport?.zoom ?? 1
  const viewX = currentViewport?.x ?? 0
  const viewY = currentViewport?.y ?? 0
  
  // 计算新的画布坐标
  // 画布坐标 = (屏幕位置 - 视口偏移) / zoom
  const newX = (e.clientX - canvasRect.left - dragOffset.value.x - viewX) / zoom
  const newY = (e.clientY - canvasRect.top - dragOffset.value.y - viewY) / zoom
  
  // 完全移除边界限制，让面板可以像表节点一样自由移动
  wherePanelPos.value = { x: newX, y: newY }
}

function stopDragWherePanel() {
  isDraggingWhere.value = false
  window.removeEventListener('mousemove', onDragWherePanel)
  window.removeEventListener('mouseup', stopDragWherePanel)
  document.body.style.userSelect = ''
  // 保存面板位置到 store
  store.updateWherePanelPosition(wherePanelPos.value.x, wherePanelPos.value.y)
}

function handleGroupPanelClick() {
  if (groupIsClick.value) {
    emit('groupPanelClick')
  }
}

function startDragGroupPanel(e: MouseEvent) {
  // 如果不是左键，直接返回，不处理
  if (e.button !== 0) return
  // 如果布局已锁定，禁止拖拽
  if (store.layoutLocked) return
  
  const panel = (e.currentTarget as HTMLElement)
  const rect = panel.getBoundingClientRect()
  const currentViewport = viewport.value
  const zoom = currentViewport?.zoom ?? 1
  const viewX = currentViewport?.x ?? 0
  const viewY = currentViewport?.y ?? 0
  
  isDraggingGroup.value = false
  groupIsClick.value = true
  groupDragStart.value = { x: e.clientX, y: e.clientY }
  
  if (groupPanelPos.value.x === 0 && groupPanelPos.value.y === 0) {
    const canvasRect = document.querySelector('.vue-flow')?.getBoundingClientRect()
    if (canvasRect) {
      groupPanelPos.value = {
        x: (rect.left - canvasRect.left - viewX) / zoom,
        y: (rect.top - canvasRect.top - viewY) / zoom
      }
    }
  }
  
  groupDragOffset.value = {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top
  }
  
  window.addEventListener('mousemove', onDragGroupPanel)
  window.addEventListener('mouseup', stopDragGroupPanel)
  document.body.style.userSelect = 'none'
}

function onDragGroupPanel(e: MouseEvent) {
  const dx = Math.abs(e.clientX - groupDragStart.value.x)
  const dy = Math.abs(e.clientY - groupDragStart.value.y)
  
  if (dx > 3 || dy > 3) {
    isDraggingGroup.value = true
    groupIsClick.value = false
  }
  
  if (!isDraggingGroup.value) return
  
  const canvasRect = document.querySelector('.vue-flow')?.getBoundingClientRect()
  if (!canvasRect) return
  
  const currentViewport = viewport.value
  const zoom = currentViewport?.zoom ?? 1
  const viewX = currentViewport?.x ?? 0
  const viewY = currentViewport?.y ?? 0
  
  const newX = (e.clientX - canvasRect.left - groupDragOffset.value.x - viewX) / zoom
  const newY = (e.clientY - canvasRect.top - groupDragOffset.value.y - viewY) / zoom
  
  groupPanelPos.value = { x: newX, y: newY }
}

function stopDragGroupPanel() {
  isDraggingGroup.value = false
  window.removeEventListener('mousemove', onDragGroupPanel)
  window.removeEventListener('mouseup', stopDragGroupPanel)
  document.body.style.userSelect = ''
  store.updateGroupPanelPosition(groupPanelPos.value.x, groupPanelPos.value.y)
}

function handleOrderPanelClick() {
  if (orderIsClick.value) {
    emit('orderPanelClick')
  }
}

function startDragOrderPanel(e: MouseEvent) {
  if (e.button !== 0) return
  // 如果布局已锁定，禁止拖拽
  if (store.layoutLocked) return
  
  const panel = (e.currentTarget as HTMLElement)
  const rect = panel.getBoundingClientRect()
  const currentViewport = viewport.value
  const zoom = currentViewport?.zoom ?? 1
  const viewX = currentViewport?.x ?? 0
  const viewY = currentViewport?.y ?? 0
  
  isDraggingOrder.value = false
  orderIsClick.value = true
  orderDragStart.value = { x: e.clientX, y: e.clientY }
  
  if (orderPanelPos.value.x === 0 && orderPanelPos.value.y === 0) {
    const canvasRect = document.querySelector('.vue-flow')?.getBoundingClientRect()
    if (canvasRect) {
      orderPanelPos.value = {
        x: (rect.left - canvasRect.left - viewX) / zoom,
        y: (rect.top - canvasRect.top - viewY) / zoom
      }
    }
  }
  
  orderDragOffset.value = {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top
  }
  
  window.addEventListener('mousemove', onDragOrderPanel)
  window.addEventListener('mouseup', stopDragOrderPanel)
  document.body.style.userSelect = 'none'
}

function onDragOrderPanel(e: MouseEvent) {
  const dx = Math.abs(e.clientX - orderDragStart.value.x)
  const dy = Math.abs(e.clientY - orderDragStart.value.y)
  
  if (dx > 3 || dy > 3) {
    isDraggingOrder.value = true
    orderIsClick.value = false
  }
  
  if (!isDraggingOrder.value) return
  
  const canvasRect = document.querySelector('.vue-flow')?.getBoundingClientRect()
  if (!canvasRect) return
  
  const currentViewport = viewport.value
  const zoom = currentViewport?.zoom ?? 1
  const viewX = currentViewport?.x ?? 0
  const viewY = currentViewport?.y ?? 0
  
  const newX = (e.clientX - canvasRect.left - orderDragOffset.value.x - viewX) / zoom
  const newY = (e.clientY - canvasRect.top - orderDragOffset.value.y - viewY) / zoom
  
  orderPanelPos.value = { x: newX, y: newY }
}

function stopDragOrderPanel() {
  isDraggingOrder.value = false
  window.removeEventListener('mousemove', onDragOrderPanel)
  window.removeEventListener('mouseup', stopDragOrderPanel)
  document.body.style.userSelect = ''
  store.updateOrderPanelPosition(orderPanelPos.value.x, orderPanelPos.value.y)
}

function showOrderPanelContextMenu(e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  const rect = (e.currentTarget as HTMLElement)?.closest('.canvas-area')?.getBoundingClientRect()
  const x = rect ? e.clientX - rect.left : e.clientX
  const y = rect ? e.clientY - rect.top : e.clientY
  contextMenu.value = { visible: true, x, y, type: 'orderPanel', nodeAlias: '', edgeId: '', groupTarget: 'panel', groupIndex: -1 }
}

function showOrderContextMenu(e: MouseEvent, index: number) {
  e.preventDefault()
  e.stopPropagation()
  const rect = (e.currentTarget as HTMLElement)?.closest('.canvas-area')?.getBoundingClientRect()
  const x = rect ? e.clientX - rect.left : e.clientX
  const y = rect ? e.clientY - rect.top : e.clientY
  contextMenu.value = { visible: true, x, y, type: 'orderPanel', nodeAlias: '', edgeId: '', groupTarget: 'field', groupIndex: index }
}

onMounted(async () => {
  if (props.initialConfig) {
    store.loadFromQueryConfig(props.initialConfig)
  }
  
  await nextTick()
  syncNodesFromStore()
  syncEdgesFromStore()
  
  // 初始化查询条件面板、分组面板和排序面板位置
  initWherePanelPosition()
  initGroupPanelPosition()
  initOrderPanelPosition()
})

function initWherePanelPosition() {
  nextTick(() => {
    // 优先使用保存的位置
    const savedPos = store.getWherePanelPosition()
    if (savedPos) {
      wherePanelPos.value = savedPos
      return
    }
    
    // 如果没有表节点，使用默认居中偏下位置
    if (store.tables.length === 0) {
      // 默认居中偏下位置
      const canvasRect = document.querySelector('.vue-flow')?.getBoundingClientRect()
      if (canvasRect) {
        const currentViewport = viewport.value
        const zoom = currentViewport?.zoom ?? 1
        wherePanelPos.value = {
          x: (canvasRect.width / zoom - 320) / 2, // 居中
          y: (canvasRect.height / zoom) * 0.65    // 偏下（65%高度位置）
        }
      }
      return
    }
    
    // 直接使用表节点的画布坐标（存储在 store.tables 中）
    let maxBottom = 0
    let centerX = 0
    
    store.tables.forEach(table => {
      const tableX = table.x || 0
      const tableY = table.y || 0
      // 假设表节点高度约 150px（根据实际渲染估算）
      const tableHeight = 150
      const tableWidth = 200 // 表节点宽度估算
      
      const bottom = tableY + tableHeight
      if (bottom > maxBottom) {
        maxBottom = bottom
      }
      centerX += (tableX + tableWidth / 2)
    })
    centerX /= store.tables.length
    
    // 设置查询条件面板位置到表节点下方（画布坐标）
    wherePanelPos.value = {
      x: Math.max(10, centerX - 160), // 居中，减去面板宽度一半
      y: Math.max(10, maxBottom + 15) // 表节点下方留15px间距
    }
  })
}

function initGroupPanelPosition() {
  nextTick(() => {
    // 优先使用保存的位置
    const savedPos = store.getGroupPanelPosition()
    if (savedPos) {
      groupPanelPos.value = savedPos
      return
    }
    
    // 查找查询条件面板的位置
    if (wherePanelPos.value.x !== 0 || wherePanelPos.value.y !== 0) {
      // 在查询条件面板右侧
      groupPanelPos.value = {
        x: wherePanelPos.value.x + 250, // 查询条件面板宽度 + 间距
        y: wherePanelPos.value.y
      }
      return
    }
    
    // 如果没有表节点，使用默认位置
    if (store.tables.length === 0) {
      const canvasRect = document.querySelector('.vue-flow')?.getBoundingClientRect()
      if (canvasRect) {
        const currentViewport = viewport.value
        const zoom = currentViewport?.zoom ?? 1
        groupPanelPos.value = {
          x: (canvasRect.width / zoom - 320) / 2 + 250, // 靠右
          y: (canvasRect.height / zoom) * 0.65
        }
      }
      return
    }
    
    // 在查询条件面板右侧（如果没有设置wherePanelPos，先计算一个基础位置）
    let maxBottom = 0
    let centerX = 0
    
    store.tables.forEach(table => {
      const tableX = table.x || 0
      const tableY = table.y || 0
      const tableHeight = 150
      const tableWidth = 200
      
      const bottom = tableY + tableHeight
      if (bottom > maxBottom) {
        maxBottom = bottom
      }
      centerX += (tableX + tableWidth / 2)
    })
    centerX /= store.tables.length
    
    groupPanelPos.value = {
      x: Math.max(10, centerX + 120), // 查询条件面板右侧
      y: Math.max(10, maxBottom + 15)
    }
  })
}

function initOrderPanelPosition() {
  nextTick(() => {
    // 优先使用保存的位置
    const savedPos = store.getOrderPanelPosition()
    if (savedPos) {
      orderPanelPos.value = savedPos
      return
    }
    
    // 查找分组面板的位置
    if (groupPanelPos.value.x !== 0 || groupPanelPos.value.y !== 0) {
      // 在分组面板右侧
      orderPanelPos.value = {
        x: groupPanelPos.value.x + 250, // 分组面板宽度 + 间距
        y: groupPanelPos.value.y
      }
      return
    }
    
    // 查找查询条件面板的位置
    if (wherePanelPos.value.x !== 0 || wherePanelPos.value.y !== 0) {
      // 在查询条件面板右侧（如果没有分组面板）
      orderPanelPos.value = {
        x: wherePanelPos.value.x + 500, // 查询条件面板宽度 + 分组面板宽度 + 间距
        y: wherePanelPos.value.y
      }
      return
    }
    
    // 如果没有表节点，使用默认位置
    if (store.tables.length === 0) {
      const canvasRect = document.querySelector('.vue-flow')?.getBoundingClientRect()
      if (canvasRect) {
        const currentViewport = viewport.value
        const zoom = currentViewport?.zoom ?? 1
        orderPanelPos.value = {
          x: (canvasRect.width / zoom - 320) / 2 + 500, // 最右侧
          y: (canvasRect.height / zoom) * 0.65
        }
      }
      return
    }
    
    // 在分组面板右侧（如果没有设置groupPanelPos，先计算一个基础位置）
    let maxBottom = 0
    let centerX = 0
    
    store.tables.forEach(table => {
      const tableX = table.x || 0
      const tableY = table.y || 0
      const tableHeight = 150
      const tableWidth = 200
      
      const bottom = tableY + tableHeight
      if (bottom > maxBottom) {
        maxBottom = bottom
      }
      centerX += (tableX + tableWidth / 2)
    })
    centerX /= store.tables.length
    
    orderPanelPos.value = {
      x: Math.max(10, centerX + 370), // 查询条件面板右侧 + 分组面板右侧
      y: Math.max(10, maxBottom + 15)
    }
  })
}

async function handleDrop(e: DragEvent) {
  e.preventDefault()
  const tableName = e.dataTransfer?.getData('tableName')
  if (!tableName) return

  if (store.tables.length >= 2) {
    emit('maxTablesReached')
    return
  }

  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const dropX = e.clientX - rect.left
  const dropY = e.clientY - rect.top

  const added = store.addTable(tableName)
  if (added) {
    const canvasPos = project({ x: dropX, y: dropY })
    added.x = canvasPos.x - 120
    added.y = canvasPos.y - 40
  }
}

function onPaneClick() {
  closeContextMenu()
  emit('paneClick')
}

function onNodeDragStop(e: any) {
  const alias = e.node.id
  const pos = e.node.position
  store.updateTablePosition(alias, pos.x, pos.y)
  emit('layoutChange')
}

function formatValue(value: any): string {
  if (value === null || value === undefined) return 'NULL'
  if (Array.isArray(value)) return `(${value.join(', ')})`
  return String(value)
}

function truncateSql(sql: string, maxLen: number): string {
  if (!sql) return ''
  if (sql.length <= maxLen) return sql
  return sql.substring(0, maxLen) + '...'
}

defineExpose({ syncNodesFromStore, syncEdgesFromStore })
</script>

<style scoped>
.canvas-area {
  width: 100%;
  flex: 1;
  min-height: 0;
  background: #16213e;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.canvas-area :deep(.vue-flow) {
  background: #16213e;
  flex: 1;
  min-height: 0;
  height: 100%;
}
.canvas-area :deep(.vue-flow__container) {
  width: 100%;
  height: 100%;
}
.canvas-area :deep(.vue-flow__background) {
  background: #16213e;
}
.canvas-area :deep(.vue-flow__minimap) {
  background: #1e3a5f;
}
.canvas-area :deep(.vue-flow__edge-textbg) {
  fill: #1e3a5f;
}
.canvas-area :deep(.vue-flow__edge-text) {
  fill: #e2e8f0;
}

.layout-lock-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(30, 58, 95, 0.8);
  border: 1px solid #2d4a6f;
  border-radius: 6px;
  cursor: pointer;
  color: #94a3b8;
  transition: all 0.2s;
  z-index: 20;
}
.layout-lock-btn:hover {
  background: rgba(30, 58, 95, 1);
  color: #e2e8f0;
  border-color: #3b82f6;
}
.layout-lock-btn.locked {
  color: #f59e0b;
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.15);
}
.layout-lock-btn.locked:hover {
  background: rgba(245, 158, 11, 0.25);
}

.where-panel {
  position: absolute;
  top: auto;
  bottom: 10px; /* 距离底部 */
  left: 50%;
  transform: translateX(-50%);
  background: #1e3a5f;
  border: 1.5px solid #2d4a6f;
  border-radius: 6px;
  padding: 0; /* 改为0，让header处理padding */
  width: 230px; /* 缩小宽度 */
  min-width: 200px; /* 最小宽度 */
  height: 240px; /* 默认显示约7行 */
  max-height: 320px; /* 最大支持12行 */
  overflow-y: auto; /* 超出时显示滚动条 */
  overflow-x: hidden;
  z-index: 10;
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
  display: flex;
  flex-direction: column;
}

/* 拖拽时的样式 */
.where-panel.is-dragging {
  cursor: grabbing !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  border-color: #3b82f6;
  opacity: 0.95;
  z-index: 100;
}

/* 拖拽手柄（标题栏） */
.where-header {
  font-size: 11px;
  font-weight: 600;
  color: #e2e8f0;
  padding: 5px 8px;
  display: flex;
  align-items: center;
  gap: 5px;
  background: rgba(15, 52, 96, 0.9);
  border-bottom: 1px solid #2d4a6f;
  border-radius: 6px 6px 0 0;
  cursor: grab;
  user-select: none;
  transition: background 0.2s;
  flex-shrink: 0;
}

.where-header.drag-handle:hover {
  background: rgba(30, 58, 95, 0.9);
}

.where-header.drag-handle:active,
.is-dragging .where-header {
  cursor: grabbing !important;
}

/* 拖拽图标 */
.drag-icon {
  font-size: 11px;
  color: #64748b;
  opacity: 0.6;
  letter-spacing: -1px;
}

/* 自定义滚动条样式 */
.where-panel::-webkit-scrollbar {
  width: 6px;
}

.where-panel::-webkit-scrollbar-track {
  background: rgba(15, 23, 42, 0.8);
  border-radius: 3px;
}

.where-panel::-webkit-scrollbar-thumb {
  background: rgba(59, 130, 246, 0.4);
  border-radius: 3px;
}

.where-panel::-webkit-scrollbar-thumb:hover {
  background: rgba(59, 130, 246, 0.6);
}

/* ========== 画布区域全局滚动条样式 ========== */
.canvas-area::-webkit-scrollbar,
.canvas-area *::-webkit-scrollbar,
:deep(.vue-flow)::-webkit-scrollbar,
:deep(.vue-flow *)::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.canvas-area::-webkit-scrollbar-track,
.canvas-area *::-webkit-scrollbar-track,
:deep(.vue-flow)::-webkit-scrollbar-track,
:deep(.vue-flow *)::-webkit-scrollbar-track {
  background: rgba(15, 23, 42, 0.6);
  border-radius: 4px;
}

.canvas-area::-webkit-scrollbar-thumb,
.canvas-area *::-webkit-scrollbar-thumb,
:deep(.vue-flow)::-webkit-scrollbar-thumb,
:deep(.vue-flow *)::-webkit-scrollbar-thumb {
  background: rgba(59, 130, 246, 0.4);
  border-radius: 4px;
  transition: background 0.2s ease;
}

.canvas-area::-webkit-scrollbar-thumb:hover,
.canvas-area *::-webkit-scrollbar-thumb:hover,
:deep(.vue-flow)::-webkit-scrollbar-thumb:hover,
:deep(.vue-flow *)::-webkit-scrollbar-thumb:hover {
  background: rgba(59, 130, 246, 0.7);
}

/* 水平滚动条 */
.canvas-area::-webkit-scrollbar-thumb:horizontal,
.canvas-area *::-webkit-scrollbar-thumb:horizontal,
:deep(.vue-flow)::-webkit-scrollbar-thumb:horizontal,
:deep(.vue-flow *)::-webkit-scrollbar-thumb:horizontal {
  background: rgba(59, 130, 246, 0.4);
}

.canvas-area::-webkit-scrollbar-thumb:hover:horizontal,
.canvas-area *::-webkit-scrollbar-thumb:hover:horizontal,
:deep(.vue-flow)::-webkit-scrollbar-thumb:hover:horizontal,
:deep(.vue-flow *)::-webkit-scrollbar-thumb:hover:horizontal {
  background: rgba(59, 130, 246, 0.7);
}

.where-panel:hover {
  border-color: #3b82f6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.where-header {
  font-size: 11px;
  font-weight: 600;
  color: #e2e8f0;
  margin-bottom: 0;
  display: flex;
  align-items: center;
  gap: 5px;
}

.click-hint {
  font-size: 10px;
  font-weight: 400;
  color: #64748b;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

.where-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 6px; /* 内容区域的内边距 */
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.where-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 3px;
  padding: 3px 6px;
  background: rgba(59, 130, 246, 0.08);
  border-radius: 3px;
  font-size: 11px;
  line-height: 1.4;
  transition: all 0.2s;
}

.where-item:hover {
  background: rgba(59, 130, 246, 0.15);
}

.where-item.is-wrapped {
  border-left: 2px solid #22c55e;
  padding-left: 5px;
}

/* 逻辑符样式 */
.where-logic {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 16px;
  font-size: 9px;
  font-weight: 700;
  color: #60a5fa;
  background: rgba(59, 130, 246, 0.15);
  border-radius: 3px;
  padding: 0 4px;
  text-transform: uppercase;
}

.where-logic.is-or {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.15);
}

/* 括号样式 */
.where-bracket {
  color: #22c55e;
  font-weight: 600;
  font-size: 11px;
}

/* 字段/操作符/值样式 */
.where-field {
  color: #60a5fa;
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.where-op {
  color: #fbbf24;
  margin: 0 1px;
  white-space: nowrap;
}

.where-value {
  color: #4ade80;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 自定义SQL样式 */
.where-custom {
  color: #a78bfa;
  font-style: italic;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 未配置状态 */
.where-empty-inline {
  color: #64748b;
  font-style: italic;
}

.where-empty {
  padding: 12px; /* 内容区域的内边距 */
  margin: 8px;
  text-align: center;
  color: #94a3b8;
  font-size: 11px;
  border: 1px dashed #475569;
  border-radius: 3px;
}

.where-panel:hover .where-empty {
  color: #cbd5e1;
  border-color: #64748b;
}

/* 排序面板样式 */
.order-panel {
  position: absolute;
  top: auto;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  background: #1e3a5f;
  border: 1.5px solid #2d4a6f;
  border-radius: 6px;
  padding: 0;
  width: 230px;
  min-width: 200px;
  height: 200px;
  max-height: 280px;
  overflow-y: auto;
  overflow-x: hidden;
  z-index: 10;
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
  display: flex;
  flex-direction: column;
}

.order-panel.is-dragging {
  cursor: grabbing !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  border-color: #f59e0b;
  opacity: 0.95;
  z-index: 100;
}

.order-header {
  font-size: 11px;
  font-weight: 600;
  color: #e2e8f0;
  padding: 5px 8px;
  display: flex;
  align-items: center;
  gap: 5px;
  background: rgba(15, 52, 96, 0.9);
  border-bottom: 1px solid #2d4a6f;
  border-radius: 6px 6px 0 0;
  cursor: grab;
  user-select: none;
  transition: background 0.2s;
  flex-shrink: 0;
}

.order-header.drag-handle:hover {
  background: rgba(30, 58, 95, 0.9);
}

.order-header.drag-handle:active,
.is-dragging .order-header {
  cursor: grabbing !important;
}

.order-panel:hover {
  border-color: #f59e0b;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 6px;
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  background: rgba(245, 158, 11, 0.08);
  border-radius: 3px;
  font-size: 11px;
  line-height: 1.4;
  transition: all 0.2s;
  cursor: context-menu;
  pointer-events: auto;
  position: relative;
  z-index: 1;
}

.order-item:hover {
  background: rgba(245, 158, 11, 0.15);
}

.order-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 9px;
  font-weight: 600;
  color: #f59e0b;
  background: rgba(245, 158, 11, 0.15);
  border-radius: 3px;
  flex-shrink: 0;
}

.order-field {
  color: #a78bfa;
  font-weight: 500;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-custom {
  color: #22c55e;
  font-style: italic;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-direction {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  font-size: 10px;
  font-weight: 600;
  border-radius: 3px;
  flex-shrink: 0;
}

.order-direction.asc {
  color: #22c55e;
  background: rgba(34, 197, 94, 0.15);
}

.order-direction.desc {
  color: #0894f1;
}

/* 分组面板样式 */
.group-panel {
  position: absolute;
  top: auto;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  background: #1e3a5f;
  border: 1.5px solid #2d4a6f;
  border-radius: 6px;
  padding: 0;
  width: 230px;
  min-width: 200px;
  height: 280px;
  max-height: 360px;
  overflow-y: auto;
  overflow-x: hidden;
  z-index: 10;
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
  display: flex;
  flex-direction: column;
}

.group-panel.is-dragging {
  cursor: grabbing !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  border-color: #22c55e;
  opacity: 0.95;
  z-index: 100;
}

.group-header {
  font-size: 11px;
  font-weight: 600;
  color: #e2e8f0;
  padding: 5px 8px;
  display: flex;
  align-items: center;
  gap: 5px;
  background: rgba(15, 52, 96, 0.9);
  border-bottom: 1px solid #2d4a6f;
  border-radius: 6px 6px 0 0;
  cursor: grab;
  user-select: none;
  transition: background 0.2s;
  flex-shrink: 0;
}

.group-header.drag-handle:hover {
  background: rgba(30, 58, 95, 0.9);
}

.group-header.drag-handle:active,
.is-dragging .group-header {
  cursor: grabbing !important;
}

.group-panel:hover {
  border-color: #22c55e;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.group-section {
  padding: 6px 8px;
  border-bottom: 1px solid #2d4a6f;
}

.group-section:last-child {
  border-bottom: none;
}

.group-section-title {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 4px;
}

.group-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.group-field-tag {
  padding: 2px 8px;
  background: rgba(59, 130, 246, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 3px;
  font-size: 10px;
  color: #60a5fa;
  cursor: context-menu;
  pointer-events: auto;
  position: relative;
  z-index: 1;
}

.group-conditions {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.group-condition-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 3px;
  padding: 3px 6px;
  background: rgba(167, 138, 250, 0.08);
  border-radius: 3px;
  font-size: 11px;
  line-height: 1.4;
  transition: all 0.2s;
  cursor: context-menu;
  pointer-events: auto;
  position: relative;
  z-index: 1;
}

.group-condition-item:hover {
  background: rgba(167, 138, 250, 0.15);
}

.group-logic {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 16px;
  font-size: 9px;
  font-weight: 700;
  color: #a78bfa;
  background: rgba(167, 138, 250, 0.15);
  border-radius: 3px;
  padding: 0 4px;
  text-transform: uppercase;
}

.group-logic.is-or {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.15);
}

.group-field {
  color: #a78bfa;
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-op {
  color: #fbbf24;
  margin: 0 1px;
  white-space: nowrap;
}

.group-value {
  color: #4ade80;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-custom {
  color: #22c55e;
  font-style: italic;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-empty-inline {
  color: #64748b;
  font-style: italic;
}

.group-empty {
  padding: 8px;
  margin: 0;
  text-align: center;
  color: #94a3b8;
  font-size: 10px;
  border: 1px dashed #475569;
  border-radius: 3px;
}

.group-panel:hover .group-empty {
  color: #cbd5e1;
  border-color: #64748b;
}

/* 分组面板滚动条 */
.group-panel::-webkit-scrollbar {
  width: 6px;
}

/* SQL Tooltip 样式 */
:deep(.sql-tooltip) {
  background: #0f172a !important;
  color: #e2e8f0 !important;
  padding: 8px 12px !important;
  border-radius: 4px !important;
  font-size: 11px !important;
  font-family: 'Courier New', Courier, monospace !important;
  max-width: 500px !important;
  white-space: pre-wrap !important;
  word-wrap: break-word !important;
  line-height: 1.5 !important;
  border: 1px solid #2d4a6f !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4) !important;
}

.group-panel::-webkit-scrollbar-track {
  background: rgba(15, 23, 42, 0.8);
  border-radius: 3px;
}

.group-panel::-webkit-scrollbar-thumb {
  background: rgba(34, 197, 94, 0.4);
  border-radius: 3px;
}

.group-panel::-webkit-scrollbar-thumb:hover {
  background: rgba(34, 197, 94, 0.6);
}

.context-menu {
  position: absolute;
  z-index: 100;
  background: #1e3a5f;
  border: 1px solid #3b82f6;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
  padding: 4px 0;
  min-width: 160px;
  font-size: 13px;
}
.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  cursor: pointer;
  color: #e2e8f0;
  transition: background 0.1s;
}
.context-menu-item:hover {
  background: rgba(59, 130, 246, 0.2);
}
.context-menu-item.danger {
  color: #f87171;
}
.context-menu-item.danger:hover {
  background: rgba(239, 68, 68, 0.15);
}
.menu-icon {
  font-size: 14px;
  width: 18px;
  text-align: center;
  flex-shrink: 0;
}
.context-menu-divider {
  height: 1px;
  background: #2d4a6f;
  margin: 4px 8px;
}
</style>
