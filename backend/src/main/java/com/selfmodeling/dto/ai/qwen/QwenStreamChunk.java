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

package com.selfmodeling.dto.ai.qwen;

import java.util.List;

/**
 * 通义千问流式响应的客户端契约。
 *
 * @author Chill
 */
public record QwenStreamChunk(
		String content,
		List<QwenToolCallDelta> toolCalls,
		String finishReason,
		boolean done
) {

	public QwenStreamChunk {
		content = content == null ? "" : content;
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
	}

	public static QwenStreamChunk doneChunk() {
		return new QwenStreamChunk("", List.of(), null, true);
	}
}
