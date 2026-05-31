import request from '@/utils/request'
import type { DataSourceInfo, TableMetaDTO, ColumnMetaDTO, SearchType } from '@/types/metadata'

export const metadataApi = {
  /** 获取所有数据源列表 */
  getDataSources() {
    return request.get<any, { data: DataSourceInfo[] }>('/api/v1/metadata/datasources')
  },

  /** 检查数据源连接状态 */
  checkConnection(dataSourceId: string) {
    return request.get<any, { data: DataSourceInfo }>(`/api/v1/metadata/datasources/${dataSourceId}/check`)
  },

  /** 获取表列表（支持搜索） */
  getTables(params: { dataSourceId?: string; keyword?: string }) {
    return request.get<any, { data: TableMetaDTO[] }>('/api/v1/metadata/tables', { params })
  },

  /** 获取表详细信息 */
  getTableInfo(dataSourceId: string, tableName: string) {
    return request.get<any, { data: TableMetaDTO }>(
      `/api/v1/metadata/tables/${tableName}`,
      { params: { dataSourceId } }
    )
  },

  /** 获取表的字段列表 */
  getTableColumns(dataSourceId: string, tableName: string) {
    return request.get<any, { data: ColumnMetaDTO[] }>(
      `/api/v1/metadata/tables/${tableName}/columns`,
      { params: { dataSourceId } }
    )
  },

  /** 搜索元数据（表或字段） */
  searchMetadata(params: {
    dataSourceId?: string
    keyword: string
    searchType?: SearchType
  }) {
    return request.get<any, { data: TableMetaDTO[] }>('/api/v1/metadata/search', { params })
  },

  /** 获取表的行数统计 */
  getTableRowCount(dataSourceId: string, tableName: string) {
    return request.get<any, { data: number }>(
      `/api/v1/metadata/tables/${tableName}/count`,
      { params: { dataSourceId } }
    )
  },

  /** 预览表数据 */
  previewTableData(dataSourceId: string, tableName: string, limit = 10) {
    return request.get<any, { data: Record<string, any>[] }>(
      `/api/v1/metadata/tables/${tableName}/preview`,
      { params: { dataSourceId, limit } }
    )
  }
}
