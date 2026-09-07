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
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.core.java.compiler.JdkJavaCompiler;
import com.alibaba.compileflow.engine.core.runtime.CompiledProcessRuntimeFactory;
import com.alibaba.compileflow.engine.core.runtime.InterpretedProcessRuntimeFactory;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeFactory;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.bpmn.semantic.BpmnSemanticFrontend;
import com.alibaba.compileflow.engine.bpmn.parser.BpmnXmlParser;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import com.alibaba.compileflow.engine.tbbpm.semantic.TbbpmSemanticFrontend;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CrossFormatSemanticGoldenTest {
    @Test
    void oneEngineExecutesBothFrontendsSequentiallyAndConcurrentlyInEveryRuntimeMode() throws Exception {
        ProcessDefinition tbbpm =
                ProcessDefinition.inline(ProcessModelType.TBBPM, "engine.multi-frontend", EXECUTABLE_TBBPM);
        ProcessDefinition bpmn =
                ProcessDefinition.inline(ProcessModelType.BPMN, "engine.multi-frontend", EXECUTABLE_BPMN);
        Map<String, Object> input = Map.of();

        for (ProcessRuntimeMode mode : ProcessRuntimeMode.values()) {
            ProcessEngineConfig config = ProcessEngineConfig.builder().runtimeMode(mode).build();
            try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
                assertSuccessful(engine.execute(tbbpm, input));
                assertSuccessful(engine.execute(bpmn, input));

                ExecutorService callers = Executors.newFixedThreadPool(4);
                try {
                    List<Callable<ProcessResult<Map<String, Object>>>> tasks = new ArrayList<>();
                    for (int invocation = 0; invocation < 16; invocation++) {
                        ProcessDefinition definition = invocation % 2 == 0 ? tbbpm : bpmn;
                        tasks.add(() -> engine.execute(definition, input));
                    }
                    for (Future<ProcessResult<Map<String, Object>>> result : callers.invokeAll(tasks, 30,
                            TimeUnit.SECONDS)) {
                        assertSuccessful(result.get(5, TimeUnit.SECONDS));
                    }
                } finally {
                    callers.shutdownNow();
                    assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                }
            }
        }
    }

    private static void assertSuccessful(ProcessResult<Map<String, Object>> result) {
        assertThat(result.isSuccess()).as("execution failed: %s", result.getError()).isTrue();
        assertThat(result.getOutput()).isEmpty();
    }

    @Test
    void sameRuntimeFactoriesRealizeBothFrontendsWithoutRecompilingSemantics() {
        ClassLoader loader = getClass().getClassLoader();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (ScriptExecutorRegistry scripts = ScriptExecutorRegistry.from(List.of())) {
            thread.setContextClassLoader(loader);
            JdkJavaCompiler compiler = new JdkJavaCompiler();
            List<ProcessRuntimeFactory> factories = List.of(new CompiledProcessRuntimeFactory(scripts, compiler,
                            JavaDiagnosticsConfig.defaults()),
                    new InterpretedProcessRuntimeFactory(compiler, JavaDiagnosticsConfig.defaults(),
                            ProcessComponentResolver.disabled(), scripts));
            Map<ProcessModelType, String> sources = Map.of(ProcessModelType.TBBPM,
                    """
                <bpm code="runtime.cross-format">
                  <start id="start"><transition to="end"/></start>
                  <end id="end"/>
                </bpm>
                """,
                    ProcessModelType.BPMN,
                    """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="urn:compileflow:test">
                  <process id="runtime.cross-format" isExecutable="true">
                    <startEvent id="start"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="to_end" sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """);
            for (var source : sources.entrySet()) {
                var definition = ProcessDefinitionSnapshot.of(source.getKey(), "default", "runtime.cross-format", null,
                        source.getValue().getBytes(StandardCharsets.UTF_8), "cross-format test");
                var compilation =
                        new com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompilerRegistry(loader)
                    .compile(definition);
                for (ProcessRuntimeFactory factory : factories) {
                    assertThat(factory.createRuntime(compilation, loader).getSemanticPlan()).isSameAs(compilation.semanticPlan());
                }
            }
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test
    void equivalentTbbpmAndBpmnSourcesProduceOneCanonicalProcessMeaning() {
        ProcessSemanticPlan tbbpm =
                new TbbpmSemanticFrontend()
            .compile(TbbpmXmlParser.getInstance().parse(source("cross-format.bpm", TBBPM)));
        ProcessSemanticPlan bpmn =
                new BpmnSemanticFrontend()
            .compile(BpmnXmlParser.getInstance().parse(source("cross-format.bpmn", BPMN)));

        assertThat(bpmn.canonicalForm()).isEqualTo(tbbpm.canonicalForm());
        assertThat(bpmn.getDigest())
            .isEqualTo(tbbpm.getDigest())
            .isEqualTo("4b0412b664b6f91d7d010a8d0bad7ac60ec3c3736c6dce35fde6d335a221c1f7");
        assertThat(bpmn.requireNode("choice").outgoingTransitions())
            .extracting(ProcessSemanticPlan.TransitionPlan::targetId, ProcessSemanticPlan.TransitionPlan::condition,
                    ProcessSemanticPlan.TransitionPlan::defaultFlow)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("accepted", "approved", false),
                    org.assertj.core.groups.Tuple.tuple("rejected", null, true));
    }

    @Test
    void timerSyntaxNormalizesToOneTimerSemantic() {
        ProcessSemanticPlan tbbpm = compileTbbpm(
                """
            <bpm code="semantic.cross-format.timer">
              <start id="start"><transition to="boundary"/></start>
              <timerTask id="boundary" duration="PT5M" g="80,0,100,40">
                <transition to="end"/>
              </timerTask>
              <end id="end"/>
            </bpm>
            """);
        ProcessSemanticPlan bpmn = compileBpmn(
                """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="urn:compileflow:test">
              <process id="semantic.cross-format.timer" isExecutable="true">
                <startEvent id="start"/>
                <intermediateCatchEvent id="boundary">
                  <timerEventDefinition><timeDuration>PT5M</timeDuration></timerEventDefinition>
                </intermediateCatchEvent>
                <endEvent id="end"/>
                <sequenceFlow id="to_boundary" sourceRef="start" targetRef="boundary"/>
                <sequenceFlow id="to_end" sourceRef="boundary" targetRef="end"/>
              </process>
            </definitions>
            """);

        assertThat(bpmn.requireNode("boundary").operation())
            .isEqualTo(tbbpm.requireNode("boundary").operation())
            .isEqualTo(new TimerPlan(TimerPlan.Kind.DURATION_LITERAL, "PT5M"));
    }

    @Test
    void processCallSyntaxNormalizesToOneCallSemantic() {
        ProcessSemanticPlan tbbpm = compileTbbpm(
                """
            <bpm code="semantic.cross-format.call">
              <start id="start"><transition to="child"/></start>
              <bpmCall id="child" code="child.process" version="v1" g="80,0,100,40">
                <transition to="end"/>
              </bpmCall>
              <end id="end"/>
            </bpm>
            """);
        ProcessSemanticPlan bpmn = compileBpmn(
                """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:cf="http://www.compileflow.org"
                         targetNamespace="urn:compileflow:test">
              <process id="semantic.cross-format.call" isExecutable="true">
                <startEvent id="start"/>
                <callActivity id="child" calledElement="child.process" cf:version="v1"/>
                <endEvent id="end"/>
                <sequenceFlow id="to_child" sourceRef="start" targetRef="child"/>
                <sequenceFlow id="to_end" sourceRef="child" targetRef="end"/>
              </process>
            </definitions>
            """);

        assertThat(bpmn.requireNode("child").operation())
            .isInstanceOf(ProcessCallPlan.class)
            .isEqualTo(tbbpm.requireNode("child").operation());
    }

    @Test
    void parallelIterationSyntaxNormalizesToOneIterationSemantic() {
        ProcessSemanticPlan tbbpm = compileTbbpm(
                """
            <bpm code="semantic.cross-format.iteration">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <start id="start"><transition to="activity"/></start>
              <foreach id="activity" execution="parallel" collection="items" item="item"
                       itemType="java.lang.String" index="index" g="80,0,100,40">
                <transition to="end"/>
                <start id="body_start"><transition to="body_end"/></start>
                <end id="body_end"/>
              </foreach>
              <end id="end"/>
            </bpm>
            """);
        ProcessSemanticPlan bpmn = compileBpmn(
                """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:cf="http://www.compileflow.org"
                         targetNamespace="urn:compileflow:test">
              <process id="semantic.cross-format.iteration" isExecutable="true">
                <extensionElements>
                  <cf:var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
                </extensionElements>
                <startEvent id="start"/>
                <serviceTask id="activity">
                  <multiInstanceLoopCharacteristics isSequential="false" cf:collection="items"
                      cf:item="item" cf:itemType="java.lang.String" cf:index="index"/>
                </serviceTask>
                <endEvent id="end"/>
                <sequenceFlow id="to_activity" sourceRef="start" targetRef="activity"/>
                <sequenceFlow id="to_end" sourceRef="activity" targetRef="end"/>
              </process>
            </definitions>
            """);

        assertThat(bpmn.requireNode("activity").iteration())
            .isInstanceOf(IterationPlan.ForEach.class)
            .isEqualTo(tbbpm.requireNode("activity").iteration());
    }

    private static ProcessSemanticPlan compileTbbpm(String value) {
        return new TbbpmSemanticFrontend()
            .compile(TbbpmXmlParser.getInstance().parse(source("cross-format.bpm", value)));
    }

    private static ProcessSemanticPlan compileBpmn(String value) {
        return new BpmnSemanticFrontend()
            .compile(BpmnXmlParser.getInstance().parse(source("cross-format.bpmn", value)));
    }

    private static FlowSource source(String name, String value) {
        return FlowSource.of(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static final String TBBPM =
            """
        <bpm code="semantic.cross-format" name="Cross-format semantic golden">
          <var name="approved" dataType="java.lang.Boolean" inOutType="param"/>
          <var name="result" dataType="java.lang.Integer" defaultValue="0" inOutType="return"/>
          <start id="start" g="0,0,32,32"><transition to="calculate"/></start>
          <autoTask id="calculate" g="80,0,100,40">
            <action type="java" class="java.lang.Math" method="abs">
                <input target="value" dataType="java.lang.Integer" defaultValue="-7"/>
                <output dataType="java.lang.Integer" target="result"/>

            </action>
            <transition to="choice"/>
          </autoTask>
          <exclusive id="choice" g="220,0,48,48">
            <transition to="accepted" condition="approved"/>
            <transition to="rejected"/>
          </exclusive>
          <end id="accepted" g="340,0,32,32"/>
          <end id="rejected" g="340,80,32,32"/>
        </bpm>
        """;
    private static final String EXECUTABLE_TBBPM =
            """
        <bpm code="engine.multi-frontend">
          <start id="start"><transition to="end"/></start>
          <end id="end"/>
        </bpm>
        """;
    private static final String EXECUTABLE_BPMN =
            """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="urn:compileflow:test">
          <process id="engine.multi-frontend" isExecutable="true">
            <startEvent id="start"/>
            <endEvent id="end"/>
            <sequenceFlow id="to_end" sourceRef="start" targetRef="end"/>
          </process>
        </definitions>
        """;
    private static final String BPMN =
            """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:cf="http://www.compileflow.org"
                     targetNamespace="urn:compileflow:test">
          <process id="semantic.cross-format" name="Cross-format semantic golden" isExecutable="true">
            <extensionElements>
              <cf:var name="approved" dataType="java.lang.Boolean" inOutType="param"/>
              <cf:var name="result" dataType="java.lang.Integer" defaultValue="0" inOutType="return"/>
            </extensionElements>
            <startEvent id="start"/>
            <serviceTask id="calculate">
              <extensionElements>
                <cf:action type="java" class="java.lang.Math" method="abs">
                    <cf:input target="value" dataType="java.lang.Integer" defaultValue="-7"/>
                    <cf:output dataType="java.lang.Integer" target="result"/>

                </cf:action>
              </extensionElements>
            </serviceTask>
            <exclusiveGateway id="choice" default="to_rejected"/>
            <endEvent id="accepted"/>
            <endEvent id="rejected"/>
            <sequenceFlow id="to_calculate" sourceRef="start" targetRef="calculate"/>
            <sequenceFlow id="to_choice" sourceRef="calculate" targetRef="choice"/>
            <sequenceFlow id="to_accepted" sourceRef="choice" targetRef="accepted">
              <conditionExpression xsi:type="tFormalExpression" language="java">approved</conditionExpression>
            </sequenceFlow>
            <sequenceFlow id="to_rejected" sourceRef="choice" targetRef="rejected"/>
          </process>
        </definitions>
        """;
}
