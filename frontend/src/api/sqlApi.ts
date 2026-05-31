import request from '@/utils/request'
import type { QueryConfig, SmartRecommendResult, SqlValidateResult, SqlExecuteResult } from '@/types/queryEditor'

export const sqlApi = {
  /** 校验 SQL */
  validate(sql: string, dataSourceId = 'master') {
    return request.post<any, { data: SqlValidateResult }>('/api/v1/sql/validate', { sql, dataSourceId })
  },

  /** 执行 SQL 查询 */
  execute(sql: string, limit = 50, dataSourceId = 'master') {
    return request.post<any, { data: SqlExecuteResult }>('/api/v1/sql/execute', { sql, limit, dataSourceId })
  },

  /** 解析 SQL 为画布配置 */
  parseToCanvas(sql: string, dataSourceId = 'master') {
    return request.post<any, { data: QueryConfig }>('/api/v1/sql/parse', { sql, dataSourceId })
  },

  /** 从画布配置生成 SQL */
  generateFromCanvas(queryConfig: QueryConfig) {
    return request.post<any, { data: { sql: string } }>('/api/v1/sql/generate', queryConfig)
  },

  /** 获取智能推荐 */
  getSmartRecommend(tableName: string, existingTables?: string[], dataSourceId = 'master') {
    return request.get<any, { data: SmartRecommendResult }>('/api/v1/sql/smart-recommend', {
      params: {
        tableName,
        existingTables: existingTables?.join(','),
        dataSourceId
      }
    })
  },

  /** 获取表关联关系 */
  getRelations(tableName: string, dataSourceId = 'master') {
    return request.get<any, { data: Array<Record<string, any>> }>(`/api/v1/sql/relations/${tableName}`, {
      params: { dataSourceId }
    })
  }
}
