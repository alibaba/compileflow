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
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import com.alibaba.compileflow.engine.test.support.mocks.MockJavaService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("integration")
@DisplayName("Compiled and interpreted ProcessRuntime differential contract")
class ProcessRuntimeModeDifferentialTest {
    @ParameterizedTest
    @ValueSource(strings = {"java.util.List", "java.util.ArrayList", "java.util.LinkedList"})
    void concurrentBranchesValidateAndCommitLoopOutputsInBothModes(String outputType) {
        ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.loop-output",
                """
            <bpm code="test.runtime.loop-output">
              <var name="items" dataType="java.util.List&lt;java.lang.Integer&gt;" inOutType="param"/>
              <var name="value" dataType="java.lang.Integer" inOutType="inner"/>
              <var name="results" dataType="%s&lt;java.lang.Integer&gt;" inOutType="return"/>
              <start id="start"><transition to="fork"/></start>
              <parallel id="fork"><transition to="loop"/><transition to="join"/></parallel>
              <foreach id="loop" collection="items" item="DataTypes" itemType="java.lang.Integer">
                <output source="value" target="results"/>
                <transition to="join"/>
                <start id="loopStart"><transition to="calculate"/></start>
                <scriptTask id="calculate">
                  <action type="script" language="java">
                    <input target="input" dataType="java.lang.Integer" source="DataTypes"/>
                    <output dataType="java.lang.Integer" target="value"/>
                    <code>return input * 2;</code>
                  </action>
                  <transition to="loopEnd"/>
                </scriptTask>
                <end id="loopEnd"/>
              </foreach>
              <parallel id="join"><transition to="end"/></parallel>
              <end id="end"/>
            </bpm>
            """
                    .formatted(outputType));
        for (List<Integer> items : List.of(List.of(2, 3), List.<Integer>of())) {
            DifferentialResult result =
                    executeBoth(ProcessEngineTestFactory::builder, engine -> engine.execute(definition,
                    Map.of("items", items)));
            if (outputType.equals("java.util.List")) {
                List<Integer> expected = items.stream().map(value -> value * 2).toList();
                assertThat(result.compiled().orElseThrow()).containsEntry("results", expected);
                assertThat(result.interpreted().orElseThrow()).containsEntry("results", expected);
            } else {
                assertThat(result.compiled().isSuccess()).isFalse();
                assertThat(result.interpreted().isSuccess()).isFalse();
                assertThat(result.compiled().getError().getCode()).isEqualTo("CF_COMPILE_001");
                assertThat(result.interpreted().getError().getCode()).isEqualTo("CF_COMPILE_001");
            }
        }
    }

    @Test
    void defaultValueTypesCannotBeShadowedByProcessVariables() {
        ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.default-names",
                """
            <bpm code="test.runtime.default-names">
              <var name="Boolean" dataType="java.lang.Boolean" defaultValue="true" inOutType="return"/>
              <var name="LocalDate" dataType="java.time.LocalDate" defaultValue="2026-09-07" inOutType="return"/>
              <var name="String" dataType="java.lang.String" defaultValue="String" inOutType="return"/>
              <start id="start"><transition to="end"/></start>
              <end id="end"/>
            </bpm>
            """);
        DifferentialResult result =
                executeBoth(ProcessEngineTestFactory::builder, engine -> engine.execute(definition, Map.of()));

        Map<String, Object> expected =
                Map.of("Boolean", true, "LocalDate", java.time.LocalDate.of(2026, 9, 7), "String", "String");
        assertThat(result.compiled().orElseThrow()).isEqualTo(expected);
        assertThat(result.interpreted().orElseThrow()).isEqualTo(expected);
    }

    @Test
    void generatorNamesDoNotRestrictProcessOrScriptInputsInEitherMode() {
        ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.binding-names",
                """
            <bpm code="test.runtime.binding-names">
              <var name="arguments" dataType="int" inOutType="param"/>
              <var name="ConditionSemantics" dataType="java.lang.Boolean" inOutType="param"/>
              <var name="result" dataType="int" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="choose"/></start>
              <exclusive id="choose" g="40,0,32,32">
                <transition to="calculate" condition="ConditionSemantics"/>
                <transition to="end"/>
              </exclusive>
              <scriptTask id="calculate" g="80,0,100,48">
                <action type="script" language="java">
                  <input target="input" dataType="int" source="arguments"/>
                  <output dataType="int" target="result"/>
                  <code>return input + 22;</code>
                </action>
                <transition to="end"/>
              </scriptTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """);
        DifferentialResult result = executeBoth(ProcessEngineTestFactory::builder, engine -> engine.execute(definition,
                Map.of("arguments", 20, "ConditionSemantics", true)));

        assertThat(result.compiled().orElseThrow()).containsEntry("result", 42);
        assertThat(result.interpreted().orElseThrow()).containsEntry("result", 42);
    }

    @Test
    @DisplayName("BPMN Java actions and standard-loop limits have identical observable semantics")
    void bpmnActionsAndLoopLimitsAreEquivalent() {
        ProcessDefinition service = ProcessDefinition.classpath(ProcessModelType.BPMN, "bpmn20.compat.simple_service",
                "bpmn20/compat/simple_service.bpmn");
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(service,
                Map.of("a", 5, "b", 3)));

        ProcessDefinition loop = ProcessDefinition.classpath(ProcessModelType.BPMN, "bpmn20.compat.standard_loop",
                "bpmn20/compat/standard_loop.bpmn");
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(loop,
                Map.of("i", 0, "msg", "hello")));

        ProcessDefinition afterLoop =
                ProcessDefinition.inline(ProcessModelType.BPMN, "test.runtime.bpmn-after-loop", bpmnAfterLoop());
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(afterLoop, Map.of()));

        ProcessDefinition nested = ProcessDefinition.classpath(ProcessModelType.BPMN,
                "bpmn20.compat.nested_multi_instance", "bpmn20/compat/nested_multi_instance.bpmn");
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(nested,
                Map.of("pList", List.of("A", "B"), "subList", List.of(1, 2))));
    }

    @Test
    @DisplayName("TBBPM expressions, break control, and script actions have identical semantics")
    void tbbpmExpressionsBreakAndScriptsAreEquivalent() {
        ProcessDefinition loop =
                ProcessDefinition.classpath(ProcessModelType.TBBPM, "bpm.loop.loopWithBreak",
                        "bpm/loop/loopWithBreak.bpm");
        try (ProcessEngine engine = ProcessEngineTestFactory.create()) {
            assertThat(engine.tooling().generateJavaCode(loop))
                .contains("private int _cf$foreachLoopWithBreak()")
                .contains("private void _cf$executeCheckAndSetIndex(String _cf$nodeId, int i)")
                .contains("private Integer _cf$invokeCheckAndSetIndex(int i)")
                .doesNotContain("_cf$invokeCheckAndSetIndex(Integer num");
        }
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(loop,
                Map.of("numbers", List.of(1, 2, 3, 4, 5, 6))));
    }

    @Test
    @DisplayName("TBBPM structured concurrent branches commit the same outputs")
    void tbbpmStructuredConcurrencyIsEquivalent() {
        ProcessDefinition definition =
                ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.parallel", parallelProcess());
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(definition,
                Map.of("a", 6, "b", 4)));
    }

    @Test
    @DisplayName("TBBPM embedded BPM scopes execute identically in both runtime modes")
    void tbbpmEmbeddedScopesAreEquivalent() {
        ProcessDefinition definition =
                ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.embedded-scope", embeddedSubBpm());

        DifferentialResult result =
                executeBoth(ProcessEngineTestFactory::builder, engine -> engine.execute(definition,
                Map.of("a", 2, "b", 3)));

        assertThat(result.compiled().isSuccess()).as("Compiled execution failed: %s", result.compiled().getError()).isTrue();
        assertThat(result.interpreted().isSuccess())
            .as("Interpreted execution failed: %s", result.interpreted().getError())
            .isTrue();
        assertThat(result.compiled().getOutput()).containsEntry("result", 5);
        assertThat(result.interpreted().getOutput()).containsEntry("result", 5);
    }

    @Test
    @DisplayName("Loop control propagates through embedded scopes in both runtime modes")
    void tbbpmEmbeddedLoopControlIsEquivalent() {
        ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM,
                "test.runtime.embedded-loop-control", embeddedLoopControl());

        DifferentialResult result = executeBoth(ProcessEngineTestFactory::builder, engine -> engine.execute(definition,
                Map.of("items", List.of(1, 2, 3))));

        assertThat(result.compiled().isSuccess()).as("Compiled execution failed: %s", result.compiled().getError()).isTrue();
        assertThat(result.interpreted().isSuccess())
            .as("Interpreted execution failed: %s", result.interpreted().getError())
            .isTrue();
        assertThat(result.compiled().getOutput()).containsEntry("count", 2);
        assertThat(result.interpreted().getOutput()).containsEntry("count", 2);
    }

    @Test
    @DisplayName("Decision ordering, defaults, and inclusive selection have identical semantics")
    void decisionAndInclusiveSelectionAreEquivalent() {
        ProcessDefinition decision =
                ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.decision", decisionProcess());
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(decision,
                Map.of("value", 20)));
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(decision,
                Map.of("value", 0)));

        ProcessDefinition inclusive =
                ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.inclusive", inclusiveProcess());
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(inclusive,
                Map.of("leftEnabled", true, "rightEnabled", true)));
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(inclusive,
                Map.of("leftEnabled", false, "rightEnabled", false)));
    }

    @Test
    @DisplayName("Child mappings and action policies have identical semantics")
    void childMappingsAndActionPoliciesAreEquivalent() {
        ProcessDefinition child = ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.child", childProcess());
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(child, Map.of()));

        ProcessDefinition defaultInput =
                ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.child-default", processCallDefault());
        DifferentialResult defaultResult =
                executeBoth(ProcessEngineTestFactory::builder, engine -> engine.execute(defaultInput, Map.of()));
        assertThat(defaultResult.compiled().getOutput()).containsEntry("defaultWasNull", true);
        assertThat(defaultResult.interpreted().getOutput()).containsEntry("defaultWasNull", true);

        ProcessDefinition policy = ProcessDefinition.classpath(ProcessModelType.TBBPM,
                "bpm.invocation-policy.fullConfiguration", "bpm/invocation-policy/fullConfiguration.bpm");
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(policy, Map.of()));
    }

    @Test
    @DisplayName("Loop-limit failure classifications are identical")
    void loopFailureIsEquivalent() {
        ProcessDefinition failingLoop =
                ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.failing-loop", failingLoop());
        assertFailedEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(failingLoop, Map.of()));
    }

    @Test
    @DisplayName("Effect actions execute in process in both runtime modes")
    void effectActionIsSuccessfulInBothRuntimeModes() {
        ProcessDefinition effect =
                ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.effect", effectProcess());
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(effect, Map.of()));
    }

    @Test
    @DisplayName("TBBPM trigger entries have identical downstream semantics")
    void tbbpmTriggerIsEquivalent() {
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                        "bpm.stateful.waitTaskProcess", "bpm/stateful/waitTaskProcess.bpm"),
                ProcessTrigger.at("waitTask1"), Map.of("taskData", "task")));
    }

    @Test
    @DisplayName("BPMN message catches accept and reject the same event")
    void bpmnMessageCatchIsEquivalent() {
        assertSuccessfulEquivalent(ProcessEngineTestFactory::builder, engine -> engine.trigger(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        "bpmn20.stateful.stateful_receive_task", "bpmn20/stateful/stateful_receive_task.bpmn"),
                ProcessTrigger.on("receiveTask1", "approval.received"), Map.of("taskData", "task")));
        assertFailedEquivalent(ProcessEngineTestFactory::builder, engine -> engine.trigger(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        "bpmn20.stateful.stateful_receive_task", "bpmn20/stateful/stateful_receive_task.bpmn"),
                ProcessTrigger.on("receiveTask1", "approvalMessage"), Map.of("taskData", "task")));
    }

    @Test
    @DisplayName("Declared return order is identical and observable in both runtime modes")
    void declaredReturnOrderIsEquivalent() {
        ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "test.runtime.return-order",
                """
            <bpm code="test.runtime.return-order">
              <var name="second" dataType="java.lang.String" defaultValue="B" inOutType="return"/>
              <var name="first" dataType="java.lang.String" defaultValue="A" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="end"/></start>
              <end id="end" g="80,0,32,32"/>
            </bpm>
            """);

        DifferentialResult result =
                executeBoth(ProcessEngineTestFactory::builder, engine -> engine.execute(definition, Map.of()));

        assertThat(result.compiled().isSuccess()).isTrue();
        assertThat(result.interpreted().isSuccess()).isTrue();
        assertThat(new ArrayList<>(result.compiled().getOutput().keySet())).containsExactly("second", "first");
        assertThat(new ArrayList<>(result.interpreted().getOutput().keySet())).containsExactly("second", "first");
    }

    @Test
    @DisplayName("Interpreter executes compiled Java Code through the same semantic contract")
    void interpretedRuntimeExecutesCompiledJavaCode() {
        ProcessDefinition definition = ProcessDefinition.classpath(ProcessModelType.TBBPM, "bpm.java-code.javaCodeSum",
                "bpm/java-code/javaCodeSum.bpm");
        try (ProcessEngine engine = engine(ProcessEngineTestFactory.builder(), ProcessRuntimeMode.INTERPRETED)) {
            ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of("inputA", 1, "inputB", 2));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("result", 3);
        }
    }

    private static void assertSuccessfulEquivalent(BuilderSupplier builderSupplier,
            Function<ProcessEngine, ProcessResult<Map<String, Object>>> invocation) {
        DifferentialResult result = executeBoth(builderSupplier, invocation);
        assertThat(result.compiled().isSuccess()).as("Compiled execution failed: %s", result.compiled().getError()).isTrue();
        assertThat(result.interpreted().isSuccess())
            .as("Interpreted execution failed: %s", result.interpreted().getError())
            .isTrue();
        assertThat(result.interpreted().getOutput()).containsExactlyInAnyOrderEntriesOf(result.compiled().getOutput());
    }

    @Test
    void failureEquivalenceRejectsTwoSuccessfulExecutions() {
        ProcessDefinition definition = ProcessDefinition.classpath(ProcessModelType.TBBPM, "bpm.java-code.javaCodeSum",
                "bpm/java-code/javaCodeSum.bpm");
        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> assertFailedEquivalent(ProcessEngineTestFactory::builder, engine -> engine.execute(definition,
                    Map.of("inputA", 1, "inputB", 2))))
            .isInstanceOf(AssertionError.class);
    }

    private static void assertFailedEquivalent(BuilderSupplier builderSupplier,
            Function<ProcessEngine, ProcessResult<Map<String, Object>>> invocation) {
        DifferentialResult result = executeBoth(builderSupplier, invocation);
        ProcessResult<Map<String, Object>> compiled = result.compiled();
        ProcessResult<Map<String, Object>> interpreted = result.interpreted();

        assertThat(compiled.isFailure()).as("Compiled execution must fail").isTrue();
        assertThat(interpreted.isFailure()).as("Interpreted execution must fail").isTrue();
        assertThat(interpreted.getError().getCode()).isEqualTo(compiled.getError().getCode());
    }

    private static DifferentialResult executeBoth(BuilderSupplier builderSupplier,
            Function<ProcessEngine, ProcessResult<Map<String, Object>>> invocation) {
        ProcessResult<Map<String, Object>> compiled;
        ProcessResult<Map<String, Object>> interpreted;
        try (ProcessEngine engine = engine(builderSupplier.get(), ProcessRuntimeMode.COMPILED)) {
            compiled = invocation.apply(engine);
        }
        try (ProcessEngine engine = engine(builderSupplier.get(), ProcessRuntimeMode.INTERPRETED)) {
            interpreted = invocation.apply(engine);
        }
        return new DifferentialResult(compiled, interpreted);
    }

    private static ProcessEngine engine(ProcessEngineConfig.Builder builder, ProcessRuntimeMode mode) {
        return ProcessEngineFactory.create(builder.runtimeMode(mode).discoverPlugins(true).build());
    }

    private static String parallelProcess() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpm code="test.runtime.parallel" name="Runtime differential">
                    <var name="a" dataType="java.lang.Integer" inOutType="param"/>
                    <var name="b" dataType="java.lang.Integer" inOutType="param"/>
                    <var name="sum" dataType="java.lang.Integer" inOutType="return"/>
                    <var name="product" dataType="java.lang.Integer" inOutType="return"/>
                    <start id="start" g="0,0,32,32"><transition to="fork"/></start>
                    <parallel id="fork" g="80,0,48,48">
                        <transition to="sumTask"/>
                        <transition to="productTask"/>
                    </parallel>
                    <autoTask id="sumTask" g="180,0,100,48">
                        <action type="java" class="com.alibaba.compileflow.engine.test.support.mocks.MockJavaService"
                                          method="add">
                                <input target="a" dataType="java.lang.Integer" source="a"/>
                                <input target="b" dataType="java.lang.Integer" source="b"/>
                                <output dataType="java.lang.Integer" target="sum"/>

                        </action>
                        <transition to="join"/>
                    </autoTask>
                    <autoTask id="productTask" g="180,80,100,48">
                        <action type="java" class="com.alibaba.compileflow.engine.test.support.mocks.MockJavaService"
                                          method="multiply">
                                <input target="a" dataType="java.lang.Integer" source="a"/>
                                <input target="b" dataType="java.lang.Integer" source="b"/>
                                <output dataType="java.lang.Integer" target="product"/>

                        </action>
                        <transition to="join"/>
                    </autoTask>
                    <parallel id="join" g="340,0,48,48"><transition to="end"/></parallel>
                    <end id="end" g="440,0,32,32"/>
                </bpm>
                """;
    }

    private static String embeddedSubBpm() {
        return """
            <bpm code="test.runtime.embedded-scope">
              <var name="a" dataType="java.lang.Integer" inOutType="param"/>
              <var name="b" dataType="java.lang.Integer" inOutType="param"/>
              <var name="result" dataType="java.lang.Integer" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="calculation"/></start>
              <subBpm id="calculation" g="60,0,180,100">
                <start id="scopeStart" g="0,0,32,32"><transition to="add"/></start>
                <autoTask id="add" g="60,0,100,48">
                  <action type="java" class="com.alibaba.compileflow.engine.test.support.mocks.MockJavaService"
                                  method="add">
                      <input target="a" dataType="java.lang.Integer" source="a"/>
                      <input target="b" dataType="java.lang.Integer" source="b"/>
                      <output dataType="java.lang.Integer"
                           target="result"/>

                  </action>
                  <transition to="scopeEnd"/>
                </autoTask>
                <end id="scopeEnd" g="180,0,32,32"/>
                <transition to="end"/>
              </subBpm>
              <end id="end" g="300,0,32,32"/>
            </bpm>
            """;
    }

    private static String decisionProcess() {
        return """
            <bpm code="test.runtime.decision">
              <var name="value" dataType="java.lang.Integer" inOutType="param"/>
              <var name="route" dataType="java.lang.String" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="choice"/></start>
              <exclusive id="choice" g="80,0,48,48">
                <transition to="low" condition="value &gt; 0"/>
                <transition to="high" condition="value &gt;= 10"/>
                <transition to="fallback"/>
              </exclusive>
              %s
              %s
              %s
              <end id="end" g="360,0,32,32"/>
            </bpm>
            """
            .formatted(labelTask("low", "low", "end"), labelTask("high", "high", "end"),
                    labelTask("fallback", "fallback", "end"));
    }

    private static String inclusiveProcess() {
        return """
            <bpm code="test.runtime.inclusive">
              <var name="leftEnabled" dataType="java.lang.Boolean" inOutType="param"/>
              <var name="rightEnabled" dataType="java.lang.Boolean" inOutType="param"/>
              <var name="left" dataType="java.lang.String" defaultValue="" inOutType="return"/>
              <var name="right" dataType="java.lang.String" defaultValue="" inOutType="return"/>
              <var name="fallback" dataType="java.lang.String" defaultValue="" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <inclusive id="fork" g="80,0,48,48">
                <transition to="leftTask" condition="leftEnabled"/>
                <transition to="rightTask" condition="rightEnabled"/>
                <transition to="fallbackTask"/>
              </inclusive>
              %s
              %s
              %s
              <inclusive id="join" g="320,0,48,48"><transition to="end"/></inclusive>
              <end id="end" g="420,0,32,32"/>
            </bpm>
            """
            .formatted(concurrentLabelTask("leftTask", "left", "left", "join"),
                    concurrentLabelTask("rightTask", "right", "right", "join"),
                    concurrentLabelTask("fallbackTask", "fallback", "fallback", "join"));
    }

    private static String labelTask(String id, String value, String target) {
        return """
            <autoTask id="%s" g="180,0,100,40">
              <action type="java" class="%s" method="processStep">
                <input target="value" dataType="java.lang.String" defaultValue="%s"/>
                <output dataType="java.lang.String" target="route"/>
              </action>
              <transition to="%s"/>
            </autoTask>
            """
            .formatted(id, MockJavaService.class.getName(), value, target);
    }

    private static String concurrentLabelTask(String id, String value, String output, String target) {
        return """
            <autoTask id="%s" g="180,0,100,40">
              <action type="java" class="%s" method="processStep">
                <input target="value" dataType="java.lang.String" defaultValue="%s"/>
                <output dataType="java.lang.String" target="%s"/>
              </action>
              <transition to="%s"/>
            </autoTask>
            """
            .formatted(id, MockJavaService.class.getName(), value, output, target);
    }

    private static String childProcess() {
        return """
            <bpm code="test.runtime.child">
              <var name="childResult" dataType="java.lang.String" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="child"/></start>
              <bpmCall id="child" code="bpm.invocation-policy.fullConfiguration"
                      classpath="bpm/invocation-policy/fullConfiguration.bpm" g="80,0,120,48">
                <output source="result" target="childResult"/>
                <transition to="end"/>
              </bpmCall>
              <end id="end" g="240,0,32,32"/>
            </bpm>
            """;
    }

    private static String processCallDefault() {
        return """
            <bpm code="test.runtime.child-default">
              <var name="defaultWasNull" dataType="java.lang.Boolean" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="child"/></start>
              <bpmCall id="child" code="bpm.subprocess.defaultInputChild"
                      classpath="bpm/subprocess/defaultInputChild.bpm" g="80,0,120,48">
                <input target="value" defaultValue=" "/>
                <output source="wasNull" target="defaultWasNull"/>
                <transition to="end"/>
              </bpmCall>
              <end id="end" g="240,0,32,32"/>
            </bpm>
            """;
    }

    private static String failingLoop() {
        return """
            <bpm code="test.runtime.failing-loop">
              <var name="count" dataType="java.lang.Integer" defaultValue="0" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="loop"/></start>
              <while id="loop" condition="true" maxIterations="2" g="60,0,220,120">
                <transition to="end"/>
                <start id="loopStart"><transition to="increment"/></start>
                <autoTask id="increment" g="90,20,100,40">
                  <action type="java" class="%s" method="add">
                    <input target="a" dataType="java.lang.Integer" source="count"/>
                    <input target="b" dataType="java.lang.Integer" defaultValue="1"/>
                    <output dataType="java.lang.Integer" target="count"/>
                  </action>
                  <transition to="loopEnd"/>
                </autoTask>
                <end id="loopEnd"/>
              </while>
              <end id="end" g="320,0,32,32"/>
            </bpm>
            """
            .formatted(MockJavaService.class.getName());
    }

    private static String embeddedLoopControl() {
        return """
            <bpm code="test.runtime.embedded-loop-control">
              <var name="items" dataType="java.util.List&lt;java.lang.Integer&gt;" inOutType="param"/>
              <var name="count" dataType="java.lang.Integer" defaultValue="0" inOutType="return"/>
              <start id="start"><transition to="loop"/></start>
              <foreach id="loop" collection="items" item="item"
                           itemType="java.lang.Integer">
                <start id="loopStart"><transition to="scope"/></start>
                <subBpm id="scope">
                  <start id="scopeStart"><transition to="increment"/></start>
                  <autoTask id="increment">
                    <action type="java" class="%s" method="add">
                      <input target="a" dataType="java.lang.Integer" source="count"/>
                      <input target="b" dataType="java.lang.Integer" defaultValue="1"/>
                      <output dataType="java.lang.Integer" target="count"/>
                    </action>
                    <transition to="stop"/>
                  </autoTask>
                  <break id="stop" condition="item == 2"><transition to="scopeEnd"/></break>
                  <end id="scopeEnd"/>
                  <transition to="loopEnd"/>
                </subBpm>
                <end id="loopEnd"/>
                <transition to="end"/>
              </foreach>
              <end id="end"/>
            </bpm>
            """
            .formatted(MockJavaService.class.getName());
    }

    private static String bpmnAfterLoop() {
        return """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:cf="http://www.compileflow.org"
                         expressionLanguage="urn:compileflow:java"
                         targetNamespace="urn:compileflow:test">
              <process id="test.runtime.bpmn-after-loop" isExecutable="true">
                <extensionElements>
                  <cf:var name="executions" dataType="java.lang.Integer" defaultValue="0" inOutType="return"/>
                </extensionElements>
                <startEvent id="start"/>
                <serviceTask id="task">
                  <extensionElements><cf:action type="java" class="%s" method="add">
                    <cf:input target="a" dataType="java.lang.Integer" source="executions"/>
                    <cf:input target="b" dataType="java.lang.Integer" defaultValue="1"/>
                    <cf:output dataType="java.lang.Integer" target="executions"/>
                  </cf:action></extensionElements>
                  <standardLoopCharacteristics testBefore="false" loopMaximum="3">
                    <loopCondition>executions &lt; 2</loopCondition>
                  </standardLoopCharacteristics>
                </serviceTask>
                <endEvent id="end"/>
                <sequenceFlow id="to_task" sourceRef="start" targetRef="task"/>
                <sequenceFlow id="to_end" sourceRef="task" targetRef="end"/>
              </process>
            </definitions>
            """
            .formatted(MockJavaService.class.getName());
    }

    private static String effectProcess() {
        return """
            <bpm code="test.runtime.effect">
              <start id="start" g="0,0,32,32"><transition to="effect"/></start>
              <autoTask id="effect" g="80,0,100,40">
                <action type="java" execution="effect" class="%s" method="logWaitPaymentTask"/>
                <transition to="end"/>
              </autoTask>
              <end id="end" g="220,0,32,32"/>
            </bpm>
            """
            .formatted(MockJavaService.class.getName());
    }

    @FunctionalInterface
    private interface BuilderSupplier {
        ProcessEngineConfig.Builder get();
    }

    private record DifferentialResult(ProcessResult<Map<String, Object>> compiled,
            ProcessResult<Map<String, Object>> interpreted) {}
}
