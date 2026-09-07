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
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.core.DefaultProcessEngine;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.assembly.EngineDependencies;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowAnalyzer;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler.ProcessSemanticCompilation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.NodeKind;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.NodePlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.TransitionPlan;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.execution.ActionExecutionContext;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ProcessCallInvocationIdentityTest {
    @ParameterizedTest
    @CsvSource({"COMPILED,false", "COMPILED,true", "INTERPRETED,false", "INTERPRETED,true"})
    void repeatedAndConcurrentCallsIsolateChildActionIdentity(ProcessRuntimeMode mode, boolean parallel) {
        Recorder recorder = new Recorder(parallel);
        ProcessComponentResolver resolver = new ProcessComponentResolver() {
            @Override
            public <T> T resolve(String name, Class<T> requiredType) {
                assertThat(name).isEqualTo("recorder");
                return requiredType.cast(recorder);
            }
        };
        ProcessEngineConfig config = ProcessEngineConfig
            .builder()
            .discoverPlugins(false)
            .classLoader(getClass().getClassLoader())
            .componentResolver(resolver)
            .runtimeMode(mode)
            .build();
        EngineDependencies dependencies = EngineAssembly.assemble(config);
        try (DefaultProcessEngine engine = (DefaultProcessEngine) EngineAssembly.create(config, dependencies)) {
            ProcessRuntimeFactory factory = mode == ProcessRuntimeMode.COMPILED
                    ? new CompiledProcessRuntimeFactory(dependencies.scriptExecutors(), dependencies.javaCompiler(),
                            config.getJavaDiagnostics())
                    : new InterpretedProcessRuntimeFactory(dependencies.javaCompiler(), config.getJavaDiagnostics(),
                            resolver, dependencies.scriptExecutors());
            ActionPlan action = new ActionPlan(null,
                    new ActionInvocation.SpringBean("recorder", Recorder.class.getName(), "capture"), List.of(), null,
                    EffectiveInvocationPolicy.defaults(), null);
            ProcessSemanticPlan child = plan("identity.child", node("start", NodeKind.START, null, null, "record"),
                    node("record", NodeKind.ACTIVITY, action, twice(), "end"), node("end", NodeKind.END, null, null));
            ProcessCallPlan call =
                    new ProcessCallPlan(child.getProcessCode(), new ProcessCallTarget.Version("v1"), List.of(),
                            List.of());
            ProcessSemanticPlan root = parallel
                    ? plan("identity.root", node("start", NodeKind.START, null, null, "fork"),
                            node("fork", NodeKind.PARALLEL_GATEWAY, null, null, "left", "right"),
                            node("left", NodeKind.ACTIVITY, call, twice(), "join"),
                            node("right", NodeKind.ACTIVITY, call, twice(), "join"),
                            node("join", NodeKind.PARALLEL_GATEWAY, null, null, "end"),
                            node("end", NodeKind.END, null, null))
                    : plan("identity.root", node("start", NodeKind.START, null, null, "call"),
                            node("call", NodeKind.ACTIVITY, call, twice(), "end"), node("end", NodeKind.END, null, null));
            ProcessCallGraph.ProcessNode parent = processNode(root, ProcessModelType.BPMN, factory, config);
            ProcessCallGraph.ProcessNode target = processNode(child, ProcessModelType.TBBPM, factory, config);
            Map<ProcessCallGraph.CallSite, ProcessCallGraph.ProcessNode> calls = new LinkedHashMap<>();
            for (String callSite : parallel ? List.of("left", "right") : List.of("call")) {
                calls.put(new ProcessCallGraph.CallSite(parent.id(), callSite), target);
            }
            ProcessCallGraph graph = new ProcessCallGraph(parent, calls);
            var result = engine.invoke(graph, parent, Map.of());
            result.orElseThrow();

            int childInvocations = parallel ? 4 : 2;
            assertThat(recorder.observations).hasSize(childInvocations * 2);
            Map<String, List<Observation>> byInvocation = recorder.observations
                .stream()
                .collect(Collectors.groupingBy(value -> value.action().getProcessInvocationId()));
            assertThat(byInvocation).hasSize(childInvocations);
            byInvocation.forEach((id, observations) -> {
                assertThat(id).isNotEqualTo(result.getExecution().getInvocationId());
                assertThat(observations)
                    .extracting(value -> value.action().getInvocationOrdinal())
                    .containsExactly(1L, 2L);
                assertThat(observations)
                    .extracting(Observation::parentInvocationId)
                    .containsOnly(result.getExecution().getInvocationId());
            });
            assertThat(recorder.observations)
                .extracting(value -> value.action().getInvocationKey())
                .doesNotHaveDuplicates();
            assertThat(recorder.observations).allSatisfy(value -> {
                assertThat(value.action().getProcessCode()).isEqualTo(child.getProcessCode());
                assertThat(value.action().getNodeId()).isEqualTo("record");
                assertThat(value.action().getModelType()).isEqualTo(ProcessModelType.TBBPM);
                assertThat(value.action().getSourceDigest()).isEqualTo(target
                    .runtimeEntry()
                    .getRuntimeIdentity()
                    .getSourceDigest());
            });
            assertThat(result.getExecution().getCompletedAt()).isAfterOrEqualTo(result.getExecution().getStartedAt());
        }
    }

    private static ProcessCallGraph.ProcessNode processNode(ProcessSemanticPlan plan, ProcessModelType type,
            ProcessRuntimeFactory factory, ProcessEngineConfig config) {
        var compilation = new ProcessSemanticCompilation(plan, new StructuredControlFlowAnalyzer().analyze(plan));
        ProcessRuntime runtime = factory.createRuntime(compilation, config.getClassLoader());
        ProcessDefinitionSnapshot source = ProcessDefinitionSnapshot.of(type, "tenant", plan.getProcessCode(), "v1",
                plan.getDigest().getBytes(StandardCharsets.UTF_8), "identity test");
        ProcessRuntimeIdentity identity =
                ProcessRuntimeIdentity.of(source, ProcessRuntimeIdentity.newPipelineIdentity(), config.getClassLoader());
        return new ProcessCallGraph.ProcessNode(plan.getProcessCode(), "tenant", plan.getProcessCode(),
                ProcessRef.version("tenant", plan.getProcessCode(), "v1"), new ProcessRuntimeEntry(runtime, identity));
    }

    private static ProcessSemanticPlan plan(String code, NodePlan... nodes) {
        LinkedHashMap<String, NodePlan> ordered = new LinkedHashMap<>();
        Arrays
            .stream(nodes)
            .forEach(node -> ordered.put(node.id(), node));
        return new ProcessSemanticPlan(code, Map.of(), ordered);
    }

    private static NodePlan node(String id, NodeKind kind, OperationPlan operation, IterationPlan iteration,
            String... targets) {
        return new NodePlan(id, kind, ProcessSemanticPlan.ROOT_SCOPE_ID, null,
                Arrays
                    .stream(targets)
                    .map(target -> new TransitionPlan(target, null, false))
                    .toList(), null, operation, iteration);
    }

    private static IterationPlan.While twice() {
        return new IterationPlan.While("true", IterationPlan.ConditionTiming.BEFORE, 2, IterationPlan.LimitBehavior.STOP,
                null);
    }

    private record Observation(ActionExecutionContext action, String parentInvocationId) {}

    public static final class Recorder {
        private final CountDownLatch firstCalls;
        private final List<Observation> observations = new CopyOnWriteArrayList<>();

        private Recorder(boolean parallel) {
            firstCalls = new CountDownLatch(parallel ? 2 : 0);
        }

        public void capture() throws InterruptedException {
            ActionExecutionContext action = ActionExecutionContext.current();
            observations.add(
                    new Observation(action, EngineExecutionContextHolder.requireCurrent().parentInvocationId()));
            if (action.getInvocationOrdinal() == 1L) {
                firstCalls.countDown();
                if (!firstCalls.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Parallel child calls did not overlap");
                }
            }
        }
    }
}
