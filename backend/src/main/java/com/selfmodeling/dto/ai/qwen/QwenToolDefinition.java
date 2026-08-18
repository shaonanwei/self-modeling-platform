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

import java.util.Map;

/**
 * OpenAI 兼容协议中的函数工具定义。
 *
 * @author Chill
 */
public record QwenToolDefinition(String type, Function function) {

	public static QwenToolDefinition function(
			String name, String description, Map<String, Object> parameters) {
		return new QwenToolDefinition("function", new Function(name, description, parameters));
	}

	/**
	 * 函数工具的名称、描述及参数模式。
	 *
	 * @author Chill
	 */
	public record Function(String name, String description, Map<String, Object> parameters) {
	}
}
