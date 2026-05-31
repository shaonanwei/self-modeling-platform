package com.selfmodeling.dto;

import lombok.Data;

/**
 * 数据库列元信息
 */
@Data
public class ColumnMeta {
    private String columnName;
    private String columnType;
    private String columnComment;
    private boolean primaryKey;
    private boolean nullable;
}
