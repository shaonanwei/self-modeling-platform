package com.selfmodeling.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 完整的查询配置（包含 SQL 和画布状态）
 * 存储在 step_config.queryConfig 中
 */
public class QueryConfig {
    private String mode;
    private String sql;
    private CanvasConfig canvasConfig;

    public QueryConfig() {
        this.mode = "canvas";
        this.sql = "";
        this.canvasConfig = new CanvasConfig();
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public CanvasConfig getCanvasConfig() { return canvasConfig; }
    public void setCanvasConfig(CanvasConfig canvasConfig) { this.canvasConfig = canvasConfig; }

    /**
     * 画布完整配置
     */
    public static class CanvasConfig {
        private List<CanvasTableConfig> tables;
        private List<CanvasJoinConfig> joins;
        private WhereCondition where;
        private List<String> groupBy;
        private WhereCondition having;
        private List<OrderByItem> orderBy;
        private Integer limit;
        private Boolean distinct;
        private String customSqlFragment;

        public CanvasConfig() {
            this.tables = new ArrayList<>();
            this.joins = new ArrayList<>();
            this.where = null;
            this.groupBy = new ArrayList<>();
            this.having = null;
            this.orderBy = new ArrayList<>();
            this.limit = 100;
            this.distinct = false;
            this.customSqlFragment = null;
        }

        public List<CanvasTableConfig> getTables() { return tables; }
        public void setTables(List<CanvasTableConfig> tables) { this.tables = tables; }

        public List<CanvasJoinConfig> getJoins() { return joins; }
        public void setJoins(List<CanvasJoinConfig> joins) { this.joins = joins; }

        public WhereCondition getWhere() { return where; }
        public void setWhere(WhereCondition where) { this.where = where; }

        public List<String> getGroupBy() { return groupBy; }
        public void setGroupBy(List<String> groupBy) { this.groupBy = groupBy; }

        public WhereCondition getHaving() { return having; }
        public void setHaving(WhereCondition having) { this.having = having; }

        public List<OrderByItem> getOrderBy() { return orderBy; }
        public void setOrderBy(List<OrderByItem> orderBy) { this.orderBy = orderBy; }

        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }

        public Boolean getDistinct() { return distinct; }
        public void setDistinct(Boolean distinct) { this.distinct = distinct; }

        public String getCustomSqlFragment() { return customSqlFragment; }
        public void setCustomSqlFragment(String customSqlFragment) { this.customSqlFragment = customSqlFragment; }
    }

    /**
     * 排序项
     */
    public static class OrderByItem {
        private String field;
        private String direction;

        public OrderByItem() {}

        public OrderByItem(String field, String direction) {
            this.field = field;
            this.direction = direction;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
    }
}
