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

import com.alibaba.compileflow.durable.spi.store.DurableStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Enforces one complete persistence authority for the Durable bounded context.
 */
@AutoConfiguration(afterName = {"com.alibaba.compileflow.durable.spring.boot.autoconfigure.postgres."
        + "CompileFlowDurablePostgresAutoConfiguration",
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure.mysql.CompileFlowDurableMySqlAutoConfiguration"}, before = CompileFlowDurableAutoConfiguration.class)
@ConditionalOnClass(DurableStore.class)
@ConditionalOnProperty(prefix = "compileflow.durable", name = "enabled", havingValue = "true")
public class CompileFlowDurableStoreAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(DurableStore.class)
    public DurableStore missingDurableStore() {
        throw new IllegalStateException(
                "CompileFlow Durable requires exactly one DurableStore; add one PostgreSQL or "
                + "MySQL Durable starter, select an available Provider when both are present, or provide one complete "
                + "custom DurableStore");
    }
}
