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
import cn.dev33.satoken.exception.NotLoginException;
import com.selfmodeling.config.AiSqlProperties;
import com.selfmodeling.dto.ai.AiSqlStreamEvent;
import com.selfmodeling.dto.Result;
import com.selfmodeling.request.AiSqlChatRequest;
import com.selfmodeling.service.ai.AiSqlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.BaseSubscriber;

import java.io.IOException;

/**
 * AI SQL 流式对话控制器。
 *
 * @author Chill
 */
@RestController
@RequestMapping("/api/v1/ai/sql")
public class AiSqlController {

	private final AiSqlService aiSqlService;
	private final AiSqlProperties properties;

	public AiSqlController(AiSqlService aiSqlService, AiSqlProperties properties) {
		this.aiSqlService = aiSqlService;
		this.properties = properties;
	}

	/**
	 * 发起 AI SQL 流式对话。
	 *
	 * @param request 已校验的对话请求
	 * @return SSE 事件流
	 */
	@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<?> chat(@Valid @RequestBody AiSqlChatRequest request) {
		if (!properties.isAvailable()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Result.error(503, "AI SQL 助手未配置"));
		}

		SseEmitter emitter = createEmitter(properties.getTimeoutSeconds() * 1000L);
		BaseSubscriber<AiSqlStreamEvent> subscriber = new BaseSubscriber<>() {

			@Override
			protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
				requestUnbounded();
			}

			@Override
			protected void hookOnNext(AiSqlStreamEvent event) {
				if (!send(emitter, event)) {
					cancel();
					completeSafely(emitter);
				}
			}

			@Override
			protected void hookOnError(Throwable error) {
				completeSafely(emitter);
			}

			@Override
			protected void hookOnComplete() {
				completeSafely(emitter);
			}
		};
		emitter.onCompletion(subscriber::dispose);
		emitter.onTimeout(() -> {
			subscriber.dispose();
			completeSafely(emitter);
		});
		emitter.onError(error -> subscriber.dispose());

		String userId = StpUtil.getLoginIdAsString();
		aiSqlService.stream(userId, request).subscribe(subscriber);
		return ResponseEntity.ok(emitter);
	}

	/**
	 * 创建 SSE 发射器，便于验证发送失败时的取消行为。
	 *
	 * @param timeoutMillis 超时时间（毫秒）
	 * @return SSE 发射器
	 */
	protected SseEmitter createEmitter(long timeoutMillis) {
		return new SseEmitter(timeoutMillis);
	}

	/**
	 * 将 SSE 请求的未登录异常固定为 JSON 401 响应。
	 *
	 * @param error 未登录异常
	 * @return 未登录响应
	 */
	@ExceptionHandler(NotLoginException.class)
	public ResponseEntity<Result<Void>> handleNotLoginException(NotLoginException error) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Result.error(401, "未登录，请先登录"));
	}

	/**
	 * 将 SSE 请求的参数校验异常固定为 JSON 400 响应。
	 *
	 * @param error 参数校验异常
	 * @return 参数校验失败响应
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Result<Void>> handleValidationException(
			MethodArgumentNotValidException error) {
		String message = error.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Result.error(400, message));
	}

	private boolean send(SseEmitter emitter, AiSqlStreamEvent event) {
		try {
			emitter.send(SseEmitter.event().name(event.type()).data(event.data()));
			return true;
		} catch (IOException error) {
			return false;
		}
	}

	private void completeSafely(SseEmitter emitter) {
		try {
			emitter.complete();
		} catch (RuntimeException ignored) {
			// 客户端断开或请求已完成时无需额外处理。
		}
	}
}
