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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfmodeling.dto.ColumnMetaDTO;
import com.selfmodeling.dto.TableMetaDTO;
import com.selfmodeling.dto.ai.AiSqlTableDescription;
import com.selfmodeling.dto.ai.AiSqlTableSummary;
import com.selfmodeling.service.MetadataService;
import com.selfmodeling.service.SqlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AI SQL 元数据工具测试。
 *
 * @author Chill
 */
@ExtendWith(MockitoExtension.class)
class AiSqlMetadataToolsTest {

	@Mock
	private MetadataService metadataService;
	@Mock
	private SqlService sqlService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void listTablesReturnsAtMostFiftyWithoutRowCounts() {
		List<TableMetaDTO> tables = IntStream.range(0, 60)
				.mapToObj(i -> {
					TableMetaDTO table = new TableMetaDTO();
					table.setTableName("table_" + i);
					table.setTableComment("comment_" + i);
					table.setRowCount(999L);
					return table;
				})
				.toList();
		when(metadataService.getAllTables("master", "order")).thenReturn(tables);

		AiSqlMetadataTools tools = new AiSqlMetadataTools(metadataService, sqlService);
		List<AiSqlTableSummary> result = tools.listTables("master", "order");

		assertEquals(50, result.size());
		JsonNode serialized = objectMapper.valueToTree(result.getFirst());
		assertFalse(serialized.has("rowCount"));
		assertFalse(serialized.has("createTime"));
		assertFalse(serialized.has("updateTime"));
		verify(metadataService, never()).previewTableData(anyString(), anyString(), anyInt());
		verify(metadataService, never()).getTableRowCount(anyString(), anyString());
		verify(metadataService, never()).getJdbcTemplateByDataSourceId(anyString());
	}

	@Test
	void describeTablesRejectsMoreThanFiveNames() {
		AiSqlMetadataTools tools = new AiSqlMetadataTools(metadataService, sqlService);

		assertThrows(IllegalArgumentException.class,
				() -> tools.describeTables("master", List.of("a", "b", "c", "d", "e", "f")));
		verifyNoInteractions(metadataService, sqlService);
	}

	@Test
	void describeTablesProjectsOnlyAllowlistedMetadata() {
		TableMetaDTO table = new TableMetaDTO();
		table.setTableName("orders");
		table.setTableComment("订单表");
		table.setTableType("TABLE");
		table.setSchemaName("app");
		table.setRowCount(999L);
		table.setCreateTime("2026-01-01");
		table.setUpdateTime("2026-01-02");
		table.setPrimaryKeys(List.of("id"));
		table.setIndexes(List.of(index("uk_orders_id", "id")));
		table.setColumns(List.of(column("id")));
		when(metadataService.getAllTables("master", null)).thenReturn(List.of(table));
		when(metadataService.getTableStructure("master", "orders")).thenReturn(table);
		when(sqlService.getTableRelations("orders", "master")).thenReturn(List.of(Map.of(
				"sourceTable", "orders",
				"sourceField", "customer_id",
				"targetTable", "customers",
				"targetField", "id",
				"relationType", "many_to_one",
				"unexpected", "must_not_leak")));

		AiSqlMetadataTools tools = new AiSqlMetadataTools(metadataService, sqlService);
		AiSqlTableDescription result = tools.describeTables("master", List.of("orders")).getFirst();

		JsonNode serialized = objectMapper.valueToTree(result);
		assertFalse(serialized.has("rowCount"));
		assertFalse(serialized.has("createTime"));
		assertFalse(serialized.has("updateTime"));
		assertFalse(serialized.at("/columns/0/defaultValue").isValueNode());
		assertFalse(serialized.at("/relations/0/unexpected").isValueNode());
		verify(metadataService).getTableStructure("master", "orders");
		verify(sqlService).getTableRelations("orders", "master");
		verify(metadataService, never()).previewTableData(anyString(), anyString(), anyInt());
		verify(metadataService, never()).getTableRowCount(anyString(), anyString());
	}

	@Test
	void describeTablesAuthorizesExactNamesAndKeepsLegalUnderscores() {
		TableMetaDTO legal = new TableMetaDTO();
		legal.setTableName("orders_archive");
		when(metadataService.getAllTables("master", null)).thenReturn(List.of(legal));
		when(metadataService.getTableStructure("master", "orders_archive"))
				.thenReturn(legal);
		when(sqlService.getTableRelations("orders_archive", "master"))
				.thenReturn(List.of());

		AiSqlMetadataTools tools = new AiSqlMetadataTools(metadataService, sqlService);
		assertEquals("orders_archive", tools.describeTables(
				"master", List.of("orders_archive")).getFirst().tableName());
		assertThrows(IllegalArgumentException.class,
				() -> tools.describeTables("master", List.of("%")));
		assertThrows(IllegalArgumentException.class,
				() -> tools.describeTables("master", List.of("orders_")));

		verify(metadataService, never()).getTableStructure("master", "%");
		verify(metadataService, never()).getTableStructure("master", "orders_");
	}

	@Test
	void describeTablesBoundsNestedMetadataAndText() {
		TableMetaDTO table = new TableMetaDTO();
		table.setTableName("orders");
		table.setTableComment("表".repeat(3000));
		table.setPrimaryKeys(IntStream.range(0, 100)
				.mapToObj(i -> "pk_" + i).toList());
		List<ColumnMetaDTO> columns = IntStream.range(0, 500)
				.mapToObj(i -> column("column_" + i)).toList();
		columns.getFirst().setColumnComment("字段".repeat(2000));
		table.setColumns(columns);
		List<TableMetaDTO.IndexInfo> indexes = IntStream.range(0, 300)
				.mapToObj(i -> index("index_" + i, "column_" + i)).toList();
		indexes.getFirst().setIndexName("索引".repeat(200));
		table.setIndexes(indexes);
		when(metadataService.getAllTables("master", null)).thenReturn(List.of(table));
		when(metadataService.getTableStructure("master", "orders")).thenReturn(table);
		when(sqlService.getTableRelations("orders", "master"))
				.thenReturn(IntStream.range(0, 300)
						.mapToObj(i -> Map.<String, Object>of(
								"sourceTable", "orders",
								"sourceField", "source_" + i,
								"targetTable", "target_" + i,
								"targetField", "id",
								"relationType", "many_to_one"))
						.toList());

		AiSqlTableDescription result = new AiSqlMetadataTools(
				metadataService, sqlService).describeTables("master", List.of("orders")).getFirst();

		assertTrue(result.columns().size() <= 200);
		assertTrue(result.indexes().size() <= 100);
		assertTrue(result.relations().size() <= 100);
		assertTrue(result.primaryKeys().size() <= 32);
		assertTrue(result.tableComment().length() <= 1000);
		assertTrue(result.columns().getFirst().columnComment().length() <= 1000);
		assertTrue(result.indexes().getFirst().indexName().length() <= 128);
	}

	private ColumnMetaDTO column(String name) {
		ColumnMetaDTO column = new ColumnMetaDTO();
		column.setColumnName(name);
		column.setColumnType("BIGINT");
		column.setDataType("-5");
		column.setColumnSize(19);
		column.setDecimalDigits(0);
		column.setColumnComment("主键");
		column.setPrimaryKey(true);
		column.setNullable(false);
		column.setAutoIncrement(true);
		column.setDefaultValue("sensitive-default");
		column.setOrdinalPosition(1);
		column.setIndexed(true);
		return column;
	}

	private TableMetaDTO.IndexInfo index(String name, String columnName) {
		TableMetaDTO.IndexInfo index = new TableMetaDTO.IndexInfo();
		index.setIndexName(name);
		index.setIndexType("BTREE");
		index.setUnique(true);
		index.setColumns(List.of(columnName));
		return index;
	}
}
