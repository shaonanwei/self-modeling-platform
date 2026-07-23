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

package com.selfmodeling.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * AI SQL 助手客户端配置。
 *
 * @author Chill
 */
@Configuration
@EnableConfigurationProperties(AiSqlProperties.class)
public class AiSqlConfig {

	@Bean
	@Qualifier("qwenWebClient")
	public WebClient qwenWebClient(AiSqlProperties properties) {
		HttpClient httpClient = HttpClient.create()
				.responseTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
		return WebClient.builder()
				.baseUrl(properties.getBaseUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.build();
	}
}
