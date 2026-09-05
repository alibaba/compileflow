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
package com.alibaba.compileflow.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProcessExecutionTest {
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    private static ProcessExecution.Builder execution() {
        return ProcessExecution
            .builder()
            .traceId("trace-1")
            .invocationId("inv-1")
            .namespace("default")
            .processCode("order")
            .startedAt(NOW)
            .completedAt(NOW);
    }

    @Test
    void describesTheExactManagedRevisionThatRan() {
        ProcessRef.Version version = ProcessRef.version("default", "order", "v17");

        ProcessExecution published = execution().processVersion(version).build();

        assertThat(published.getProcessCode()).isEqualTo("order");
        assertThat(published.getNamespace()).isEqualTo("default");
        assertThat(published.getProcessVersion()).isEqualTo(version);
    }

    @Test
    void localExecutionHasNoPublishedVersion() {
        assertThat(execution().build().getProcessVersion()).isNull();
    }

    @Test
    void rejectsAVersionFromAnotherProcessCode() {
        assertThatThrownBy(() -> execution().processVersion(ProcessRef.version("trade", "payment", "v1")).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("executed process");
    }

    @Test
    void rejectsAVersionFromAnotherNamespace() {
        assertThatThrownBy(() -> execution().processVersion(ProcessRef.version("trade", "order", "v1")).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("executed process");
    }

    @Test
    void rejectsCompletionBeforeStart() {
        assertThatThrownBy(() -> execution().completedAt(NOW.minusSeconds(1)).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("completedAt");
    }
}
