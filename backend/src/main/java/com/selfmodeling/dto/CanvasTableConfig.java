package com.selfmodeling.dto;

import java.util.List;
import java.util.Map;

/**
 * 画布表节点配置
 */
public class CanvasTableConfig {
    private String tableName;
    private String alias;
    private int x;
    private int y;
    private boolean expanded;
    private List<String> selectedFields;
    private Map<String, String> fieldAliases;
    private Map<String, String> fieldAggregations;

    public CanvasTableConfig() {}

    public CanvasTableConfig(String tableName, String alias) {
        this.tableName = tableName;
        this.alias = alias;
        this.x = 0;
        this.y = 0;
        this.expanded = true;
        this.selectedFields = new java.util.ArrayList<>();
        this.fieldAliases = new java.util.LinkedHashMap<>();
        this.fieldAggregations = new java.util.LinkedHashMap<>();
    }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    public List<String> getSelectedFields() { return selectedFields; }
    public void setSelectedFields(List<String> selectedFields) { this.selectedFields = selectedFields; }

    public Map<String, String> getFieldAliases() { return fieldAliases; }
    public void setFieldAliases(Map<String, String> fieldAliases) { this.fieldAliases = fieldAliases; }

    public Map<String, String> getFieldAggregations() { return fieldAggregations; }
    public void setFieldAggregations(Map<String, String> fieldAggregations) { this.fieldAggregations = fieldAggregations; }
}
