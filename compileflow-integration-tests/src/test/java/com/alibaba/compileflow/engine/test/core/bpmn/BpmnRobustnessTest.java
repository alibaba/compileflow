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
package com.alibaba.compileflow.engine.test.core.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("BPMN robustness tests")
@Tag("integration")
@Tag("bpmn")
@Execution(ExecutionMode.SAME_THREAD)
public class BpmnRobustnessTest {
    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.createBpmn();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {
        @Test
        @DisplayName("should handle nested structures when inputs are provided")
        void shouldHandleNestedStructuresWhenInputsAreProvided() {
            ProcessDefinition definition =
                    ProcessDefinition.classpath("bpmn20.compat.nested_subprocess",
                            "bpmn20/compat/nested_subprocess.bpmn");
            String source = engine.tooling().generateJavaCode(definition);
            Map<String, Object> context = new HashMap<>();
            context.put("a", 100);
            context.put("b", 200);

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpmn20.compat."
                            + "nested_subprocess", "bpmn20.compat.nested_subprocess".replace(".", "/") + ".bpmn"),
                    context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isNotNull();
            assertThat(result.getOutput().get("deepServiceResult")).isEqualTo(300);
            assertThat(source)
                .contains("private int _cf$runSubprocess1()", "private int _cf$runSubprocess2()")
                .doesNotContain("_cf$activitySubprocess");
        }
    }

    @Nested
    @DisplayName("Repeated and nested execution")
    class RepeatedExecutionTests {
        @Test
        @DisplayName("should preserve parallel gateway correctness across repeated executions")
        void shouldPreserveParallelGatewayCorrectnessAcrossRepeatedExecutions() {
            int iterations = 100;
            for (int i = 0; i < iterations; i++) {
                Map<String, Object> context = new HashMap<>();
                context.put("a", i);
                context.put("b", i * 2);

                ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpmn20."
                                + "gateway.parallel_gateway",
                                "bpmn20.gateway.parallel_gateway".replace(".", "/") + ".bpmn"), context);

                assertThat(result.isSuccess()).as("Parallel gateway execution %d", i).isTrue();
            }
        }

        @Test
        @DisplayName("should execute nested multi-instance loops with multiple collections")
        void shouldExecuteNestedMultiInstanceLoopsWithMultipleCollections() {
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Arrays.asList("A", "B", "C"));
            context.put("subList", Arrays.asList(1, 2, 3, 4, 5));

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpmn20.compat."
                            + "nested_multi_instance", "bpmn20.compat.nested_multi_instance".replace(".", "/") + ".bpmn"),
                    context);

            assertThat(result.isSuccess()).isTrue();
        }
    }
}
