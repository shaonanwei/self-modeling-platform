package com.selfmodeling.dto;

import java.util.List;

/**
 * 数据库表元信息 DTO（增强版）
 * 包含表的完整元数据信息
 */
public class TableMetaDTO {

    private String tableName;
    private String tableComment;
    private String tableType;
    private String schemaName;
    private Long rowCount;
    private String createTime;
    private String updateTime;
    private List<ColumnMetaDTO> columns;
    private List<String> primaryKeys;
    private List<IndexInfo> indexes;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableComment() {
        return tableComment;
    }

    public void setTableComment(String tableComment) {
        this.tableComment = tableComment;
    }

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public List<ColumnMetaDTO> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnMetaDTO> columns) {
        this.columns = columns;
    }

    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }

    public void setPrimaryKeys(List<String> primaryKeys) {
        this.primaryKeys = primaryKeys;
    }

    public List<IndexInfo> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<IndexInfo> indexes) {
        this.indexes = indexes;
    }

    /**
     * 索引信息内部类
     */
    public static class IndexInfo {
        private String indexName;
        private String indexType;
        private boolean unique;
        private List<String> columns;

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public String getIndexType() {
            return indexType;
        }

        public void setIndexType(String indexType) {
            this.indexType = indexType;
        }

        public boolean isUnique() {
            return unique;
        }

        public void setUnique(boolean unique) {
            this.unique = unique;
        }

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns;
        }
    }
}
