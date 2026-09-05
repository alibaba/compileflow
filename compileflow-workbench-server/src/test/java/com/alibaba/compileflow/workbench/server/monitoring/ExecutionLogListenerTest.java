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
package com.alibaba.compileflow.workbench.server.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessError;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExecutionLogListenerTest {
    private static ProcessExecution execution(String invocationId, Instant now) {
        return execution(invocationId, null, 0, now);
    }

    private static ProcessExecution execution(String invocationId, String parentInvocationId, int callDepth,
            Instant now) {
        return ProcessExecution
            .builder()
            .traceId("trace-" + invocationId)
            .invocationId(invocationId)
            .namespace("default")
            .processCode("payment.approve")
            .processVersion(ProcessRef.version("default", "payment.approve", "v2"))
            .startedAt(now.minusMillis(12L))
            .completedAt(now)
            .build();
    }

    private static ProcessEvent.ExecutionAttribution attribution(String parentInvocationId, int callDepth) {
        return new ProcessEvent.ExecutionAttribution(parentInvocationId, callDepth, ProcessModelType.TBBPM,
                "a".repeat(64), ProcessRef.alias("default", "payment.approve", "production"), 7L,
                ProcessAliasTarget.STABLE);
    }

    @Test
    void persistsOneObservationForEachTerminalExecutionEvent() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogListener listener = new ExecutionLogListener(logService);
        Instant now = Instant.parse("2026-07-25T00:00:00Z");

        listener.onEvent(new ProcessEvent.ExecutionStarted("default", "payment.approve", "invocation-1", null, now));
        listener.onEvent(
                new ProcessEvent.ExecutionCompleted(execution("invocation-1", now), attribution(null, 0), 12L, now));
        listener.onEvent(new ProcessEvent.ExecutionStarted("default", "payment.approve", "invocation-2", null, now));
        listener.onEvent(
                new ProcessEvent.ExecutionFailed(execution("invocation-2", "invocation-1", 1, now),
                        attribution("invocation-1", 1), 7L, new ProcessError("CF_EXEC_001", "boom"), now));

        ArgumentCaptor<ExecutionLogRecord> captor = ArgumentCaptor.forClass(ExecutionLogRecord.class);
        verify(logService, org.mockito.Mockito.times(2)).persist(captor.capture());
        assertThat(captor.getAllValues()).extracting(ExecutionLogRecord::status).containsExactly("success", "failed");
        ExecutionLogRecord failure = captor.getAllValues().get(1);
        assertThat(failure.errorCode()).isEqualTo("CF_EXEC_001");
        assertThat(failure.errorMessage()).isEqualTo("boom");
        assertThat(failure.processCode()).isEqualTo("payment.approve");
        assertThat(failure.traceId()).isEqualTo("trace-invocation-2");
        assertThat(failure.parentInvocationId()).isEqualTo("invocation-1");
        assertThat(failure.callDepth()).isOne();
        assertThat(failure.namespace()).isEqualTo("default");
        assertThat(failure.modelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(failure.sourceDigest()).isEqualTo("a".repeat(64));
        assertThat(failure.effectiveVersion()).isEqualTo("v2");
        assertThat(failure.routeAlias()).isEqualTo("production");
        assertThat(failure.routeRevision()).isEqualTo(7L);
    }

    @Test
    void recordsDefinitionExecutionWithoutInventingVersionAttribution() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogListener listener = new ExecutionLogListener(logService);
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        ProcessExecution execution = ProcessExecution
            .builder()
            .traceId("trace-definition")
            .invocationId("definition-invocation")
            .namespace("default")
            .processCode("payment.approve")
            .startedAt(now.minusMillis(5L))
            .completedAt(now)
            .build();
        ProcessEvent.ExecutionAttribution attribution =
                new ProcessEvent.ExecutionAttribution(null, 0, ProcessModelType.TBBPM, "a".repeat(64), null, null, null);

        listener.onEvent(new ProcessEvent.ExecutionCompleted(execution, attribution, 5L, now));

        ArgumentCaptor<ExecutionLogRecord> captor = ArgumentCaptor.forClass(ExecutionLogRecord.class);
        verify(logService).persist(captor.capture());
        ExecutionLogRecord record = captor.getValue();
        assertThat(record.namespace()).isEqualTo("default");
        assertThat(record.requestedVersion()).isNull();
        assertThat(record.effectiveVersion()).isNull();
        assertThat(record.routingSource()).isEqualTo("definition");
    }

    @Test
    void ignoresNonExecutionEvents() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ExecutionLogListener listener = new ExecutionLogListener(logService);
        Instant now = Instant.parse("2026-07-25T00:00:00Z");

        listener.onEvent(
                new ProcessEvent.TriggerStarted("default", "a", "invocation-1", ProcessTrigger.on("t", "e"), null, now));

        verify(logService, never()).persist(org.mockito.ArgumentMatchers.any(ExecutionLogRecord.class));
    }

    @Test
    void supportsOnlyTerminalExecutionEvents() {
        ExecutionLogListener listener = new ExecutionLogListener(mock(ExecutionLogService.class));
        Instant now = Instant.parse("2026-07-25T00:00:00Z");

        assertThat(listener.supports(
                new ProcessEvent.ExecutionCompleted(execution("invocation-1", now), attribution(null, 0), 12L, now)))
            .isTrue();
        assertThat(listener.supports(
                new ProcessEvent.ExecutionFailed(execution("invocation-2", now), attribution(null, 0), 7L,
                        new ProcessError("CF_EXEC_001", "boom"), now)))
            .isTrue();
        assertThat(listener.supports(
                new ProcessEvent.ExecutionStarted("default", "payment.approve", "invocation-3", null, now)))
            .isFalse();
    }
}
