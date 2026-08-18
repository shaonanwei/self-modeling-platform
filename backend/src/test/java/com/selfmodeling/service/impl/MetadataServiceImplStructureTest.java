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

package com.selfmodeling.service.impl;

import com.selfmodeling.dto.ColumnMetaDTO;
import com.selfmodeling.dto.TableMetaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 元数据结构读取测试。
 *
 * @author Chill
 */
@ExtendWith(MockitoExtension.class)
class MetadataServiceImplStructureTest {

	@Mock
	private JdbcTemplate jdbcTemplate;
	@Mock
	private DataSource dataSource;
	@Mock
	private Connection connection;
	@Mock
	private DatabaseMetaData databaseMetaData;
	@Mock
	private ResultSet tableResultSet;
	@Mock
	private ResultSet primaryKeyResultSet;
	@Mock
	private ResultSet indexResultSet;
	@Mock
	private ResultSet wildcardTableResultSet;
	@Mock
	private ResultSet columnResultSet;
	@Spy
	private MetadataServiceImpl metadataService = new MetadataServiceImpl();

	@BeforeEach
	void setUp() throws SQLException {
		doReturn(jdbcTemplate).when(metadataService).getJdbcTemplateByDataSourceId("master");
		when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getConnection()).thenReturn(connection);
		when(connection.getCatalog()).thenReturn("app");
		when(databaseMetaData.getSearchStringEscape()).thenReturn("\\");
	}

	@Test
	void getTableStructureReadsOnlyMetadataWithoutRowCount() throws SQLException {
		ColumnMetaDTO column = new ColumnMetaDTO();
		column.setColumnName("id");
		column.setColumnType("BIGINT");
		column.setPrimaryKey(true);
		doReturn(List.of(column)).when(metadataService).getTableColumns("master", "orders");
		when(databaseMetaData.getTables("app", null, "orders", null))
				.thenReturn(tableResultSet);
		when(tableResultSet.next()).thenReturn(true, false);
		when(tableResultSet.getString("TABLE_NAME")).thenReturn("orders");
		when(tableResultSet.getString("REMARKS")).thenReturn("订单表");
		when(tableResultSet.getString("TABLE_TYPE")).thenReturn("TABLE");
		when(databaseMetaData.getPrimaryKeys("app", null, "orders"))
				.thenReturn(primaryKeyResultSet);
		when(primaryKeyResultSet.next()).thenReturn(true, false);
		when(primaryKeyResultSet.getString("COLUMN_NAME")).thenReturn("id");
		when(databaseMetaData.getIndexInfo("app", null, "orders", false, true))
				.thenReturn(indexResultSet);
		when(indexResultSet.next()).thenReturn(true, false);
		when(indexResultSet.getString("INDEX_NAME")).thenReturn("uk_orders_id");
		when(indexResultSet.getString("TYPE")).thenReturn("BTREE");
		when(indexResultSet.getString("NON_UNIQUE")).thenReturn("FALSE");
		when(indexResultSet.getString("COLUMN_NAME")).thenReturn("id");

		TableMetaDTO structure = metadataService.getTableStructure("master", "orders");

		assertEquals("orders", structure.getTableName());
		assertEquals("订单表", structure.getTableComment());
		assertEquals("TABLE", structure.getTableType());
		assertEquals(1, structure.getColumns().size());
		assertEquals("id", structure.getColumns().getFirst().getColumnName());
		assertEquals(List.of("id"), structure.getPrimaryKeys());
		assertEquals(1, structure.getIndexes().size());
		assertEquals("uk_orders_id", structure.getIndexes().getFirst().getIndexName());
		assertNull(structure.getRowCount());
		verify(metadataService, never()).getTableRowCount(anyString(), anyString());
	}

	@Test
	void getTableStructureEscapesPatternsAndRequiresExactReturnedName() throws SQLException {
		doReturn(List.of()).when(metadataService)
				.getTableColumns("master", "orders_archive");
		when(databaseMetaData.getTables(
				"app", null, "orders\\_archive", null)).thenReturn(wildcardTableResultSet);
		when(wildcardTableResultSet.next()).thenReturn(true, false);
		when(wildcardTableResultSet.getString("TABLE_NAME"))
				.thenReturn("orders_archive");
		when(wildcardTableResultSet.getString("REMARKS")).thenReturn("归档订单");
		when(wildcardTableResultSet.getString("TABLE_TYPE")).thenReturn("TABLE");
		when(databaseMetaData.getPrimaryKeys("app", null, "orders_archive"))
				.thenReturn(primaryKeyResultSet);
		when(databaseMetaData.getIndexInfo(
				"app", null, "orders_archive", false, true)).thenReturn(indexResultSet);

		TableMetaDTO structure = metadataService.getTableStructure(
				"master", "orders_archive");

		assertEquals("归档订单", structure.getTableComment());
		verify(databaseMetaData).getTables(
				"app", null, "orders\\_archive", null);
	}

	@Test
	void getTableStructureIgnoresWildcardPatternMatches() throws SQLException {
		when(databaseMetaData.getTables("app", null, "\\%", null))
				.thenReturn(wildcardTableResultSet);
		when(wildcardTableResultSet.next()).thenReturn(true, false);
		when(wildcardTableResultSet.getString("TABLE_NAME")).thenReturn("orders");

		TableMetaDTO structure = metadataService.getTableStructure("master", "%");

		assertNull(structure.getTableComment());
		verify(metadataService, never()).getTableColumns("master", "%");
	}

	@Test
	void getTableColumnsEscapesTablePatternAndChecksEveryReturnedTable() throws SQLException {
		when(databaseMetaData.getColumns(
				"app", null, "orders\\_archive", "%")).thenReturn(columnResultSet);
		when(columnResultSet.next()).thenReturn(true, true, false);
		when(columnResultSet.getString("TABLE_NAME"))
				.thenReturn("orders_other", "orders_archive");
		when(columnResultSet.getString("COLUMN_NAME")).thenReturn("id");
		when(columnResultSet.getString("TYPE_NAME")).thenReturn("BIGINT");
		when(columnResultSet.getString("DATA_TYPE")).thenReturn("-5");
		when(columnResultSet.getString("REMARKS")).thenReturn(null);
		when(columnResultSet.getString("IS_NULLABLE")).thenReturn("NO");
		when(columnResultSet.getString("COLUMN_DEF")).thenReturn(null);
		when(databaseMetaData.getPrimaryKeys("app", null, "orders_archive"))
				.thenReturn(primaryKeyResultSet);
		when(databaseMetaData.getIndexInfo(
				"app", null, "orders_archive", false, true)).thenReturn(indexResultSet);

		List<ColumnMetaDTO> columns = metadataService.getTableColumns(
				"master", "orders_archive");

		assertEquals(1, columns.size());
		assertEquals("id", columns.getFirst().getColumnName());
		verify(databaseMetaData).getColumns(
				"app", null, "orders\\_archive", "%");
	}
}
