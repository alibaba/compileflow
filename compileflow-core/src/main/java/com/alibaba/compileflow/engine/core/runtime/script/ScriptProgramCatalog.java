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

import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ReconcilePlan;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Compiles the disposable script programs owned by one Process runtime.
 *
 * @author yusu
 */
public final class ScriptProgramCatalog {
    private ScriptProgramCatalog() {
    }

    /**
     * Compiles every script reachable from immutable Process semantics exactly once.
     *
     * @param semanticPlan source-format-neutral Process semantics
     * @param executors language-provider registry for the owning engine
     * @return immutable programs keyed by their complete specification
     */
    public static Map<ScriptProgramSpec, ScriptProgram> compile(ProcessSemanticPlan semanticPlan,
            ScriptExecutorRegistry executors) {
        ProcessSemanticPlan semantics = Objects.requireNonNull(semanticPlan, "semanticPlan");
        ScriptExecutorRegistry registry = Objects.requireNonNull(executors, "executors");
        Map<ScriptProgramSpec, ScriptProgram> programs = new LinkedHashMap<>();
        forEachSpec(semantics, spec -> programs.computeIfAbsent(spec, registry::compile));
        return Map.copyOf(programs);
    }

    /**
     * Validates every script reachable from immutable Process semantics without creating a
     * runtime-owned program. Tooling uses this to report unsupported languages and
     * source diagnostics before a Process is loaded.
     *
     * @param semanticPlan source-format-neutral Process semantics
     * @param executors language-provider registry for the owning engine
     */
    public static void validate(ProcessSemanticPlan semanticPlan, ScriptExecutorRegistry executors) {
        ProcessSemanticPlan semantics = Objects.requireNonNull(semanticPlan, "semanticPlan");
        ScriptExecutorRegistry registry = Objects.requireNonNull(executors, "executors");
        Set<ScriptProgramSpec> validatedSpecs = new LinkedHashSet<>();
        forEachSpec(semantics, spec -> {
            if (validatedSpecs.add(spec)) {
                registry.validate(spec);
            }
        });
    }

    private static void forEachSpec(ProcessSemanticPlan semantics, Consumer<ScriptProgramSpec> consumer) {
        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            if (node.operation() instanceof ActionPlan action) {
                visitSpecs(action, consumer);
            }
        }
    }

    private static void visitSpecs(ActionPlan action, Consumer<ScriptProgramSpec> consumer) {
        if (action.invocation() instanceof ActionInvocation.Script) {
            consumer.accept(action.scriptProgramSpec());
        }
        if (action.effectPolicy() != null && action.effectPolicy().reconcileAction() != null) {
            visitSpec(action.effectPolicy().reconcileAction(), consumer);
        }
    }

    private static void visitSpec(ReconcilePlan reconcile, Consumer<ScriptProgramSpec> consumer) {
        if (reconcile.invocation() instanceof ActionInvocation.Script) {
            consumer.accept(reconcile.scriptProgramSpec());
        }
    }
}
