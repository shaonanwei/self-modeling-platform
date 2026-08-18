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

package com.selfmodeling.service.ai.impl;

import com.selfmodeling.config.AiSqlProperties;
import com.selfmodeling.dto.ai.AiSqlCandidate;
import com.selfmodeling.dto.ai.AiSqlMessage;
import com.selfmodeling.dto.ai.AiSqlStreamEvent;
import com.selfmodeling.dto.ai.qwen.QwenAssistantToolCall;
import com.selfmodeling.dto.ai.qwen.QwenMessage;
import com.selfmodeling.dto.ai.qwen.QwenStreamChunk;
import com.selfmodeling.request.AiSqlChatRequest;
import com.selfmodeling.service.ai.AiSqlPrompt;
import com.selfmodeling.service.ai.AiSqlResponseInspector;
import com.selfmodeling.service.ai.AiSqlService;
import com.selfmodeling.service.ai.AiSqlToolExecutor;
import com.selfmodeling.service.ai.CompletedToolCall;
import com.selfmodeling.service.ai.QwenAuthenticationException;
import com.selfmodeling.service.ai.QwenClient;
import com.selfmodeling.service.ai.QwenProtocolException;
import com.selfmodeling.service.ai.QwenRateLimitException;
import com.selfmodeling.service.ai.QwenTimeoutException;
import com.selfmodeling.service.ai.QwenToolCallAccumulator;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于通义千问工具调用的有界 AI SQL 编排实现。
 *
 * @author Chill
 */
@Service
public class AiSqlServiceImpl implements AiSqlService {

	private final QwenClient qwenClient;
	private final AiSqlToolExecutor toolExecutor;
	private final AiSqlResponseInspector inspector;
	private final AiSqlProperties properties;
	private final Map<String, Boolean> activeUsers = new ConcurrentHashMap<>();

	public AiSqlServiceImpl(QwenClient qwenClient, AiSqlToolExecutor toolExecutor,
			AiSqlResponseInspector inspector, AiSqlProperties properties) {
		this.qwenClient = qwenClient;
		this.toolExecutor = toolExecutor;
		this.inspector = inspector;
		this.properties = properties;
	}

	@Override
	public Flux<AiSqlStreamEvent> stream(String userId, AiSqlChatRequest request) {
		return Flux.defer(() -> {
			if (userId == null || userId.isBlank()) {
				return Flux.just(failedEvent());
			}
			if (activeUsers.putIfAbsent(userId, Boolean.TRUE) != null) {
				return Flux.just(AiSqlStreamEvent.error(
						"AI_SQL_BUSY", "当前已有生成任务，请先停止后再试", true));
			}
			return Flux.defer(() -> executeRound(buildConversation(request), 0))
					.onErrorResume(error -> Flux.just(mapError(error)))
					.transform(this::prependStatusBeforeFirstDelta)
					.doFinally(signal -> activeUsers.remove(userId, Boolean.TRUE));
		});
	}

	private Flux<AiSqlStreamEvent> executeRound(Conversation conversation, int round) {
		if (round >= properties.getMaxToolRounds()) {
			return Flux.just(AiSqlStreamEvent.error(
					"TOOL_ROUND_LIMIT", "元数据查询轮次过多，请缩小问题范围", false));
		}
		return Flux.defer(() -> {
			RoundState state = new RoundState();
			Flux<AiSqlStreamEvent> currentRound = qwenClient.stream(
						conversation.messages(), AiSqlPrompt.tools(), true)
					.takeUntil(QwenStreamChunk::done)
					.concatMap(chunk -> onChunk(chunk, state));
			return currentRound
					.concatWith(Flux.defer(() -> continueOrComplete(
							conversation, round, state)))
					.onErrorResume(error -> Flux.just(mapError(error)));
		});
	}

	private Flux<AiSqlStreamEvent> onChunk(QwenStreamChunk chunk, RoundState state) {
		state.accept(chunk);
		if (chunk.content().isEmpty()) {
			return Flux.empty();
		}
		return Flux.just(AiSqlStreamEvent.delta(chunk.content()));
	}

	private Flux<AiSqlStreamEvent> continueOrComplete(
			Conversation conversation, int round, RoundState state) {
		List<CompletedToolCall> calls = state.completeCalls();
		if ("stop".equals(state.finishReason())) {
			if (!calls.isEmpty()) {
				throw protocolError();
			}
			return finalEvents(state.content());
		}
		if (!"tool_calls".equals(state.finishReason()) || calls.isEmpty()) {
			throw protocolError();
		}
		List<ToolResult> results = new ArrayList<>();
		return Flux.fromIterable(calls)
				.concatMap(call -> Flux.concat(
						Flux.just(AiSqlStreamEvent.status(toolStatus(call.name()))),
						executeToolBounded(conversation, call)
								.doOnNext(results::add)
								.thenMany(Flux.empty())))
				.concatWith(Flux.defer(() -> executeRound(
						conversation.withToolResults(state.content(), calls, results), round + 1)));
	}

	private String toolStatus(String toolName) {
		return switch (toolName) {
			case "list_tables" -> "正在查找相关表";
			case "describe_tables" -> "正在读取表结构";
			case "validate_read_only_sql" -> "正在校验 SQL";
			default -> throw new IllegalArgumentException("不允许的 AI 工具");
		};
	}

	private Duration toolTimeout() {
		return Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
	}

	private Mono<ToolResult> executeToolBounded(
			Conversation conversation, CompletedToolCall call) {
		AtomicBoolean cancelled = new AtomicBoolean();
		return Mono.fromCallable(() -> {
			try {
				return executeTool(conversation, call);
			} catch (RuntimeException exception) {
				if (cancelled.get()) {
					return new ToolResult(call.id(), "");
				}
				throw exception;
			}
		})
				.subscribeOn(Schedulers.boundedElastic())
				.doOnCancel(() -> cancelled.set(true))
				.timeout(toolTimeout());
	}

	private ToolResult executeTool(Conversation conversation, CompletedToolCall call) {
		String result = toolExecutor.execute(
				call.name(), call.argumentsJson(), conversation.dataSourceId());
		return new ToolResult(call.id(), result);
	}

	private Flux<AiSqlStreamEvent> finalEvents(String assistantContent) {
		return Flux.fromIterable(inspector.inspect(assistantContent))
				.map(this::sqlEvent)
				.concatWithValues(new AiSqlStreamEvent("done", Map.of()));
	}

	private AiSqlStreamEvent sqlEvent(AiSqlCandidate candidate) {
		return new AiSqlStreamEvent("sql", Map.of(
				"sql", candidate.sql(),
				"valid", candidate.valid(),
				"message", candidate.message()));
	}

	private Flux<AiSqlStreamEvent> prependStatusBeforeFirstDelta(
			Flux<AiSqlStreamEvent> events) {
		return events.switchOnFirst((signal, eventFlux) -> {
			AiSqlStreamEvent first = signal.get();
			if (signal.hasValue() && first != null && "delta".equals(first.type())) {
				return eventFlux.startWith(AiSqlStreamEvent.status("正在生成只读 SQL"));
			}
			return eventFlux;
		});
	}

	private Conversation buildConversation(AiSqlChatRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("AI SQL 请求不能为空");
		}
		List<QwenMessage> messages = new ArrayList<>();
		messages.add(QwenMessage.system(AiSqlPrompt.SYSTEM));
		messages.add(QwenMessage.user(
				AiSqlPrompt.context(request.dataSourceId(), request.currentSql())));
		if (request.messages() != null) {
			for (AiSqlMessage message : request.messages()) {
				if (message == null || message.content() == null || message.content().isBlank()) {
					continue;
				}
				if ("user".equals(message.role())) {
					messages.add(QwenMessage.user(message.content()));
				} else if ("assistant".equals(message.role())) {
					messages.add(QwenMessage.assistant(message.content()));
				}
			}
		}
		return new Conversation(request.dataSourceId(), List.copyOf(messages));
	}

	private AiSqlStreamEvent mapError(Throwable error) {
		Throwable unwrapped = Exceptions.unwrap(error);
		if (hasCause(unwrapped, QwenAuthenticationException.class)) {
			return AiSqlStreamEvent.error(
					"QWEN_AUTH_FAILED", "AI 服务认证失败，请联系管理员", false);
		}
		if (hasCause(unwrapped, QwenRateLimitException.class)) {
			return AiSqlStreamEvent.error(
					"QWEN_RATE_LIMIT", "AI 服务繁忙，请稍后重试", true);
		}
		if (hasCause(unwrapped, QwenTimeoutException.class) || isConnectivityError(unwrapped)) {
			return AiSqlStreamEvent.error(
					"QWEN_TIMEOUT", "AI 服务连接或响应超时，请稍后重试", true);
		}
		if (hasCause(unwrapped, QwenProtocolException.class)) {
			return AiSqlStreamEvent.error(
					"QWEN_PROTOCOL_ERROR", "AI 服务响应格式异常，请稍后重试", false);
		}
		return failedEvent();
	}

	private boolean isConnectivityError(Throwable error) {
		return hasCause(error, WebClientRequestException.class)
				|| hasCause(error, TimeoutException.class)
				|| hasCause(error, ConnectException.class)
				|| hasCause(error, UnknownHostException.class)
				|| hasCause(error, NoRouteToHostException.class)
				|| hasCause(error, SocketTimeoutException.class);
	}

	private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
		Throwable current = error;
		while (current != null) {
			if (type.isInstance(current)) {
				return true;
			}
			if (current == current.getCause()) {
				break;
			}
			current = current.getCause();
		}
		return false;
	}

	private AiSqlStreamEvent failedEvent() {
		return AiSqlStreamEvent.error(
				"AI_SQL_FAILED", "SQL 生成失败，请稍后重试", false);
	}

	private QwenProtocolException protocolError() {
		return new QwenProtocolException("AI 服务返回了不一致的终止状态");
	}

	private record Conversation(String dataSourceId, List<QwenMessage> messages) {

		private Conversation withToolResults(String assistantContent,
				List<CompletedToolCall> calls, List<ToolResult> results) {
			List<QwenAssistantToolCall> assistantCalls = calls.stream()
					.map(call -> QwenAssistantToolCall.function(
							call.id(), call.name(), call.argumentsJson()))
					.toList();
			List<QwenMessage> nextMessages = new ArrayList<>(messages);
			nextMessages.add(new QwenMessage(
					"assistant",
					assistantContent.isBlank() ? null : assistantContent,
					null,
					assistantCalls));
			for (ToolResult result : results) {
				nextMessages.add(QwenMessage.tool(result.callId(), result.content()));
			}
			return new Conversation(dataSourceId, List.copyOf(nextMessages));
		}
	}

	private record ToolResult(String callId, String content) {
	}

	private final class RoundState {

		private final QwenToolCallAccumulator toolCalls = new QwenToolCallAccumulator();
		private final StringBuilder content = new StringBuilder();
		private String finishReason;
		private boolean terminalSeen;

		private void accept(QwenStreamChunk chunk) {
			toolCalls.add(chunk.toolCalls());
			content.append(chunk.content());
			if (chunk.finishReason() != null) {
				if (chunk.finishReason().isBlank()
						|| finishReason != null && !finishReason.equals(chunk.finishReason())) {
					throw protocolError();
				}
				finishReason = chunk.finishReason();
			}
			if (chunk.done()) {
				terminalSeen = true;
			}
		}

		private List<CompletedToolCall> completeCalls() {
			if (!terminalSeen || finishReason == null) {
				throw protocolError();
			}
			return toolCalls.complete();
		}

		private String content() {
			return content.toString();
		}

		private String finishReason() {
			return finishReason;
		}
	}
}
