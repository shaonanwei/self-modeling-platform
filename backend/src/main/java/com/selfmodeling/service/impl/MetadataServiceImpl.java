package com.selfmodeling.service.impl;

import com.selfmodeling.dto.ColumnMetaDTO;
import com.selfmodeling.dto.DataSourceInfo;
import com.selfmodeling.dto.TableMetaDTO;
import com.selfmodeling.service.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import com.selfmodeling.config.DataSourceConfig;
/**
 * 元数据服务实现类（增强版）
 * 支持动态数据源的元数据查询
 */
@Service
public class MetadataServiceImpl implements MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataServiceImpl.class);



    @Override
    public List<DataSourceInfo> getDataSources() {
        List<DataSourceInfo> dataSources = new ArrayList<>();
        Map<String, DataSource> dataSourceMap = DataSourceConfig.getDataSources();

        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            String dataSourceId = entry.getKey();
            JdbcTemplate jdbcTemplate = new JdbcTemplate(entry.getValue());
            String dataSourceName = getDataSourceName(dataSourceId);
            String dataSourceType = getDataSourceType(dataSourceId);

            DataSourceInfo dsInfo = new DataSourceInfo(dataSourceId, dataSourceName, dataSourceType);
            dsInfo.setConnected(checkConnection(jdbcTemplate));
            dataSources.add(dsInfo);
        }

        return dataSources;
    }

    /**
     * 根据数据源ID获取数据源名称
     */
    private String getDataSourceName(String dataSourceId) {
        switch (dataSourceId.toLowerCase()) {
            case "master":
                return "MySQL (系统数据库)";
            case "postgres":
                return "PostgreSQL";
            case "hive":
                return "Apache Hive";
            case "sqlite":
                return "SQLite";
            default:
                return dataSourceId;
        }
    }

    /**
     * 根据数据源ID获取数据源类型
     */
    private String getDataSourceType(String dataSourceId) {
        switch (dataSourceId.toLowerCase()) {
            case "master":
                return "MYSQL";
            case "postgres":
                return "POSTGRESQL";
            case "hive":
                return "HIVE";
            case "sqlite":
                return "SQLITE";
            default:
                return "UNKNOWN";
        }
    }

    @Override
    public DataSourceInfo checkDataSourceConnection(String dataSourceId) {
        JdbcTemplate jdbcTemplate = getJdbcTemplateByDataSourceId(dataSourceId);
        DataSourceInfo info = new DataSourceInfo();
        info.setDataSourceId(dataSourceId);
        info.setConnected(checkConnection(jdbcTemplate));
        info.setLastCheckTime(System.currentTimeMillis());
        return info;
    }

    @Override
    public List<TableMetaDTO> getAllTables(String dataSourceId, String keyword) {
        List<TableMetaDTO> tables = new ArrayList<>();
        log.info("getAllTables called with dataSourceId={}", dataSourceId);
        JdbcTemplate jdbcTemplate = getJdbcTemplateByDataSourceId(dataSourceId);
        if (jdbcTemplate == null) {
            log.warn("数据源 {} 未配置，无法获取表列表", dataSourceId);
            return tables;
        }

        try {
            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource == null) {
                log.error("数据源 {} 配置异常，无法获取数据库连接", dataSourceId);
                return tables;
            }
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String catalog = getConnectionCatalog(metaData, dataSourceId);

                // MySQL需要特殊处理
                String effectiveCatalog = catalog;
                String effectiveSchema = null;
                
                // 对于MySQL，使用null作为catalog参数可以返回所有数据库的表
                // 然后我们再根据数据库名过滤
                if ("master".equalsIgnoreCase(dataSourceId)) {
                    effectiveCatalog = null;
                    effectiveSchema = null;
                }

                try (ResultSet rs = metaData.getTables(effectiveCatalog, effectiveSchema, "%", new String[]{"TABLE", "VIEW"})) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        String tableType = rs.getString("TABLE_TYPE");
                        String remarks = rs.getString("REMARKS");
                        String tableCatalog = rs.getString("TABLE_CAT");

                        // 对于MySQL，过滤非当前数据库的表
                        if ("master".equalsIgnoreCase(dataSourceId) && catalog != null && !catalog.equalsIgnoreCase(tableCatalog)) {
                            continue;
                        }

                        // 过滤系统表
                        if (isSystemTable(tableName, dataSourceId)) {
                            continue;
                        }

                        // 关键字过滤
                        if (keyword != null && !keyword.trim().isEmpty()) {
                            String kw = keyword.toLowerCase();
                            if (!tableName.toLowerCase().contains(kw) &&
                                (remarks == null || !remarks.toLowerCase().contains(kw))) {
                                continue;
                            }
                        }

                        TableMetaDTO tableMeta = new TableMetaDTO();
                        tableMeta.setTableName(tableName);
                        tableMeta.setTableComment(remarks);
                        tableMeta.setTableType(tableType);
                        tables.add(tableMeta);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("获取表列表失败: dataSourceId={}", dataSourceId, e);
        }

        return tables;
    }

    @Override
    public TableMetaDTO getTableStructure(String dataSourceId, String tableName) {
        TableMetaDTO tableMeta = new TableMetaDTO();
        tableMeta.setTableName(tableName);
        JdbcTemplate jdbcTemplate = getJdbcTemplateByDataSourceId(dataSourceId);
        if (jdbcTemplate == null) {
            log.warn("数据源 {} 未配置，无法获取表信息", dataSourceId);
            return tableMeta;
        }

        try {
            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource == null) {
                log.error("数据源 {} 配置异常，无法获取数据库连接", dataSourceId);
                return tableMeta;
            }
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String catalog = getConnectionCatalog(metaData, dataSourceId);

                String tablePattern = escapeMetadataPattern(metaData, tableName);
                boolean exactTableFound = false;
                // JDBC 元数据参数是搜索 pattern，必须转义并再次核对返回表名。
                try (ResultSet rs = metaData.getTables(catalog, null, tablePattern, null)) {
                    while (rs.next()) {
                        if (!tableName.equals(rs.getString("TABLE_NAME"))) {
                            continue;
                        }
                        tableMeta.setTableComment(rs.getString("REMARKS"));
                        tableMeta.setTableType(rs.getString("TABLE_TYPE"));
                        tableMeta.setSchemaName(rs.getString("TABLE_SCHEM"));
                        exactTableFound = true;
                        break;
                    }
                }
                if (!exactTableFound) {
                    return tableMeta;
                }

                // 获取字段信息
                tableMeta.setColumns(getTableColumns(dataSourceId, tableName));

                // 获取主键信息
                tableMeta.setPrimaryKeys(getPrimaryKeys(metaData, catalog, tableName));

                // 获取索引信息
                tableMeta.setIndexes(getIndexes(metaData, catalog, tableName));

            }
        } catch (SQLException e) {
            log.error("获取表信息失败: dataSourceId={}, tableName={}", dataSourceId, tableName, e);
        }

        return tableMeta;
    }

    @Override
    public TableMetaDTO getTableInfo(String dataSourceId, String tableName) {
        TableMetaDTO tableMeta = getTableStructure(dataSourceId, tableName);
        try {
            tableMeta.setRowCount(getTableRowCount(dataSourceId, tableName));
        } catch (Exception e) {
            log.warn("获取表行数失败: {}", e.getMessage());
        }
        return tableMeta;
    }

    @Override
    public List<ColumnMetaDTO> getTableColumns(String dataSourceId, String tableName) {
        List<ColumnMetaDTO> columns = new ArrayList<>();
        JdbcTemplate jdbcTemplate = getJdbcTemplateByDataSourceId(dataSourceId);
        if (jdbcTemplate == null) {
            log.warn("数据源 {} 未配置，无法获取字段列表", dataSourceId);
            return columns;
        }

        try {
            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource == null) {
                log.error("数据源 {} 配置异常，无法获取数据库连接", dataSourceId);
                return columns;
            }
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String catalog = getConnectionCatalog(metaData, dataSourceId);

                String tablePattern = escapeMetadataPattern(metaData, tableName);
                try (ResultSet rs = metaData.getColumns(catalog, null, tablePattern, "%")) {
                    while (rs.next()) {
                        if (!tableName.equals(rs.getString("TABLE_NAME"))) {
                            continue;
                        }
                        ColumnMetaDTO column = new ColumnMetaDTO();
                        column.setColumnName(rs.getString("COLUMN_NAME"));
                        column.setColumnType(rs.getString("TYPE_NAME"));
                        column.setDataType(rs.getString("DATA_TYPE"));
                        column.setColumnSize(rs.getInt("COLUMN_SIZE"));
                        column.setDecimalDigits(rs.getInt("DECIMAL_DIGITS"));
                        column.setColumnComment(rs.getString("REMARKS"));
                        column.setNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                        column.setDefaultValue(rs.getString("COLUMN_DEF"));
                        column.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));

                        // 判断是否自增（PostgreSQL 特有）
                        if ("POSTGRESQL".equals(dataSourceId.toUpperCase())) {
                            column.setAutoIncrement(isAutoIncrement(jdbcTemplate, tableName, column.getColumnName()));
                        }

                        columns.add(column);
                    }
                }

                // 标记主键字段
                List<String> pks = getPrimaryKeys(metaData, catalog, tableName);
                columns.forEach(col -> col.setPrimaryKey(pks.contains(col.getColumnName())));

                // 标记有索引的字段
                List<String> indexedCols = getIndexedColumns(metaData, catalog, tableName);
                columns.forEach(col -> col.setIndexed(indexedCols.contains(col.getColumnName())));
            }
        } catch (SQLException e) {
            log.error("获取字段列表失败: dataSourceId={}, tableName={}", dataSourceId, tableName, e);
        }

        return columns;
    }

	/**
	 * 转义 JDBC 元数据搜索 pattern 中的通配符。
	 */
	private String escapeMetadataPattern(DatabaseMetaData metaData, String value)
			throws SQLException {
		String escape = metaData.getSearchStringEscape();
		if (escape == null || escape.isEmpty()) {
			throw new SQLException("数据库未提供元数据搜索转义符");
		}
		return value.replace(escape, escape + escape)
				.replace("%", escape + "%")
				.replace("_", escape + "_");
	}

    @Override
    public List<TableMetaDTO> searchMetadata(String dataSourceId, String keyword, String searchType) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllTables(dataSourceId, null);
        }

        List<TableMetaDTO> allTables = getAllTables(dataSourceId, null);
        String kw = keyword.toLowerCase().trim();

        if ("column".equalsIgnoreCase(searchType)) {
            // 搜索字段名或注释
            return allTables.stream()
                .filter(table -> {
                    List<ColumnMetaDTO> cols = getTableColumns(dataSourceId, table.getTableName());
                    return cols.stream().anyMatch(col ->
                        col.getColumnName().toLowerCase().contains(kw) ||
                        (col.getColumnComment() != null && col.getColumnComment().toLowerCase().contains(kw))
                    );
                })
                .collect(Collectors.toList());
        } else {
            // 搜索表名（默认）
            return allTables.stream()
                .filter(table ->
                    table.getTableName().toLowerCase().contains(kw) ||
                    (table.getTableComment() != null && table.getTableComment().toLowerCase().contains(kw))
                )
                .collect(Collectors.toList());
        }
    }

    @Override
    public Long getTableRowCount(String dataSourceId, String tableName) {
        JdbcTemplate jdbcTemplate = getJdbcTemplateByDataSourceId(dataSourceId);
        if (jdbcTemplate == null) {
            log.warn("数据源 {} 未配置，无法获取表行数", dataSourceId);
            return 0L;
        }
        try {
            // 使用 COUNT(*) 获取行数
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + quoteIdentifier(tableName, dataSourceId),
                Long.class
            );
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("获取表行数失败: {}", e.getMessage());
            return 0L;
        }
    }

    @Override
    public List<Map<String, Object>> previewTableData(String dataSourceId, String tableName, int limit) {
        JdbcTemplate jdbcTemplate = getJdbcTemplateByDataSourceId(dataSourceId);
        if (jdbcTemplate == null) {
            log.warn("数据源 {} 未配置，无法预览表数据", dataSourceId);
            return new ArrayList<>();
        }
        try {
            String sql = "SELECT * FROM " + quoteIdentifier(tableName, dataSourceId) + " LIMIT " + limit;
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("预览表数据失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 根据数据源ID获取对应的 JdbcTemplate
     * 如果数据源未配置则返回 null
     */
    public JdbcTemplate getJdbcTemplateByDataSourceId(String dataSourceId) {
        return com.selfmodeling.config.DataSourceConfig.getJdbcTemplate(dataSourceId);
    }

    /**
     * 检查数据库连接是否可用
     */
    private boolean checkConnection(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断是否为系统表
     */
    private boolean isSystemTable(String tableName, String dataSourceId) {
        if ("sqlite".equalsIgnoreCase(dataSourceId)) {
            return tableName.startsWith("sqlite_") || tableName.startsWith("temp_");
        } else if ("master".equalsIgnoreCase(dataSourceId)) {
            // MySQL 系统表 - 只排除明确的系统数据库中的表
            // 注意：不要使用 tableName.startsWith("sys")，因为用户可能有名为 sys_user 等的自定义表
            return tableName.startsWith("mysql_") || 
                   tableName.startsWith("innodb_") ||
                   tableName.startsWith("ndb_");
        } else if ("postgres".equalsIgnoreCase(dataSourceId)) {
            return tableName.startsWith("pg_") || tableName.startsWith("sql_");
        } else if ("hive".equalsIgnoreCase(dataSourceId)) {
            // Hive 通常没有系统表需要排除
            return false;
        }
        return false;
    }

    /**
     * 获取连接的 Catalog（数据库名）
     */
    private String getConnectionCatalog(DatabaseMetaData metaData, String dataSourceId) throws SQLException {
        try {
            String catalog = metaData.getConnection().getCatalog();
            // 如果catalog为null，尝试从JDBC URL中提取数据库名
            if (catalog == null || catalog.isEmpty()) {
                String url = metaData.getURL();
                catalog = extractDatabaseNameFromUrl(url, dataSourceId);
            }
            // Hive 可能返回 null
            if (catalog == null && "hive".equalsIgnoreCase(dataSourceId)) {
                return "default";
            }
            return catalog;
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * 从JDBC URL中提取数据库名
     */
    private String extractDatabaseNameFromUrl(String url, String dataSourceId) {
        if (url == null) {
            return null;
        }
        // MySQL: jdbc:mysql://localhost:3306/databaseName
        if ("master".equalsIgnoreCase(dataSourceId) || url.startsWith("jdbc:mysql:")) {
            int start = url.lastIndexOf('/') + 1;
            int end = url.indexOf('?', start);
            if (end == -1) {
                end = url.length();
            }
            return url.substring(start, end);
        }
        // PostgreSQL: jdbc:postgresql://localhost:5432/databaseName
        if ("postgres".equalsIgnoreCase(dataSourceId) || url.startsWith("jdbc:postgresql:")) {
            int start = url.lastIndexOf('/') + 1;
            int end = url.indexOf('?', start);
            if (end == -1) {
                end = url.length();
            }
            return url.substring(start, end);
        }
        // SQLite: jdbc:sqlite:databaseName.db
        if ("sqlite".equalsIgnoreCase(dataSourceId) || url.startsWith("jdbc:sqlite:")) {
            return url.substring("jdbc:sqlite:".length());
        }
        // Hive: jdbc:hive2://localhost:10000/databaseName
        if ("hive".equalsIgnoreCase(dataSourceId) || url.startsWith("jdbc:hive2:")) {
            int start = url.lastIndexOf('/') + 1;
            int end = url.indexOf('?', start);
            if (end == -1) {
                end = url.length();
            }
            return url.substring(start, end);
        }
        return null;
    }

    /**
     * 获取表的主键列表
     */
    private List<String> getPrimaryKeys(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        List<String> primaryKeys = new ArrayList<>();
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, null, tableName)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }
        return primaryKeys;
    }

    /**
     * 获取表的索引信息
     */
    private List<TableMetaDTO.IndexInfo> getIndexes(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        List<TableMetaDTO.IndexInfo> indexes = new ArrayList<>();
        try (ResultSet rs = metaData.getIndexInfo(catalog, null, tableName, false, true)) {
            Map<String, TableMetaDTO.IndexInfo> indexMap = new LinkedHashMap<>();
            while (rs.next()) {
                try {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName == null) continue;

                    TableMetaDTO.IndexInfo index = indexMap.computeIfAbsent(indexName, k -> {
                        TableMetaDTO.IndexInfo idx = new TableMetaDTO.IndexInfo();
                        idx.setIndexName(k);
                        try {
                            idx.setIndexType(rs.getString("TYPE"));
                            idx.setUnique(!"FALSE".equalsIgnoreCase(rs.getString("NON_UNIQUE")));
                        } catch (SQLException ex) {
                            log.warn("读取索引信息失败: {}", ex.getMessage());
                        }
                        idx.setColumns(new ArrayList<>());
                        return idx;
                    });
                    index.getColumns().add(rs.getString("COLUMN_NAME"));
                } catch (SQLException e) {
                    log.warn("读取索引行信息时出错: {}", e.getMessage());
                }
            }
            indexes.addAll(indexMap.values());
        }
        return indexes;
    }

    /**
     * 获取有索引的字段名列表
     */
    private List<String> getIndexedColumns(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        Set<String> indexedCols = new HashSet<>();
        try (ResultSet rs = metaData.getIndexInfo(catalog, null, tableName, false, true)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                if (colName != null) {
                    indexedCols.add(colName);
                }
            }
        }
        return new ArrayList<>(indexedCols);
    }

    /**
     * 判断 PostgreSQL 字段是否为自增
     */
    private boolean isAutoIncrement(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        try {
            String sql = "SELECT column_default FROM information_schema.columns " +
                         "WHERE table_name = ? AND column_name = ? " +
                         "AND column_default LIKE 'nextval%'";
            String defaultValue = jdbcTemplate.queryForObject(sql, String.class, tableName, columnName);
            return defaultValue != null && defaultValue.contains("nextval");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 根据数据源类型对标识符进行引用（防止关键字冲突）
     */
    private String quoteIdentifier(String identifier, String dataSourceId) {
        if ("postgres".equalsIgnoreCase(dataSourceId) || "hive".equalsIgnoreCase(dataSourceId)) {
            return "\"" + identifier + "\"";
        } else if ("sqlite".equalsIgnoreCase(dataSourceId)) {
            return "[" + identifier + "]"; // SQLite
        } else {
            return "`" + identifier + "`"; // MySQL 或其他
        }
    }
}
