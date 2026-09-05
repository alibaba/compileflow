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
package com.alibaba.compileflow.engine.core.runtime.execution;

import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.rejectingProcessCallInvoker;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionScope;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExecutionContextPropagatorTest {
    private static final ThreadLocal<String> AMBIENT = new ThreadLocal<>();
    private ProcessEngineExecutors engineExecutors;
    private ExecutorService worker;

    @AfterEach
    void tearDown() {
        AMBIENT.remove();
        if (worker != null) {
            worker.shutdownNow();
        }
        if (engineExecutors != null) {
            engineExecutors.close();
        }
    }

    @Test
    void applicationContextIsBoundForTheTaskAndWorkerContextIsRestored() throws Exception {
        worker = Executors.newSingleThreadExecutor();
        worker.submit(() -> AMBIENT.set("worker")).get();
        engineExecutors = ProcessEngineExecutors.create("context-propagation-test",
                ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(1).actionTimeoutMaxConcurrency(1).build());
        EngineExecutionContext context = EngineExecutionContext
            .builder()
            .namespace("default")
            .processCode("context.process")
            .modelType(ProcessModelType.TBBPM)
            .processCallInvoker(rejectingProcessCallInvoker())
            .executors(engineExecutors)
            .componentResolver(ProcessComponentResolver.disabled())
            .scriptExecutors(ScriptExecutorRegistry.builtIns(ProcessEngineConfig.tbbpm()))
            .contextPropagator(threadLocalPropagator())
            .build();

        AMBIENT.set("caller");
        ExecutionContextPropagator.ContextSnapshot snapshot;
        try (EngineExecutionScope scope = EngineExecutionScope.open(context)) {
            assertThat(scope.context()).isSameAs(context);
            snapshot = ExecutionContextPropagator.capture();
        }

        String observed = worker.submit(ExecutionContextPropagator.wrap(AMBIENT::get, snapshot)).get();
        assertThat(observed).isEqualTo("caller");
        assertThat(worker.submit(AMBIENT::get).get()).isEqualTo("worker");
    }

    private static ProcessContextPropagator threadLocalPropagator() {
        return () -> {
            String captured = AMBIENT.get();
            return () -> {
                String previous = AMBIENT.get();
                setAmbient(captured);
                return () -> setAmbient(previous);
            };
        };
    }

    private static void setAmbient(String value) {
        if (value == null) {
            AMBIENT.remove();
        } else {
            AMBIENT.set(value);
        }
    }
}
