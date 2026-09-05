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
package com.alibaba.compileflow.engine.core.runtime.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.java.compiler.JavaCompileOptions;
import com.alibaba.compileflow.engine.core.java.compiler.JavaSource;
import com.alibaba.compileflow.engine.core.java.compiler.JdkJavaCompiler;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScriptExecutorRegistryTest {
    @Test
    void getScriptExecutorUsesExactCanonicalNames() {
        NamedExecutor executor = new NamedExecutor("mvel");
        ScriptExecutorRegistry registry = ScriptExecutorRegistry.from(List.of(executor));

        assertThat(registry.getScriptExecutor("mvel")).isSameAs(executor);
        assertThatThrownBy(() -> registry.getScriptExecutor(" MvEl "))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("surrounding whitespace");
    }

    @Test
    void fromExecutorsRejectsNonCanonicalLanguageNames() {
        NamedExecutor first = new NamedExecutor("groovy");
        NamedExecutor second = new NamedExecutor("Groovy");

        assertThatThrownBy(() -> ScriptExecutorRegistry.from(List.of(first, second)))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("lowercase kebab-case");
    }

    @Test
    void fromExecutorsRejectsBlankExecutorName() {
        assertThatThrownBy(() -> ScriptExecutorRegistry.from(List.of(new NamedExecutor(" "))))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("must not be blank");
    }

    @Test
    void getScriptExecutorRejectsBlankLookupName() {
        ScriptExecutorRegistry registry = ScriptExecutorRegistry.builtIns(ProcessEngineConfig.tbbpm());

        assertThatThrownBy(() -> registry.getScriptExecutor("\t"))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("must not be blank");
    }

    @Test
    void fromExecutorsRejectsNullExecutor() {
        assertThatNullPointerException()
            .isThrownBy(() -> ScriptExecutorRegistry.from(Arrays.asList((ScriptExecutor) null)))
            .withMessage("script executor must not be null");
    }

    @Test
    void configuredRegistryRejectsNullExecutorCollection() {
        assertThatNullPointerException()
            .isThrownBy(() -> ScriptExecutorRegistry.configured(ProcessEngineConfig.tbbpm(), null))
            .withMessage("script executors must not be null");
    }

    @Test
    void getScriptExecutorCompilesFromGeneratedScriptInvocation() throws Exception {
        String className = "com.alibaba.compileflow.generated.ScriptExecutorRegistryProbe";
        String source =
                "package com.alibaba.compileflow.generated;\n"
                + "import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;\n"
                + "import java.util.HashMap;\n" + "import java.util.Map;\n"
                + "public class ScriptExecutorRegistryProbe {\n" + "    public Object execute() {\n"
                + "        Map<String, Object> _ScriptContext = new HashMap<>();\n"
                + "        return EngineExecutionContextHolder.evaluateScript(\n"
                + "                new com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec(\"mvel\",\n"
                + "                        \"base + delta\", java.util.List.of(), null), _ScriptContext);\n" + "    }\n"
                + "}\n";
        JavaCompileOptions options =
                new JavaCompileOptions(JavaDiagnosticsConfig.DebugSymbols.LINES, getClass().getClassLoader());
        Map<String, byte[]> classes = new HashMap<>();

        new JdkJavaCompiler()
            .compile(JavaSource.of(source, className), (name, bytes) -> classes.put(name, bytes.clone()), options);

        assertThat(classes).containsKey(className);
    }

    @Test
    void configuredRegistryRejectsSilentReplacementOfBundledLanguage() {
        NamedExecutor duplicate = new NamedExecutor("qlexpress");

        assertThatThrownBy(() -> ScriptExecutorRegistry.configured(ProcessEngineConfig
                    .tbbpmBuilder()
                    .discoverPlugins(false)
                    .build(), List.of(duplicate)))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Duplicate script executors for language 'qlexpress'");
        assertThat(duplicate.closed).isFalse();
    }

    @Test
    void configuredRegistryAlwaysRejectsCustomQlBecauseQlIsABuiltInLanguage() {
        NamedExecutor customQl = new NamedExecutor("qlexpress");
        assertThatThrownBy(() -> ScriptExecutorRegistry.configured(ProcessEngineConfig.tbbpm(), List.of(customQl)))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Duplicate script executors for language 'qlexpress'");
        assertThat(customQl.closed).isFalse();
    }

    @Test
    void configuredRegistryRejectsReplacementOfBuiltInJava() {
        NamedExecutor java = new NamedExecutor("java");

        assertThatThrownBy(() -> ScriptExecutorRegistry.configured(ProcessEngineConfig.tbbpm(), List.of(java)))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Duplicate script executors for language 'java'");
    }

    @Test
    void builtInRegistriesDoNotShareStatefulExecutors() {
        ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder().build();

        ScriptExecutorRegistry first = ScriptExecutorRegistry.builtIns(config);
        ScriptExecutorRegistry second = ScriptExecutorRegistry.builtIns(config);

        assertThat(first.getScriptExecutor("qlexpress")).isNotSameAs(second.getScriptExecutor("qlexpress"));
    }

    @Test
    void builtInLanguagesAreAlwaysRegistered() {
        assertThat(ScriptExecutorRegistry.builtIns(ProcessEngineConfig.tbbpm()).getLanguageNames())
            .containsExactly("qlexpress", "java");
    }

    @Test
    void preparationSignaturePreventsReuseAcrossDifferentTypedInputContracts() {
        CountingExecutor executor = new CountingExecutor("counting");
        ScriptExecutorRegistry registry = ScriptExecutorRegistry.from(List.of(executor));
        ScriptProgramSpec integerInput = new ScriptProgramSpec("counting", "value + 1",
                List.of(new ScriptProgramSpec.Input("value", "java.lang.Integer")), "java.lang.Integer");
        ScriptProgramSpec longInput = new ScriptProgramSpec("counting", "value + 1",
                List.of(new ScriptProgramSpec.Input("value", "java.lang.Long")), "java.lang.Long");

        registry.compile(integerInput);
        registry.compile(longInput);

        assertThat(executor.validateCalls).isZero();
        assertThat(executor.prepareCalls).isEqualTo(2);
    }

    @Test
    void scriptProgramCatalogProcessesDuplicateSpecsExactlyOnce() {
        CountingExecutor executor = new CountingExecutor("counting");
        ScriptExecutorRegistry registry = ScriptExecutorRegistry.from(List.of(executor));
        ActionPlan script = new ActionPlan(ActionExecution.REPLAYABLE,
                new ActionInvocation.Script("counting", "value + 1"),
                List.of(ActionPlan.Input.literal("1", "value", Integer.class.getName())), null,
                EffectiveInvocationPolicy.defaults(), null);
        ProcessSemanticPlan.NodePlan first = new ProcessSemanticPlan.NodePlan("first",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, script,
                null);
        ProcessSemanticPlan.NodePlan second = new ProcessSemanticPlan.NodePlan("second",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, script,
                null);
        ProcessSemanticPlan plan =
                new ProcessSemanticPlan("duplicate.scripts", Map.of(), Map.of(first.id(), first, second.id(), second));

        ScriptProgramCatalog.validate(plan, registry);
        Map<ScriptProgramSpec, ScriptProgram> programs = ScriptProgramCatalog.compile(plan, registry);

        assertThat(executor.validateCalls).isOne();
        assertThat(executor.prepareCalls).isOne();
        assertThat(programs).hasSize(1);
    }

    private static class NamedExecutor implements ScriptExecutor, AutoCloseable {
        private final String name;
        private boolean closed;

        private NamedExecutor(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void validate(ScriptProgramSpec spec) {}

        @Override
        public ScriptProgram compile(ScriptProgramSpec spec) {
            return new NamedProgram(ScriptExecutor.requireCanonicalName(name), spec.source());
        }

        @Override
        public Object evaluate(ScriptProgram script, Map<String, Object> context) {
            return ((NamedProgram) script).source();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private record NamedProgram(String language, String source) implements ScriptProgram {}

    private static final class CountingExecutor extends NamedExecutor {
        private int prepareCalls;
        private int validateCalls;
        private int evaluateCalls;

        private CountingExecutor(String name) {
            super(name);
        }

        @Override
        public ScriptProgram compile(ScriptProgramSpec spec) {
            prepareCalls++;
            return super.compile(spec);
        }

        @Override
        public void validate(ScriptProgramSpec spec) {
            validateCalls++;
        }

        @Override
        public Object evaluate(ScriptProgram script, Map<String, Object> context) {
            evaluateCalls++;
            return super.evaluate(script, context);
        }
    }
}
