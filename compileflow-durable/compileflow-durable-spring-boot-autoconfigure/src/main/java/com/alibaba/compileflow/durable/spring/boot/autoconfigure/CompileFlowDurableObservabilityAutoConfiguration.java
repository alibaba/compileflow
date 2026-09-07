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

import com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngine;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime.DurableHealthIndicator;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.observability.DurableRuntimeMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional observers of the engine-owned runtime.
 * @author yusu
 */
@AutoConfiguration(after = CompileFlowDurableAutoConfiguration.class)
public class CompileFlowDurableObservabilityAutoConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(DefaultDurableProcessEngine.class)
    static class MetricsConfiguration {
        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnMissingBean(name = "compileFlowDurableMetricsBinder")
        MeterBinder compileFlowDurableMetricsBinder(DefaultDurableProcessEngine engine) {
            return new DurableRuntimeMetricsBinder(engine.getMetrics(), engine.getRuntimeCache());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnBean(DefaultDurableProcessEngine.class)
    static class HealthConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "compileFlowDurableHealthIndicator")
        DurableHealthIndicator compileFlowDurableHealthIndicator(DefaultDurableProcessEngine engine) {
            return new DurableHealthIndicator(engine.getStore(), engine.getRuntimeCache(), engine::getWorkerCoordinator,
                    engine::getLeaseRenewer, engine.isOutboxSinkConfigured());
        }
    }
}
