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
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.rejectingProcessCallInvoker;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EngineExecutionContextTimeTest {
    private ProcessEngineExecutors executors;

    @AfterEach
    void tearDown() {
        if (executors != null) {
            executors.close();
        }
    }

    @Test
    void derivesCompletionFromMonotonicElapsedTime() {
        Instant startedAt = Instant.parse("2026-07-27T10:15:30Z");

        Instant completedAt = EngineExecutionContext.completionTime(startedAt, 1_000_000L, 1_250_000L);

        assertThat(completedAt).isEqualTo(startedAt.plusNanos(250_000L));
    }

    @Test
    void processExecutionIgnoresBackwardWallClockAdjustments() {
        Instant futureStartedAt = Instant.now().plusSeconds(60L);
        executors = ProcessEngineExecutors.create("execution-time-test",
                ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(1).actionTimeoutMaxConcurrency(1).build());
        EngineExecutionContext context = EngineExecutionContext
            .builder()
            .traceId("trace-1")
            .invocationId("invocation-1")
            .namespace("default")
            .processCode("time-test")
            .modelType(ProcessModelType.TBBPM)
            .processCallInvoker(rejectingProcessCallInvoker())
            .executors(executors)
            .componentResolver(ProcessComponentResolver.disabled())
            .scriptExecutors(ScriptExecutorRegistry.from(List.of()))
            .executionStartedAt(futureStartedAt, System.nanoTime())
            .build();

        ProcessExecution execution = context.processExecution();

        assertThat(execution.getStartedAt()).isEqualTo(futureStartedAt);
        assertThat(execution.getCompletedAt()).isAfterOrEqualTo(futureStartedAt);
    }
}
