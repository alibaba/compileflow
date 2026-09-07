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
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import com.alibaba.compileflow.engine.test.support.mocks.ProcessCallAdmissionTestService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NestedProcessFailureIntegrationTest {
    private static final String MISSING_CHILD = "test.nested.missing-child";
    private static final String RECURSIVE_CHILD = "bpm.subprocess.recursive";
    private static final ProcessDefinition PARENT = ProcessDefinition.inline(ProcessModelType.TBBPM,
            "test.nested.parent",
            """
        <?xml version="1.0" encoding="UTF-8" ?>
        <bpm code="test.nested.parent" name="Nested failure parent">
            <start id="start" name="Start" g="50,50,32,32">
                <transition to="child"/>
            </start>
            <bpmCall id="child" name="Missing child"
                    code="test.nested.missing-child"
                    classpath="test/nested/missing-child.bpm"
                    g="140,42,120,48">
                <transition to="end"/>
            </bpmCall>
            <end id="end" name="End" g="320,50,32,32"/>
        </bpm>
        """);

    @Test
    void nestedExecutionPreservesTheChildTypedFailureAndParentAttribution() {
        ProcessEngineConfig config = ProcessEngineTestFactory.builder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessResult<Map<String, Object>> directChild = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            MISSING_CHILD, MISSING_CHILD.replace(".", "/") + ".bpm"), Map.of());
            ProcessResult<Map<String, Object>> parent = engine.execute(PARENT, Map.of());

            assertThat(directChild.isFailure()).isTrue();
            assertThat(parent.isFailure()).isTrue();
            assertThat(parent.getError()).isEqualTo(directChild.getError());
            assertThat(parent.getExecution().getProcessCode()).isEqualTo(PARENT.code());
            assertThat(parent.getExecution().getProcessVersion()).isNull();
        }
    }

    @Test
    void recursiveProcessCallsAreRejectedDuringGraphResolution() {
        ProcessEngineConfig config = ProcessEngineTestFactory.builder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            RECURSIVE_CHILD, RECURSIVE_CHILD.replace(".", "/") + ".bpm"), Map.of());

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo("CF_EXEC_014");
            assertThat(result.getError().getMessage()).isEqualTo(
                    "Process call graph is invalid before action execution");
        }
    }

    @Test
    void invalidProcessCallContractIsRejectedBeforeParentActionsRun() {
        ProcessCallAdmissionTestService.reset();
        ProcessEngineConfig config = ProcessEngineTestFactory.builder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessResult<Map<String, Object>> result = engine.execute(invalidMappingParent(), Map.of("value", 1));

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo("CF_EXEC_014");
            assertThat(ProcessCallAdmissionTestService.invocations()).isZero();
        }
    }

    private static ProcessDefinition invalidMappingParent() {
        return ProcessDefinition.inline(ProcessModelType.TBBPM, "test.nested.invalid-contract",
                """
            <bpm code="test.nested.invalid-contract">
              <var name="value" dataType="java.lang.Integer" inOutType="param"/>
              <start id="start" g="0,0,32,32"><transition to="sideEffect"/></start>
              <autoTask id="sideEffect" g="50,0,100,40">
                <action type="java" class="%s" method="mark"/>
                <transition to="child"/>
              </autoTask>
              <bpmCall id="child" code="bpm.java-code.javaCodeSum"
                       classpath="bpm/java-code/javaCodeSum.bpm" g="180,0,120,48">
                <input source="value" target="missing"/>
                <transition to="end"/>
              </bpmCall>
              <end id="end" g="340,0,32,32"/>
            </bpm>
            """
                    .formatted(ProcessCallAdmissionTestService.class.getName()));
    }
}
