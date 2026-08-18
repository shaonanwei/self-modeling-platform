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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfmodeling.service.sql.ReadOnlySqlGuard;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI SQL 工具白名单执行器。
 *
 * @author Chill
 */
@Service
public class AiSqlToolExecutor {

	private static final Set<String> ALLOWED_TOOLS = Set.of(
			"list_tables", "describe_tables", "validate_read_only_sql");
	private static final int MAX_DATA_SOURCE_ID_LENGTH = 128;
	private static final int MAX_KEYWORD_LENGTH = 100;
	private static final int MAX_TABLE_NAME_LENGTH = 128;
	private static final int MAX_SQL_LENGTH = 20000;
	private static final int MAX_ERROR_MESSAGE_LENGTH = 200;
	private static final int MAX_TOOL_RESULT_BYTES = 256 * 1024;

	private final AiSqlMetadataTools metadataTools;
	private final ReadOnlySqlGuard readOnlySqlGuard;
	private final ObjectMapper objectMapper;

	public AiSqlToolExecutor(AiSqlMetadataTools metadataTools,
			ReadOnlySqlGuard readOnlySqlGuard, ObjectMapper objectMapper) {
		this.metadataTools = metadataTools;
		this.readOnlySqlGuard = readOnlySqlGuard;
		this.objectMapper = objectMapper;
	}

	public String execute(String toolName, String argumentsJson, String dataSourceId) {
		if (!ALLOWED_TOOLS.contains(toolName)) {
			throw new IllegalArgumentException("不允许的 AI 工具: " + toolName);
		}
		String normalizedDataSourceId = requireDataSourceId(dataSourceId);
		return switch (toolName) {
			case "list_tables" -> {
				ListTablesArgs arguments = read(argumentsJson, ListTablesArgs.class);
				yield write(metadataTools.listTables(
						normalizedDataSourceId, validateKeyword(arguments)));
			}
			case "describe_tables" -> {
				DescribeTablesArgs arguments = read(argumentsJson, DescribeTablesArgs.class);
				yield write(metadataTools.describeTables(
						normalizedDataSourceId, validateTableNames(arguments)));
			}
			case "validate_read_only_sql" -> {
				ValidateSqlArgs arguments = read(argumentsJson, ValidateSqlArgs.class);
				yield write(validate(validateSql(arguments)));
			}
			default -> throw new IllegalStateException("工具白名单分支不完整");
		};
	}

	private Map<String, Object> validate(String sql) {
		try {
			String normalized = readOnlySqlGuard.validate(sql);
			return Map.of("valid", true, "safeSql", normalized, "message", "校验通过");
		} catch (IllegalArgumentException exception) {
			return Map.of("valid", false, "message", safeMessage(exception));
		}
	}

	private <T> T read(String argumentsJson, Class<T> argumentType) {
		try {
			T arguments = objectMapper.readerFor(argumentType)
					.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
					.readValue(argumentsJson);
			if (arguments == null) {
				throw new IllegalArgumentException("AI 工具参数不能为空");
			}
			return arguments;
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("AI 工具参数格式不正确");
		}
	}

	private String write(Object result) {
		try {
			String json = objectMapper.writeValueAsString(result);
			if (json.getBytes(StandardCharsets.UTF_8).length > MAX_TOOL_RESULT_BYTES) {
				throw new IllegalArgumentException("AI 工具结果过大");
			}
			return json;
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("AI 工具结果序列化失败", exception);
		}
	}

	private String requireDataSourceId(String dataSourceId) {
		if (dataSourceId == null || dataSourceId.isBlank()
				|| dataSourceId.length() > MAX_DATA_SOURCE_ID_LENGTH) {
			throw new IllegalArgumentException("数据源标识不合法");
		}
		return dataSourceId.trim();
	}

	private String validateKeyword(ListTablesArgs arguments) {
		String keyword = arguments.keyword();
		if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
			throw new IllegalArgumentException("表搜索关键词过长");
		}
		return keyword == null ? null : keyword.trim();
	}

	private List<String> validateTableNames(DescribeTablesArgs arguments) {
		List<String> tableNames = arguments.tableNames();
		if (tableNames == null || tableNames.isEmpty() || tableNames.size() > 5) {
			throw new IllegalArgumentException("一次必须描述 1 到 5 张表");
		}
		return tableNames.stream().map(tableName -> {
			if (tableName == null || tableName.isBlank()
					|| tableName.length() > MAX_TABLE_NAME_LENGTH) {
				throw new IllegalArgumentException("表名不合法");
			}
			return tableName.trim();
		}).toList();
	}

	private String validateSql(ValidateSqlArgs arguments) {
		String sql = arguments.sql();
		if (sql == null || sql.isBlank() || sql.length() > MAX_SQL_LENGTH) {
			throw new IllegalArgumentException("SQL 参数不合法");
		}
		return sql;
	}

	private String safeMessage(IllegalArgumentException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return "SQL 校验失败";
		}
		return message.length() <= MAX_ERROR_MESSAGE_LENGTH
				? message : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
	}

	private record ListTablesArgs(String keyword) {
	}

	private record DescribeTablesArgs(List<String> tableNames) {
	}

	private record ValidateSqlArgs(String sql) {
	}
}
