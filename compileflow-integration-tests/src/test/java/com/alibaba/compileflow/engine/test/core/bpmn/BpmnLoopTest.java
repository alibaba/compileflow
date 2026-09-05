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

import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("BPMN Loop and Multi-Instance Tests")
@Tag("integration")
@Tag("bpmn")
public class BpmnLoopTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BpmnLoopTest.class);
    protected ProcessEngine engine;
    private ProcessRuntimeManager runtimeManager;
    private ProcessToolingService toolingService;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.createBpmn();
        runtimeManager = engine.runtime();
        toolingService = engine.tooling();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("should execute standard loop with iteration counter when initial values are provided")
    void shouldExecuteStandardLoopWhenInitialValuesAreProvided() {
        // Given: Standard loop flow with initial counter and message
        String standardLoopFlowCode = "bpmn20.compat.standard_loop";
        Map<String, Object> loopContext = new HashMap<>();
        loopContext.put("i", 0);
        loopContext.put("msg", "hello");
        // When: Execute the flow
        ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(standardLoopFlowCode,
                        standardLoopFlowCode.replace(".", "/") + ".bpmn"), loopContext);
        // Then: Verify execution success and semantic output
        assertThat(executionResult.isSuccess()).as("Standard loop should execute successfully").isTrue();
        assertThat(executionResult.getOutput())
            .as("Standard loop should produce processedMessage")
            .isNotNull()
            .containsKey("processedMessage");
        assertThat((String) executionResult.getOutput().get("processedMessage"))
            .as("processedMessage should be derived from msg")
            .isNotBlank();
    }

    @Test
    @DisplayName("should execute multi-instance loop processing each element when collection is provided")
    void shouldExecuteMultiInstanceLoopWhenCollectionIsProvided() {
        // Given: Multi-instance loop flow with collection
        String multiInstanceLoopFlowCode = "bpmn20.compat.multi_instance_loop";
        Map<String, Object> multiInstanceContext = new HashMap<>();
        multiInstanceContext.put("pList", Arrays.asList("A", "B", "C"));
        // When: Execute the flow
        ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(multiInstanceLoopFlowCode,
                        multiInstanceLoopFlowCode.replace(".", "/") + ".bpmn"), multiInstanceContext);
        // Then: Verify execution success and semantic output
        assertThat(executionResult.isSuccess()).as("Multi-instance loop should execute successfully").isTrue();
        assertThat(executionResult.getOutput())
            .as("Multi-instance loop should produce processedMessage")
            .isNotNull()
            .containsKey("processedMessage");
        assertThat((String) executionResult.getOutput().get("processedMessage"))
            .as("processedMessage should be derived from elements")
            .isNotBlank()
            .startsWith("processed_");
    }

    @Test
    @DisplayName("should execute multi-instance lexical bindings through explicit Code Task inputs")
    void shouldExecuteMultiInstanceLexicalBindingsThroughCodeTask() {
        ProcessDefinition definition = ProcessDefinition.inline("bpmn20.loop.snapshot",
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions
                xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                xmlns:cf="http://www.compileflow.org"
                targetNamespace="urn:compileflow:test">
              <process id="bpmn20.loop.snapshot"
                       isExecutable="true">
                <extensionElements>
                  <cf:var name="items"
                      dataType="java.util.List&lt;java.lang.String&gt;"
                      inOutType="param"/>
                  <cf:var name="visited"
                      dataType="java.lang.String"
                      defaultValue=""
                      inOutType="return"/>
                </extensionElements>
                <startEvent id="start"/>
                <subProcess id="loop">
                  <multiInstanceLoopCharacteristics
                      isSequential="true"
                      cf:collection="items"
                      cf:item="item"
                      cf:index="itemIndex"
                      cf:itemType="java.lang.String"/>
                  <startEvent id="inner_start"/>
                  <scriptTask id="visit" scriptFormat="java">
                    <extensionElements>
                      <cf:input target="current" dataType="java.lang.String"
                              source="visited"/>
                      <cf:input target="item" dataType="java.lang.String"
                              source="item"/>
                      <cf:output dataType="java.lang.String"
                              target="visited"/>
                    </extensionElements>
                    <script>return current + item;</script>
                  </scriptTask>
                  <endEvent id="inner_end"/>
                  <sequenceFlow id="inner_to_visit"
                      sourceRef="inner_start" targetRef="visit"/>
                  <sequenceFlow id="inner_to_end"
                      sourceRef="visit" targetRef="inner_end"/>
                </subProcess>
                <endEvent id="end"/>
                <sequenceFlow id="to_loop"
                    sourceRef="start" targetRef="loop"/>
                <sequenceFlow id="to_end"
                    sourceRef="loop" targetRef="end"/>
              </process>
            </definitions>
            """);
        List<String> items = new ArrayList<>(List.of("A", "B"));

        ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of("items", items));

        assertThat(result.isSuccess())
            .as("Generated multi-instance flow should compile and execute: %s", result.getError())
            .isTrue();
        assertThat(result.getOutput()).containsEntry("visited", "AB");
        assertThat(items).containsExactly("A", "B");
    }

    @Test
    @DisplayName("should execute nested multi-instance loops when outer and inner collections are provided")
    void shouldExecuteNestedMultiInstanceLoopsWhenOuterAndInnerCollectionsAreProvided() {
        // Given: Nested multi-instance loop flow with outer and inner collections
        String nestedMultiInstanceFlowCode = "bpmn20.compat.nested_multi_instance";
        Map<String, Object> nestedLoopContext = new HashMap<>();
        nestedLoopContext.put("pList", Arrays.asList("A", "B"));
        nestedLoopContext.put("subList", Arrays.asList(1, 2, 3));
        // When: Execute the flow
        ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(nestedMultiInstanceFlowCode,
                        nestedMultiInstanceFlowCode.replace(".", "/") + ".bpmn"), nestedLoopContext);
        // Then: Verify execution success and semantic output
        assertThat(executionResult.isSuccess()).as("Nested multi-instance loop should execute successfully").isTrue();
        assertThat(executionResult.getOutput())
            .as("Nested multi-instance loop should produce processedMessage")
            .isNotNull()
            .containsKey("processedMessage");
        assertThat((String) executionResult.getOutput().get("processedMessage"))
            .as("processedMessage should be derived from nested loop elements")
            .isNotBlank()
            .startsWith("processed_");
    }

    @Test
    @DisplayName("should compile and execute nested standard loops without local variable collisions")
    void shouldExecuteNestedStandardLoops() {
        Map<String, Object> context = new HashMap<>();
        context.put("msg", "nested");

        ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpmn20.compat.nested_"
                        + "standard_loop", "bpmn20.compat.nested_standard_loop".replace(".", "/") + ".bpmn"), context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("processedMessage", "processed_nested");
    }

    @Test
    @DisplayName("should not evaluate a standard-loop condition after loopMaximum is reached")
    void shouldShortCircuitConditionAfterLoopMaximum() {
        ProcessDefinition definition = ProcessDefinition.inline("bpmn20.loop.maximum-short-circuit",
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions
                xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                xmlns:cf="http://www.compileflow.org"
                expressionLanguage="urn:compileflow:java"
                targetNamespace="urn:compileflow:test">
              <process id="bpmn20.loop.maximum-short-circuit"
                       isExecutable="true">
                <extensionElements>
                  <cf:var name="executions"
                          dataType="java.lang.Integer"
                          defaultValue="0"
                          inOutType="return"/>
                </extensionElements>
                <startEvent id="start"/>
                <scriptTask id="task" scriptFormat="java">
                  <extensionElements>
                    <cf:input target="count" dataType="java.lang.Integer"
                            source="executions"/>
                    <cf:output dataType="java.lang.Integer"
                            target="executions"/>
                  </extensionElements>
                  <standardLoopCharacteristics
                      testBefore="false"
                      loopMaximum="2">
                    <loopCondition><![CDATA[
                      executions < 2
                          || 1 / (executions - 2) > 0
                    ]]></loopCondition>
                  </standardLoopCharacteristics>
                  <script>return count + 1;</script>
                </scriptTask>
                <endEvent id="end"/>
                <sequenceFlow id="to_task"
                              sourceRef="start"
                              targetRef="task"/>
                <sequenceFlow id="to_end"
                              sourceRef="task"
                              targetRef="end"/>
              </process>
            </definitions>
            """);

        ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of());

        assertThat(result.isSuccess()).as("standard loop must compile and execute: %s", result.getError()).isTrue();
        assertThat(result.getOutput()).containsEntry("executions", 2);
    }

    @Test
    @DisplayName("should preserve multi-instance locals when invocation policy extracts an action method")
    void shouldExecuteMultiInstanceInvocationPolicyWithLexicalInputs() {
        ProcessDefinition definition = ProcessDefinition.inline("bpmn20.loop.policy-lexical-inputs",
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions
                xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                xmlns:cf="http://www.compileflow.org"
                targetNamespace="urn:compileflow:test">
              <process id="bpmn20.loop.policy-lexical-inputs"
                       isExecutable="true">
                <extensionElements>
                  <cf:var name="items"
                      dataType="java.util.List&lt;java.lang.String&gt;"
                      inOutType="param"/>
                  <cf:var name="result" dataType="java.lang.String"
                      defaultValue="" inOutType="return"/>
                </extensionElements>
                <startEvent id="start"/>
                <subProcess id="loop">
                  <multiInstanceLoopCharacteristics
                      isSequential="true"
                      cf:collection="items"
                      cf:item="item"
                      cf:index="itemIndex"
                      cf:itemType="java.lang.String"/>
                  <startEvent id="inner_start"/>
                  <serviceTask id="append">
                    <extensionElements>
                      <cf:action type="java"
                            class="com.alibaba.compileflow.engine.test.support.mocks.MockJavaService"
                            method="appendStep">
                          <cf:input target="path" dataType="java.lang.String"
                              source="result"/>
                          <cf:input target="step" dataType="java.lang.String"
                              source="item + itemIndex"/>
                          <cf:output dataType="java.lang.String"
                              target="result"/>

                        <cf:invocationPolicy maxAttempts="1"/>
                      </cf:action>
                    </extensionElements>
                  </serviceTask>
                  <endEvent id="inner_end"/>
                  <sequenceFlow id="inner_to_append"
                      sourceRef="inner_start" targetRef="append"/>
                  <sequenceFlow id="inner_to_end"
                      sourceRef="append" targetRef="inner_end"/>
                </subProcess>
                <endEvent id="end"/>
                <sequenceFlow id="to_loop"
                    sourceRef="start" targetRef="loop"/>
                <sequenceFlow id="to_end"
                    sourceRef="loop" targetRef="end"/>
              </process>
            </definitions>
            """);

        ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of("items", List.of("A", "B")));

        assertThat(result.isSuccess())
            .as("multi-instance action with InvocationPolicy must compile and execute: %s", result.getError())
            .isTrue();
        assertThat(result.getOutput()).containsEntry("result", "A0->B1");
    }

    @Test
    @DisplayName("should isolate sequential multi-instance subprocesses in parallel branches")
    void shouldIsolateParallelSequentialMultiInstanceSubprocesses() {
        String parallelGatewayMultiInstanceFlowCode = "bpmn20.gateway.parallel_gateway_multi_instance";
        Map<String, Object> parallelGatewayMultiInstanceContext = new HashMap<>();
        parallelGatewayMultiInstanceContext.put("pList", Arrays.asList("X", "Y"));
        parallelGatewayMultiInstanceContext.put("subList", Arrays.asList(10, 20));

        String source = toolingService.generateJavaCode(ProcessDefinition.classpath(parallelGatewayMultiInstanceFlowCode,
                "bpmn20/gateway/parallel_gateway_multi_instance.bpmn"));
        ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(parallelGatewayMultiInstanceFlowCode,
                        parallelGatewayMultiInstanceFlowCode.replace(".", "/") + ".bpmn"),
                parallelGatewayMultiInstanceContext);

        assertThat(source)
            .contains("LoopSemantics.<String>snapshot(this.pList, \"branch1\"",
                    "LoopSemantics.<Integer>snapshot(this.subList, \"branch2\"");
        assertThat(executionResult.isSuccess())
            .as("Each branch frame must own its subprocess iteration state: %s", executionResult.getError())
            .isTrue();
    }
}
