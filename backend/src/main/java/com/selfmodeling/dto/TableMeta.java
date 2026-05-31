package com.selfmodeling.dto;

import lombok.Data;
import java.util.List;

/**
 * 数据库表元信息 DTO
 */
@Data
public class TableMeta {
    private String tableName;
    private String tableComment;
    private List<ColumnMeta> columns;
}
