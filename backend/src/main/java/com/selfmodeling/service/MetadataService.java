package com.selfmodeling.service;

import com.selfmodeling.dto.ColumnMetaDTO;
import com.selfmodeling.dto.DataSourceInfo;
import com.selfmodeling.dto.TableMetaDTO;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 元数据服务接口（增强版）
 * 提供多数据源的数据库表和字段元数据查询能力
 */
public interface MetadataService {

    /**
     * 获取所有可用数据源列表
     * @return 数据源信息列表
     */
    List<DataSourceInfo> getDataSources();

    /**
     * 检查指定数据源的连接状态
     * @param dataSourceId 数据源ID (postgres, hive)
     * @return 数据源连接状态
     */
    DataSourceInfo checkDataSourceConnection(String dataSourceId);

    /**
     * 获取指定数据源的所有表列表
     * @param dataSourceId 数据源ID (postgres, hive, sqlite, 默认为 master)
     * @param keyword 搜索关键字（可选）
     * @return 表元信息列表
     */
    List<TableMetaDTO> getAllTables(String dataSourceId, String keyword);

    /**
     * 获取指定表的详细信息（包含字段、索引等）
     * @param dataSourceId 数据源ID
     * @param tableName 表名
     * @return 表完整元信息
     */
    TableMetaDTO getTableInfo(String dataSourceId, String tableName);

    /**
     * 获取指定表的字段列表
     * @param dataSourceId 数据源ID
     * @param tableName 表名
     * @return 字段元信息列表
     */
    List<ColumnMetaDTO> getTableColumns(String dataSourceId, String tableName);

    /**
     * 搜索表或字段
     * @param dataSourceId 数据源ID
     * @param keyword 搜索关键字
     * @param searchType 搜索类型：table（搜索表名）、column（搜索字段名）、all（全部）
     * @return 匹配的表列表
     */
    List<TableMetaDTO> searchMetadata(String dataSourceId, String keyword, String searchType);

    /**
     * 获取表的行数统计
     * @param dataSourceId 数据源ID
     * @param tableName 表名
     * @return 行数
     */
    Long getTableRowCount(String dataSourceId, String tableName);

    /**
     * 预览表数据（前N条记录）
     * @param dataSourceId 数据源ID
     * @param tableName 表名
     * @param limit 返回记录数限制
     * @return 数据列表（List of Map）
     */
    List<java.util.Map<String, Object>> previewTableData(String dataSourceId, String tableName, int limit);

    /**
     * 根据数据源ID获取对应的JdbcTemplate
     * @param dataSourceId 数据源ID (postgres, hive, sqlite, master)
     * @return JdbcTemplate实例，如果数据源未配置则返回null
     */
    JdbcTemplate getJdbcTemplateByDataSourceId(String dataSourceId);
}
