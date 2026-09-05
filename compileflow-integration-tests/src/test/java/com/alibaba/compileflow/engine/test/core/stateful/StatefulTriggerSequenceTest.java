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
package com.alibaba.compileflow.engine.test.core.stateful;

import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Stateful trigger sequence tests")
@Tag("integration")
@Tag("stateful")
@Execution(ExecutionMode.SAME_THREAD)
public class StatefulTriggerSequenceTest {
    protected ProcessEngine engine;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.createTbbpm();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("should preserve results across repeated triggers")
    void shouldPreserveResultsAcrossRepeatedTriggers() {
        String code = "bpm.stateful.highFrequencyTriggerProcess";
        int iterations = 100;

        int successCount = 0;

        for (int i = 0; i < iterations; i++) {
            Map<String, Object> context = new HashMap<>();
            context.put("triggerId", "trigger_" + i);
            context.put("triggerData", "data_" + i);

            ProcessResult<Map<String, Object>> result = engine.trigger(ProcessDefinition.classpath(code,
                            code.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on("waitHighFrequencyEvent", "highFrequencyEventComplete"), context);

            if (result.isSuccess()) {
                successCount++;
                assertThat(result.getOutput().get("processedData"))
                    .as("Processed data should match expected pattern")
                    .isEqualTo("combined_trigger_" + i + "_data_" + i);
            }
        }

        assertThat(successCount).as("Every trigger should succeed").isEqualTo(iterations);
    }

    @Test
    @DisplayName("should execute a stateful flow with multiple waiting nodes")
    void shouldExecuteStatefulFlowWithMultipleWaitingNodes() {
        String code = "bpm.stateful.complexMultiWaitProcess";
        Map<String, Object> context = new HashMap<>();
        context.put("processId", "performance_test_process");
        // Sequential triggers for complex flow
        String[] triggerNodeIds = {"waitStep1", "waitStep2", "waitStep3", "waitStep4", "waitStep5"};
        String[] triggerEvents = {"step1Complete", "step2Complete", "step3Complete", "step4Complete", "step5Complete"};

        ProcessResult<Map<String, Object>> finalResult = null;
        for (int i = 0; i < triggerNodeIds.length; i++) {
            Map<String, Object> triggerContext = new HashMap<>(context);
            triggerContext.put("step" + (i + 1) + "_data", "step" + (i + 1) + "_result");

            finalResult = engine.trigger(ProcessDefinition.classpath(code, code.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on(triggerNodeIds[i], triggerEvents[i]), triggerContext);

            assertThat(finalResult.isSuccess()).as("Step %d trigger should succeed", i + 1).isTrue();
        }
        // Semantic assertions:
        // 1) Each trigger step succeeded (asserted above)
        // The final trigger continues through combineResults and finalizeResult.
        assertThat(finalResult.getOutput()).as("Final result data should not be null").isNotNull();
        assertThat(finalResult.getOutput().containsKey("finalComplexResult"))
            .as("Final result should contain finalComplexResult key")
            .isTrue();
        Object finalComplexResultObj = finalResult.getOutput().get("finalComplexResult");
        assertThat(finalComplexResultObj).as("Final complex result object should not be null").isNotNull();
        String finalComplexResult = String.valueOf(finalComplexResultObj);
        // Final step: combineResults(step4_data, step5_data) should reflect at least step5_result
        assertThat(finalComplexResult).as("finalComplexResult should contain step5_result").contains("step5_result");
        assertThat(finalComplexResult)
            .as("Final complex result should include finalization")
            .isEqualTo("completed_combined_null_step5_result");
    }
}
