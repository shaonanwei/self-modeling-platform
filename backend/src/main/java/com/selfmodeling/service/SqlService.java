package com.selfmodeling.service;

import com.selfmodeling.dto.QueryConfig;
import com.selfmodeling.dto.SmartRecommendResult;
import java.util.List;
import java.util.Map;

/**
 * SQL 服务接口
 * 提供 SQL 校验、测试执行、解析、生成和智能推荐功能
 */
public interface SqlService {

    /**
     * 校验 SQL 语句语法（仅允许 SELECT）
     * @param sql SQL 语句
     * @param dataSourceId 数据源ID
     * @return 校验结果：{valid: boolean, message: string}
     */
    Map<String, Object> validateSql(String sql, String dataSourceId);

    /**
     * 测试执行 SQL 查询（只读，限行）
     * @param sql SQL 查询语句
     * @param limit 返回行数限制（最大100）
     * @param dataSourceId 数据源ID
     * @return 查询结果：{columns: string[], rows: List<Map>, total: long}
     */
    Map<String, Object> executeQuery(String sql, int limit, String dataSourceId);

    /**
     * 将 SQL 解析为画布配置（JSqlParser）
     * 支持解析 SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT/DISTINCT
     * 不支持子查询、窗口函数等复杂语法的部分标记为 customSqlFragment
     * @param sql SQL 语句
     * @param dataSourceId 数据源ID
     * @return QueryConfig 包含完整画布配置
     */
    QueryConfig parseSqlToCanvas(String sql, String dataSourceId);

    /**
     * 从画布配置生成 SQL 语句
     * 根据表节点、关联连线、条件、分组、排序等配置拼接标准 SELECT SQL
     * @param queryConfig 查询配置
     * @return 生成的 SQL 字符串
     */
    String generateSqlFromCanvas(QueryConfig queryConfig);

    /**
     * 获取表的智能推荐信息
     * 包括关联推荐（基于字段名/类型匹配）、聚合推荐、条件推荐
     * @param tableName 表名
     * @param existingTables 已在画布中的表名列表
     * @param dataSourceId 数据源ID
     * @return 智能推荐结果
     */
    SmartRecommendResult getSmartRecommendations(String tableName, List<String> existingTables, String dataSourceId);

    /**
     * 获取表的外键关联关系
     * @param tableName 表名
     * @param dataSourceId 数据源ID
     * @return 关联关系列表
     */
    List<Map<String, Object>> getTableRelations(String tableName, String dataSourceId);
}
