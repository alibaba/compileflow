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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.observability;

import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Objects;

/**
 * Binds dependency-free Durable counters to Micrometer when it is available.
 *
 * @author yusu
 */
public final class DurableRuntimeMetricsBinder implements MeterBinder {
    private final DurableRuntimeMetrics metrics;
    private final DurableProcessRuntimeCache runtimeCache;

    public DurableRuntimeMetricsBinder(DurableRuntimeMetrics metrics, DurableProcessRuntimeCache runtimeCache) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.runtimeCache = Objects.requireNonNull(runtimeCache, "runtimeCache");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (Operation operation : Operation.values()) {
            for (Outcome outcome : Outcome.values()) {
                FunctionCounter
                    .builder("compileflow.durable.operations", metrics, source -> source.count(operation, outcome))
                    .description("Durable runtime operations by bounded operation and outcome")
                    .tags("operation", operation.tagValue(), "outcome", outcome.tagValue())
                    .register(registry);
            }
        }
        Gauge
            .builder("compileflow.durable.loaded.runtimes", runtimeCache, cache -> cache.snapshot().size())
            .description("Node-local disposable Durable process runtimes currently loaded")
            .strongReference(true)
            .register(registry);
    }
}
