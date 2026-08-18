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
import com.selfmodeling.dto.ai.AiSqlTableSummary;
import com.selfmodeling.service.sql.ReadOnlySqlGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AI SQL 工具白名单执行器测试。
 *
 * @author Chill
 */
@ExtendWith(MockitoExtension.class)
class AiSqlToolExecutorTest {

	@Mock
	private AiSqlMetadataTools metadataTools;
	@Mock
	private ReadOnlySqlGuard readOnlySqlGuard;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void rejectsUnknownOrExecutionTools() {
		AiSqlToolExecutor executor =
				new AiSqlToolExecutor(metadataTools, readOnlySqlGuard, objectMapper);

		assertThrows(IllegalArgumentException.class,
				() -> executor.execute("execute_sql", "{}", "master"));
		verifyNoInteractions(metadataTools, readOnlySqlGuard);
	}

	@Test
	void validateToolUsesParserGuardOnly() {
		when(readOnlySqlGuard.validate("SELECT 1")).thenReturn("SELECT 1");

		AiSqlToolExecutor executor =
				new AiSqlToolExecutor(metadataTools, readOnlySqlGuard, objectMapper);
		String result = executor.execute(
				"validate_read_only_sql", "{\"sql\":\"SELECT 1\"}", "master");

		assertTrue(result.contains("\"valid\":true"));
		verify(readOnlySqlGuard).validate("SELECT 1");
		verifyNoInteractions(metadataTools);
	}

	@Test
	void validationFailureReturnsBoundedInvalidResult() throws Exception {
		when(readOnlySqlGuard.validate("DELETE FROM orders"))
				.thenThrow(new IllegalArgumentException("x".repeat(500)));

		AiSqlToolExecutor executor =
				new AiSqlToolExecutor(metadataTools, readOnlySqlGuard, objectMapper);
		String result = executor.execute(
				"validate_read_only_sql", "{\"sql\":\"DELETE FROM orders\"}", "master");
		JsonNode serialized = objectMapper.readTree(result);

		assertFalse(serialized.get("valid").asBoolean());
		assertTrue(serialized.get("message").asText().length() <= 200);
		verify(readOnlySqlGuard).validate("DELETE FROM orders");
		verifyNoInteractions(metadataTools);
	}

	@Test
	void rejectsToolJsonBeyondUtf8ByteBudgetWithFixedError() {
		when(metadataTools.listTables("master", null)).thenReturn(
				List.of(new AiSqlTableSummary(
						"orders", "app", "TABLE", "中".repeat(100_000))));
		AiSqlToolExecutor executor =
				new AiSqlToolExecutor(metadataTools, readOnlySqlGuard, objectMapper);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> executor.execute("list_tables", "{}", "master"));

		assertEquals("AI 工具结果过大", error.getMessage());
	}
}
