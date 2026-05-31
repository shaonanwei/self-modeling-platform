package com.selfmodeling.controller;

import com.selfmodeling.dto.ColumnMetaDTO;
import com.selfmodeling.dto.DataSourceInfo;
import com.selfmodeling.dto.Result;
import com.selfmodeling.dto.TableMetaDTO;
import com.selfmodeling.service.MetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 元数据控制器（增强版）
 * 提供数据库表和字段的元数据查询接口
 * 支持多数据源（SQLite、PostgreSQL、Hive）
 */
@RestController
@RequestMapping("/api/v1/metadata")
public class MetadataController {

    @Autowired
    private MetadataService metadataService;

    /**
     * 获取所有可用数据源列表及其连接状态
     * @return 数据源信息列表
     */
    @GetMapping("/datasources")
    public Result<List<DataSourceInfo>> getDataSources() {
        List<DataSourceInfo> dataSources = metadataService.getDataSources();
        return Result.success("获取成功", dataSources);
    }

    /**
     * 检查指定数据源的连接状态
     * @param dataSourceId 数据源ID (postgres, hive)
     * @return 连接状态信息
     */
    @GetMapping("/datasources/{dataSourceId}/check")
    public Result<DataSourceInfo> checkConnection(@PathVariable String dataSourceId) {
        DataSourceInfo info = metadataService.checkDataSourceConnection(dataSourceId);
        return Result.success("检查成功", info);
    }

    /**
     * 获取所有表列表（支持关键字搜索）
     * @param dataSourceId 数据源ID（可选，默认 sqlite）
     * @param keyword 搜索关键字（可选，搜索表名或注释）
     * @return 表元信息列表
     */
    @GetMapping("/tables")
    public Result<List<TableMetaDTO>> getAllTables(
            @RequestParam(required = false, defaultValue = "master") String dataSourceId,
            @RequestParam(required = false) String keyword) {
        List<TableMetaDTO> tables = metadataService.getAllTables(dataSourceId, keyword);
        return Result.success("获取成功", tables);
    }

    /**
     * 获取指定表的详细信息（包含字段、索引、主键等完整信息）
     * @param dataSourceId 数据源ID
     * @param tableName 表名
     * @return 表完整元信息
     */
    @GetMapping("/tables/{tableName}")
    public Result<TableMetaDTO> getTableInfo(
            @RequestParam(required = false, defaultValue = "master") String dataSourceId,
            @PathVariable String tableName) {
        TableMetaDTO tableInfo = metadataService.getTableInfo(dataSourceId, tableName);
        return Result.success("获取成功", tableInfo);
    }

    /**
     * 获取指定表的字段列表
     * @param dataSourceId 数据源ID
     * @param tableName 表名
     * @return 字段元信息列表
     */
    @GetMapping("/tables/{tableName}/columns")
    public Result<List<ColumnMetaDTO>> getTableColumns(
            @RequestParam(required = false, defaultValue = "master") String dataSourceId,
            @PathVariable String tableName) {
        List<ColumnMetaDTO> columns = metadataService.getTableColumns(dataSourceId, tableName);
        return Result.success("获取成功", columns);
    }

    /**
     * 搜索表或字段（支持模糊匹配）
     * @param dataSourceId 数据源ID
     * @param keyword 搜索关键字
     * @param searchType 搜索类型：table（表名）、column（字段名）、all（全部），默认 table
     * @return 匹配的表列表
     */
    @GetMapping("/search")
    public Result<List<TableMetaDTO>> searchMetadata(
            @RequestParam(required = false, defaultValue = "master") String dataSourceId,
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "table") String searchType) {
        List<TableMetaDTO> results = metadataService.searchMetadata(dataSourceId, keyword, searchType);
        return Result.success("搜索完成", results);
    }

    /**
     * 获取表的行数统计
     * @param dataSourceId 数据源ID
     * @param tableName 表名
     * @return 行数
     */
    @GetMapping("/tables/{tableName}/count")
    public Result<Long> getTableRowCount(
            @RequestParam(required = false, defaultValue = "master") String dataSourceId,
            @PathVariable String tableName) {
        Long count = metadataService.getTableRowCount(dataSourceId, tableName);
        return Result.success("获取成功", count);
    }

    /**
     * 预览表数据（前N条记录）
     * @param dataSourceId 数据源ID
     * @param tableName 表名
     * @param limit 返回记录数限制（默认 10 条）
     * @return 数据列表
     */
    @GetMapping("/tables/{tableName}/preview")
    public Result<List<Map<String, Object>>> previewTableData(
            @RequestParam(required = false, defaultValue = "master") String dataSourceId,
            @PathVariable String tableName,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        List<Map<String, Object>> data = metadataService.previewTableData(dataSourceId, tableName, limit);
        return Result.success("获取成功", data);
    }
}
