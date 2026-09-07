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
package com.alibaba.compileflow.engine.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.core.DefaultProcessEngine;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.assembly.EngineDependencies;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowAnalyzer;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.RetryJitter;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler.ProcessSemanticCompilation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.NodeKind;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.NodePlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.TransitionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.VariablePlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.VariableRole;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class RuntimeRealizationParityTest {
    @ParameterizedTest
    @EnumSource(ProcessRuntimeMode.class)
    void primitiveForeachItemsAreBoxedForSnapshotValidation(ProcessRuntimeMode mode) {
        Map<String, VariablePlan> variables = Map.of("items",
                new VariablePlan("items", "java.util.List<java.lang.Integer>", VariableRole.PARAM, null), "element",
                new VariablePlan("element", "java.lang.Integer", VariableRole.INNER, null), "results",
                new VariablePlan("results", "java.util.List<java.lang.Integer>", VariableRole.RETURN, null));
        ActionPlan action = new ActionPlan(null, new ActionInvocation.Java(IntIdentity.class.getName(), "apply"),
                List.of(ActionPlan.Input.expression("item", "value", "int")),
                new ActionPlan.Output("element", "java.lang.Integer", "java.lang.Integer"),
                EffectiveInvocationPolicy.defaults(), null);
        IterationPlan.ForEach loop = new IterationPlan.ForEach("items", "item", "int", null,
                IterationPlan.Execution.SEQUENTIAL, "element", "results");

        assertThat(execute(mode, plan(variables, action, loop), Map.of("items", List.of(3, 1, 2))))
            .containsEntry("results", List.of(3, 1, 2));
    }

    @ParameterizedTest
    @EnumSource(ProcessRuntimeMode.class)
    void primitiveArrayVariablesProduceValidSource(ProcessRuntimeMode mode) {
        var variables = Map.of("items", new VariablePlan("items", "int[]", VariableRole.PARAM, null));

        assertThat(execute(mode, plan(variables, null, null), Map.of("items", new int[] {3, 1, 2}))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ProcessRuntimeMode.class)
    void actionInputEvaluationObeysContinuePolicy(ProcessRuntimeMode mode) {
        EffectiveInvocationPolicy policy =
                EffectiveInvocationPolicy.of(0L, 0L, 1, 0L, 1.0d, 0L, RetryJitter.NONE, "never", "continue");
        ActionPlan action = new ActionPlan(null, new ActionInvocation.Java(IntIdentity.class.getName(), "apply"),
                List.of(ActionPlan.Input.expression("Integer.parseInt(\"bad\")", "value", "int")), null, policy, null);

        assertThat(execute(mode, plan(Map.of(), action, null), Map.of())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ProcessRuntimeMode.class)
    void expressionBindingsRetainGenericElementTypes(ProcessRuntimeMode mode) {
        var variables = Map.of("items",
                new VariablePlan("items", "java.util.List<java.lang.Integer>", VariableRole.PARAM, null), "result",
                new VariablePlan("result", "java.lang.Integer", VariableRole.RETURN, null));
        ActionPlan action = new ActionPlan(null, new ActionInvocation.Java(IntIdentity.class.getName(), "apply"),
                List.of(ActionPlan.Input.expression("items.get(0).intValue()", "value", "int")),
                new ActionPlan.Output("result", "java.lang.Integer", "java.lang.Integer"),
                EffectiveInvocationPolicy.defaults(), null);

        assertThat(execute(mode, plan(variables, action, null), Map.of("items", List.of(42)))).containsEntry("result",
                42);
    }

    @ParameterizedTest
    @EnumSource(ProcessRuntimeMode.class)
    void constructorErrorsCannotBeSwallowedByContinuePolicy(ProcessRuntimeMode mode) {
        EffectiveInvocationPolicy policy =
                EffectiveInvocationPolicy.of(0L, 0L, 1, 0L, 1.0d, 0L, RetryJitter.NONE, "never", "continue");
        ActionPlan action = new ActionPlan(null, new ActionInvocation.Java(FailingConstructor.class.getName(), "run"),
                List.of(), null, policy, null);

        assertThatThrownBy(() -> execute(mode, plan(Map.of(), action, null), Map.of()))
            .isInstanceOf(AssertionError.class)
            .hasMessage("constructor failure");
    }

    private Map<String, Object> execute(ProcessRuntimeMode mode, ProcessSemanticPlan plan, Map<String, Object> inputs) {
        ProcessEngineConfig config = ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .classLoader(getClass().getClassLoader())
            .runtimeMode(mode)
            .build();
        EngineDependencies dependencies = EngineAssembly.assemble(config);
        try (DefaultProcessEngine engine = (DefaultProcessEngine) EngineAssembly.create(config, dependencies)) {
            ProcessRuntimeFactory factory = mode == ProcessRuntimeMode.COMPILED
                    ? new CompiledProcessRuntimeFactory(dependencies.scriptExecutors(), dependencies.javaCompiler(),
                            config.getJavaDiagnostics())
                    : new InterpretedProcessRuntimeFactory(dependencies.javaCompiler(), config.getJavaDiagnostics(),
                            config.getComponentResolver(), dependencies.scriptExecutors());
            ProcessRuntime runtime = factory.createRuntime(new ProcessSemanticCompilation(plan,
                            new StructuredControlFlowAnalyzer().analyze(plan)), config.getClassLoader());
            ProcessDefinitionSnapshot source = ProcessDefinitionSnapshot.of(ProcessModelType.TBBPM, "tenant",
                    plan.getProcessCode(), "v1", plan.getDigest().getBytes(StandardCharsets.UTF_8), "parity test");
            ProcessRuntimeIdentity identity =
                    ProcessRuntimeIdentity.of(source, ProcessRuntimeIdentity.newPipelineIdentity(),
                            config.getClassLoader());
            ProcessCallGraph.ProcessNode root = new ProcessCallGraph.ProcessNode(plan.getProcessCode(), "tenant",
                    plan.getProcessCode(), ProcessRef.version("tenant", plan.getProcessCode(), "v1"),
                    new ProcessRuntimeEntry(runtime, identity));
            return engine.invoke(new ProcessCallGraph(root, Map.of()), root, inputs).orElseThrow();
        }
    }

    private static ProcessSemanticPlan plan(Map<String, VariablePlan> variables, ActionPlan action,
            IterationPlan iteration) {
        LinkedHashMap<String, NodePlan> nodes = new LinkedHashMap<>();
        nodes.put("start",
                new NodePlan("start", NodeKind.START, ProcessSemanticPlan.ROOT_SCOPE_ID, null,
                        List.of(new TransitionPlan("action", null, false)), null, null, null));
        nodes.put("action",
                new NodePlan("action", NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null,
                        List.of(new TransitionPlan("end", null, false)), null, action, iteration));
        nodes.put("end",
                new NodePlan("end", NodeKind.END, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null, null));
        return new ProcessSemanticPlan("parity", variables, nodes);
    }

    public static final class IntIdentity {
        public Integer apply(int value) {
            return value;
        }
    }

    public static final class FailingConstructor {
        public FailingConstructor() {
            throw new AssertionError("constructor failure");
        }

        public void run() {}
    }
}
