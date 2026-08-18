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

import com.selfmodeling.dto.ai.AiSqlStreamEvent;
import com.selfmodeling.request.AiSqlChatRequest;
import reactor.core.publisher.Flux;

/**
 * AI SQL 有界流式编排服务。
 *
 * @author Chill
 */
public interface AiSqlService {

	Flux<AiSqlStreamEvent> stream(String userId, AiSqlChatRequest request);
}
