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
import com.selfmodeling.config.AiSqlProperties;
import com.selfmodeling.dto.ai.qwen.QwenMessage;
import com.selfmodeling.dto.ai.qwen.QwenStreamChunk;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 兼容通义千问客户端的本地 HTTP 集成测试。
 *
 * @author Chill
 */
class OpenAiCompatibleQwenClientTest {

	private HttpServer server;
	private final AtomicReference<String> capturedBody = new AtomicReference<>();

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/chat/completions", exchange -> {
			capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] response = (": heartbeat\n\n"
					+ "data: {\"choices\":[{\"delta\":{\"content\":\"SELECT \"},\"finish_reason\":null}]}\n\n"
					+ "data: {\"choices\":[{\"delta\":{\"content\":\"1\"},\"finish_reason\":\"stop\"}]}\n\n"
					+ "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/event-stream");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	void postsOpenAiCompatibleRequestAndStreamsChunks() {
		AiSqlProperties properties = new AiSqlProperties();
		properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
		OpenAiCompatibleQwenClient client = new OpenAiCompatibleQwenClient(
				WebClient.builder()
						.baseUrl(properties.getBaseUrl())
						.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer sk-test")
						.build(),
				properties,
				new QwenSseDecoder(new ObjectMapper()));

		List<QwenStreamChunk> chunks = client.stream(
				List.of(QwenMessage.user("生成 SELECT 1")), List.of(), false)
				.collectList()
				.block(Duration.ofSeconds(3));

		assertEquals("SELECT 1", chunks.stream()
				.map(QwenStreamChunk::content)
				.collect(Collectors.joining()));
		assertTrue(capturedBody.get().contains("\"model\":\"qwen-plus\""));
		assertTrue(capturedBody.get().contains("\"stream\":true"));
		assertFalse(capturedBody.get().contains("sk-test"));
	}

	@Test
	void mapsConnectivityFailureToTimeoutException() throws IOException {
		int unavailablePort;
		try (ServerSocket socket = new ServerSocket(
				0, 1, InetAddress.getLoopbackAddress())) {
			unavailablePort = socket.getLocalPort();
		}
		AiSqlProperties properties = new AiSqlProperties();
		properties.setBaseUrl("http://127.0.0.1:" + unavailablePort);
		OpenAiCompatibleQwenClient client = new OpenAiCompatibleQwenClient(
				WebClient.builder().baseUrl(properties.getBaseUrl()).build(),
				properties,
				new QwenSseDecoder(new ObjectMapper()));

		reactor.test.StepVerifier.create(client.stream(
					List.of(QwenMessage.user("生成 SELECT 1")), List.of(), false))
				.expectError(QwenTimeoutException.class)
				.verify(Duration.ofSeconds(3));
	}
}
