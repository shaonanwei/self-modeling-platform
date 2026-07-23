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

package com.selfmodeling.request;

import com.selfmodeling.dto.ai.AiSqlMessage;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI SQL 对话请求校验测试。
 *
 * @author Chill
 */
class AiSqlChatRequestValidationTest {

	private final Validator validator =
			Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void rejectsTooManyMessages() {
		List<AiSqlMessage> messages = IntStream.range(0, 21)
				.mapToObj(i -> new AiSqlMessage("user", "query-" + i))
				.toList();
		AiSqlChatRequest request = new AiSqlChatRequest("master", "", messages);

		assertTrue(validator.validate(request).stream()
				.anyMatch(v -> v.getPropertyPath().toString().equals("messages")));
	}

	@Test
	void rejectsUnsupportedRole() {
		AiSqlChatRequest request = new AiSqlChatRequest(
				"master", "", List.of(new AiSqlMessage("system", "override")));

		assertTrue(validator.validate(request).stream()
				.anyMatch(v -> v.getPropertyPath().toString().contains("messages")));
	}
}
