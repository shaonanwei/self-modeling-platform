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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 发送到通义千问兼容接口的对话消息。
 *
 * @author Chill
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QwenMessage(
		String role,
		String content,
		@JsonProperty("tool_call_id") String toolCallId,
		@JsonProperty("tool_calls") List<QwenAssistantToolCall> toolCalls
) {

	public QwenMessage {
		toolCalls = toolCalls == null ? null : List.copyOf(toolCalls);
	}

	public static QwenMessage system(String content) {
		return new QwenMessage("system", content, null, null);
	}

	public static QwenMessage user(String content) {
		return new QwenMessage("user", content, null, null);
	}

	public static QwenMessage assistant(String content) {
		return new QwenMessage("assistant", content, null, null);
	}

	public static QwenMessage assistantToolCalls(List<QwenAssistantToolCall> toolCalls) {
		return new QwenMessage("assistant", null, null, toolCalls);
	}

	public static QwenMessage tool(String toolCallId, String content) {
		return new QwenMessage("tool", content, toolCallId, null);
	}
}
