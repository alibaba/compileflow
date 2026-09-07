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

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.DefaultProcessEngine;
import com.alibaba.compileflow.engine.core.concurrent.ProcessExecutorMetrics;
import com.alibaba.compileflow.engine.core.event.ProcessEventDeliveryMetrics;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Objects;

/**
 * Binds engine configuration and JVM-process-wide executor and event-delivery metrics.
 *
 * @author yusu
 */
public class CompileFlowMetricsBinder implements MeterBinder {
    private static final String METRIC_PREFIX = "compileflow.engine.";
    private final ProcessEngine engine;

    public CompileFlowMetricsBinder(ProcessEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    private static void bindConfiguration(MeterRegistry registry, ProcessEngineConfig config) {
        Tags tags = Tags.empty();

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
        if (engine instanceof DefaultProcessEngine defaultEngine) {
            bindConfiguration(registry, defaultEngine.getConfiguration());
            bindExecutor(registry, "runtime.load", defaultEngine.getRuntimeLoadMetrics());
            bindExecutor(registry, "action.timeout", defaultEngine.getActionTimeoutMetrics());
            bindExecutor(registry, "event.delivery", defaultEngine.getEventDeliveryMetrics());
        }
        FunctionCounter
            .builder(METRIC_PREFIX + "events.dropped", ProcessEventDeliveryMetrics.global(),
                    ProcessEventDeliveryMetrics::droppedCount)
            .description("JVM-wide best-effort asynchronous lifecycle events rejected by event executors")
            .register(registry);
    }

    private static void bindExecutor(MeterRegistry registry, String name, ProcessExecutorMetrics metrics) {
        String prefix = METRIC_PREFIX + "executor." + name;
        registry.gauge(prefix + ".active", metrics, ProcessExecutorMetrics::activeCount);
        registry.gauge(prefix + ".pending", metrics, ProcessExecutorMetrics::pendingCount);
        FunctionCounter
            .builder(prefix + ".rejected", metrics, ProcessExecutorMetrics::rejectedCount)
            .description("Rejected submissions to this engine-owned executor, including saturation and shutdown")
            .register(registry);
    }
}
