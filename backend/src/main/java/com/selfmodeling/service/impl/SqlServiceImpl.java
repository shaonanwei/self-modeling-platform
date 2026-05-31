package com.selfmodeling.service.impl;

import com.selfmodeling.dto.*;
import com.selfmodeling.service.MetadataService;
import com.selfmodeling.service.SqlService;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.*;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SQL 服务实现类
 * 提供 SQL 校验、执行、解析（JSqlParser）、生成和智能推荐功能
 */
@Service
public class SqlServiceImpl implements SqlService {

    private static final Logger log = LoggerFactory.getLogger(SqlServiceImpl.class);

    private static final Set<String> NUMERIC_TYPES = new HashSet<>(Arrays.asList(
            "INTEGER", "INT", "BIGINT", "SMALLINT", "TINYINT",
            "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL",
            "NUMBER", "MONEY"
    ));

    private static final Set<String> DATE_TYPES = new HashSet<>(Arrays.asList(
            "DATE", "DATETIME", "TIMESTAMP", "TIME", "YEAR"
    ));

    @Autowired
    private MetadataService metadataService;

    // ========== SQL 校验与执行 ==========

    @Override
    public Map<String, Object> validateSql(String sql, String dataSourceId) {
        Map<String, Object> result = new HashMap<>();
        if (sql == null || sql.trim().isEmpty()) {
            result.put("valid", false);
            result.put("message", "SQL 语句不能为空");
            return result;
        }
        String trimmedSql = sql.trim().toUpperCase();
        if (isDangerousOperation(trimmedSql)) {
            result.put("valid", false);
            result.put("message", "不支持数据修改操作，仅允许 SELECT 查询");
            return result;
        }
        try {
            JdbcTemplate targetJdbcTemplate = metadataService.getJdbcTemplateByDataSourceId(dataSourceId);
            if (targetJdbcTemplate == null) {
                result.put("valid", false);
                result.put("message", "未知的数据源: " + dataSourceId);
                return result;
            }
            DataSource dataSource = targetJdbcTemplate.getDataSource();
            if (dataSource == null) {
                log.error("数据源 {} 配置异常，无法获取数据库连接", dataSourceId);
                result.put("valid", false);
                result.put("message", "数据源配置异常，无法获取数据库连接");
                return result;
            }
            try (Connection conn = dataSource.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                String dbProductName = conn.getMetaData().getDatabaseProductName().toLowerCase();
                String explainSql;
                if (dbProductName.contains("sqlite")) {
                    explainSql = "EXPLAIN QUERY PLAN " + sql;
                } else {
                    // MySQL, PostgreSQL 等使用 EXPLAIN
                    explainSql = "EXPLAIN " + sql;
                }
                stmt.executeQuery(explainSql);
                result.put("valid", true);
                result.put("message", "SQL 语法正确");
            }
        } catch (SQLException e) {
            log.warn("SQL 校验失败: {}", e.getMessage());
            result.put("valid", false);
            result.put("message", "SQL 语法错误: " + e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> executeQuery(String sql, int limit, String dataSourceId) {
        Map<String, Object> result = new HashMap<>();
        String trimmedSql = sql.trim().toUpperCase();
        if (!trimmedSql.startsWith("SELECT")) {
            result.put("success", false);
            result.put("message", "仅支持 SELECT 查询");
            return result;
        }
        String limitedSql = sql + " LIMIT " + Math.min(limit, 100);
        try {
            JdbcTemplate targetJdbcTemplate = metadataService.getJdbcTemplateByDataSourceId(dataSourceId);
            if (targetJdbcTemplate == null) {
                result.put("success", false);
                result.put("message", "未知的数据源: " + dataSourceId);
                return result;
            }
            DataSource dataSource = targetJdbcTemplate.getDataSource();
            if (dataSource == null) {
                log.error("数据源 {} 配置异常，无法获取数据库连接", dataSourceId);
                result.put("success", false);
                result.put("message", "数据源配置异常，无法获取数据库连接");
                return result;
            }
            try (Connection conn = dataSource.getConnection();
                 java.sql.Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(limitedSql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(metaData.getColumnName(i));
                }
                result.put("columns", columns);
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        if (value instanceof Timestamp) value = value.toString();
                        row.put(columnName, value);
                    }
                    rows.add(row);
                }
                result.put("rows", rows);
                result.put("total", rows.size());
                result.put("success", true);
                result.put("message", "查询成功");
            }
        } catch (SQLException e) {
            log.error("SQL 执行失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "SQL 执行错误: " + e.getMessage());
        }
        return result;
    }

    // ========== SQL 解析为画布配置 ==========

    @Override
    public QueryConfig parseSqlToCanvas(String sql, String dataSourceId) {
        QueryConfig config = new QueryConfig();
        config.setMode("canvas");
        config.setSql(sql);

        QueryConfig.CanvasConfig canvasConfig = new QueryConfig.CanvasConfig();
        config.setCanvasConfig(canvasConfig);

        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select)) {
                canvasConfig.setCustomSqlFragment(sql);
                return config;
            }
            Select selectStatement = (Select) statement;
            
            try {
                java.lang.reflect.Method getSelectBodyMethod = selectStatement.getClass().getMethod("getSelectBody");
                Object selectBody = getSelectBodyMethod.invoke(selectStatement);
                
                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;
                    parseFromAndJoins(plainSelect, canvasConfig);
                    parseSelectItems(plainSelect, canvasConfig);
                    parseWhere(plainSelect.getWhere(), canvasConfig);
                    parseGroupBy(plainSelect.getGroupBy(), canvasConfig);
                    parseHaving(plainSelect.getHaving(), canvasConfig);
                    parseOrderBy(plainSelect.getOrderByElements(), canvasConfig);
                    parseLimit(plainSelect.getLimit(), canvasConfig);
                    parseDistinct(plainSelect.getDistinct(), canvasConfig);
                } else {
                    canvasConfig.setCustomSqlFragment(sql);
                }
            } catch (Exception e) {
                canvasConfig.setCustomSqlFragment(sql);
            }
        } catch (Exception e) {
            log.error("SQL 解析失败: {}", e.getMessage());
            canvasConfig.setCustomSqlFragment(sql);
        }
        return config;
    }

    /**
     * 解析 FROM 和 JOIN 子句，构建表节点和关联连线
     */
    private void parseFromAndJoins(PlainSelect plainSelect, QueryConfig.CanvasConfig canvasConfig) {
        List<CanvasTableConfig> tables = new ArrayList<>();
        Map<String, String> aliasMap = new HashMap<>();

        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            String tableName = table.getName();
            String alias = table.getAlias() != null ? table.getAlias().getName() : generateAlias(tableName, 0);
            CanvasTableConfig tc = new CanvasTableConfig(tableName, alias);
            tc.setX(100 + tables.size() * 320);
            tc.setY(80);
            tables.add(tc);
            aliasMap.put(alias, tableName);
        }

        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            List<CanvasJoinConfig> joinConfigs = new ArrayList<>();
            int joinIndex = 0;
            for (Join join : joins) {
                if (join.getRightItem() instanceof Table) {
                    Table jTable = (Table) join.getRightItem();
                    String jTableName = jTable.getName();
                    String jAlias = jTable.getAlias() != null ? jTable.getAlias().getName() : generateAlias(jTableName, tables.size());
                    CanvasTableConfig jtc = new CanvasTableConfig(jTableName, jAlias);
                    jtc.setX(100 + tables.size() * 320);
                    jtc.setY(80 + joinIndex * 60);
                    tables.add(jtc);
                    aliasMap.put(jAlias, jTableName);

                    String joinType = parseJoinType(join);
                    @SuppressWarnings("deprecation")
                    Expression onExpression = join.getOnExpression();
                    if (onExpression instanceof EqualsTo) {
                        EqualsTo equalsTo = (EqualsTo) onExpression;
                        CanvasJoinConfig jc = new CanvasJoinConfig();
                        jc.setId("j" + System.currentTimeMillis() + joinIndex);
                        jc.setJoinType(joinType);
                        jc.setSourceTable(aliasMap.getOrDefault(extractTableAlias(equalsTo.getLeftExpression()), ""));
                        jc.setSourceField(extractFieldName(equalsTo.getLeftExpression()));
                        jc.setTargetTable(aliasMap.getOrDefault(extractTableAlias(equalsTo.getRightExpression()), ""));
                        jc.setTargetField(extractFieldName(equalsTo.getRightExpression()));
                        joinConfigs.add(jc);
                    }
                    joinIndex++;
                }
            }
            canvasConfig.setJoins(joinConfigs);
        }
        canvasConfig.setTables(tables);
    }

    /**
     * 解析 SELECT 子句，标记选中字段
     */
    private void parseSelectItems(PlainSelect plainSelect, QueryConfig.CanvasConfig canvasConfig) {
        List<SelectItem<?>> items = plainSelect.getSelectItems();
        for (CanvasTableConfig table : canvasConfig.getTables()) {
            List<String> selectedFields = new ArrayList<>();
            Map<String, String> fieldAliases = new LinkedHashMap<>();
            Map<String, String> fieldAggregations = new LinkedHashMap<>();

            for (SelectItem<?> item : items) {
                String itemStr = item.toString();
                if (itemStr.equals("*")) {
                    selectedFields.clear();
                    break;
                }
                
                try {
                    java.lang.reflect.Method getExprMethod = item.getClass().getMethod("getExpression");
                    Expression expr = (Expression) getExprMethod.invoke(item);
                    String fieldName = extractFieldName(expr);
                    
                    String aliasName = null;
                    try {
                        java.lang.reflect.Method getAliasMethod = item.getClass().getMethod("getAlias");
                        Object aliasObj = getAliasMethod.invoke(item);
                        if (aliasObj != null) {
                            java.lang.reflect.Method getNameMethod = aliasObj.getClass().getMethod("getName");
                            aliasName = (String) getNameMethod.invoke(aliasObj);
                        }
                    } catch (Exception ignored) {}
                    
                    if (belongsToTable(expr, table.getAlias())) {
                        String baseField = stripAliasPrefix(fieldName, table.getAlias());
                        selectedFields.add(baseField);
                        if (aliasName != null && !aliasName.equals(baseField)) {
                            fieldAliases.put(baseField, aliasName);
                        }
                        String aggFunc = extractAggregateFunction(expr);
                        if (aggFunc != null) {
                            fieldAggregations.put(baseField, aggFunc);
                        }
                    }
                } catch (Exception ignored) {}
            }
            table.setSelectedFields(selectedFields);
            table.setFieldAliases(fieldAliases);
            table.setFieldAggregations(fieldAggregations);
        }
    }

    /**
     * 解析 WHERE 条件
     */
    private void parseWhere(Expression whereExpr, QueryConfig.CanvasConfig canvasConfig) {
        if (whereExpr == null) return;
        WhereCondition where = buildWhereCondition(whereExpr);
        canvasConfig.setWhere(where);
    }

    /**
     * 解析 GROUP BY
     */
    private void parseGroupBy(GroupByElement groupBy, QueryConfig.CanvasConfig canvasConfig) {
        if (groupBy == null || groupBy.getGroupByExpressionList() == null) return;
        @SuppressWarnings("unchecked")
        List<Expression> groupByExprList = (List<Expression>) (List<?>) groupBy.getGroupByExpressionList();
        List<String> groupByFields = groupByExprList.stream()
                .map(this::extractFieldName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        canvasConfig.setGroupBy(groupByFields);
    }

    /**
     * 解析 HAVING
     */
    private void parseHaving(Expression havingExpr, QueryConfig.CanvasConfig canvasConfig) {
        if (havingExpr == null) return;
        WhereCondition having = buildWhereCondition(havingExpr);
        canvasConfig.setHaving(having);
    }

    /**
     * 解析 ORDER BY
     */
    private void parseOrderBy(List<OrderByElement> orderByList, QueryConfig.CanvasConfig canvasConfig) {
        if (orderByList == null || orderByList.isEmpty()) return;
        List<QueryConfig.OrderByItem> orderByItems = orderByList.stream()
                .map(ob -> new QueryConfig.OrderByItem(
                        extractFieldName(ob.getExpression()),
                        ob.isAsc() ? "ASC" : "DESC"))
                .collect(Collectors.toList());
        canvasConfig.setOrderBy(orderByItems);
    }

    /**
     * 解析 LIMIT
     */
    private void parseLimit(Limit limit, QueryConfig.CanvasConfig canvasConfig) {
        if (limit == null) return;
        try {
            Expression rowCount = limit.getRowCount();
            if (rowCount instanceof LongValue) {
                canvasConfig.setLimit((int) ((LongValue) rowCount).getValue());
            }
        } catch (Exception ignored) {}
    }

    /**
     * 解析 DISTINCT
     */
    private void parseDistinct(Distinct distinct, QueryConfig.CanvasConfig canvasConfig) {
        canvasConfig.setDistinct(distinct != null);
    }

    // ========== 从画布配置生成 SQL ==========

    @Override
    public String generateSqlFromCanvas(QueryConfig queryConfig) {
        StringBuilder sb = new StringBuilder();
        QueryConfig.CanvasConfig cc = queryConfig.getCanvasConfig();
        if (cc == null) return "";

        boolean distinct = Boolean.TRUE.equals(cc.getDistinct());

        sb.append("SELECT ");
        if (distinct) sb.append("DISTINCT ");

        sb.append(buildSelectClause(cc)).append("\n");

        sb.append("FROM ").append(buildFromClause(cc)).append("\n");

        String joinClause = buildJoinClause(cc);
        if (!joinClause.isEmpty()) {
            sb.append(joinClause).append("\n");
        }

        String whereClause = buildWhereClause(cc.getWhere());
        if (!whereClause.isEmpty()) {
            sb.append("WHERE ").append(whereClause).append("\n");
        }

        if (cc.getGroupBy() != null && !cc.getGroupBy().isEmpty()) {
            sb.append("GROUP BY ").append(String.join(", ", cc.getGroupBy())).append("\n");
        }

        String havingClause = buildWhereClause(cc.getHaving());
        if (!havingClause.isEmpty()) {
            sb.append("HAVING ").append(havingClause).append("\n");
        }

        if (cc.getOrderBy() != null && !cc.getOrderBy().isEmpty()) {
            List<String> orderParts = cc.getOrderBy().stream()
                    .map(o -> o.getField() + " " + o.getDirection())
                    .collect(Collectors.toList());
            sb.append("ORDER BY ").append(String.join(", ", orderParts)).append("\n");
        }

        if (cc.getLimit() != null && cc.getLimit() > 0) {
            sb.append("LIMIT ").append(cc.getLimit()).append("\n");
        }

        return sb.toString().trim();
    }

    // ========== 智能推荐 ==========

    @Override
    public SmartRecommendResult getSmartRecommendations(String tableName, List<String> existingTables, String dataSourceId) {
        SmartRecommendResult result = new SmartRecommendResult();
        List<SmartRecommendResult.RelationRecommend> relations = new ArrayList<>();
        List<SmartRecommendResult.AggregateRecommend> aggregates = new ArrayList<>();
        List<SmartRecommendResult.ConditionRecommend> conditions = new ArrayList<>();

        try {
            List<ColumnMetaDTO> columns = metadataService.getTableColumns(dataSourceId, tableName);

            for (String existingTable : existingTables) {
                List<ColumnMetaDTO> existingCols = metadataService.getTableColumns(dataSourceId, existingTable);
                for (ColumnMetaDTO col : columns) {
                    for (ColumnMetaDTO existCol : existingCols) {
                        double confidence = calculateMatchConfidence(col, existCol);
                        if (confidence >= 0.5) {
                            SmartRecommendResult.RelationRecommend rr = new SmartRecommendResult.RelationRecommend();
                            rr.setSourceTable(tableName);
                            rr.setSourceField(col.getColumnName());
                            rr.setTargetTable(existingTable);
                            rr.setTargetField(existCol.getColumnName());
                            rr.setRecommendType(col.getColumnName().equalsIgnoreCase(existCol.getColumnName())
                                    ? "name_match" : "type_match");
                            rr.setConfidence(confidence);
                            relations.add(rr);
                        }
                    }
                }
            }

            for (ColumnMetaDTO col : columns) {
                String typeUpper = col.getColumnType().toUpperCase();
                if (isNumericType(typeUpper)) {
                    SmartRecommendResult.AggregateRecommend ar = new SmartRecommendResult.AggregateRecommend();
                    ar.setFieldName(col.getColumnName());
                    ar.setFieldType(typeUpper);
                    ar.setRecommendedFunctions(Arrays.asList("SUM", "AVG", "MAX", "MIN", "COUNT"));
                    aggregates.add(ar);
                }

                if ("STATUS".equalsIgnoreCase(col.getColumnName())
                        || "TYPE".equalsIgnoreCase(col.getColumnName())
                        || "STATE".equalsIgnoreCase(col.getColumnName())) {
                    SmartRecommendResult.ConditionRecommend cr = new SmartRecommendResult.ConditionRecommend();
                    cr.setFieldName(col.getColumnName());
                    cr.setFieldType(typeUpper);
                    cr.setRecommendedOperators(Arrays.asList("=", "!=", "IN"));
                    conditions.add(cr);
                }
                if (isDateType(typeUpper)) {
                    SmartRecommendResult.ConditionRecommend cr = new SmartRecommendResult.ConditionRecommend();
                    cr.setFieldName(col.getColumnName());
                    cr.setFieldType(typeUpper);
                    cr.setRecommendedOperators(Arrays.asList(">", ">=", "<", "<=", "BETWEEN", "="));
                    conditions.add(cr);
                }
            }
        } catch (Exception e) {
            log.error("获取智能推荐失败: {}", e.getMessage());
        }

        result.setRelations(relations);
        result.setAggregates(aggregates);
        result.setConditions(conditions);
        return result;
    }

    @Override
    public List<Map<String, Object>> getTableRelations(String tableName, String dataSourceId) {
        List<Map<String, Object>> relations = new ArrayList<>();
        try {
            List<TableMetaDTO> allTables = metadataService.getAllTables(dataSourceId, null);
            List<ColumnMetaDTO> myColumns = metadataService.getTableColumns(dataSourceId, tableName);

            for (TableMetaDTO otherTable : allTables) {
                if (otherTable.getTableName().equals(tableName)) continue;
                List<ColumnMetaDTO> otherColumns = metadataService.getTableColumns(dataSourceId, otherTable.getTableName());

                for (ColumnMetaDTO myCol : myColumns) {
                    for (ColumnMetaDTO otherCol : otherColumns) {
                        if (myCol.getColumnName().equalsIgnoreCase(otherCol.getColumnName())
                                || (myCol.getColumnName().toLowerCase().contains("_id")
                                && otherCol.getColumnName().toLowerCase().endsWith("_id"))) {
                            Map<String, Object> rel = new LinkedHashMap<>();
                            rel.put("sourceTable", tableName);
                            rel.put("sourceField", myCol.getColumnName());
                            rel.put("targetTable", otherTable.getTableName());
                            rel.put("targetField", otherCol.getColumnName());
                            rel.put("relationType", myCol.isPrimaryKey() ? "one_to_many" : "many_to_one");
                            relations.add(rel);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取表关联关系失败: {}", e.getMessage());
        }
        return relations;
    }

    // ========== 内部工具方法 ==========

    private boolean isDangerousOperation(String sql) {
        return sql.startsWith("DROP") || sql.startsWith("DELETE") || sql.startsWith("UPDATE")
                || sql.startsWith("INSERT") || sql.startsWith("ALTER")
                || sql.startsWith("CREATE") || sql.startsWith("TRUNCATE");
    }

    private String generateAlias(String tableName, int index) {
        String[] parts = tableName.split("_");
        StringBuilder alias = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) alias.append(part.charAt(0));
        }
        if (alias.length() == 0) alias.append('t');
        return alias.toString().toLowerCase() + (index > 0 ? index : "");
    }

    private String parseJoinType(Join join) {
        if (join.isLeft()) return "LEFT";
        if (join.isRight()) return "RIGHT";
        if (join.isFull() || join.isOuter()) return "FULL";
        if (join.isCross()) return "CROSS";
        return "INNER";
    }

    @SuppressWarnings("deprecation")
    private String extractFieldName(Expression expr) {
        if (expr instanceof Column) {
            Column col = (Column) expr;
            return col.getColumnName();
        }
        if (expr instanceof Function) {
            Function func = (Function) expr;
            if (!func.getParameters().getExpressions().isEmpty()) {
                return extractFieldName((Expression) func.getParameters().getExpressions().get(0));
            }
        }
        return expr != null ? expr.toString() : null;
    }

    private String extractTableAlias(Expression expr) {
        if (expr instanceof Column) {
            Column col = (Column) expr;
            if (col.getTable() != null) {
                return col.getTable().getName();
            }
        }
        return null;
    }

    private boolean belongsToTable(Expression expr, String alias) {
        if (expr instanceof Column) {
            Column col = (Column) expr;
            if (col.getTable() != null) {
                return alias.equals(col.getTable().getName());
            }
            return true;
        }
        return true;
    }

    private String stripAliasPrefix(String fieldName, String alias) {
        if (fieldName != null && fieldName.toLowerCase().startsWith(alias.toLowerCase() + ".")) {
            return fieldName.substring(fieldName.indexOf('.') + 1);
        }
        return fieldName;
    }

    private String extractAggregateFunction(Expression expr) {
        if (expr instanceof Function) {
            Function func = (Function) expr;
            String name = func.getName().toUpperCase();
            if (name.equals("SUM") || name.equals("AVG") || name.equals("MAX")
                    || name.equals("MIN") || name.equals("COUNT")) {
                return name;
            }
        }
        return null;
    }

    private WhereCondition buildWhereCondition(Expression expr) {
        WhereCondition condition = new WhereCondition();
        if (expr instanceof AndExpression) {
            condition.setLogic("AND");
            AndExpression andExpr = (AndExpression) expr;
            addSubConditions(condition, andExpr.getLeftExpression());
            addSubConditions(condition, andExpr.getRightExpression());
        } else if (expr instanceof OrExpression) {
            condition.setLogic("OR");
            OrExpression orExpr = (OrExpression) expr;
            addSubConditions(condition, orExpr.getLeftExpression());
            addSubConditions(condition, orExpr.getRightExpression());
        } else {
            WhereCondition.ConditionItem item = buildConditionItem(expr);
            if (item != null) condition.getConditions().add(item);
        }
        return condition;
    }

    private void addSubConditions(WhereCondition parent, Expression expr) {
        if (expr instanceof AndExpression || expr instanceof OrExpression) {
            WhereCondition subGroup = buildWhereCondition(expr);
            parent.getGroups().add(subGroup);
        } else {
            WhereCondition.ConditionItem item = buildConditionItem(expr);
            if (item != null) parent.getConditions().add(item);
        }
    }

    private WhereCondition.ConditionItem buildConditionItem(Expression expr) {
        if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            WhereCondition.ConditionItem item = new WhereCondition.ConditionItem();
            item.setField(extractFieldName(binary.getLeftExpression()));
            item.setOperator(binary.getStringExpression().trim());
            Object val = extractValue(binary.getRightExpression());
            item.setValue(val != null ? val : binary.getRightExpression().toString());
            return item;
        } else if (expr instanceof InExpression) {
            InExpression inExpr = (InExpression) expr;
            WhereCondition.ConditionItem item = new WhereCondition.ConditionItem();
            item.setField(extractFieldName(inExpr.getLeftExpression()));
            item.setOperator(inExpr.isNot() ? "NOT IN" : "IN");
            item.setValue(inExpr.getRightExpression().toString());
            return item;
        } else if (expr instanceof IsNullExpression) {
            IsNullExpression isNull = (IsNullExpression) expr;
            WhereCondition.ConditionItem item = new WhereCondition.ConditionItem();
            item.setField(extractFieldName(isNull.getLeftExpression()));
            item.setOperator(isNull.isNot() ? "IS NOT NULL" : "IS NULL");
            item.setValue(null);
            return item;
        }
        return null;
    }

    private Object extractValue(Expression expr) {
        if (expr instanceof LongValue) return ((LongValue) expr).getValue();
        if (expr instanceof DoubleValue) return ((DoubleValue) expr).getValue();
        if (expr instanceof StringValue) return ((StringValue) expr).getValue();
        if (expr instanceof DateValue) return ((DateValue) expr).getValue().toString();
        return null;
    }

    private String buildSelectClause(QueryConfig.CanvasConfig cc) {
        if (cc.getTables() == null || cc.getTables().isEmpty()) return "*";

        List<String> parts = new ArrayList<>();
        for (CanvasTableConfig table : cc.getTables()) {
            if (table.getSelectedFields() == null || table.getSelectedFields().isEmpty()) continue;
            String alias = table.getAlias() != null ? table.getAlias() : table.getTableName();

            for (String field : table.getSelectedFields()) {
                String agg = table.getFieldAggregations() != null ? table.getFieldAggregations().get(field) : null;
                String fieldAlias = table.getFieldAliases() != null ? table.getFieldAliases().get(field) : null;

                StringBuilder part = new StringBuilder();
                if (agg != null) {
                    part.append(agg).append("(").append(alias).append(".").append(field).append(")");
                } else {
                    part.append(alias).append(".").append(field);
                }
                if (fieldAlias != null && !fieldAlias.equals(field)) {
                    part.append(" AS ").append(fieldAlias);
                }
                parts.add(part.toString());
            }
        }
        return parts.isEmpty() ? "*" : String.join(", ", parts);
    }

    private String buildFromClause(QueryConfig.CanvasConfig cc) {
        if (cc.getTables() == null || cc.getTables().isEmpty()) return "";
        CanvasTableConfig mainTable = cc.getTables().get(0);
        return mainTable.getTableName()
                + (mainTable.getAlias() != null ? " " + mainTable.getAlias() : "");
    }

    private String buildJoinClause(QueryConfig.CanvasConfig cc) {
        if (cc.getJoins() == null || cc.getJoins().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (CanvasJoinConfig join : cc.getJoins()) {
            sb.append(join.getJoinType()).append(" JOIN ")
              .append(join.getTargetTable()).append(" ").append(getAliasForTable(cc, join.getTargetTable()))
              .append(" ON ")
              .append(getAliasForTable(cc, join.getSourceTable())).append(".").append(join.getSourceField())
              .append(" = ")
              .append(getAliasForTable(cc, join.getTargetTable())).append(".").append(join.getTargetField())
              .append("\n");
        }
        return sb.toString().trim();
    }

    private String getAliasForTable(QueryConfig.CanvasConfig cc, String tableName) {
        if (cc.getTables() != null) {
            for (CanvasTableConfig t : cc.getTables()) {
                if (t.getTableName().equals(tableName) && t.getAlias() != null) {
                    return t.getAlias();
                }
            }
        }
        return tableName;
    }

    private String buildWhereClause(WhereCondition where) {
        if (where == null) return "";
        List<String> parts = new ArrayList<>();

        for (WhereCondition.ConditionItem ci : where.getConditions()) {
            String op = ci.getOperator();
            Object val = ci.getValue();
            String valStr;
            if (val == null) {
                valStr = "NULL";
            } else if (val instanceof String) {
                valStr = "'" + val + "'";
            } else if (op.equalsIgnoreCase("IN") || op.equalsIgnoreCase("NOT IN")) {
                valStr = val.toString();
            } else {
                valStr = val.toString();
            }
            parts.add(ci.getField() + " " + op + " " + valStr);
        }

        for (WhereCondition g : where.getGroups()) {
            parts.add("(" + buildWhereClause(g) + ")");
        }

        if (parts.isEmpty()) return "";
        return String.join(" " + where.getLogic() + " ", parts);
    }

    private double calculateMatchConfidence(ColumnMetaDTO a, ColumnMetaDTO b) {
        double score = 0.0;
        if (a.getColumnName().equalsIgnoreCase(b.getColumnName())) score += 0.8;
        if (a.getColumnName().toLowerCase().replace("_id", "").equals(
                b.getColumnName().toLowerCase().replace("_id", ""))) score += 0.5;
        if (a.getColumnType() != null && b.getColumnType() != null
                && a.getColumnType().equalsIgnoreCase(b.getColumnType())) score += 0.3;
        if (a.isPrimaryKey() || b.isPrimaryKey()) score += 0.2;
        return Math.min(score, 1.0);
    }

    private boolean isNumericType(String type) {
        return NUMERIC_TYPES.contains(type.toUpperCase().replaceAll("\\(.*\\)", ""));
    }

    private boolean isDateType(String type) {
        return DATE_TYPES.contains(type.toUpperCase().replaceAll("\\(.*\\)", ""));
    }
}
