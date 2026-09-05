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
package com.alibaba.compileflow.durable.runtime.action;

import com.alibaba.compileflow.durable.api.validation.DurablePayload;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ReconcilePlan;
import com.alibaba.compileflow.engine.core.runtime.action.ProcessActionInvoker;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Current-deployment resolver and invoker for immutable {@link ActionPlan}s.
 *
 * <p>Application objects, methods, script providers and compiled programs never leave
 * this Runtime boundary and are never persisted or used as Process identity.</p>
 *
 * @author yusu
 */
public final class DurableActionInvoker {
    private final ProcessComponentResolver components;
    private final ScriptExecutorRegistry scripts;
    private final ClassLoader classLoader;
    private final ProcessActionInvoker delegate;

    public DurableActionInvoker(ProcessComponentResolver components, ScriptExecutorRegistry scripts,
            ClassLoader classLoader) {
        this(components, scripts, classLoader, Map.of());
    }

    private DurableActionInvoker(ProcessComponentResolver components, ScriptExecutorRegistry scripts,
            ClassLoader classLoader, Map<ScriptProgramSpec, ScriptProgram> scriptPrograms) {
        this.components = Objects.requireNonNull(components, "components");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.delegate = new ProcessActionInvoker(this.components, this.scripts, this.classLoader, scriptPrograms);
    }

    public static DurableActionInvoker unavailable() {
        return new DurableActionInvoker(ProcessComponentResolver.disabled(), ScriptExecutorRegistry.from(List.of()),
                DurableActionInvoker.class.getClassLoader());
    }

    /**
     * Binds this deployment's component resolver to one exact runtime-owned Script catalog.
     * The returned invoker is immutable and may be used concurrently for all turns of that
     * loaded Durable program.
     */
    public DurableActionInvoker withScriptPrograms(Map<ScriptProgramSpec, ScriptProgram> scriptPrograms) {
        return new DurableActionInvoker(components, scripts, classLoader, scriptPrograms);
    }

    /**
     * Checks only structural prerequisites without entering arbitrary application code.
     *
     * <p>In particular this method never resolves a Spring bean, constructs a
     * Java target, or invokes a ScriptExecutor. Those operations are beyond the
     * Effect possible-dispatch boundary and every uncertain failure after that
     * boundary must be handled as an UNKNOWN outcome.</p>
     */
    public boolean isStructurallyReady(ActionPlan action) {
        return delegate.isStructurallyReady(action);
    }

    /**
     * Checks only structural prerequisites of a reconcile adapter.
     */
    public boolean isStructurallyReady(ReconcilePlan reconcile) {
        return delegate.isStructurallyReady(reconcile);
    }

    /**
     * Resolves exact detached input values without invoking application code.
     */
    public Map<String, Object> materializeInput(ActionPlan action, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        Objects.requireNonNull(action, "action");
        Map<String, Object> processState = Objects.requireNonNull(state, "state");
        Map<String, Object> lexical = Objects.requireNonNull(lexicalBindings, "lexicalBindings");
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        for (ActionPlan.Input mapping : action.inputs()) {
            if (mapping.source() instanceof ActionPlan.InputSource.EffectId) {
                continue;
            }
            Object raw = resolveInput(mapping, processState, lexical);
            input.put(mapping.target(), delegate.convertValue(raw, mapping.declaredType()));
        }
        return DurablePayload.immutablePayload(input, "action input");
    }

    private static Object resolveInput(ActionPlan.Input mapping, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        if (mapping.source() instanceof ActionPlan.InputSource.Literal literal) {
            return literal.value();
        }
        if (!(mapping.source() instanceof ActionPlan.InputSource.Expression expression)) {
            throw new IllegalStateException(
                    "Durable runtime cannot materialize Action input source " + mapping
                        .source()
                        .getClass()
                        .getSimpleName());
        }
        if (lexicalBindings.containsKey(expression.value())) {
            return lexicalBindings.get(expression.value());
        }
        if (state.containsKey(expression.value())) {
            return state.get(expression.value());
        }
        throw new IllegalArgumentException("Action input source is unavailable: " + expression.value());
    }

    /**
     * Invokes an Action from an already materialized input snapshot.
     */
    public Map<String, Object> invoke(ActionPlan action, Map<String, Object> input, String effectId) throws Exception {
        Objects.requireNonNull(action, "action");
        Map<String, Object> detachedInput =
                DurablePayload.immutablePayload(Objects.requireNonNull(input, "input"), "action input");
        Map<String, Object> output = delegate.invoke(action, detachedInput, effectId);
        return DurablePayload.immutablePayload(output, "action output");
    }

    /**
     * Invokes an already resolved Action while preserving its raw return value.
     */
    public Object invokeRaw(ActionPlan action, Map<String, Object> input, String effectId) throws Exception {
        Objects.requireNonNull(action, "action");
        Map<String, Object> detachedInput =
                DurablePayload.immutablePayload(Objects.requireNonNull(input, "input"), "action input");
        return delegate.invokeRaw(action, detachedInput, effectId);
    }

    /**
     * Invokes a reconcile adapter from the persisted original Effect request.
     */
    public Object invokeRaw(ReconcilePlan reconcile, Map<String, Object> input, String effectId) throws Exception {
        Objects.requireNonNull(reconcile, "reconcile");
        Map<String, Object> detachedInput =
                DurablePayload.immutablePayload(Objects.requireNonNull(input, "input"), "effect input");
        return delegate.invokeRaw(reconcile, detachedInput, effectId);
    }

    /**
     * Applies the original Action's declared return mapping to a raw result.
     */
    public Map<String, Object> mapResult(ActionPlan action, Object result) {
        Map<String, Object> output = delegate.mapResult(action, result);
        return DurablePayload.immutablePayload(output, "action output");
    }
}
