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
package com.alibaba.compileflow.durable.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessRunResultTest {
    private static final ProcessRunId RUN_ID = new ProcessRunId("3ef19fe9-65b7-443f-818f-8a61e1b70cd7");
    private static final String PROCESS_CODE = "orders";
    private static final ProcessRef.Version PROCESS = ProcessRef.version("default", PROCESS_CODE, "1");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-14T00:00:00Z");

    @Test
    void succeededResultDeeplyOwnsAndRedactsOutput() {
        LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
        nested.put("secret", "value");
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("order", nested);

        ProcessRunResult.Succeeded result =
                new ProcessRunResult.Succeeded(RUN_ID, PROCESS.namespace(), PROCESS_CODE, PROCESS, output, COMPLETED_AT);
        nested.put("late", true);
        output.put("late", true);

        assertThat(result.output()).isEqualTo(Map.of("order", Map.of("secret", "value")));
        assertThat(result.toString()).contains("outputKeys=[order]").doesNotContain("secret", "value");
    }

    @Test
    void notCompletedRejectsTerminalStates() {
        assertThatThrownBy(() -> new ProcessRunResult.NotCompleted(RUN_ID, PROCESS.namespace(), PROCESS_CODE, PROCESS,
                ProcessRunStatus.SUCCEEDED))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedResultRequiresCallerSafeMessage() {
        assertThatThrownBy(() -> new ProcessRunResult.Failed(RUN_ID, PROCESS.namespace(), PROCESS_CODE, PROCESS,
                "FAILED", null, COMPLETED_AT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("errorMessage");
    }
}
