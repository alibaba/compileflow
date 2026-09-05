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
package com.alibaba.compileflow.durable.runtime.program;

import static com.alibaba.compileflow.durable.runtime.kernel.DurableProgramTestSupport.advanceStart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.runtime.machine.DurableModelEligibilityException;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.runtime.script.JavaSourceScriptExecutor;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TbbpmDurableScriptTaskTest {
    private TbbpmModel parse(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("durable-script", xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void arbitraryScriptLanguageUsesTheSameProgramPath() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        String xml = scriptFlow();
        CompiledMachineProgram compiled = compile(xml, executor);

        FrontierStepResult.Completed completed = (FrontierStepResult.Completed) advanceStart(compiled.program(),
                Map.of("value", 3, "result", 0), context(compiled, executor));

        assertThat(completed.output()).containsEntry("result", 5);
        assertThat(executor.validated).isEqualTo("value + 2");
        assertThat(executor.context).containsExactly(Map.entry("value", 3));
        assertThat(compiled.machinePlan().semanticPlan().getNodes())
            .extractingByKey("calculate")
            .extracting(node -> node.operation())
            .isInstanceOf(com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan.class);
        assertThat(source(xml, executor))
            .contains("context.invokeReplayable(\n                  \"calculate\"")
            .doesNotContain("getScriptExecutor");
    }

    @Test
    void providerIdentityIsRuntimeOnly() throws Exception {
        String xml = scriptFlow();
        assertThat(source(xml, new ReplacementExecutor())).isEqualTo(source(xml, new RecordingExecutor()));
    }

    @Test
    void missingCurrentProviderFailsCompilationWithoutBecomingProcessIdentity() {
        assertThatThrownBy(() -> DurableCompilerTestSupport.lower(parse(scriptFlow())))
            .isInstanceOf(DurableModelEligibilityException.class)
            .hasMessageContaining("DURABLE_SCRIPT_EXECUTOR_NOT_REGISTERED");
    }

    @Test
    void javaScriptUsesTheSameTypedCompilationPath() throws Exception {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        CompiledMachineProgram compiled = compile(javaScriptFlow(), executor);

        FrontierStepResult.Completed completed = (FrontierStepResult.Completed) advanceStart(compiled.program(),
                Map.of("value", 3, "result", 0), context(compiled, executor));

        assertThat(completed.output()).containsEntry("result", 5);
    }

    private CompiledMachineProgram compile(String xml, ScriptExecutor executor) {
        ScriptExecutorRegistry registry = ScriptExecutorRegistry.from(List.of(executor));
        return DurableCompilerTestSupport.compile(parse(xml), registry, getClass().getClassLoader());
    }

    private String source(String xml, ScriptExecutor executor) {
        ScriptExecutorRegistry registry = ScriptExecutorRegistry.from(List.of(executor));
        var machinePlan = DurableCompilerTestSupport.lower(parse(xml), registry);
        return new DurableJavaProgramCompiler().generateSource(machinePlan);
    }

    private DurableExecutionContext context(CompiledMachineProgram compiled, ScriptExecutor executor) {
        ScriptExecutorRegistry registry = ScriptExecutorRegistry.from(List.of(executor));
        return new DurableExecutionContext(compiled.machinePlan(),
                new DurableActionInvoker(ProcessComponentResolver.disabled(), registry, getClass().getClassLoader())
                    .withScriptPrograms(compiled.scriptPrograms()), DurableWaitDescriptionProvider.defaults(),
                new DurableValueSerializer(compiled.machinePlan()));
    }

    private String scriptFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="durable.script" name="Durable Script">
                <var name="value" dataType="java.lang.Integer" inOutType="param"/>
                <var name="result" dataType="java.lang.Integer" inOutType="return"/>
                <start id="start" g="0,0,32,32"><transition to="calculate"/></start>
                <scriptTask id="calculate" g="80,0,100,40">
                    <action type="script" execution="replayable" language="math-v1">
                        <input target="value" dataType="java.lang.Integer" source="value"/>
                        <output dataType="java.lang.Integer" target="result"/>
                        <code>value + 2</code>

                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" g="220,0,32,32"/>
            </bpm>
            """;
    }

    private String javaScriptFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="durable.java-script" name="Durable Java Script">
                <var name="value" dataType="java.lang.Integer" inOutType="param"/>
                <var name="result" dataType="java.lang.Integer" inOutType="return"/>
                <start id="start" g="0,0,32,32"><transition to="calculate"/></start>
                <scriptTask id="calculate" g="80,0,100,40">
                    <action type="script" execution="replayable" language="java">
                            <input target="value" dataType="java.lang.Integer"
                                 source="value"/>
                            <output dataType="java.lang.Integer"
                                 target="result"/>
                            <code>return value + 2;</code>

                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" g="220,0,32,32"/>
            </bpm>
            """;
    }

    private static class RecordingExecutor implements ScriptExecutor {
        private String validated;
        private Map<String, Object> context;

        @Override
        public String name() {
            return "math-v1";
        }

        @Override
        public void validate(ScriptProgramSpec spec) {
            validated = spec.source();
        }

        @Override
        public ScriptProgram compile(ScriptProgramSpec spec) {
            return new TestProgram(spec.source());
        }

        @Override
        public Object evaluate(ScriptProgram script, Map<String, Object> context) {
            this.context = context;
            return ((Number) context.get("value")).intValue() + 2;
        }
    }

    private record TestProgram(String source) implements ScriptProgram {
        @Override
        public String language() {
            return "math-v1";
        }
    }

    private static final class ReplacementExecutor extends RecordingExecutor {
    }
}
