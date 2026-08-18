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

import com.selfmodeling.dto.ai.qwen.QwenToolCallDelta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按供应商工具索引聚合流式工具调用分片。
 *
 * @author Chill
 */
public class QwenToolCallAccumulator {

	private static final int MAX_TOOL_CALLS = 3;
	private static final int MAX_TOOL_NAME_LENGTH = 64;
	private static final int MAX_ARGUMENTS_LENGTH = 8_000;

	private final Map<Integer, MutableToolCall> calls = new LinkedHashMap<>();

	public void accept(QwenToolCallDelta delta) {
		if (delta == null || delta.index() < 0) {
			throw new QwenProtocolException("AI 服务返回了无效的工具调用");
		}
		MutableToolCall call = calls.get(delta.index());
		if (call == null) {
			if (calls.size() >= MAX_TOOL_CALLS) {
				throw new QwenProtocolException("单轮 AI 工具调用数量超过限制");
			}
			call = new MutableToolCall();
			calls.put(delta.index(), call);
		}
		call.append(delta);
	}

	public List<CompletedToolCall> add(List<QwenToolCallDelta> deltas) {
		if (deltas != null) {
			deltas.forEach(this::accept);
		}
		return List.of();
	}

	public List<CompletedToolCall> completedCalls() {
		return complete();
	}

	/**
	 * 在流式响应结束后校验并输出完整工具调用。
	 *
	 * @return 已完成的工具调用
	 */
	public List<CompletedToolCall> complete() {
		List<CompletedToolCall> completedCalls = new ArrayList<>();
		for (MutableToolCall call : calls.values()) {
			completedCalls.add(call.complete());
		}
		return List.copyOf(completedCalls);
	}

	private static final class MutableToolCall {

		private String id;
		private final StringBuilder name = new StringBuilder();
		private final StringBuilder arguments = new StringBuilder();

		private void append(QwenToolCallDelta delta) {
			if (delta.id() != null && !delta.id().isBlank()) {
				if (id != null && !id.equals(delta.id())) {
					throw new QwenProtocolException("AI 服务返回了冲突的工具调用标识");
				}
				id = delta.id();
			}
			append(name, delta.name(), MAX_TOOL_NAME_LENGTH, "工具名称长度超过限制");
			append(arguments, delta.arguments(), MAX_ARGUMENTS_LENGTH, "工具参数长度超过限制");
		}

		private void append(StringBuilder target, String fragment, int maxLength, String message) {
			if (fragment != null) {
				target.append(fragment);
				if (target.length() > maxLength) {
					throw new QwenProtocolException(message);
				}
			}
		}

		private CompletedToolCall complete() {
			String completedName = name.toString();
			if (id == null || id.isBlank() || completedName.isBlank()) {
				throw new QwenProtocolException("AI 服务返回了不完整的工具调用");
			}
			return new CompletedToolCall(id, completedName, arguments.toString());
		}
	}
}
