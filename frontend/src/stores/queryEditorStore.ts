import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type {
  QueryConfig,
  CanvasConfig,
  CanvasTableConfig,
  CanvasJoinConfig,
  CustomField,
  WhereCondition,
  OrderByItem,
  ConditionItem,
  FieldMeta
} from '@/types/queryEditor'

export const useQueryEditorStore = defineStore('queryEditor', () => {
  // ========== 模式 ==========
  const mode = ref<'canvas' | 'sql' | 'dual'>('dual')
  const dataSourceId = ref('master')

  // ========== SQL ==========
  const sqlText = ref('')

  // ========== 画布配置 ==========
  const canvasConfig = ref<CanvasConfig>(createEmptyCanvasConfig())

  // ========== 选中的表节点（用于右侧属性面板） ==========
  const selectedTableAlias = ref<string | null>(null)
  const selectedJoinId = ref<string | null>(null)

  // ========== 属性面板定位 ==========
  const focusCustomFields = ref(false)
  const activeTabName = ref<'basic' | 'where' | 'groupBy' | 'orderBy' | 'recommend' | null>(null)

  function triggerFocusCustomFields() {
    focusCustomFields.value = true
    activeTabName.value = 'basic'
    setTimeout(() => {
      focusCustomFields.value = false
    }, 100)
  }

  function setActiveTab(tab: 'basic' | 'where' | 'groupBy' | 'orderBy' | 'recommend') {
  // 先清空，再设置，确保响应式能正确触发
  activeTabName.value = null
  // 使用 nextTick 确保响应式更新
  setTimeout(() => {
    activeTabName.value = tab
  }, 0)
}

  // ========== 布局锁定 ==========
  const layoutLocked = ref(false)

  // ========== 计算属性 ==========
  const tables = computed(() => canvasConfig.value.tables)
  const joins = computed(() => canvasConfig.value.joins)

  const selectedTable = computed(() => {
    if (!selectedTableAlias.value) return null
    return canvasConfig.value.tables.find(t => t.alias === selectedTableAlias.value) || null
  })

  const selectedJoin = computed(() => {
    if (!selectedJoinId.value) return null
    return canvasConfig.value.joins.find(j => j.id === selectedJoinId.value) || null
  })

  const allTableNames = computed(() => canvasConfig.value.tables.map(t => t.tableName))

  // ========== 方法 ==========

  function createEmptyCanvasConfig(): CanvasConfig {
    return {
      tables: [],
      joins: [],
      where: null,
      groupBy: [],
      having: null,
      orderBy: [],
      limit: 100,
      distinct: false,
      customSqlFragment: null,
      wherePanelPosition: undefined,
      groupPanelPosition: undefined
    }
  }

  function reset() {
    sqlText.value = ''
    canvasConfig.value = createEmptyCanvasConfig()
    selectedTableAlias.value = null
    selectedJoinId.value = null
    mode.value = 'dual'
  }

  function loadFromQueryConfig(config: Partial<QueryConfig>) {
    if (config.mode) mode.value = config.mode
    if (config.sql) sqlText.value = config.sql
    if (config.canvasConfig) {
      canvasConfig.value = {
        ...createEmptyCanvasConfig(),
        ...config.canvasConfig
      }
      if (config.canvasConfig.layoutLocked !== undefined) {
        layoutLocked.value = config.canvasConfig.layoutLocked
      }
    }
  }

  function exportToQueryConfig(): QueryConfig {
    // 过滤空的自定义SQL条件
    const where = canvasConfig.value.where
    const cleanedWhere = where ? {
      ...where,
      conditions: where.conditions.filter(c => {
        // 过滤掉空的自定义SQL行
        if (c.type === 'custom' && (!c.customSql || !c.customSql.trim())) {
          return false
        }
        // 过滤掉空的字段条件
        if (c.type === 'field' && (!c.field || !c.field.trim())) {
          return false
        }
        return true
      })
    } : where

    return {
      mode: mode.value,
      sql: sqlText.value,
      canvasConfig: { ...canvasConfig.value, where: cleanedWhere, layoutLocked: layoutLocked.value }
    }
  }

  // ========== 查询条件面板位置管理 ==========
  function updateWherePanelPosition(x: number, y: number) {
    canvasConfig.value.wherePanelPosition = { x, y }
  }

  function getWherePanelPosition() {
    return canvasConfig.value.wherePanelPosition
  }

  // ========== 分组面板位置管理 ==========
  function updateGroupPanelPosition(x: number, y: number) {
    canvasConfig.value.groupPanelPosition = { x, y }
  }

  function getGroupPanelPosition() {
    return canvasConfig.value.groupPanelPosition
  }

  // ========== 排序面板位置管理 ==========
  function updateOrderPanelPosition(x: number, y: number) {
    canvasConfig.value.orderPanelPosition = { x, y }
  }

  function getOrderPanelPosition() {
    return canvasConfig.value.orderPanelPosition
  }

  function addTable(tableName: string, alias?: string, posX?: number, posY?: number) {
    const existingCount = canvasConfig.value.tables.length
    // 限制最多两张表
    if (existingCount >= 2) {
      return null
    }
    const tableAlias = alias || generateAlias(tableName, existingCount)
    const defaultX = 60 + existingCount * 360
    const defaultY = 60 + existingCount * 180
    const newTable: CanvasTableConfig = {
      tableName,
      alias: tableAlias,
      x: posX ?? defaultX,
      y: posY ?? defaultY,
      expanded: true,
      selectedFields: [],
      fieldAliases: {},
      fieldAggregations: {},
      customFields: [],
      dataSourceId: dataSourceId.value
    }
    canvasConfig.value.tables.push(newTable)
    return newTable
  }

  function removeTable(alias: string) {
    canvasConfig.value.tables = canvasConfig.value.tables.filter(t => t.alias !== alias)
    canvasConfig.value.joins = canvasConfig.value.joins.filter(
      j => j.sourceTable !== alias && j.targetTable !== alias
    )
    if (selectedTableAlias.value === alias) selectedTableAlias.value = null
  }

  function toggleFieldSelection(tableAlias: string, fieldName: string) {
    const table = canvasConfig.value.tables.find(t => t.alias === tableAlias)
    if (!table) return
    const idx = table.selectedFields.indexOf(fieldName)
    if (idx >= 0) {
      table.selectedFields.splice(idx, 1)
    } else {
      table.selectedFields.push(fieldName)
    }
  }

  function addJoin(join: Omit<CanvasJoinConfig, 'id'>): CanvasJoinConfig {
    const newJoin: CanvasJoinConfig = {
      ...join,
      id: 'j' + Date.now() + Math.random().toString(36).substr(2, 5)
    }
    canvasConfig.value.joins.push(newJoin)
    return newJoin
  }

  function removeJoin(id: string) {
    canvasConfig.value.joins = canvasConfig.value.joins.filter(j => j.id !== id)
    if (selectedJoinId.value === id) selectedJoinId.value = null
  }

  function cycleJoinType(id: string): 'INNER' | 'LEFT' | 'RIGHT' | 'FULL' {
    const join = canvasConfig.value.joins.find(j => j.id === id)
    if (!join) return 'INNER'
    const types: ('INNER' | 'LEFT' | 'RIGHT' | 'FULL')[] = ['INNER', 'LEFT', 'RIGHT', 'FULL']
    const idx = types.indexOf(join.joinType as 'INNER' | 'LEFT' | 'RIGHT' | 'FULL')
    const newType = types[(idx + 1) % types.length]
    join.joinType = newType
    return newType
  }

  function updateJoinField(id: string, field: 'sourceField' | 'targetField', value: string) {
    const join = canvasConfig.value.joins.find(j => j.id === id)
    if (!join) return
    if (field === 'sourceField') {
      join.sourceField = value
    } else {
      join.targetField = value
    }
  }

  function setWhere(where: WhereCondition | null) {
    canvasConfig.value = {
      ...canvasConfig.value,
      where: where ? { ...where, conditions: [...where.conditions] } : null
    }
  }

  function addWhereCondition(condition: ConditionItem) {
    if (!canvasConfig.value.where) {
      canvasConfig.value.where = { logic: 'AND', conditions: [], groups: [] }
    }
    canvasConfig.value.where.conditions.push(condition)
  }

  function removeWhereCondition(index: number) {
    if (canvasConfig.value?.where) {
      canvasConfig.value.where.conditions.splice(index, 1)
    }
  }

  function setGroupBy(fields: string[]) {
    canvasConfig.value.groupBy = fields
    if (!fields || fields.length === 0) {
      canvasConfig.value.having = null
    }
  }

  function addGroupByField(field: string) {
    if (!canvasConfig.value.groupBy.includes(field)) {
      canvasConfig.value.groupBy.push(field)
    }
  }

  function removeGroupByField(index: number) {
    if (index >= 0 && index < canvasConfig.value.groupBy.length) {
      canvasConfig.value.groupBy.splice(index, 1)
      if (canvasConfig.value.groupBy.length === 0) {
        canvasConfig.value.having = null
      }
    }
  }

  function setHaving(having: WhereCondition | null) {
    canvasConfig.value.having = having
  }

  function addHavingCondition(condition: ConditionItem) {
    if (!canvasConfig.value.having) {
      canvasConfig.value.having = { logic: 'AND', conditions: [], groups: [] }
    }
    canvasConfig.value.having.conditions.push(condition)
  }

  function removeHavingCondition(index: number) {
    if (canvasConfig.value?.having) {
      canvasConfig.value.having.conditions.splice(index, 1)
    }
  }

  function clearGroupBy() {
    canvasConfig.value.groupBy = []
    canvasConfig.value.having = null
  }

  function setOrderBy(items: OrderByItem[]) {
    canvasConfig.value.orderBy = items
  }

  function setLimit(limit: number) {
    canvasConfig.value.limit = limit
  }

  function setDistinct(distinct: boolean) {
    canvasConfig.value.distinct = distinct
  }

  function updateTablePosition(alias: string, x: number, y: number) {
    const table = canvasConfig.value.tables.find(t => t.alias === alias)
    if (table) {
      table.x = x
      table.y = y
    }
  }

  function updateTableAlias(oldAlias: string, newAlias: string) {
    const table = canvasConfig.value.tables.find(t => t.alias === oldAlias)
    if (table) {
      table.alias = newAlias
      canvasConfig.value.joins.forEach(j => {
        if (j.sourceTable === oldAlias) j.sourceTable = newAlias
        if (j.targetTable === oldAlias) j.targetTable = newAlias
      })
    }
  }

  function updateTableFields(alias: string, fields: FieldMeta[]) {
    const table = canvasConfig.value.tables.find(t => t.alias === alias)
    if (table) {
      table.fields = fields
    }
  }

  function addCustomField(tableAlias: string): CustomField | null {
    const table = canvasConfig.value.tables.find(t => t.alias === tableAlias)
    if (!table) return null
    if (!table.customFields) table.customFields = []
    const id = 'cf_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5)
    const cf: CustomField = { id, expression: '', alias: '' }
    table.customFields.push(cf)
    return cf
  }

  function removeCustomField(tableAlias: string, cfId: string) {
    const table = canvasConfig.value.tables.find(t => t.alias === tableAlias)
    if (!table || !table.customFields) return
    table.customFields = table.customFields.filter(cf => cf.id !== cfId)
  }

  function updateCustomField(tableAlias: string, cfId: string, updates: Partial<CustomField>) {
    const table = canvasConfig.value.tables.find(t => t.alias === tableAlias)
    if (!table || !table.customFields) return
    const cf = table.customFields.find(c => c.id === cfId)
    if (!cf) return
    if (updates.expression !== undefined) cf.expression = updates.expression
    if (updates.alias !== undefined) cf.alias = updates.alias
  }

  function setSql(sql: string) {
    sqlText.value = sql
  }

  function selectTable(alias: string | null) {
    selectedTableAlias.value = alias
    selectedJoinId.value = null
  }

  function selectJoin(id: string | null) {
    selectedJoinId.value = id
    selectedTableAlias.value = null
  }

  function generateAlias(tableName: string, index: number): string {
    const parts = tableName.split('_')
    let alias = parts.map(p => p.charAt(0)).join('').toLowerCase()
    if (!alias) alias = 't'
    return alias + (index > 0 ? index : '')
  }

  function ensureUniqueAlias(base: string, used: string[]): string {
    if (!used.includes(base)) return base
    let i = 1
    while (used.includes(`${base}_${i}`)) i++
    return `${base}_${i}`
  }

  function validateConfig(): { valid: boolean; errors: string[] } {
    const errors: string[] = []
    const tables = canvasConfig.value.tables

    const allAliases: string[] = []
    const customAliasSet = new Set<string>()
    tables.forEach(t => {
      if (t.customFields) {
        t.customFields.forEach(cf => {
          if (!cf.expression) {
            errors.push(`自定义字段表达式不能为空（表 ${t.alias}）`)
          }
          if (!cf.alias) {
            errors.push(`自定义字段别名不能为空（表 ${t.alias}）`)
          } else if (customAliasSet.has(cf.alias)) {
            errors.push(`自定义字段别名重复: ${cf.alias}（表 ${t.alias}），请修改别名`)
          } else {
            customAliasSet.add(cf.alias)
            allAliases.push(cf.alias)
          }
        })
      }
    })

    const joins = canvasConfig.value.joins
    if (tables.length === 2 && joins.length === 0) {
      errors.push('两张表之间未建立关联')
    }

    return { valid: errors.length === 0, errors }
  }

  function parseWhereClause(whereStr: string): WhereCondition | null {
    const trimmed = whereStr.trim()
    if (!trimmed) return null

    const conditions: ConditionItem[] = []
    
    // 智能分割：考虑括号嵌套
    const parts = splitConditions(trimmed)
    
    let hasOr = false
    
    parts.forEach((part, idx) => {
      const cond = part.condition.trim()
      if (!cond) return
      
      // 记录是否有OR逻辑符
      if (part.logic === 'OR') hasOr = true
      
      // 尝试匹配字段条件
      const eqMatch = cond.match(/^(\w+\.\w+)\s*(=|!=|>|<|>=|<=|LIKE|NOT\s+LIKE|IN|NOT\s+IN|IS\s+NULL|IS\s+NOT\s+NULL)\s*(.*)$/is)
      if (eqMatch && !cond.match(/^\(/)) {
        // 去掉外层括号（如果有）
        let value: any = eqMatch[3].trim()
        // 处理值：去掉引号和括号
        if ((value.startsWith("'") && value.endsWith("'")) || 
            (value.startsWith('"') && value.endsWith('"'))) {
          value = value.slice(1, -1)
        }
        
        if (eqMatch[2].toUpperCase() === 'IS NULL' || eqMatch[2].toUpperCase() === 'IS NOT NULL') {
          value = null
        }
        
        conditions.push({
          id: 'c_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5),
          type: 'field',
          field: eqMatch[1],
          operator: eqMatch[2].toUpperCase(),
          value,
          logic: idx > 0 ? (part.logic || 'AND') : undefined,
          wrapped: cond.startsWith('(') && cond.endsWith(')')
        })
      } else {
        // 自定义SQL条件（包括 EXISTS、子查询、复杂表达式等）
        // 去掉外层括号（如果整个条件被括号包裹）
        let customSql = cond
        if (customSql.startsWith('(') && customSql.endsWith(')')) {
          // 检查是否是完整的括号对（不是部分括号）
          let depth = 0
          let completePair = true
          for (let i = 0; i < customSql.length; i++) {
            if (customSql[i] === '(') depth++
            else if (customSql[i] === ')') depth--
            if (depth === 0 && i < customSql.length - 1) {
              completePair = false
              break
            }
          }
          if (completePair && depth === 0) {
            customSql = customSql.slice(1, -1).trim()
          }
        }
        
        conditions.push({
          id: 'c_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5),
          type: 'custom',
          customSql,
          logic: idx > 0 ? (part.logic || 'AND') : undefined,
          wrapped: cond.startsWith('(') && cond.endsWith(')')
        })
      }
    })
    
    if (conditions.length === 0) return null
    
    return {
      logic: hasOr ? 'OR' : 'AND',
      conditions,
      groups: []
    }
  }

  function splitConditions(sql: string): Array<{ condition: string; logic?: 'AND' | 'OR' }> {
    const result: Array<{ condition: string; logic?: 'AND' | 'OR' }> = []
    let current = ''
    let depth = 0
    let i = 0
    
    while (i < sql.length) {
      const char = sql[i]
      
      // 跟踪括号深度
      if (char === '(') {
        depth++
        current += char
        i++
      } else if (char === ')') {
        depth--
        current += char
        i++
      } else if (depth === 0) {
        // 在最外层，检查是否是 AND 或 OR 关键字
        const remaining = sql.substring(i)
        const orMatch = remaining.match(/^\s+OR\s+/i)
        const andMatch = remaining.match(/^\s+AND\s+/i)
        
        if (orMatch) {
          result.push({ condition: current.trim(), logic: undefined })
          current = ''
          i += orMatch[0].length
          // OR 会作为下一个条件的逻辑符
        } else if (andMatch) {
          result.push({ condition: current.trim(), logic: 'AND' })
          current = ''
          i += andMatch[0].length
        } else {
          current += char
          i++
        }
      } else {
        current += char
        i++
      }
    }
    
    // 添加最后一个条件
    if (current.trim()) {
      result.push({ condition: current.trim(), logic: undefined })
    }
    
    return result
  }

  function buildWhereClause(where: WhereCondition): string {
    const parts: string[] = []

    where.conditions.forEach((c, idx) => {
      let cond: string | null = null

      if (c.type === 'field' && c.field) {
        const value = c.value
        if (['IS NULL', 'IS NOT NULL'].includes(c.operator || '')) {
          cond = `${c.field} ${c.operator}`
        } else if (['IN', 'NOT IN'].includes(c.operator || '')) {
          cond = `${c.field} ${c.operator} (${value})`
        } else if (c.operator === 'LIKE' || c.operator === 'NOT LIKE') {
          cond = `${c.field} ${c.operator} '${value}'`
        } else if (typeof value === 'number') {
          cond = `${c.field} ${c.operator} ${value}`
        } else {
          cond = `${c.field} ${c.operator} '${value}'`
        }
      } else if (c.type === 'custom' && c.customSql) {
        cond = c.customSql
      }

      if (cond) {
        if (c.leftBracket) {
          cond = `(${cond}`
        }
        if (c.rightBracket) {
          cond = `${cond})`
        }

        // 添加逻辑符（AND/OR），如果没有指定则默认使用 AND
        if (idx > 0) {
          parts.push(c.logic || 'AND')
        }

        parts.push(cond)
      }
    })

    where.groups.forEach((g, idx) => {
      const subSql = buildWhereClause(g)
      if (subSql) {
        if (idx > 0 || where.conditions.length > 0) {
          parts.push(where.logic)
        }
        parts.push(`(${subSql})`)
      }
    })

    return parts.join(' ')
  }

  function generateSql(): string {
    const tables = canvasConfig.value.tables
    const joins = canvasConfig.value.joins
    const where = canvasConfig.value.where
    const groupBy = canvasConfig.value.groupBy
    const having = canvasConfig.value.having
    const orderBy = canvasConfig.value.orderBy
    const limit = canvasConfig.value.limit
    const distinct = canvasConfig.value.distinct

    if (tables.length === 0) return ''

    const parts: string[] = ['SELECT']
    if (distinct) parts.push('DISTINCT')

    const selectParts: string[] = []
    const usedFieldNames: string[] = []
    tables.forEach(t => {
      if (t.selectedFields.length === 0 && t.customFields.length === 0) {
        selectParts.push(`${t.alias}.*`)
      } else {
        if (t.selectedFields.includes('*')) {
          selectParts.push(`${t.alias}.*`)
        } else {
          t.selectedFields.forEach(f => {
            const fieldAlias = t.fieldAliases[f]
            let expr = `${t.alias}.${f}`
            if (fieldAlias) {
              expr += ` AS ${fieldAlias}`
              usedFieldNames.push(fieldAlias)
            } else if (usedFieldNames.includes(f)) {
              const autoAlias = ensureUniqueAlias(f, usedFieldNames)
              usedFieldNames.push(autoAlias)
              expr += ` AS ${autoAlias}`
            } else {
              usedFieldNames.push(f)
            }
            selectParts.push(expr)
          })
        }
        t.customFields.forEach(cf => {
          if (!cf.expression) return
          let expr = cf.expression
          if (cf.alias) expr += ` AS ${cf.alias}`
          selectParts.push(expr)
        })
      }
    })
    parts.push(selectParts.join(', '))

    const tableNames = tables.map(t => {
      if (t.alias !== t.tableName) {
        return `${t.tableName} AS ${t.alias}`
      }
      return t.tableName
    })

    if (joins.length > 0) {
      const mainTable = tableNames[0]
      const joinClauses: string[] = []
      for (let i = 1; i < tables.length; i++) {
        // 找到所有连接这两张表的关联条件
        const tableJoins = joins.filter(j => j.targetTable === tables[i].alias && j.sourceTable && j.targetTable)
        if (tableJoins.length > 0) {
          const joinType = tableJoins[0].joinType || 'INNER'
          // 过滤有字段的关联条件
          const validJoins = tableJoins.filter(j => j.sourceField && j.targetField)
          if (validJoins.length > 0) {
            const conditions = validJoins.map(j => `${j.sourceTable}.${j.sourceField} = ${j.targetTable}.${j.targetField}`).join(' AND ')
            joinClauses.push(`${joinType} JOIN ${tables[i].tableName} AS ${tables[i].alias} ON ${conditions}`)
          } else {
            joinClauses.push(', ' + tableNames[i])
          }
        } else {
          joinClauses.push(', ' + tableNames[i])
        }
      }
      parts.push('FROM ' + mainTable + ' ' + joinClauses.join(' '))
    } else {
      parts.push('FROM ' + tableNames.join(', '))
    }

    if (where) {
      const whereSql = buildWhereClause(where)
      if (whereSql) {
        parts.push('WHERE ' + whereSql)
      }
    }

    if (groupBy.length > 0) {
      parts.push('GROUP BY ' + groupBy.join(', '))
    }

    if (having && having.conditions && having.conditions.length > 0) {
      const havingSql = buildWhereClause(having as WhereCondition)
      if (havingSql) {
        parts.push('HAVING ' + havingSql)
      }
    }

    if (orderBy.length > 0) {
      const orderParts = orderBy.map(o => {
        let orderField = ''
        if ((o as any).type === 'custom') {
          orderField = (o as any).customSql || o.field
        } else {
          orderField = o.field
        }
        const direction = o.direction === 'DESC' ? ' DESC' : ''
        return orderField ? `${orderField}${direction}` : ''
      }).filter(Boolean)
      
      if (orderParts.length > 0) {
        parts.push('ORDER BY ' + orderParts.join(', '))
      }
    }

    if (limit > 0) {
      parts.push('LIMIT ' + limit)
    }

    return parts.join('\n')
  }

  async function parseSqlToCanvas(sql: string): Promise<void> {
    const trimmed = sql.trim()
    if (!trimmed.toUpperCase().startsWith('SELECT')) {
      throw new Error('仅支持 SELECT 语句')
    }

    canvasConfig.value = createEmptyCanvasConfig()

    const fromMatch = trimmed.match(/FROM\s+([^\sWHERE\sGROUP\sORDER\sLIMIT\sHAVING\s;]+)/i)
    if (!fromMatch) throw new Error('无法解析 FROM 子句')

    let fromClause = fromMatch[1]
    const tableParts: Array<{ tableName: string; alias: string }> = []

    const joinPatterns = [
      /\s*(INNER|LEFT|RIGHT|FULL|CROSS)\s+JOIN\s+(\w+)(?:\s+AS\s+(\w+))?/gi,
      /,\s*(\w+)(?:\s+AS\s+(\w+))?/gi
    ]

    let match
    const mainTableMatch = fromClause.match(/^(\w+)(?:\s+AS\s+(\w+))?/i)
    if (mainTableMatch) {
      tableParts.push({
        tableName: mainTableMatch[1],
        alias: mainTableMatch[2] || mainTableMatch[1]
      })
    }

    for (const pattern of joinPatterns) {
      while ((match = pattern.exec(fromClause)) !== null) {
        if (match[1] && !['INNER', 'LEFT', 'RIGHT', 'FULL', 'CROSS', ','].includes(match[1].toUpperCase())) {
          continue
        }
        const tableName = match[2] || match[1]
        const alias = match[3] || match[2] || match[1]
        if (tableName && !tableParts.find(t => t.tableName === tableName)) {
          tableParts.push({ tableName, alias })
        }
      }
    }

    tableParts.forEach((t, idx) => {
      addTable(t.tableName, t.alias)
    })

    const joinPattern = /(INNER|LEFT|RIGHT|FULL|CROSS)\s+JOIN\s+(\w+)(?:\s+AS\s+(\w+))?\s+ON\s+(\w+)\.(\w+)\s*=\s*(\w+)\.(\w+)/gi
    while ((match = joinPattern.exec(fromClause)) !== null) {
      const joinType = match[1].toUpperCase() as 'INNER' | 'LEFT' | 'RIGHT' | 'FULL' | 'CROSS'
      const targetTable = match[3] || match[2]
      const sourceTable = match[4]
      const sourceField = match[5]
      const targetField = match[6]

      const srcAlias = tableParts.find(t => t.tableName === sourceTable)?.alias || sourceTable
      const tgtAlias = tableParts.find(t => t.tableName === targetTable)?.alias || targetTable

      addJoin({
        sourceTable: srcAlias,
        sourceField,
        targetTable: tgtAlias,
        targetField,
        joinType
      })
    }

    const selectMatch = trimmed.match(/SELECT\s+(DISTINCT\s+)?(.+?)\s+FROM/i)
    if (selectMatch) {
      const isDistinct = !!selectMatch[1]
      canvasConfig.value.distinct = isDistinct
      const selectPart = selectMatch[2]
      
      const fieldPattern = /(\w+)\.(\*)(?:\s+AS\s+(\w+))?|(\w+)\.(\w+)(?:\s+AS\s+(\w+))?|(\w+)\(([^)]+)\)(?:\s+AS\s+(\w+))?/g
      let fieldMatch
      while ((fieldMatch = fieldPattern.exec(selectPart)) !== null) {
        if (fieldMatch[1] && fieldMatch[2] === '*') {
          // table.* 格式 - 选择所有字段
          const tableAlias = fieldMatch[1]
          const table = canvasConfig.value.tables.find(t => t.alias === tableAlias)
          if (table) {
            table.selectedFields = ['*']
          }
        } else if (fieldMatch[4] && fieldMatch[5]) {
          // table.field 格式
          const tableAlias = fieldMatch[4]
          const fieldName = fieldMatch[5]
          const fieldAlias = fieldMatch[6]
          const table = canvasConfig.value.tables.find(t => t.alias === tableAlias)
          if (table) {
            if (!table.selectedFields.includes(fieldName)) {
              table.selectedFields.push(fieldName)
            }
            if (fieldAlias) {
              table.fieldAliases[fieldName] = fieldAlias
            }
          }
        } else if (fieldMatch[7]) {
          // 函数调用格式，如 COUNT(*), SUM(field)
          const funcName = fieldMatch[7].toUpperCase()
          const funcArg = fieldMatch[8]
          const fieldAlias = fieldMatch[9]
          
          const argMatch = funcArg.match(/(\w+)\.(\w+)|(\*)/)
          if (argMatch) {
            const tableAlias = argMatch[1]
            const fieldName = argMatch[2] || '*'
            if (tableAlias) {
              const table = canvasConfig.value.tables.find(t => t.alias === tableAlias)
              if (table) {
                if (!table.selectedFields.includes(fieldName)) {
                  table.selectedFields.push(fieldName)
                }
                if (fieldName !== '*') {
                  table.fieldAggregations[fieldName] = funcName
                }
                if (fieldAlias) {
                  table.fieldAliases[fieldName] = fieldAlias
                }
              }
            }
          }
        }
      }
    }

    const whereMatch = trimmed.match(/WHERE\s+(.+?)(?:\s+GROUP|\s+ORDER|\s+LIMIT|\s+HAVING|;|$)/i)
    if (whereMatch) {
      const whereStr = whereMatch[1].trim()
      canvasConfig.value.where = parseWhereClause(whereStr)
    }

    const groupMatch = trimmed.match(/GROUP\s+BY\s+(.+?)(?:\s+HAVING|\s+ORDER|\s+LIMIT|;|$)/i)
    if (groupMatch) {
      const fields = groupMatch[1].split(',').map(f => f.trim())
      canvasConfig.value.groupBy = fields
    }

    const orderMatch = trimmed.match(/ORDER\s+BY\s+(.+?)(?:\s+LIMIT|;|$)/i)
    if (orderMatch) {
      const orderParts = orderMatch[1].split(',').map(p => p.trim())
      canvasConfig.value.orderBy = orderParts.map(p => {
        const [field, direction] = p.split(/\s+/)
        return { field, direction: (direction || 'ASC').toUpperCase() as 'ASC' | 'DESC' }
      })
    }

    const limitMatch = trimmed.match(/LIMIT\s+(\d+)/i)
    if (limitMatch) {
      canvasConfig.value.limit = parseInt(limitMatch[1], 10)
    }
  }

  watch(canvasConfig, () => {
    const sql = generateSql()
    if (sql) sqlText.value = sql
  }, { deep: true })

  return {
    mode,
    dataSourceId,
    sqlText,
    canvasConfig,
    selectedTableAlias,
    selectedJoinId,
    layoutLocked,
    tables,
    joins,
    selectedTable,
    selectedJoin,
    allTableNames,
    reset,
    loadFromQueryConfig,
    exportToQueryConfig,
    addTable,
    removeTable,
    toggleFieldSelection,
    addJoin,
    removeJoin,
    cycleJoinType,
    updateJoinField,
    setWhere,
    addWhereCondition,
    removeWhereCondition,
    setGroupBy,
    addGroupByField,
    removeGroupByField,
    clearGroupBy,
    setHaving,
    addHavingCondition,
    removeHavingCondition,
    setOrderBy,
    setLimit,
    setDistinct,
    updateTablePosition,
    updateTableAlias,
    updateTableFields,
    addCustomField,
    removeCustomField,
    updateCustomField,
    setSql,
    selectTable,
    selectJoin,
    generateSql,
    validateConfig,
    parseSqlToCanvas,
    focusCustomFields,
    triggerFocusCustomFields,
    activeTabName,
    setActiveTab,
    updateWherePanelPosition,
    getWherePanelPosition,
    updateGroupPanelPosition,
    getGroupPanelPosition,
    updateOrderPanelPosition,
    getOrderPanelPosition
  }
})
