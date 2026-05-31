/**
 * 画布配置相关类型定义
 */

/** 字段元数据 */
export interface FieldMeta {
  columnName: string
  dataType: string
  columnComment?: string
}

/** 自定义字段（支持任意SQL表达式） */
export interface CustomField {
  id: string
  expression: string
  alias: string
}

/** 画布表节点配置 */
export interface CanvasTableConfig {
  tableName: string
  alias: string
  x: number
  y: number
  expanded: boolean
  selectedFields: string[]
  fieldAliases: Record<string, string>
  fieldAggregations: Record<string, string>
  customFields: CustomField[]
  dataSourceId: string
  fields?: FieldMeta[]
}

/** 画布关联（JOIN）配置 */
export interface CanvasJoinConfig {
  id: string
  sourceTable: string
  sourceField: string
  targetTable: string
  targetField: string
  joinType: 'INNER' | 'LEFT' | 'RIGHT' | 'FULL' | 'CROSS'
}

/** WHERE 条件项 */
export interface ConditionItem {
  id: string
  type: 'field' | 'custom'
  field?: string
  operator?: string
  value?: any
  customSql?: string
  wrapped?: boolean
  leftBracket?: boolean
  rightBracket?: boolean
  logic?: 'AND' | 'OR'
}

/** WHERE 条件组（支持嵌套 AND/OR） */
export interface WhereCondition {
  logic: 'AND' | 'OR'
  conditions: ConditionItem[]
  groups: WhereCondition[]
}

/** 排序项 */
export interface OrderByItem {
  field: string
  direction: 'ASC' | 'DESC'
  type?: 'field' | 'custom'
  customSql?: string
}

/** 画布完整配置 */
export interface CanvasConfig {
  tables: CanvasTableConfig[]
  joins: CanvasJoinConfig[]
  where: WhereCondition | null
  groupBy: string[]
  having: WhereCondition | null
  orderBy: OrderByItem[]
  limit: number
  distinct: boolean
  customSqlFragment: string | null
  layoutLocked?: boolean
  wherePanelPosition?: { x: number; y: number }
  groupPanelPosition?: { x: number; y: number }
  orderPanelPosition?: { x: number; y: number }
}

/** 完整查询配置 */
export interface QueryConfig {
  mode: 'sql' | 'canvas' | 'dual'
  sql: string
  canvasConfig: CanvasConfig
}

/** 智能推荐 - 关联推荐 */
export interface RelationRecommend {
  sourceTable: string
  sourceField: string
  targetTable: string
  targetField: string
  recommendType: 'name_match' | 'type_match'
  confidence: number
}

/** 智能推荐 - 聚合推荐 */
export interface AggregateRecommend {
  fieldName: string
  fieldType: string
  recommendedFunctions: string[]
}

/** 智能推荐 - 条件推荐 */
export interface ConditionRecommend {
  fieldName: string
  fieldType: string
  recommendedOperators: string[]
  suggestedValues?: any[]
}

/** 智能推荐结果 */
export interface SmartRecommendResult {
  relations: RelationRecommend[]
  aggregates: AggregateRecommend[]
  conditions: ConditionRecommend[]
}

/** SQL 执行结果 */
export interface SqlExecuteResult {
  columns: string[]
  rows: Record<string, any>[]
  total: number
  success: boolean
  message: string
}

/** SQL 校验结果 */
export interface SqlValidateResult {
  valid: boolean
  message: string
}
