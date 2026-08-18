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

package com.selfmodeling.dto.ai;

import java.util.List;

/**
 * AI SQL 可见的表结构描述。
 *
 * @author Chill
 */
public record AiSqlTableDescription(
		String tableName,
		String schemaName,
		String tableType,
		String tableComment,
		List<ColumnDescription> columns,
		List<String> primaryKeys,
		List<IndexDescription> indexes,
		List<RelationDescription> relations
) {

	/**
	 * AI SQL 可见的字段结构描述。
	 *
	 * @author Chill
	 */
	public record ColumnDescription(
			String columnName,
			String columnType,
			String dataType,
			Integer columnSize,
			Integer decimalDigits,
			String columnComment,
			boolean primaryKey,
			boolean nullable,
			boolean autoIncrement,
			Integer ordinalPosition,
			boolean indexed
	) {
	}

	/**
	 * AI SQL 可见的索引结构描述。
	 *
	 * @author Chill
	 */
	public record IndexDescription(
			String indexName,
			String indexType,
			boolean unique,
			List<String> columns
	) {
	}

	/**
	 * AI SQL 可见的表关联描述。
	 *
	 * @author Chill
	 */
	public record RelationDescription(
			String sourceTable,
			String sourceField,
			String targetTable,
			String targetField,
			String relationType
	) {
	}
}
