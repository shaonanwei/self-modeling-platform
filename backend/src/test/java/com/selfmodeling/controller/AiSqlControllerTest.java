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

package com.selfmodeling.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.selfmodeling.config.AiSqlProperties;
import com.selfmodeling.dto.ai.AiSqlStreamEvent;
import com.selfmodeling.exception.GlobalExceptionHandler;
import com.selfmodeling.request.AiSqlChatRequest;
import com.selfmodeling.service.ai.AiSqlService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI SQL SSE 控制器测试。
 *
 * @author Chill
 */
@WebMvcTest(controllers = AiSqlController.class)
@ContextConfiguration(classes = {
		AiSqlController.class,
		GlobalExceptionHandler.class
})
class AiSqlControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AiSqlService aiSqlService;

	@MockitoBean
	private AiSqlProperties properties;

	@Test
	void unavailableAiConfigurationReturnsServiceUnavailable() throws Exception {
		when(properties.isAvailable()).thenReturn(false);

		mockMvc.perform(authenticatedChat())
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value(503))
				.andExpect(jsonPath("$.message").value("AI SQL 助手未配置"));
		verifyNoInteractions(aiSqlService);
	}

	@Test
	void authenticatedRequestStartsEventStream() throws Exception {
		when(properties.isAvailable()).thenReturn(true);
		when(properties.getTimeoutSeconds()).thenReturn(60);
		when(aiSqlService.stream(eq("1001"), any()))
				.thenReturn(Flux.just(
						AiSqlStreamEvent.status("正在生成"),
						new AiSqlStreamEvent("done", Map.of("finishReason", "stop"))));

		try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
			stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("1001");
			MvcResult result = mockMvc.perform(authenticatedChat())
					.andExpect(request().asyncStarted())
					.andReturn();

			mockMvc.perform(asyncDispatch(result))
					.andExpect(status().isOk())
					.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
					.andExpect(content().string(containsString("event:status")))
					.andExpect(content().string(containsString("event:done")));
		}
		verify(aiSqlService).stream(eq("1001"), any());
	}

	@Test
	void invalidRequestDoesNotInvokeService() throws Exception {
		when(properties.isAvailable()).thenReturn(true);

		mockMvc.perform(post("/api/v1/ai/sql/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"dataSourceId":"master","currentSql":"",
								 "messages":[]}
								"""))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(aiSqlService);
	}

	@Test
	void invalidMessageRoleWithSseAcceptReturnsJsonBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/ai/sql/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.TEXT_EVENT_STREAM)
						.content("""
								{"dataSourceId":"master","currentSql":"",
								 "messages":[{"role":"system","content":"生成 SELECT 1"}]}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.message").value("消息角色仅支持 user 或 assistant"));
		verifyNoInteractions(aiSqlService);
	}

	@Test
	void oversizedCurrentSqlWithSseAcceptReturnsJsonBadRequest() throws Exception {
		String request = """
				{"dataSourceId":"master","currentSql":"%s",
				 "messages":[{"role":"user","content":"生成 SELECT 1"}]}
				""".formatted("x".repeat(20001));

		mockMvc.perform(post("/api/v1/ai/sql/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.TEXT_EVENT_STREAM)
						.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.message").value("size must be between 0 and 20000"));
		verifyNoInteractions(aiSqlService);
	}

	@Test
	void sendFailureCancelsUpstreamSubscription() {
		AiSqlProperties directProperties = mock(AiSqlProperties.class);
		AiSqlService directService = mock(AiSqlService.class);
		AtomicBoolean cancelled = new AtomicBoolean();
		when(directProperties.isAvailable()).thenReturn(true);
		when(directProperties.getTimeoutSeconds()).thenReturn(60);
		when(directService.stream(eq("1001"), any())).thenReturn(
				Flux.just(AiSqlStreamEvent.status("正在生成"))
						.doOnCancel(() -> cancelled.set(true)));

		AiSqlController controller = new FailingAiSqlController(directService, directProperties);
		try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
			stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("1001");

			controller.chat(new AiSqlChatRequest("master", "", List.of()));
		}

		assertTrue(cancelled.get());
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedChat() {
		return post("/api/v1/ai/sql/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.content("""
						{"dataSourceId":"master","currentSql":"",
						 "messages":[{"role":"user","content":"生成 SELECT 1"}]}
						""");
	}

	private static class FailingAiSqlController extends AiSqlController {

		private FailingAiSqlController(AiSqlService aiSqlService, AiSqlProperties properties) {
			super(aiSqlService, properties);
		}

		@Override
		protected SseEmitter createEmitter(long timeoutMillis) {
			return new SseEmitter(timeoutMillis) {

				@Override
				public void send(SseEventBuilder builder) throws IOException {
					throw new IOException("客户端已断开");
				}
			};
		}
	}
}
