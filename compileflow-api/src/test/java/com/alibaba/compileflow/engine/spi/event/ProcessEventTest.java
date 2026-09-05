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
package com.alibaba.compileflow.engine.spi.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProcessEventTest {
    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    @Test
    void rejectsRatherThanNormalizesEventIdentities() {
        assertThatThrownBy(() -> new ProcessEvent.ExecutionStarted(" default ", "flow", "invocation-1", null, NOW))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThatThrownBy(() -> new ProcessEvent.ExecutionAttribution(" parent ", 1, ProcessModelType.TBBPM, null, null,
                null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThatThrownBy(() -> new ProcessEvent.ExecutionAttribution(null, 0, ProcessModelType.TBBPM, "not-a-digest",
                null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SHA-256");
    }

    @Test
    void terminalEventsDeriveTheirSingleTraceIdentityFromExecution() {
        ProcessExecution execution = ProcessExecution
            .builder()
            .traceId("trace-1")
            .invocationId("invocation-1")
            .namespace("default")
            .processCode("flow")
            .startedAt(NOW.minusMillis(1))
            .completedAt(NOW)
            .build();
        ProcessEvent.ExecutionAttribution attribution =
                new ProcessEvent.ExecutionAttribution(null, 0, ProcessModelType.TBBPM, null, null, null, null);

        assertThat(new ProcessEvent.ExecutionCompleted(execution, attribution, 1, NOW).traceId()).isEqualTo("trace-1");
    }

    @Test
    void terminalEventsRejectAttributionForADifferentProcessOrAnUnresolvedAlias() {
        ProcessExecution unversioned = ProcessExecution
            .builder()
            .traceId("trace-1")
            .invocationId("invocation-1")
            .namespace("default")
            .processCode("flow")
            .startedAt(NOW.minusMillis(1))
            .completedAt(NOW)
            .build();
        ProcessEvent.ExecutionAttribution foreign = new ProcessEvent.ExecutionAttribution(null, 0,
                ProcessModelType.TBBPM, null, ProcessRef.alias("default", "other", "stable"), 1L,
                com.alibaba.compileflow.engine.ProcessAliasTarget.STABLE);
        ProcessEvent.ExecutionAttribution selected = new ProcessEvent.ExecutionAttribution(null, 0,
                ProcessModelType.TBBPM, null, ProcessRef.alias("default", "flow", "stable"), 1L,
                com.alibaba.compileflow.engine.ProcessAliasTarget.STABLE);

        assertThatThrownBy(() -> new ProcessEvent.ExecutionCompleted(unversioned, foreign, 1, NOW))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("executed process");
        assertThatThrownBy(() -> new ProcessEvent.ExecutionCompleted(unversioned, selected, 1, NOW))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exact executed process Version");
    }
}
