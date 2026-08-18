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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfmodeling.dto.ai.qwen.QwenStreamChunk;
import com.selfmodeling.dto.ai.qwen.QwenToolCallDelta;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 仅解码 SSE data 值的通义千问兼容协议解码器。
 *
 * @author Chill
 */
@Component
public class QwenSseDecoder {

	private final ObjectMapper objectMapper;

	public QwenSseDecoder(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public QwenStreamChunk decode(String sseData) {
		if ("[DONE]".equals(sseData)) {
			return QwenStreamChunk.doneChunk();
		}
		try {
			JsonNode root = objectMapper.readTree(sseData);
			if (root == null || !root.isObject()) {
				throw protocolError();
			}
			JsonNode choices = requireArray(root, "choices");
			if (choices.isEmpty()) {
				return new QwenStreamChunk("", List.of(), null, false);
			}
			if (choices.size() != 1) {
				throw protocolError();
			}
			JsonNode choice = requireObject(choices.get(0));
			JsonNode delta = requireObject(choice.get("delta"));
			return new QwenStreamChunk(
					optionalText(delta, "content"),
					toolCalls(delta.get("tool_calls")),
					optionalText(choice, "finish_reason"),
					false);
		} catch (JsonProcessingException | IllegalArgumentException exception) {
			throw protocolError();
		}
	}

	private List<QwenToolCallDelta> toolCalls(JsonNode nodes) {
		if (nodes == null || nodes.isNull()) {
			return List.of();
		}
		if (!nodes.isArray()) {
			throw protocolError();
		}
		List<QwenToolCallDelta> deltas = new ArrayList<>();
		for (JsonNode node : nodes) {
			if (!node.isObject()) {
				throw protocolError();
			}
			JsonNode index = node.get("index");
			if (index == null || !index.isIntegralNumber() || !index.canConvertToInt()
					|| index.intValue() < 0) {
				throw protocolError();
			}
			JsonNode function = requireObject(node.get("function"));
			deltas.add(new QwenToolCallDelta(
					index.intValue(),
					optionalText(node, "id"),
					optionalText(function, "name"),
					optionalText(function, "arguments")));
		}
		return List.copyOf(deltas);
	}

	private JsonNode requireArray(JsonNode node, String fieldName) {
		JsonNode value = node.get(fieldName);
		if (value == null || !value.isArray()) {
			throw protocolError();
		}
		return value;
	}

	private JsonNode requireObject(JsonNode node) {
		if (node == null || !node.isObject()) {
			throw protocolError();
		}
		return node;
	}

	private String optionalText(JsonNode node, String fieldName) {
		JsonNode value = node.get(fieldName);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isTextual()) {
			throw protocolError();
		}
		return value.textValue();
	}

	private QwenProtocolException protocolError() {
		return new QwenProtocolException("AI 服务返回了无效的流式协议数据");
	}
}
