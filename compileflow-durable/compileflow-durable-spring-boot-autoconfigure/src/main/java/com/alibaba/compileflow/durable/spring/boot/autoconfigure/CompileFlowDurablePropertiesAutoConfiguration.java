/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.durable.spring.boot.autoconfigure;

import com.alibaba.compileflow.durable.spring.boot.autoconfigure.properties.CompileFlowDurableProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Binds and validates Durable configuration independently of runtime activation.
 *
 * @author yusu
 */
@AutoConfiguration
@EnableConfigurationProperties(CompileFlowDurableProperties.class)
public class CompileFlowDurablePropertiesAutoConfiguration {
}
