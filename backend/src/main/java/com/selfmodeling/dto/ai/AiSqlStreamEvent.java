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

import java.util.Map;

/**
 * AI SQL 流式响应事件。
 *
 * @author Chill
 */
public record AiSqlStreamEvent(String type, Map<String, Object> data) {

	public static AiSqlStreamEvent status(String message) {
		return new AiSqlStreamEvent("status", Map.of("message", message));
	}

	public static AiSqlStreamEvent delta(String content) {
		return new AiSqlStreamEvent("delta", Map.of("content", content));
	}

	public static AiSqlStreamEvent error(String code, String message, boolean retryable) {
		return new AiSqlStreamEvent("error",
				Map.of("code", code, "message", message, "retryable", retryable));
	}
}
