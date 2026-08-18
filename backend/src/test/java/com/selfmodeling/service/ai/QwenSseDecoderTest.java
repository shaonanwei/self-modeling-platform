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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfmodeling.dto.ai.qwen.QwenStreamChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通义千问 SSE 数据解码测试。
 *
 * @author Chill
 */
class QwenSseDecoderTest {

	private final QwenSseDecoder decoder = new QwenSseDecoder(new ObjectMapper());

	@Test
	void decodesContentAndFinishReason() {
		QwenStreamChunk chunk = decoder.decode("""
				{"choices":[{"delta":{"content":"SELECT"},"finish_reason":null}]}
				""");

		assertEquals("SELECT", chunk.content());
		assertFalse(chunk.done());
	}

	@Test
	void decodesDoneMarker() {
		QwenStreamChunk chunk = decoder.decode("[DONE]");
		assertTrue(chunk.done());
		assertNull(chunk.finishReason());
	}

	@Test
	void decodesFragmentedToolArguments() {
		QwenStreamChunk chunk = decoder.decode("""
				{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1",
				"function":{"name":"describe_tables","arguments":"{\\\"tableNames\\\":["}}]}}]}
				""");

		assertEquals("describe_tables", chunk.toolCalls().getFirst().name());
	}

	@Test
	void acceptsEmptyChoicesAsAnIgnorableChunk() {
		QwenStreamChunk chunk = decoder.decode("{\"choices\":[]}");

		assertEquals("", chunk.content());
		assertTrue(chunk.toolCalls().isEmpty());
	}

	@Test
	void rejectsMultipleChoicesAndInvalidToolCallShapes() {
		assertThrows(QwenProtocolException.class, () -> decoder.decode("""
				{"choices":[{"delta":{}},{"delta":{}}]}
				"""));
		assertThrows(QwenProtocolException.class, () -> decoder.decode("""
				{"choices":[{"delta":{"tool_calls":[{"function":{}}]}}]}
				"""));
		assertThrows(QwenProtocolException.class, () -> decoder.decode("""
				{"choices":[{"delta":{"tool_calls":[{"index":"0","function":{}}]}}]}
				"""));
		assertThrows(QwenProtocolException.class, () -> decoder.decode("""
				{"choices":[{"delta":{"content":1}}]}
				"""));
	}
}
