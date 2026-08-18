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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI SQL 最终响应检查器，只输出经过后端安全门禁确认的候选语句。
 *
 * @author Chill
 */
@Service
public class AiSqlResponseInspector {

	private static final Pattern SQL_FENCE =
			Pattern.compile("(?is)```sql[ \\t]*\\r?\\n(.*?)\\s*```");
	private static final int MAX_CANDIDATES = 5;
	private static final int MAX_SQL_LENGTH = 20000;

	private final ReadOnlySqlGuard guard;

	public AiSqlResponseInspector(ReadOnlySqlGuard guard) {
		this.guard = guard;
	}

	public List<AiSqlCandidate> inspect(String assistantContent) {
		if (assistantContent == null || assistantContent.isBlank()) {
			return List.of();
		}
		List<AiSqlCandidate> result = new ArrayList<>();
		Matcher matcher = SQL_FENCE.matcher(assistantContent);
		while (matcher.find() && result.size() < MAX_CANDIDATES) {
			String sql = matcher.group(1).trim();
			if (sql.isEmpty() || sql.length() > MAX_SQL_LENGTH) {
				result.add(new AiSqlCandidate(sql, false, "SQL 长度不合法"));
				continue;
			}
			try {
				guard.validate(sql);
				result.add(new AiSqlCandidate(sql, true, "校验通过"));
			} catch (IllegalArgumentException exception) {
				result.add(new AiSqlCandidate(sql, false, safeMessage(exception)));
			}
		}
		return List.copyOf(result);
	}

	private String safeMessage(IllegalArgumentException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return "SQL 校验失败";
		}
		return switch (message) {
			case "SQL must not be blank" -> "SQL 不能为空";
			case "Exactly one SELECT statement is required" -> "仅允许一条 SELECT 语句";
			case "SQL syntax is invalid" -> "SQL 语法无效";
			case "The SELECT uses a forbidden capability" -> "SELECT 使用了禁止的能力";
			default -> "SQL 校验失败";
		};
	}
}
