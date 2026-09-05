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
// Millisecond timeout assertions should not compete with parallel
@Execution(ExecutionMode.SAME_THREAD)
class // tests.
PreflightTest {
    protected ProcessEngine engine;
    private ProcessToolingService tooling;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.createBpmn();
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
        ProcessDefinition valid =
                ProcessDefinition.classpath("bpmn20.gateway.parallel_gateway", "bpmn20/gateway/parallel_gateway.bpmn");
        String invalidXml = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"></definitions>";
        ProcessDefinition invalid = ProcessDefinition.inline("preflight.invalid.noprocess", invalidXml);
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
        // Input: minimal timeout to reliably trigger timeout path.
        ProcessDefinition slow =
                ProcessDefinition.classpath("bpmn20.gateway.parallel_gateway", "bpmn20/gateway/parallel_gateway.bpmn");
        ProcessPreflightOptions opts = ProcessPreflightOptions
            .builder()
            .lintEnabled(true)
            .compileEnabled(true)
            .timeout(Duration.ofMillis(1))
            .build();
        // Behavior: execute preflight.
        ProcessPreflightReport r = tooling.preflight(slow, opts);
        // Assertion: overall FAIL, and the active stage reflects TIMEOUT.
        assertThat(r.getOverallStatus())
            .as("Timeout should result in FAIL overall status")
            .isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
        assertThat(r.getItems())
            .as("Timeout should identify the stage active at the deadline")
            .anyMatch(it -> it.getStatus() == TIMEOUT);
    }
}
