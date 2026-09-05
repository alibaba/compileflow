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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class DurableRuntimeMetricsBinderTest {
    @Test
    void bindsOnlyBoundedOperationalDimensions() {
        DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();
        metrics.record(Operation.EFFECT_DISPATCH, Outcome.UNKNOWN);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new DurableRuntimeMetricsBinder(metrics, new InMemoryDurableProcessRuntimeCache()).bindTo(registry);

        assertThat(registry
            .find("compileflow.durable.operations")
            .tags("operation", "effect_dispatch", "outcome", "unknown")
            .functionCounter()
            .count())
            .isOne();
        assertThat(registry.find("compileflow.durable.loaded.runtimes").gauge().value()).isZero();
        assertThat(registry.getMeters())
            .allSatisfy(meter -> assertThat(meter.getId().getTags())
                .allSatisfy(tag -> assertThat(tag.getKey()).isIn("operation", "outcome")));
    }
}
