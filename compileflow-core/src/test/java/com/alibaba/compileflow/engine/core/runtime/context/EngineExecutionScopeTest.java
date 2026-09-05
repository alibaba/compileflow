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
package com.alibaba.compileflow.engine.core.runtime.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.rejectingProcessCallInvoker;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.observability.LogContext;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EngineExecutionScopeTest {
    private ProcessEngineExecutors executors;

    @AfterEach
    void clearThreadState() {
        EngineExecutionContextHolder.clear();
        LogContext.clear();
        if (executors != null) {
            executors.close();
        }
    }

    @Test
    void restoresCallerMdcAndOuterEngineContextAfterNestedExecution() {
        EngineExecutionContext outer = context("outer", "outer-trace");
        EngineExecutionContext inner = context("inner", "inner-trace");
        EngineExecutionContextHolder.set(outer);
        LogContext.setContext(Map.of("traceId", "caller-trace", "processCode", "caller-process", "requestId",
                "request-1"));

        try (EngineExecutionScope scope = EngineExecutionScope.open(inner)) {
            assertThat(scope.context()).isSameAs(inner);
            assertThat(EngineExecutionContextHolder.current()).isSameAs(inner);
            assertThat(LogContext.getContext())
                .containsExactlyInAnyOrderEntriesOf(Map.of("traceId", "inner-trace", "processCode", "inner", "requestId",
                        "request-1"));
        }

        assertThat(EngineExecutionContextHolder.current()).isSameAs(outer);
        assertThat(LogContext.getContext())
            .containsExactlyInAnyOrderEntriesOf(Map.of("traceId", "caller-trace", "processCode", "caller-process",
                    "requestId", "request-1"));
    }

    @Test
    void rejectsClosingOnAnotherThreadWithoutDestroyingOwnerState() throws InterruptedException {
        EngineExecutionContext context = context("owner", "trace");
        EngineExecutionScope scope = EngineExecutionScope.open(context);
        Throwable[] failure = new Throwable[1];
        Thread thread = new Thread(() -> {
            try {
                scope.close();
            } catch (Throwable thrown) {
                failure[0] = thrown;
            }
        });

        thread.start();
        thread.join();

        assertThatThrownBy(() -> {
            if (failure[0] != null) {
                throw failure[0];
            }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(EngineExecutionContextHolder.current()).isSameAs(context);
        scope.close();
        assertThat(EngineExecutionContextHolder.current()).isNull();
    }

    @Test
    void removesMissingExecutionMetadataWithoutLeakingCallerValues() {
        EngineExecutionContext context = context("inner", null);
        LogContext.setTraceId("caller-trace");
        LogContext.setProcessCode("caller-process");

        try (EngineExecutionScope scope = EngineExecutionScope.open(context)) {
            assertThat(scope.context()).isSameAs(context);
            assertThat(LogContext.getTraceId()).isNull();
            assertThat(LogContext.getProcessCode()).isEqualTo("inner");
        }

        assertThat(LogContext.getTraceId()).isEqualTo("caller-trace");
        assertThat(LogContext.getProcessCode()).isEqualTo("caller-process");
    }

    @Test
    void rejectsOutOfOrderCloseWithoutCorruptingTheActiveScope() {
        EngineExecutionScope outer = EngineExecutionScope.open(context("outer", "outer-trace"));
        EngineExecutionScope inner = EngineExecutionScope.open(context("inner", "inner-trace"));

        assertThatThrownBy(outer::close).isInstanceOf(IllegalStateException.class).hasMessageContaining("LIFO");
        assertThat(EngineExecutionContextHolder.current()).isSameAs(inner.context());

        inner.close();
        outer.close();
        assertThat(EngineExecutionContextHolder.current()).isNull();
    }

    private EngineExecutionContext context(String processCode, String traceId) {
        if (executors == null) {
            executors = ProcessEngineExecutors.create("execution-scope-test",
                    ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(1).actionTimeoutMaxConcurrency(1).build());
        }
        return EngineExecutionContext
            .builder()
            .namespace("default")
            .processCode(processCode)
            .modelType(ProcessModelType.TBBPM)
            .traceId(traceId)
            .processCallInvoker(rejectingProcessCallInvoker())
            .executors(executors)
            .componentResolver(ProcessComponentResolver.disabled())
            .scriptExecutors(ScriptExecutorRegistry.from(List.of()))
            .build();
    }
}
