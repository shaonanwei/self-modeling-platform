package com.selfmodeling.dto;

/**
 * 画布关联（JOIN）配置
 */
public class CanvasJoinConfig {
    private String id;
    private String sourceTable;
    private String sourceField;
    private String targetTable;
    private String targetField;
    private String joinType;

    public CanvasJoinConfig() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }

    public String getSourceField() { return sourceField; }
    public void setSourceField(String sourceField) { this.sourceField = sourceField; }

    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }

    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }

    public String getJoinType() { return joinType; }
    public void setJoinType(String joinType) { this.joinType = joinType; }
}
