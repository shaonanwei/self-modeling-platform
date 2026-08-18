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
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * AI SQL 对话请求。
 *
 * @author Chill
 */
public record AiSqlChatRequest(
		@NotBlank String dataSourceId,
		@Size(max = 20000) String currentSql,
		@NotNull @Size(min = 1, max = 20) @Valid List<AiSqlMessage> messages
) {

	@AssertTrue(message = "消息总长度不能超过 24000 个字符")
	public boolean isTotalMessageLengthValid() {
		return messages == null || messages.stream()
				.map(AiSqlMessage::content)
				.filter(Objects::nonNull)
				.mapToInt(String::length)
				.sum() <= 24000;
	}
}
