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

import com.selfmodeling.config.AiSqlProperties;
import com.selfmodeling.dto.ai.qwen.QwenMessage;
import com.selfmodeling.dto.ai.qwen.QwenStreamChunk;
import com.selfmodeling.dto.ai.qwen.QwenToolDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 基于 OpenAI 兼容协议调用通义千问的流式客户端。
 *
 * @author Chill
 */
@Service
public class OpenAiCompatibleQwenClient implements QwenClient {

	private final WebClient webClient;
	private final AiSqlProperties properties;
	private final QwenSseDecoder decoder;

	public OpenAiCompatibleQwenClient(@Qualifier("qwenWebClient") WebClient webClient,
			AiSqlProperties properties, QwenSseDecoder decoder) {
		this.webClient = webClient;
		this.properties = properties;
		this.decoder = decoder;
	}

	@Override
	public Flux<QwenStreamChunk> stream(List<QwenMessage> messages,
			List<QwenToolDefinition> tools, boolean allowTools) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.getModel());
		body.put("messages", messages == null ? List.of() : messages);
		body.put("temperature", properties.getTemperature());
		body.put("stream", true);
		if (allowTools) {
			body.put("tools", tools == null ? List.of() : tools);
			body.put("tool_choice", "auto");
		} else {
			body.put("tool_choice", "none");
		}

		return webClient.post()
				.uri("/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(body)
				.retrieve()
				.onStatus(status -> status.value() == 401,
						response -> releaseWith(response, new QwenAuthenticationException()))
				.onStatus(status -> status.value() == 429,
						response -> releaseWith(response, new QwenRateLimitException()))
				.onStatus(HttpStatusCode::isError,
						response -> releaseWith(response, new QwenRequestException()))
				.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() { })
				.filter(event -> event.data() != null)
				.map(ServerSentEvent::data)
				.map(decoder::decode)
				.timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
				.onErrorMap(throwable -> !(throwable instanceof QwenClientException),
						this::toInternalException);
	}

	private Mono<? extends Throwable> releaseWith(
			org.springframework.web.reactive.function.client.ClientResponse response,
			QwenClientException exception) {
		return response.releaseBody().then(Mono.error(exception));
	}

	private QwenClientException toInternalException(Throwable throwable) {
		Throwable unwrapped = Exceptions.unwrap(throwable);
		if (unwrapped instanceof TimeoutException
				|| unwrapped instanceof WebClientRequestException) {
			return new QwenTimeoutException();
		}
		return new QwenRequestException();
	}
}
