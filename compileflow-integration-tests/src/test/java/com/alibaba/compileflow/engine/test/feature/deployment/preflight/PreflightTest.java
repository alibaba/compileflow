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
package com.alibaba.compileflow.engine.test.feature.deployment.preflight;

import com.alibaba.compileflow.engine.ProcessModelType;
import static com.alibaba.compileflow.engine.preflight.ProcessPreflightReport.ItemStatus.FAIL;
import static com.alibaba.compileflow.engine.preflight.ProcessPreflightReport.ItemStatus.PASS;
import static com.alibaba.compileflow.engine.preflight.ProcessPreflightReport.ItemStatus.TIMEOUT;
import static com.alibaba.compileflow.engine.preflight.ProcessPreflightReport.ItemType.COMPILE;
import static com.alibaba.compileflow.engine.preflight.ProcessPreflightReport.ItemType.LINT;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Tooling.preflight integration: valid/invalid/timeout reports")
@Execution(ExecutionMode.SAME_THREAD)
class PreflightTest {
    protected ProcessEngine engine;
    private ProcessToolingService tooling;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.create();
        tooling = engine.tooling();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("preflight: valid + invalid flows produce PASS/FAIL reports")
    void shouldPassPreflightWhenValidAndInvalid() {
        // Input: a parseable flow + a structurally incomplete XML (to trigger FAIL).
        ProcessDefinition valid = ProcessDefinition.classpath(ProcessModelType.BPMN, "bpmn20.gateway.parallel_gateway",
                "bpmn20/gateway/parallel_gateway.bpmn");
        String invalidXml = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"></definitions>";
        ProcessDefinition invalid =
                ProcessDefinition.inline(ProcessModelType.BPMN, "preflight.invalid.noprocess", invalidXml);
        // Behavior: execute preflight with strict options.
        ProcessPreflightOptions opts = ProcessPreflightOptions.strict();
        ProcessPreflightReport ok = tooling.preflight(valid, opts);
        ProcessPreflightReport bad = tooling.preflight(invalid, opts);
        // Assertion: valid flow PASS, and lint/compile both PASS.
        assertThat(ok.getOverallStatus())
            .as("Valid flow should have PASS status")
            .isEqualTo(ProcessPreflightReport.OverallStatus.PASS);
        assertThat(ok.getItems())
            .as("Valid flow should have LINT PASS item")
            .anyMatch(it -> it.getType() == LINT && it.getStatus() == PASS);
        assertThat(ok.getItems())
            .as("Valid flow should have COMPILE PASS item")
            .anyMatch(it -> it.getType() == COMPILE && it.getStatus() == PASS);
        // Assertion: invalid flow FAIL, at least one item is FAIL.
        assertThat(bad.getOverallStatus())
            .as("Invalid flow should have FAIL status")
            .isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
        assertThat(bad.getItems())
            .as("Invalid flow should have at least one FAIL item")
            .anyMatch(it -> it.getStatus() == FAIL);
    }

    @Test
    @DisplayName("preflight: per-flow timeout yields TIMEOUT item and FAIL overall")
    void shouldHandlePreflightTimeout() {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean compilationEntered = new java.util.concurrent.atomic.AtomicBoolean();
        var executor = new com.alibaba.compileflow.engine.spi.script.ScriptExecutor() {
            @Override
            public String name() {
                return "blocking-test";
            }

            @Override
            public void validate(com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec spec) {}

            @Override
            public com.alibaba.compileflow.engine.spi.script.ScriptProgram compile(
                    com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec spec) {
                compilationEntered.set(true);
                try {
                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test compilation was not released");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Test compilation interrupted", interrupted);
                }
                throw new IllegalStateException("Timed-out compilation must not install a program");
            }

            @Override
            public Object evaluate(com.alibaba.compileflow.engine.spi.script.ScriptProgram program,
                    java.util.Map<String, Object> variables) {
                throw new AssertionError("Preflight must not evaluate scripts");
            }
        };
        ProcessDefinition slow = ProcessDefinition.inline(ProcessModelType.TBBPM, "preflight.blocked",
                """
            <bpm code="preflight.blocked">
              <start id="start"><transition to="task"/></start>
              <scriptTask id="task"><action type="script" language="blocking-test"><code>blocked</code></action>
                <transition to="end"/></scriptTask>
              <end id="end"/>
            </bpm>
            """);
        ProcessPreflightOptions opts = ProcessPreflightOptions
            .builder()
            .lintEnabled(true)
            .compileEnabled(true)
            .timeout(Duration.ofSeconds(2))
            .build();
        // Behavior: execute preflight.
        ProcessPreflightReport r;
        try (ProcessEngine slowEngine = com.alibaba.compileflow.engine.ProcessEngineFactory.create(ProcessEngineTestFactory
            .builder()
            .scriptExecutor(executor)
            .build())) {
            try {
                r = slowEngine.tooling().preflight(slow, opts);
                assertThat(compilationEntered).isTrue();
            } finally {
                release.countDown();
            }
        }
        // Assertion: overall FAIL, and the active stage reflects TIMEOUT.
        assertThat(r.getOverallStatus())
            .as("Timeout should result in FAIL overall status")
            .isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
        assertThat(r.getItems())
            .as("Timeout should identify the stage active at the deadline")
            .anyMatch(it -> it.getStatus() == TIMEOUT);
    }
}
