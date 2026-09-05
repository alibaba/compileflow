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
package com.alibaba.compileflow.engine.test.quality.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Proves invalid correlation input is contained before it can corrupt execution attribution.
 *
 * @author yusu
 */
public class TraceIdBoundaryIntegrationTest {
    private static final AtomicInteger ACTIONS = new AtomicInteger();
    private static final ProcessDefinition DEFINITION = ProcessDefinition.inline("test.trace-boundary",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <bpm code="test.trace-boundary" name="Trace Boundary">
                <start id="start" name="Start" g="50,50,32,32">
                    <transition g=":-15,20" to="action"/>
                </start>
                <autoTask id="action" name="Action" g="140,42,120,48">
                    <action type="java" class="com.alibaba.compileflow.engine.test.quality.boundary.TraceIdBoundaryIntegrationTest$TraceRecordingAction"
                                      method="recordAction"/>
                    <transition g=":-15,20" to="end"/>
                </autoTask>
                <end id="end" name="End" g="320,50,32,32"/>
            </bpm>
            """);

    @AfterEach
    void reset() {
        ACTIONS.set(0);
    }

    @Test
    void oversizedProviderFallsBackBeforeActionAndReturnsControlledSuccess() {
        String oversized = "t".repeat(ProcessIdentifiers.MAX_TRACE_ID_LENGTH + 1);
        try (ProcessEngine engine = ProcessEngineFactory.create(ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .traceIdProvider(() -> oversized)
            .build())) {
            String source = engine.tooling().generateJavaCode(DEFINITION);
            ProcessResult<Map<String, Object>> result = engine.execute(DEFINITION, Map.of());

            assertThat(source).contains("TraceRecordingAction()\n            .recordAction();");
            assertThat(source.lines().mapToInt(String::length).max().orElseThrow()).isLessThanOrEqualTo(120);
            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(ACTIONS).hasValue(1);
            assertThat(result.getExecution().getTraceId()).matches("[0-9a-f]{32}").isNotEqualTo(oversized);
        }
    }

    /**
     * Application-owned capability used by the trace boundary test.
     */
    public static final class TraceRecordingAction {
        public void recordAction() {
            TraceIdBoundaryIntegrationTest.record();
        }
    }

    public static void record() {
        ACTIONS.incrementAndGet();
    }
}
