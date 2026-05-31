/**
 * 元数据相关类型定义
 */

/** 数据源信息 */
export interface DataSourceInfo {
  dataSourceId: string
  dataSourceName: string
  type: 'SQLITE' | 'POSTGRESQL' | 'HIVE'
  url?: string
  connected: boolean
  statusMessage?: string
  lastCheckTime?: number
}

/** 列元信息（增强版） */
export interface ColumnMetaDTO {
  columnName: string
  columnType: string
  dataType?: string
  columnSize?: number
  decimalDigits?: number
  columnComment: string
  primaryKey: boolean
  nullable: boolean
  autoIncrement?: boolean
  defaultValue?: string
  ordinalPosition?: number
  indexed?: boolean
}

/** 索引信息 */
export interface IndexInfo {
  indexName: string
  indexType?: string
  unique: boolean
  columns: string[]
}

/** 表元信息（增强版） */
export interface TableMetaDTO {
  tableName: string
  tableComment: string
  tableType?: string
  schemaName?: string
  rowCount?: number
  createTime?: string
  updateTime?: string
  columns?: ColumnMetaDTO[]
  primaryKeys?: string[]
  indexes?: IndexInfo[]
}

/** 搜索类型 */
export type SearchType = 'table' | 'column' | 'all'
