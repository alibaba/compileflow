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
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessVariableBoundaryIntegrationTest {
    @Test
    void tbbpmExecutionAcceptsOnlyDeclaredParameterVariables() {
        try (ProcessEngine engine = ProcessEngineTestFactory.createTbbpm()) {
            ProcessDefinition ref = ProcessDefinition.classpath("bpm.java-code.javaCodeSum",
                    "bpm.java-code.javaCodeSum".replace(".", "/") + ".bpm");

            assertRejected(engine.execute(ref, Map.of("inputA", 15, "inputB", 25, "result", 999)));
            assertRejected(engine.execute(ref, Map.of("inputA", 15, "inputB", 25, "unknown", true)));
            assertRejected(engine.execute(ProcessDefinition.classpath("bpm.stateful.waitTaskProcess",
                            "bpm.stateful.waitTaskProcess".replace(".", "/") + ".bpm"),
                    Map.of("taskData", "task-a", "processedData", "forged-inner-state")));

            ProcessResult<Map<String, Object>> accepted = engine.execute(ref, Map.of("inputA", 15, "inputB", 25));
            assertThat(accepted.isSuccess()).as(String.valueOf(accepted.getError())).isTrue();
            assertThat(accepted.getOutput()).containsExactly(Map.entry("result", 40));
        }
    }

    @Test
    void bpmnExecutionUsesTheSameClosedParameterContract() {
        try (ProcessEngine engine = ProcessEngineTestFactory.createBpmn()) {
            ProcessDefinition ref = ProcessDefinition.classpath("bpmn20.compat.simple_service",
                    "bpmn20.compat.simple_service".replace(".", "/") + ".bpmn");

            assertRejected(engine.execute(ref, Map.of("a", 15, "b", 25, "serviceTask1Result", 999)));
            assertRejected(engine.execute(ref, Map.of("a", 15, "b", 25, "unknown", true)));

            ProcessResult<Map<String, Object>> accepted = engine.execute(ref, Map.of("a", 15, "b", 25));
            assertThat(accepted.isSuccess()).as(String.valueOf(accepted.getError())).isTrue();
            assertThat(accepted.getOutput()).containsExactly(Map.entry("serviceTask1Result", 40));
        }
    }

    @Test
    void typedInputMustMapToTheSameDeclaredParameterContract() {
        try (ProcessEngine engine = ProcessEngineTestFactory.createTbbpm()) {
            ProcessResult<TypedOutput> result = engine.execute(ProcessDefinition.classpath("bpm.java-code.javaCodeSum",
                            "bpm.java-code.javaCodeSum".replace(".", "/") + ".bpm"), new SupersetInput(15, 25, 999),
                    TypedOutput.class, ProcessExecutionOptions.defaults());

            assertRejected(result);
        }
    }

    @Test
    void nativeTriggerAcceptsDeclaredStateButRejectsUndeclaredState() {
        ProcessRef.Version ref = ProcessRef.version("default", "bpm.stateful.waitTaskProcess", "v1");
        ProcessDefinition definition = ProcessDefinition.classpath(ref.code(), "bpm/stateful/waitTaskProcess.bpm");
        try (ProcessEngine engine = ProcessEngineTestFactory.createTbbpm()) {
            engine.runtime().load(ref, definition);

            ProcessResult<Map<String, Object>> accepted = engine.trigger(ref, ProcessTrigger.at("waitTask1"),
                    Map.of("taskData", "task-a", "processedData", "prepared-a", "waitResult", "resume-a"));
            assertThat(accepted.isSuccess()).as(String.valueOf(accepted.getError())).isTrue();
            assertThat(accepted.getOutput()).containsEntry("waitResult", "completed_resume-a");

            assertRejected(engine.trigger(ref, ProcessTrigger.at("waitTask1"),
                    Map.of("taskData", "task-a", "unknown", true)));
        }
    }

    private static void assertRejected(ProcessResult<?> result) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_VALIDATION_001.getCode());
    }

    private record SupersetInput(Integer inputA, Integer inputB, Integer result) {}

    private static final class TypedOutput {
        public Integer result;
    }
}
