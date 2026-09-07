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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.Test;

class CompileFlowContextPropagationAutoConfigurationTest {
    private static final String ACCESSOR_KEY = "compileflow-test-context";
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    @Test
    void missingCallerContextDoesNotExposeTheWorkersPreviousContext() {
        ContextRegistry registry = ContextRegistry.getInstance();
        registry.registerThreadLocalAccessor(ACCESSOR_KEY, CONTEXT);
        try {
            ProcessContextPropagator propagator =
                    new CompileFlowContextPropagationAutoConfiguration().processContextPropagator();
            CONTEXT.remove();
            ProcessContextPropagator.Snapshot snapshot = propagator.capture();
            CONTEXT.set("previous-worker-context");

            try (ProcessContextPropagator.Scope scope = snapshot.open()) {
                assertThat(scope).isNotNull();
                assertThat(CONTEXT.get()).isNull();
            }

            assertThat(CONTEXT.get()).isEqualTo("previous-worker-context");
        } finally {
            CONTEXT.remove();
            registry.removeThreadLocalAccessor(ACCESSOR_KEY);
        }
    }

    @Test
    void micrometerAdapterCapturesBindsAndRestoresRegisteredThreadLocals() {
        ContextRegistry registry = ContextRegistry.getInstance();
        registry.registerThreadLocalAccessor(ACCESSOR_KEY, CONTEXT);
        try {
            ProcessContextPropagator propagator =
                    new CompileFlowContextPropagationAutoConfiguration().processContextPropagator();
            CONTEXT.set("caller");
            ProcessContextPropagator.Snapshot snapshot = propagator.capture();
            CONTEXT.set("worker");

            try (ProcessContextPropagator.Scope scope = snapshot.open()) {
                assertThat(scope).isNotNull();
                assertThat(CONTEXT.get()).isEqualTo("caller");
            }

            assertThat(CONTEXT.get()).isEqualTo("worker");
        } finally {
            CONTEXT.remove();
            registry.removeThreadLocalAccessor(ACCESSOR_KEY);
        }
    }
}
