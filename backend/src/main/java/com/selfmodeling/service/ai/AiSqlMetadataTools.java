/*
 * Copyright 2026 Chill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.selfmodeling.service.ai;

import com.selfmodeling.dto.ColumnMetaDTO;
import com.selfmodeling.dto.TableMetaDTO;
import com.selfmodeling.dto.ai.AiSqlTableDescription;
import com.selfmodeling.dto.ai.AiSqlTableSummary;
import com.selfmodeling.service.MetadataService;
import com.selfmodeling.service.SqlService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI SQL 只读元数据工具。
 *
 * @author Chill
 */
@Service
public class AiSqlMetadataTools {

	private static final int MAX_TABLE_RESULTS = 50;
	private static final int MAX_DESCRIBE_TABLES = 5;
	private static final int MAX_DATA_SOURCE_ID_LENGTH = 128;
	private static final int MAX_KEYWORD_LENGTH = 100;
	private static final int MAX_TABLE_NAME_LENGTH = 128;
	private static final int MAX_COLUMNS_PER_TABLE = 200;
	private static final int MAX_PRIMARY_KEYS_PER_TABLE = 32;
	private static final int MAX_INDEXES_PER_TABLE = 100;
	private static final int MAX_INDEX_COLUMNS = 32;
	private static final int MAX_RELATIONS_PER_TABLE = 100;
	private static final int MAX_COMMENT_LENGTH = 1000;
	private static final int MAX_METADATA_TEXT_LENGTH = 256;

	private final MetadataService metadataService;
	private final SqlService sqlService;

	public AiSqlMetadataTools(MetadataService metadataService, SqlService sqlService) {
		this.metadataService = metadataService;
		this.sqlService = sqlService;
	}

	public List<AiSqlTableSummary> listTables(String dataSourceId, String keyword) {
		return safeList(metadataService.getAllTables(
				requireDataSourceId(dataSourceId), normalizeKeyword(keyword))).stream()
				.limit(MAX_TABLE_RESULTS)
				.map(this::toSummary)
				.toList();
	}

	public List<AiSqlTableDescription> describeTables(
			String dataSourceId, List<String> tableNames) {
		if (tableNames == null || tableNames.isEmpty()
				|| tableNames.size() > MAX_DESCRIBE_TABLES) {
			throw new IllegalArgumentException("一次必须描述 1 到 5 张表");
		}
		String normalizedDataSourceId = requireDataSourceId(dataSourceId);
		List<String> normalizedNames = tableNames.stream()
				.map(this::requireTableName)
				.distinct()
				.toList();
		Set<String> allowedNames = safeList(metadataService.getAllTables(
				normalizedDataSourceId, null)).stream()
				.map(TableMetaDTO::getTableName)
				.filter(name -> name != null && !name.isBlank())
				.collect(Collectors.toUnmodifiableSet());
		if (!allowedNames.containsAll(normalizedNames)) {
			throw new IllegalArgumentException("请求的表不存在或不可访问");
		}
		return normalizedNames.stream()
				.map(tableName -> describeOne(normalizedDataSourceId, tableName))
				.toList();
	}

	private AiSqlTableDescription describeOne(String dataSourceId, String tableName) {
		TableMetaDTO table = metadataService.getTableStructure(dataSourceId, tableName);
		List<Map<String, Object>> relations = sqlService.getTableRelations(tableName, dataSourceId);
		return new AiSqlTableDescription(
				bounded(table.getTableName(), MAX_TABLE_NAME_LENGTH),
				bounded(table.getSchemaName(), MAX_METADATA_TEXT_LENGTH),
				bounded(table.getTableType(), MAX_METADATA_TEXT_LENGTH),
				bounded(table.getTableComment(), MAX_COMMENT_LENGTH),
				safeList(table.getColumns()).stream()
						.limit(MAX_COLUMNS_PER_TABLE)
						.map(this::toColumnDescription).toList(),
				safeList(table.getPrimaryKeys()).stream()
						.limit(MAX_PRIMARY_KEYS_PER_TABLE)
						.map(value -> bounded(value, MAX_TABLE_NAME_LENGTH)).toList(),
				safeList(table.getIndexes()).stream()
						.limit(MAX_INDEXES_PER_TABLE)
						.map(this::toIndexDescription).toList(),
				safeList(relations).stream()
						.limit(MAX_RELATIONS_PER_TABLE)
						.map(this::toRelationDescription).toList());
	}

	private AiSqlTableSummary toSummary(TableMetaDTO table) {
		return new AiSqlTableSummary(
				bounded(table.getTableName(), MAX_TABLE_NAME_LENGTH),
				bounded(table.getSchemaName(), MAX_METADATA_TEXT_LENGTH),
				bounded(table.getTableType(), MAX_METADATA_TEXT_LENGTH),
				bounded(table.getTableComment(), MAX_COMMENT_LENGTH));
	}

	private AiSqlTableDescription.ColumnDescription toColumnDescription(ColumnMetaDTO column) {
		return new AiSqlTableDescription.ColumnDescription(
				bounded(column.getColumnName(), MAX_TABLE_NAME_LENGTH),
				bounded(column.getColumnType(), MAX_METADATA_TEXT_LENGTH),
				bounded(column.getDataType(), MAX_METADATA_TEXT_LENGTH),
				column.getColumnSize(),
				column.getDecimalDigits(),
				bounded(column.getColumnComment(), MAX_COMMENT_LENGTH),
				column.isPrimaryKey(),
				column.isNullable(),
				column.isAutoIncrement(),
				column.getOrdinalPosition(),
				column.isIndexed());
	}

	private AiSqlTableDescription.IndexDescription toIndexDescription(TableMetaDTO.IndexInfo index) {
		return new AiSqlTableDescription.IndexDescription(
				bounded(index.getIndexName(), MAX_TABLE_NAME_LENGTH),
				bounded(index.getIndexType(), MAX_METADATA_TEXT_LENGTH),
				index.isUnique(),
				safeList(index.getColumns()).stream()
						.limit(MAX_INDEX_COLUMNS)
						.map(value -> bounded(value, MAX_TABLE_NAME_LENGTH)).toList());
	}

	private AiSqlTableDescription.RelationDescription toRelationDescription(Map<String, Object> relation) {
		return new AiSqlTableDescription.RelationDescription(
				bounded(stringValue(relation, "sourceTable"), MAX_TABLE_NAME_LENGTH),
				bounded(stringValue(relation, "sourceField"), MAX_TABLE_NAME_LENGTH),
				bounded(stringValue(relation, "targetTable"), MAX_TABLE_NAME_LENGTH),
				bounded(stringValue(relation, "targetField"), MAX_TABLE_NAME_LENGTH),
				bounded(stringValue(relation, "relationType"), MAX_METADATA_TEXT_LENGTH));
	}

	private String requireDataSourceId(String dataSourceId) {
		if (dataSourceId == null || dataSourceId.isBlank()
				|| dataSourceId.length() > MAX_DATA_SOURCE_ID_LENGTH) {
			throw new IllegalArgumentException("数据源标识不合法");
		}
		return dataSourceId.trim();
	}

	private String requireTableName(String tableName) {
		if (tableName == null || tableName.isBlank()
				|| tableName.length() > MAX_TABLE_NAME_LENGTH) {
			throw new IllegalArgumentException("表名不合法");
		}
		return tableName.trim();
	}

	private String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		if (keyword.length() > MAX_KEYWORD_LENGTH) {
			throw new IllegalArgumentException("表搜索关键词过长");
		}
		return keyword.trim();
	}

	private String stringValue(Map<String, Object> values, String key) {
		Object value = values.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private String bounded(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private <T> List<T> safeList(List<T> values) {
		return values == null ? List.of() : values;
	}
}
