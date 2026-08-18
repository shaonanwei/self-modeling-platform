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

import com.selfmodeling.dto.ai.AiSqlCandidate;
import com.selfmodeling.service.sql.ReadOnlySqlGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AI SQL 最终响应安全门禁测试。
 *
 * @author Chill
 */
@ExtendWith(MockitoExtension.class)
class AiSqlResponseInspectorTest {

	@Mock
	private ReadOnlySqlGuard guard;

	@Test
	void emitsOnlyGuardApprovedSqlAsApplicable() {
		when(guard.validate("SELECT id FROM orders"))
				.thenReturn("SELECT id FROM orders");
		when(guard.validate("DELETE FROM orders"))
				.thenThrow(new IllegalArgumentException(
						"Exactly one SELECT statement is required"));

		AiSqlResponseInspector inspector = new AiSqlResponseInspector(guard);
		List<AiSqlCandidate> candidates = inspector.inspect("""
				可使用以下 SQL：
				```sql
				SELECT id FROM orders
				```
				```sql
				DELETE FROM orders
				```
				""");

		assertEquals(2, candidates.size());
		assertTrue(candidates.get(0).valid());
		assertFalse(candidates.get(1).valid());
	}

	@Test
	void ignoresNonSqlCodeFencesAndPlainText() {
		AiSqlResponseInspector inspector = new AiSqlResponseInspector(guard);
		assertTrue(inspector.inspect("```java\nselect();\n```").isEmpty());
		verifyNoInteractions(guard);
	}

	@Test
	void ignoresSqlPrefixedNonSqlFenceLabels() {
		AiSqlResponseInspector inspector = new AiSqlResponseInspector(guard);
		String content = "```sqlSELECT\nSELECT id FROM orders\n```\n"
				+ "```sqlplus\nSELECT id FROM orders\n```";

		assertTrue(inspector.inspect(content).isEmpty());
		verifyNoInteractions(guard);
	}

	@Test
	void realGuardNeverMarksDangerousCandidatesApplicable() {
		AiSqlResponseInspector inspector =
				new AiSqlResponseInspector(new ReadOnlySqlGuard());
		List<AiSqlCandidate> candidates = inspector.inspect("""
				```sql
				SELECT pg_read_file('/etc/passwd')
				```
				```sql
				SELECT id FROM orders FOR UPDATE
				```
				```sql
				WITH changed AS (DELETE FROM orders RETURNING id) SELECT id FROM changed
				```
				""");

		assertEquals(3, candidates.size());
		assertTrue(candidates.stream().noneMatch(AiSqlCandidate::valid));
	}

	@Test
	void realGuardRejectsQualifiedAndClauseHiddenCapabilities() {
		AiSqlResponseInspector inspector =
				new AiSqlResponseInspector(new ReadOnlySqlGuard());
		List<AiSqlCandidate> candidates = inspector.inspect("""
				```sql
				SELECT sys.utl_http.request('https://example.invalid') FROM dual
				```
				```sql
				SELECT category, COUNT(*) FROM orders GROUP BY pg_read_file('/etc/passwd')
				```
				```sql
				SELECT id FROM orders ORDER BY pg_read_file('/etc/passwd')
				```
				""");

		assertEquals(3, candidates.size());
		assertTrue(candidates.stream().noneMatch(AiSqlCandidate::valid));
	}
}
