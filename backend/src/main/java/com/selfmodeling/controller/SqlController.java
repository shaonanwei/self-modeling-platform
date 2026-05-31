package com.selfmodeling.controller;

import com.selfmodeling.dto.QueryConfig;
import com.selfmodeling.dto.Result;
import com.selfmodeling.dto.SmartRecommendResult;
import com.selfmodeling.service.SqlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * SQL 控制器
 * 提供 SQL 校验、执行、解析、生成和智能推荐接口
 */
@RestController
@RequestMapping("/api/v1/sql")
public class SqlController {

    @Autowired
    private SqlService sqlService;

    /**
     * 校验 SQL 语句语法（仅允许 SELECT）
     * @param requestBody 包含 sql 和 dataSourceId 字段
     * @return 校验结果 {valid, message}
     */
    @PostMapping("/validate")
    public Result<Map<String, Object>> validateSql(@RequestBody Map<String, String> requestBody) {
        String sql = requestBody.get("sql");
        String dataSourceId = requestBody.getOrDefault("dataSourceId", "master");
        Map<String, Object> result = sqlService.validateSql(sql, dataSourceId);
        return Result.success("校验完成", result);
    }

    /**
     * 测试执行 SQL 查询（只读，限100行）
     * @param requestBody 包含 sql、limit 和 dataSourceId
     * @return 查询结果 {columns, rows, total}
     */
    @PostMapping("/execute")
    public Result<Map<String, Object>> executeQuery(@RequestBody Map<String, Object> requestBody) {
        String sql = (String) requestBody.get("sql");
        int limit = requestBody.containsKey("limit") ? ((Number) requestBody.get("limit")).intValue() : 50;
        String dataSourceId = (String) requestBody.getOrDefault("dataSourceId", "master");
        Map<String, Object> result = sqlService.executeQuery(sql, limit, dataSourceId);
        if ((Boolean) result.getOrDefault("success", false)) {
            return Result.success("查询成功", result);
        } else {
            return Result.error((String) result.get("message"));
        }
    }

    /**
     * 将 SQL 解析为画布配置（JSqlParser）
     * @param requestBody 包含 sql 和 dataSourceId
     * @return QueryConfig 画布配置对象
     */
    @PostMapping("/parse")
    public Result<QueryConfig> parseSqlToCanvas(@RequestBody Map<String, String> requestBody) {
        String sql = requestBody.get("sql");
        String dataSourceId = requestBody.getOrDefault("dataSourceId", "master");
        QueryConfig config = sqlService.parseSqlToCanvas(sql, dataSourceId);
        return Result.success("解析完成", config);
    }

    /**
     * 从画布配置生成 SQL 语句
     * @param queryConfig 查询配置（含 canvasConfig）
     * @return 生成的 SQL 字符串
     */
    @PostMapping("/generate")
    public Result<Map<String, String>> generateSqlFromCanvas(@RequestBody QueryConfig queryConfig) {
        String sql = sqlService.generateSqlFromCanvas(queryConfig);
        return Result.success("生成完成", Map.of("sql", sql));
    }

    /**
     * 获取智能推荐信息
     * @param tableName 表名
     * @param existingTables 已在画布中的表名（逗号分隔）
     * @param dataSourceId 数据源ID
     * @return 智能推荐结果（关联/聚合/条件）
     */
    @GetMapping("/smart-recommend")
    public Result<SmartRecommendResult> getSmartRecommendations(
            @RequestParam String tableName,
            @RequestParam(required = false) String existingTables,
            @RequestParam(defaultValue = "master") String dataSourceId) {
        List<String> tableList = existingTables != null && !existingTables.isEmpty()
                ? List.of(existingTables.split(","))
                : List.of();
        SmartRecommendResult result = sqlService.getSmartRecommendations(tableName, tableList, dataSourceId);
        return Result.success("获取推荐成功", result);
    }

    /**
     * 获取表的外键关联关系
     * @param tableName 表名
     * @param dataSourceId 数据源ID
     * @return 关联关系列表
     */
    @GetMapping("/relations/{tableName}")
    public Result<List<Map<String, Object>>> getTableRelations(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "master") String dataSourceId) {
        List<Map<String, Object>> relations = sqlService.getTableRelations(tableName, dataSourceId);
        return Result.success("获取关联关系成功", relations);
    }
}
