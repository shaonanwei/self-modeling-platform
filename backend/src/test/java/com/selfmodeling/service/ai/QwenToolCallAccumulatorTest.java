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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通义千问工具调用分片聚合测试。
 *
 * @author Chill
 */
class QwenToolCallAccumulatorTest {

	@Test
	void joinsNameAndArgumentsByToolIndex() {
		QwenToolCallAccumulator accumulator = new QwenToolCallAccumulator();
		accumulator.accept(new QwenToolCallDelta(
				0, "call_1", "describe_", "{\"tableNames\":["));
		accumulator.accept(new QwenToolCallDelta(
				0, null, "tables", "\"orders\"]}"));

		CompletedToolCall call = accumulator.completedCalls().getFirst();
		assertEquals("describe_tables", call.name());
		assertEquals("{\"tableNames\":[\"orders\"]}", call.argumentsJson());
	}

	@Test
	void addAcceptsIncompleteFragmentsUntilTheStreamIsFinalized() {
		QwenToolCallAccumulator accumulator = new QwenToolCallAccumulator();

		assertTrue(accumulator.add(List.of(new QwenToolCallDelta(
				0, null, null, "{\"tableNames\":["))).isEmpty());
		assertTrue(accumulator.add(List.of(new QwenToolCallDelta(
				0, "call_1", "describe_tables", "\"orders\"]}"))).isEmpty());

		CompletedToolCall call = accumulator.complete().getFirst();
		assertEquals("call_1", call.id());
		assertEquals("describe_tables", call.name());
	}

	@Test
	void rejectsConflictingIdsForTheSameToolIndex() {
		QwenToolCallAccumulator accumulator = new QwenToolCallAccumulator();
		accumulator.add(List.of(new QwenToolCallDelta(0, "call_1", null, null)));

		assertThrows(QwenProtocolException.class,
				() -> accumulator.add(List.of(new QwenToolCallDelta(0, "call_2", null, null))));
	}
}
