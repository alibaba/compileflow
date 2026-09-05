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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.java.compiler.GeneratedClassCompiler;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.executable.ExecutableProcess;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import com.alibaba.compileflow.engine.test.support.mocks.AtomicParallelService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ParallelBranchIsolationIntegrationTest {
    private static final String CODE = "test.gateway.atomicParallelCommit";
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([^;]+);");
    private static final Pattern PUBLIC_CLASS =
            Pattern.compile("(?m)^public\\s+final\\s+class\\s+([\\p{javaJavaIdentifierPart}]+)");

    private static int occurrences(String source, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }

    private static String generatedClassName(String source) {
        Matcher packageMatcher = PACKAGE.matcher(source);
        Matcher classMatcher = PUBLIC_CLASS.matcher(source);
        if (!packageMatcher.find() || !classMatcher.find()) {
            throw new IllegalArgumentException("Generated source does not declare a public class");
        }
        return packageMatcher.group(1) + "." + classMatcher.group(1);
    }

    private static String definition() {
        return """
            <bpm code="%s" name="Atomic parallel commit">
                <var name="leftResult"
                     dataType="java.lang.Integer"
                     defaultValue="7"
                     inOutType="return"/>
                <start id="start" g="0,0,32,32">
                    <transition to="fork"/>
                </start>
                <parallel id="fork" g="80,0,48,48">
                    <transition to="writeOutput"/>
                    <transition to="fail"/>
                </parallel>
                <autoTask id="writeOutput" g="180,0,100,48">
                    <action type="java"
                            class="com.alibaba.compileflow.engine.test.support.mocks.AtomicParallelService"
                            method="produceValue">
                            <output
                                 dataType="java.lang.Integer"
                                 target="leftResult"/>
                    </action>
                    <transition to="confirmOutput"/>
                </autoTask>
                <autoTask id="confirmOutput" g="320,0,100,48">
                    <action type="java"
                            class="com.alibaba.compileflow.engine.test.support.mocks.AtomicParallelService"
                            method="confirmOutputWritten">
                            <input target="value"
                                 dataType="java.lang.Integer"
                                 source="leftResult"/>
                    </action>
                    <transition to="join"/>
                </autoTask>
                <autoTask id="fail" g="180,80,100,48">
                    <action type="java"
                            class="com.alibaba.compileflow.engine.test.support.mocks.AtomicParallelService"
                            method="failAfterOutputWritten"/>
                    <transition to="join"/>
                </autoTask>
                <parallel id="join" g="460,0,48,48">
                    <transition to="end"/>
                </parallel>
                <end id="end" g="560,0,32,32"/>
            </bpm>
            """
            .formatted(CODE);
    }

    private static String parallelLoopDefinition(boolean conflictingSiblingOutput) {
        String siblingOutput = conflictingSiblingOutput ? "sum" : "product";
        String processCode =
                conflictingSiblingOutput ? "test.gateway.parallelLoopConflict" : "test.gateway.parallelLoop";
        return """
            <bpm code="%s"
                 name="Parallel branch with serial loop">
                <var name="numbers" dataType="java.util.List&lt;java.lang.Integer&gt;" inOutType="param"/>
                <var name="sum" dataType="java.lang.Integer" inOutType="return" defaultValue="0"/>
                <var name="base" dataType="java.lang.Integer" inOutType="param"/>
                <var name="factor" dataType="java.lang.Integer" inOutType="param"/>
                <var name="product" dataType="java.lang.Integer" inOutType="return"/>
                <start id="start" g="0,0,32,32">
                    <transition to="fork"/>
                </start>
                <parallel id="fork" g="80,0,48,48">
                    <transition to="loop"/>
                    <transition to="sibling"/>
                </parallel>
                <foreach id="loop"
                     collection="numbers"
                     item="item"
                     itemType="java.lang.Integer"
                     g="180,0,260,160">
                    <transition to="join"/>
                    <start id="loopStart"><transition to="add"/></start>
                    <autoTask id="add" g="210,40,100,48">
                        <action type="java"
                                class="com.alibaba.compileflow.engine.test.support.mocks.MockJavaService"
                                method="add">
                                <input target="a"
                                     dataType="java.lang.Integer"
                                     source="sum"/>
                                <input target="b"
                                     dataType="java.lang.Integer"
                                     source="item"/>
                                <output
                                     dataType="java.lang.Integer"
                                     target="sum"/>
                        </action>
                        <transition to="loopEnd"/>
                    </autoTask>
                    <end id="loopEnd"/>
                </foreach>
                <autoTask id="sibling" g="180,200,100,48">
                    <action type="java"
                            class="com.alibaba.compileflow.engine.test.support.mocks.MockJavaService"
                            method="multiply">
                            <input target="a"
                                 dataType="java.lang.Integer"
                                 source="base"/>
                            <input target="b"
                                 dataType="java.lang.Integer"
                                 source="factor"/>
                            <output
                                 dataType="java.lang.Integer"
                                     target="%s"/>
                    </action>
                    <transition to="join"/>
                </autoTask>
                <parallel id="join" g="500,0,48,48">
                    <transition to="end"/>
                </parallel>
                <end id="end" g="600,0,32,32"/>
            </bpm>
            """
            .formatted(processCode, siblingOutput);
    }

    private static String bpmnParallelSubProcessDefinition() {
        return """
            <definitions
                xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                xmlns:cf="http://www.compileflow.org"
                targetNamespace="urn:compileflow:test">
              <process id="test.gateway.bpmnParallelSubProcess"
                       isExecutable="true">
                <extensionElements>
                  <cf:var name="a" dataType="java.lang.Integer"
                          inOutType="param"/>
                  <cf:var name="b" dataType="java.lang.Integer"
                          inOutType="param"/>
                  <cf:var name="sum" dataType="java.lang.Integer"
                          inOutType="return"/>
                  <cf:var name="product" dataType="java.lang.Integer"
                          inOutType="return"/>
                </extensionElements>
                <startEvent id="start"/>
                <sequenceFlow id="toFork"
                              sourceRef="start" targetRef="fork"/>
                <parallelGateway id="fork"/>
                <sequenceFlow id="toSub"
                              sourceRef="fork" targetRef="sub"/>
                <sequenceFlow id="toProduct"
                              sourceRef="fork" targetRef="productTask"/>
                <subProcess id="sub">
                  <startEvent id="subStart"/>
                  <sequenceFlow id="subToSum"
                                sourceRef="subStart" targetRef="sumTask"/>
                  <serviceTask id="sumTask">
                    <extensionElements>
                      <cf:action type="java"
                            class="com.alibaba.compileflow.engine.test.support.mocks.MockJavaService"
                            method="add">
                          <cf:input target="a"
                                  dataType="java.lang.Integer"
                                  source="a"/>
                          <cf:input target="b"
                                  dataType="java.lang.Integer"
                                  source="b"/>
                          <cf:output
                                  dataType="java.lang.Integer"
                                  target="sum"/>
                      </cf:action>
                    </extensionElements>
                  </serviceTask>
                  <sequenceFlow id="sumToSubEnd"
                                sourceRef="sumTask" targetRef="subEnd"/>
                  <endEvent id="subEnd"/>
                </subProcess>
                <sequenceFlow id="subToJoin"
                              sourceRef="sub" targetRef="join"/>
                <serviceTask id="productTask">
                  <extensionElements>
                    <cf:action type="java"
                          class="com.alibaba.compileflow.engine.test.support.mocks.MockJavaService"
                          method="multiply">
                        <cf:input target="a"
                                dataType="java.lang.Integer"
                                source="a"/>
                        <cf:input target="b"
                                dataType="java.lang.Integer"
                                source="b"/>
                        <cf:output
                                dataType="java.lang.Integer"
                                target="product"/>
                    </cf:action>
                  </extensionElements>
                </serviceTask>
                <sequenceFlow id="productToJoin"
                              sourceRef="productTask" targetRef="join"/>
                <parallelGateway id="join"/>
                <sequenceFlow id="toEnd"
                              sourceRef="join" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;
    }

    @Test
    void failedSiblingCannotPublishACompletedBranchOutput() throws Exception {
        String source;
        try (ProcessEngine engine = ProcessEngineTestFactory.createTbbpm()) {
            source = engine.tooling().generateJavaCode(ProcessDefinition.inline(CODE, definition()));
        }
        assertThat(source)
            .contains("for (var _cf$branchResult : _cf$branches.run()) {")
            .contains("this.leftResult = _cf$branchOutcome.branchFrame().leftResult;")
            .doesNotContain("case 1 -> {}", "while (current");
        assertThat(source.indexOf("_cf$branches.run()"))
            .isLessThan(source.indexOf("this.leftResult = _cf$branchOutcome.branchFrame().leftResult;"));

        Class<?> generatedClass = new GeneratedClassCompiler(ProcessEngineTestFactory.javaDiagnosticsConfig())
            .compile(generatedClassName(source), source, getClass().getClassLoader());
        ExecutableProcess executable = (ExecutableProcess) generatedClass.getDeclaredConstructor().newInstance();

        AtomicParallelService.reset();
        ProcessEngineExecutors executors =
                ProcessEngineExecutors.create("parallel-isolation-test", ProcessExecutorConfig.defaults());
        ScriptExecutorRegistry scripts = ScriptExecutorRegistry.builtIns(ProcessEngineTestFactory.tbbpmConfig());
        try {
            EngineExecutionContext context = EngineExecutionContext
                .builder()
                .invocationId("parallel-isolation-invocation")
                .namespace("default")
                .processCode(CODE)
                .modelType(ProcessModelType.TBBPM)
                .sourceDigest("0".repeat(64))
                .processCallInvoker((callGraph, target, variables) -> {
                    throw new AssertionError("Process calls are not expected in this test");
                })
                .executors(executors)
                .componentResolver(ProcessComponentResolver.disabled())
                .scriptExecutors(scripts)
                .build();
            EngineExecutionContextHolder.set(context);

            Throwable failure = catchThrowable(() -> executable.execute(Map.of()));
            assertThat(failure)
                .isInstanceOf(CompileFlowException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("planned branch failure");
            assertThat(((CompileFlowException) failure).getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_001);

            Field leftResult = generatedClass.getDeclaredField("leftResult");
            leftResult.setAccessible(true);
            assertThat(leftResult.get(executable)).as("parent frame must remain unchanged after any branch fails").isEqualTo(
                    7);
        } finally {
            EngineExecutionContextHolder.clear();
            scripts.close();
            executors.close();
        }
    }

    @Test
    void serialLoopInsideParallelBranchExecutesOnItsBranchFrame() throws Exception {
        ProcessDefinition definition =
                ProcessDefinition.inline("test.gateway.parallelLoop", parallelLoopDefinition(false));

        try (ProcessEngine engine = ProcessEngineTestFactory.createTbbpm()) {
            String source = engine.tooling().generateJavaCode(definition);

            assertThat(source).contains("LoopSemantics.<Integer>snapshot(this.numbers, \"loop\", Integer.class)");
            assertThat(occurrences(source, "LoopSemantics.<Integer>snapshot(this.numbers,"))
                .as("the loop body must have one generated owner")
                .isEqualTo(1);

            ProcessResult<Map<String, Object>> result =
                    engine.execute(definition, Map.of("numbers", List.of(1, 2, 3), "base", 4, "factor", 5));

            assertThat(result.isSuccess())
                .as("generated parallel-loop source must compile and run: %s", result.getError())
                .isTrue();
            assertThat(result.getOutput()).containsEntry("sum", 6).containsEntry("product", 20);
        }
    }

    @Test
    void loopEffectsConflictWithSiblingBeforeJavaGeneration() {
        ProcessDefinition definition =
                ProcessDefinition.inline("test.gateway.parallelLoopConflict", parallelLoopDefinition(true));

        try (ProcessEngine engine = ProcessEngineTestFactory.createTbbpm()) {
            assertThatThrownBy(() -> engine.tooling().generateJavaCode(definition))
                .hasMessageContaining("conflicting process-variable access: sum");
        }
    }

    @Test
    void embeddedBpmnSubProcessInsideParallelBranchExecutesOnce() throws Exception {
        ProcessDefinition definition =
                ProcessDefinition.inline("test.gateway.bpmnParallelSubProcess", bpmnParallelSubProcessDefinition());

        try (ProcessEngine engine = ProcessEngineTestFactory.createBpmn()) {
            String source = engine.tooling().generateJavaCode(definition);

            assertThat(occurrences(source, "new MockJavaService().add(this.a, this.b)"))
                .as("the embedded subprocess action must have one owner")
                .isEqualTo(1);

            ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of("a", 3, "b", 4));

            assertThat(result.isSuccess())
                .as("generated BPMN subprocess source must compile and run: %s", result.getError())
                .isTrue();
            assertThat(result.getOutput()).containsEntry("sum", 7).containsEntry("product", 12);
        }
    }
}
