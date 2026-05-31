package com.selfmodeling.dto;

import lombok.Data;
import java.util.List;

/**
 * 数据库表信息
 */
@Data
public class TableInfo {
    private String tableName;
    private String tableComment;
    private List<ColumnInfo> columns;

    @Data
    public static class ColumnInfo {
        private String columnName;
        private String columnType;
        private String columnComment;
        private boolean primaryKey;
        private boolean nullable;
    }
}
