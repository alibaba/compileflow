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
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptException;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ScriptExecutorExtensionIntegrationTest {
    private static final String EXPRESSION = "concat(\"hello\", \"C:\\temp\")";
    private static final String FLOW =
            """
        <?xml version="1.0" encoding="UTF-8" ?>
        <bpm code="test.custom-script" name="Custom Script">
            <var name="result" dataType="java.lang.String" inOutType="return"/>
            <start id="start" name="Start" g="50,50,32,32">
                <transition g=":-15,20" to="script"/>
            </start>
            <scriptTask id="script" name="Custom Script" g="140,42,120,48">
                <transition g=":-15,20" to="end"/>
                <action type="script" language="custom-script">
                        <output dataType="java.lang.String"
                             target="result"/>
                        <code>concat(&quot;hello&quot;, &quot;C:\\temp&quot;)</code>

                </action>
            </scriptTask>
            <end id="end" name="End" g="320,50,32,32"/>
        </bpm>
        """;
    private static final String BOUNDARY_FLOW =
            """
        <?xml version="1.0" encoding="UTF-8" ?>
        <bpm code="test.boundary-script" name="Boundary Script">
            <start id="start" g="0,0,32,32">
                <transition to="beforeWait"/>
            </start>
            <scriptTask id="beforeWait" g="60,0,100,40">
                <action type="script" language="custom-script"><code>boundary-in</code>
                </action>
                <transition to="wait"/>
            </scriptTask>
            <waitEventTask id="wait" event="done" g="80,0,100,40">
                <transition to="afterWait"/>
            </waitEventTask>
            <scriptTask id="afterWait" g="180,0,100,40">
                <action type="script" language="custom-script"><code>boundary-out</code>
                </action>
                <transition to="end"/>
            </scriptTask>
            <end id="end" g="300,0,32,32"/>
        </bpm>
        """;
    private static final String BPMN_FLOW =
            """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:cf="http://www.compileflow.org"
                     targetNamespace="http://www.compileflow.org/bpmn/test">
            <process id="test.custom-script-bpmn" name="Custom Script" isExecutable="true">
                <extensionElements>
                    <cf:var name="result" dataType="java.lang.String" inOutType="return"/>
                </extensionElements>
                <startEvent id="start"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="script"/>
                <scriptTask id="script" name="Custom Script" scriptFormat="custom-script">
                    <extensionElements>
                        <cf:output dataType="java.lang.String"
                                target="result"/>
                    </extensionElements>
                    <script><![CDATA[concat("hello", "C:\\temp")]]></script>
                </scriptTask>
                <sequenceFlow id="flow2" sourceRef="script" targetRef="end"/>
                <endEvent id="end"/>
            </process>
        </definitions>
        """;
    private static final String NUMERIC_FLOW =
            """
        <?xml version="1.0" encoding="UTF-8" ?>
        <bpm code="test.custom-number-script" name="Custom Number Script">
            <var name="result" dataType="long" inOutType="return"/>
            <start id="start" name="Start" g="50,50,32,32">
                <transition g=":-15,20" to="script"/>
            </start>
            <scriptTask id="script" name="Custom Number Script" g="140,42,120,48">
                <transition g=":-15,20" to="end"/>
                <action type="script" language="custom-number">
                        <output dataType="long"
                             target="result"/>
                        <code>number()</code>

                </action>
            </scriptTask>
            <end id="end" name="End" g="320,50,32,32"/>
        </bpm>
        """;

    private static ScriptExecutor customExecutor() {
        return customExecutor(new AtomicInteger());
    }

    private static ScriptExecutor customExecutor(AtomicInteger preparationCount) {
        return TestScriptExecutors.of("custom-script",
                (source, context) -> {
                    assertThat(source).isEqualTo(EXPRESSION);
                    return "custom-ok";
                }, source -> preparationCount.incrementAndGet());
    }

    private static ScriptExecutor rejectingExecutor() {
        return new ScriptExecutor() {
            @Override
            public String name() {
                return "custom-script";
            }

            @Override
            public void validate(ScriptProgramSpec spec) {
                throw new ScriptException(ScriptException.Kind.INVALID_SOURCE, "Rejected by test language");
            }

            @Override
            public ScriptProgram compile(ScriptProgramSpec spec) {
                throw new AssertionError("Invalid source must not be compiled");
            }

            @Override
            public Object evaluate(ScriptProgram script, Map<String, Object> context) {
                throw new AssertionError("Invalid source must not be evaluated");
            }
        };
    }

    @Test
    void registeredLanguageWorksThroughParseCompileAndExecution() {
        AtomicInteger preparationCount = new AtomicInteger();
        ProcessEngineConfig config = ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .scriptExecutor(customExecutor(preparationCount))
            .build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessDefinition definition = ProcessDefinition.inline("test.custom-script", FLOW);
            engine.tooling().generateJavaCode(definition);
            ProcessResult<Map<String, Object>> first = engine.execute(definition, Map.of());
            ProcessResult<Map<String, Object>> second = engine.execute(definition, Map.of());

            assertThat(first.isSuccess()).isTrue();
            assertThat(first.getOutput()).containsEntry("result", "custom-ok");
            assertThat(second.isSuccess()).isTrue();
            assertThat(second.getOutput()).containsEntry("result", "custom-ok");
            assertThat(preparationCount.get()).isEqualTo(1);
        }
    }

    @Test
    void explicitActionsAroundTriggerEntriesUseTheSameScriptExecutorRegistry() {
        AtomicInteger preparationCount = new AtomicInteger();
        ProcessEngineConfig config = ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .scriptExecutor(TestScriptExecutors.of("custom-script", (source, context) -> "custom-ok", source -> preparationCount.incrementAndGet()))
            .build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessDefinition definition = ProcessDefinition.inline("test.boundary-script", BOUNDARY_FLOW);
            ProcessRef.Version ref = ProcessRef.version("default", definition.code(), "v1");

            engine.tooling().generateJavaCode(definition);
            assertThat(preparationCount.get()).isZero();

            engine.runtime().load(ref, definition);
            assertThat(preparationCount.get()).isEqualTo(2);

            ProcessResult<Map<String, Object>> first =
                    engine.trigger(ref, ProcessTrigger.on("wait", "done"), Map.of(), ProcessExecutionOptions.defaults());
            assertThat(first.isSuccess()).isTrue();
            assertThat(preparationCount.get()).isEqualTo(2);

            engine.runtime().unload(ref);
            engine.runtime().load(ref, definition);
            assertThat(preparationCount.get()).isEqualTo(4);

            ProcessResult<Map<String, Object>> second =
                    engine.trigger(ref, ProcessTrigger.on("wait", "done"), Map.of(), ProcessExecutionOptions.defaults());
            assertThat(second.isSuccess()).isTrue();
            assertThat(preparationCount.get()).isEqualTo(4);
        }
    }

    @Test
    void registeredLanguageWorksForBpmnScriptFormat() {
        ProcessEngineConfig config =
                ProcessEngineTestFactory
            .bpmnBuilder()
            .discoverPlugins(false)
            .scriptExecutor(customExecutor())
            .build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessDefinition.inline("test.custom-script-bpmn", BPMN_FLOW), Map.of());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("result", "custom-ok");
        }
    }

    @Test
    void scriptResultsUseTheCanonicalDataTypeConversion() {
        ProcessEngineConfig config = ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .scriptExecutor(TestScriptExecutors.of("custom-number", (source, context) -> Integer.valueOf(7)))
            .build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessDefinition.inline("test.custom-number-script", NUMERIC_FLOW), Map.of());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("result", 7L);
        }
    }

    @Test
    void missingLanguageFailsDuringCodeGeneration() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            assertThatThrownBy(() -> engine
                .tooling()
                .generateJavaCode(ProcessDefinition.inline("test.custom-script", FLOW)))
                .isInstanceOf(CompileFlowException.ConfigurationException.class)
                .hasMessageContaining("No script executor is registered for language 'custom-script'");
        }
    }

    @Test
    void providerValidationFailsDuringCodeGeneration() {
        ProcessEngineConfig config =
                ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .scriptExecutor(rejectingExecutor())
            .build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            assertThatThrownBy(() -> engine
                .tooling()
                .generateJavaCode(ProcessDefinition.inline("test.custom-script", FLOW)))
                .isInstanceOf(ScriptException.class)
                .hasMessage("Rejected by test language");
        }
    }
}
