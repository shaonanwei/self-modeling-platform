/**
 * SQL ↔ Visual 双向转换工具
 *
 * 1. SQL → Visual：解析 SQL 语句，提取表/字段/JOIN/WHERE
 * 2. Visual → SQL：根据图形化配置生成 SQL 语句
 *
 * 优化：缓存正则表达式，避免每次调用时重新编译
 */

// ========== 预编译正则（性能优化：避免重复编译） ==========
const RE_WHITESPACE = /\s+/g
const RE_SELECT_FROM = /^SELECT\s+(.*?)\s+FROM/i
const RE_FROM = /\bFROM\b/i
const RE_WHERE = /\bWHERE\s+(.*?)(?:\bGROUP\b|\bORDER\b|\bLIMIT\b|\bHAVING\b|$)/i
const RE_JOIN_TYPE = /\s+(INNER\s+JOIN|LEFT\s+(?:OUTER\s+)?JOIN|RIGHT\s+(?:OUTER\s+)?JOIN)\s+/gi
const RE_ON_CONDITION = /^(.*?)\s+ON\s+(.*)$/i
const RE_TABLE_ALIAS = /^(\w+)\s+(?:AS\s+)?(\w+)$/i
const RE_ON_EQ = /(\w+)\.(\w+)\s*=\s*(\w+)\.(\w+)/
const RE_LOGIC_OP = /\s+(AND|OR)\s+/i
const RE_IS_AND_OR = /^(AND|OR)$/i
const RE_TRIM_QUOTES = /^['"]|['"]$/g

// 操作符正则（按优先级排序，长操作符在前）
const OPERATOR_PATTERNS = [
  { regex: /\s+IS\s+NOT\s+NULL\s*$/i, op: 'IS NOT NULL', hasValue: false },
  { regex: /\s+IS\s+NULL\s*$/i, op: 'IS NULL', hasValue: false },
  { regex: /\s+NOT\s+LIKE\s+/i, op: 'NOT LIKE', hasValue: true },
  { regex: /\s+LIKE\s+/i, op: 'LIKE', hasValue: true },
  { regex: /\s+NOT\s+IN\s+/i, op: 'NOT IN', hasValue: true },
  { regex: /\s+IN\s+/i, op: 'IN', hasValue: true },
  { regex: /\s+BETWEEN\s+/i, op: 'BETWEEN', hasValue: true },
  { regex: /\s*!=\s*/, op: '!=', hasValue: true },
  { regex: /\s*<>\s*/, op: '!=', hasValue: true },
  { regex: /\s*>=\s*/, op: '>=', hasValue: true },
  { regex: /\s*<=\s*/, op: '<=', hasValue: true },
  { regex: /\s*>\s*/, op: '>', hasValue: true },
  { regex: /\s*<\s*/, op: '<', hasValue: true },
  { regex: /\s*=\s*/, op: '=', hasValue: true }
] as const

// ========== 类型定义 ==========
export interface VisualTable {
  name: string
  alias?: string
}

export interface VisualJoin {
  type: 'INNER' | 'LEFT' | 'RIGHT'
  leftTable: string
  leftField: string
  rightTable: string
  rightField: string
}

export interface VisualCondition {
  logic: 'AND' | 'OR'
  leftParen: string
  field: string
  operator: string
  value: string
  rightParen: string
}

export interface VisualGroupBy {
  field: string
  having?: string
}

export interface VisualOrderBy {
  field: string
  direction: 'ASC' | 'DESC'
}

export interface VisualQueryConfig {
  tables: VisualTable[]
  selectedFields: string[]
  joins: VisualJoin[]
  conditions: VisualCondition[]
  groupBy: VisualGroupBy[]
  orderBy: VisualOrderBy[]
}

// ========== 公开 API ==========

/**
 * SQL → Visual 转换
 * 解析 SQL 语句，提取表、字段、JOIN、WHERE、GROUP BY、ORDER BY
 */
export function sqlToVisual(sql: string): VisualQueryConfig {
  const result: VisualQueryConfig = {
    tables: [],
    selectedFields: [],
    joins: [],
    conditions: [],
    groupBy: [],
    orderBy: []
  }

  // 标准化 SQL
  const normalized = sql.replace(RE_WHITESPACE, ' ').trim()
  const upperSql = normalized.toUpperCase()

  // 1. 解析 SELECT 字段
  const selectMatch = normalized.match(RE_SELECT_FROM)
  if (selectMatch) {
    const fieldsStr = selectMatch[1].trim()
    result.selectedFields = fieldsStr === '*'
      ? ['*']
      : fieldsStr.split(',').map(f => parseSelectField(f.trim()))
  }

  // 2. 解析 FROM 和 JOIN
  const { tables, joins } = extractTablesAndJoins(normalized, upperSql)
  result.tables = tables
  result.joins = joins

  // 3. 解析 WHERE 条件
  const whereMatch = normalized.match(RE_WHERE)
  if (whereMatch) {
    result.conditions = parseWhereClause(whereMatch[1].trim())
  }

  // 4. 解析 GROUP BY
  const groupByMatch = normalized.match(/\bGROUP\s+BY\s+(.*?)(?:\bORDER\b|\bLIMIT\b|\bHAVING\b|$)/i)
  if (groupByMatch) {
    const groupByStr = groupByMatch[1].trim()
    // 检查是否有 HAVING
    const havingMatch = groupByStr.match(/\bHAVING\s+(.*)$/i)
    if (havingMatch) {
      const fieldsStr = groupByStr.substring(0, groupByStr.indexOf('HAVING')).trim()
      result.groupBy = fieldsStr.split(',').map(field => ({
        field: field.trim(),
        having: havingMatch[1].trim()
      }))
    } else {
      result.groupBy = groupByStr.split(',').map(field => ({
        field: field.trim()
      }))
    }
  }

  // 5. 解析 ORDER BY
  const orderByMatch = normalized.match(/\bORDER\s+BY\s+(.*?)(?:\bLIMIT\b|$)/i)
  if (orderByMatch) {
    const orderByStr = orderByMatch[1].trim()
    result.orderBy = orderByStr.split(',').map(item => {
      const parts = item.trim().split(/\s+/)
      const field = parts[0].trim()
      const direction = parts.length > 1 && parts[1].toUpperCase() === 'DESC' ? 'DESC' : 'ASC'
      return { field, direction }
    })
  }

  return result
}

/**
 * Visual → SQL 转换
 * 根据图形化配置生成 SQL 语句
 */
export function visualToSql(config: VisualQueryConfig, _dataSource?: string): string {
  if (config.tables.length === 0) return '-- 请先选择表'

  // SELECT 子句
  const fields = config.selectedFields.length > 0 && config.selectedFields[0] !== '*'
    ? config.selectedFields.join(', ')
    : '*'

  let sql = `SELECT ${fields}\nFROM `

  // FROM 表
  const firstTable = config.tables[0]
  sql += formatTable(firstTable)

  // JOIN 子句
  for (const join of config.joins) {
    const rightTable = config.tables.find(t => t.name === join.rightTable)
    sql += `\n${join.type} JOIN ${formatTable(rightTable || { name: join.rightTable }, rightTable)}`
    sql += ` ON ${join.leftTable}.${join.leftField} = ${join.rightTable}.${join.rightField}`
  }

  // WHERE 子句
  if (config.conditions.length > 0) {
    sql += '\nWHERE '
    sql += config.conditions.map((c, i) => formatCondition(c, i)).join('')
  }

  // GROUP BY 子句
  if (config.groupBy && config.groupBy.length > 0) {
    sql += '\nGROUP BY ' + config.groupBy.map(g => g.field).join(', ')
    // HAVING 子句
    const havingClause = config.groupBy.find(g => g.having)?.having
    if (havingClause) {
      sql += '\nHAVING ' + havingClause
    }
  }

  // ORDER BY 子句
  if (config.orderBy && config.orderBy.length > 0) {
    sql += '\nORDER BY ' + config.orderBy.map(o => `${o.field} ${o.direction}`).join(', ')
  }

  return sql
}

// ========== 内部函数 ==========

function parseSelectField(field: string): string {
  const asMatch = field.match(/^(.+?)\s+(?:AS\s+)?(\w+)$/)
  return asMatch ? `${asMatch[1].trim()} AS ${asMatch[2]}` : field
}

function extractTablesAndJoins(sql: string, upperSql: string) {
  const tables: VisualTable[] = []
  const joins: VisualJoin[] = []

  const fromIndex = upperSql.indexOf(' FROM ')
  if (fromIndex === -1) return { tables, joins }

  // 提取 FROM 之后到 WHERE/GROUP/ORDER/LIMIT/HAVING 之前的部分
  let afterFrom = sql.substring(fromIndex + 6)
  const endMatch = afterFrom.match(RE_WHERE) || afterFrom.match(/\s*(?:GROUP\s+BY|ORDER\s+BY|LIMIT|HAVING)\s/i)
  if (endMatch && endMatch.index !== undefined) {
    afterFrom = afterFrom.substring(0, endMatch.index)
  }

  // 分割 JOIN 部分
  const parts = afterFrom.split(RE_JOIN_TYPE).filter(Boolean)

  if (parts.length > 0) {
    // 第一部分：FROM 表
    const fromTable = parseTableWithAlias(parts[0].trim())
    if (fromTable) tables.push(fromTable)

    // 后续部分：JOIN 表
    for (let i = 1; i < parts.length; i += 2) {
      const joinType = parts[i].trim().toUpperCase()
      const joinPart = parts[i + 1]?.trim()
      if (!joinPart) continue

      let type: 'INNER' | 'LEFT' | 'RIGHT' = 'INNER'
      if (joinType.includes('LEFT')) type = 'LEFT'
      else if (joinType.includes('RIGHT')) type = 'RIGHT'

      const onMatch = joinPart.match(RE_ON_CONDITION)
      if (onMatch) {
        const joinTable = parseTableWithAlias(onMatch[1].trim())
        if (joinTable) tables.push(joinTable)

        const eqMatch = onMatch[2].trim().match(RE_ON_EQ)
        if (eqMatch) {
          joins.push({
            type,
            leftTable: resolveTableName(eqMatch[1], tables),
            leftField: eqMatch[2],
            rightTable: resolveTableName(eqMatch[3], tables),
            rightField: eqMatch[4]
          })
        }
      }
    }
  }

  return { tables, joins }
}

function parseTableWithAlias(part: string): VisualTable | null {
  const cleaned = part.replace(/\bWHERE\b.*$/i, '').trim()
  if (!cleaned) return null

  const asMatch = cleaned.match(RE_TABLE_ALIAS)
  return asMatch
    ? { name: asMatch[1], alias: asMatch[2] }
    : { name: cleaned }
}

function resolveTableName(name: string, tables: VisualTable[]): string {
  const exact = tables.find(t => t.name === name)
  if (exact) return exact.name

  const alias = tables.find(t => t.alias === name)
  return alias ? alias.name : name
}

function parseWhereClause(whereClause: string): VisualCondition[] {
  const conditions: VisualCondition[] = []
  const parts = whereClause.split(RE_LOGIC_OP)

  let currentLogic: 'AND' | 'OR' = 'AND'

  for (const part of parts) {
    const trimmed = part.trim()
    if (!trimmed) continue

    if (RE_IS_AND_OR.test(trimmed)) {
      currentLogic = trimmed.toUpperCase() as 'AND' | 'OR'
      continue
    }

    const condition = parseCondition(trimmed, currentLogic)
    if (condition) conditions.push(condition)
  }

  return conditions
}

function parseCondition(expr: string, logic: 'AND' | 'OR'): VisualCondition | null {
  const trimmed = expr.trim()
  if (!trimmed) return null

  let leftParen = ''
  let rightParen = ''
  let innerExpr = trimmed

  // 提取括号
  const parenMatch = trimmed.match(/^(\(?)(.*?)(\)?)$/)
  if (parenMatch) {
    leftParen = parenMatch[1] || ''
    rightParen = parenMatch[3] || ''
    innerExpr = parenMatch[2] || trimmed
  }

  // 匹配操作符
  for (const { regex, op, hasValue } of OPERATOR_PATTERNS) {
    const match = innerExpr.match(regex)
    if (match) {
      const parts = innerExpr.split(regex)
      const field = parts[0]?.trim() || ''
      const value = hasValue ? (parts[1]?.trim().replace(RE_TRIM_QUOTES, '') || '') : ''

      return {
        logic,
        leftParen,
        field,
        operator: op,
        value,
        rightParen
      }
    }
  }

  return null
}

function formatTable(table: { name: string; alias?: string }, fallback?: { alias?: string }): string {
  const alias = table.alias || fallback?.alias
  return `${table.name}${alias ? ` ${alias}` : ''}`
}

function formatCondition(c: VisualCondition, index: number): string {
  const logic = index === 0 ? '' : ` ${c.logic} `
  const leftP = c.leftParen || ''
  const rightP = c.rightParen || ''
  const needsValue = !['IS NULL', 'IS NOT NULL'].includes(c.operator)

  if (c.operator === 'BETWEEN') {
    const betweenVal = c.value ? `BETWEEN ${c.value}` : ''
    return `${logic}${leftP}${c.field} ${betweenVal}${rightP}`
  }

  const val = needsValue && c.value ? `'${c.value}'` : ''
  return `${logic}${leftP}${c.field} ${c.operator}${val ? ' ' + val : ''}${rightP}`
}
