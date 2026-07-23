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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI SQL 配置属性测试。
 *
 * @author Chill
 */
class AiSqlPropertiesTest {

	@Test
	void apiIsUnavailableWithoutEnabledFlagAndKey() {
		AiSqlProperties properties = new AiSqlProperties();
		assertFalse(properties.isAvailable());

		properties.setEnabled(true);
		properties.setApiKey("sk-test");
		assertTrue(properties.isAvailable());
	}
}
