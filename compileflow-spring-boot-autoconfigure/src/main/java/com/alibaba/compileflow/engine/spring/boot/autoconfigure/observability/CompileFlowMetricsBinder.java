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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure.observability;

import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.event.ProcessEventDeliveryMetrics;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Binds engine configuration metrics and JVM-process-wide event-delivery metrics to a Micrometer registry.
 *
 * @author yusu
 */
public class CompileFlowMetricsBinder implements MeterBinder {
    private static final String METRIC_PREFIX = "compileflow.engine.";
    private final List<ProcessEngineConfig> configurations;

    public CompileFlowMetricsBinder(Collection<ProcessEngineConfig> configurations) {
        this.configurations = List.copyOf(Objects.requireNonNull(configurations, "configurations"));
        if (this.configurations.isEmpty()) {
            throw new IllegalArgumentException("configurations must not be empty");
        }
    }

    private static void bindConfiguration(MeterRegistry registry, ProcessEngineConfig config) {
        Tags tags = Tags.of("model.type", config.getModelType().name());

        registry.gauge(METRIC_PREFIX + "executor.runtime.load.max.concurrency", tags, config, c -> c
            .getExecutorConfig()
            .getRuntimeLoadMaxConcurrency());

        registry.gauge(METRIC_PREFIX + "executor.runtime.load.max.pending", tags, config, c -> c
            .getExecutorConfig()
            .getRuntimeLoadMaxPending());

        registry.gauge(METRIC_PREFIX + "executor.action.timeout.max.concurrency", tags, config, c -> c
            .getExecutorConfig()
            .getActionTimeoutMaxConcurrency());

        registry.gauge(METRIC_PREFIX + "executor.action.timeout.max.pending", tags, config, c -> c
            .getExecutorConfig()
            .getActionTimeoutMaxPending());

        registry.gauge(METRIC_PREFIX + "executor.event.delivery.max.concurrency", tags, config, c -> c
            .getObservabilityConfig()
            .getEventDeliveryMaxConcurrency());

        registry.gauge(METRIC_PREFIX + "executor.event.delivery.max.pending", tags, config, c -> c
            .getObservabilityConfig()
            .getEventDeliveryMaxPending());
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        configurations.forEach(config -> bindConfiguration(registry, config));
        FunctionCounter
            .builder(METRIC_PREFIX + "events.dropped", ProcessEventDeliveryMetrics.global(),
                    ProcessEventDeliveryMetrics::droppedCount)
            .description("Best-effort asynchronous lifecycle events rejected by the bounded event executor")
            .register(registry);
    }
}
