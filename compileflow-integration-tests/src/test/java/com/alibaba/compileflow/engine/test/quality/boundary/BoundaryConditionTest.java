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

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@Tag("integration")
@Tag("boundary")
@DisplayName("Boundary Condition Tests")
class BoundaryConditionTest {
    private ProcessEngine engine;

    @BeforeEach
    @Timeout(30)
    void setUp() {
        engine = ProcessEngineTestFactory.create();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("should reject null context before execution")
    void shouldRejectNullContextBeforeExecution() {
        assertThatThrownBy(() -> engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        "bpmn20.compat.simple_service", "bpmn20.compat.simple_service".replace(".", "/") + ".bpmn"),
                (Map<String, Object>) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("variables");
    }

    @Test
    @DisplayName("should use the sample action's zero defaults for empty input")
    void shouldUseZeroDefaultsWhenContextIsEmpty() {
        ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        "bpmn20.compat.simple_" + "service", "bpmn20.compat.simple_service".replace(".", "/") + ".bpmn"),
                Collections.emptyMap());
        assertThat(result.orElseThrow()).containsEntry("serviceTask1Result", 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1000000, 0, 1000000, Integer.MAX_VALUE})
    @DisplayName("should preserve Java integer arithmetic at the input boundaries")
    @Timeout(30)
    void shouldNotCrashWhenInputsContainExtremeIntegers(int value) {
        Map<String, Object> context = new HashMap<>();
        context.put("a", value);
        context.put("b", 1);
        // Constraint: numeric extremes should not cause runtime crash.
        ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        "bpmn20.compat.simple_" + "service", "bpmn20.compat.simple_service".replace(".", "/") + ".bpmn"),
                context);
        assertThat(result.orElseThrow()).containsEntry("serviceTask1Result", value + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("should handle invalid flow codes")
    void shouldHandleInvalidFlowCodes(String flowCode) {
        assertThatThrownBy(() -> ProcessDefinition.classpath(ProcessModelType.BPMN, flowCode,
                flowCode.replace(".", "/") + ".bpmn"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("code must not be blank");
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("should reject null flow code")
    void shouldRejectNullFlowCode(String flowCode) {
        assertThatThrownBy(() -> ProcessDefinition.classpath(ProcessModelType.BPMN, flowCode,
                "bpmn20/compat/simple_service.bpmn"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("code");
    }

    @Test
    @DisplayName("should use the sample action's zero default for a missing operand")
    void shouldUseZeroDefaultWhenOperandIsMissing() {
        Map<String, Object> context = new HashMap<>();
        context.put("a", 1);
        // missing "b"
        ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        "bpmn20.compat.simple_" + "service", "bpmn20.compat.simple_service".replace(".", "/") + ".bpmn"),
                context);
        assertThat(result.orElseThrow()).containsEntry("serviceTask1Result", 1);
    }

    @Test
    @DisplayName("should surface compilation error when process code does not exist")
    void shouldSurfaceCompilationErrorWhenProcessCodeDoesNotExist() {
        // Current status: compile layer failure is caught by execute and converted to
        // ProcessResult.failure.
        // Therefore we only assert failure result (should not throw exception).
        ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        "bpmn20.compat.not_exist", "bpmn20.compat.not_exist".replace(".", "/") + ".bpmn"),
                Collections.emptyMap());
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
    }
}
