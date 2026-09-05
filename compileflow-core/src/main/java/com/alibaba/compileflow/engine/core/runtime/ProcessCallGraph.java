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

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.semantic.SemanticText;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exact Process nodes and call-site bindings resolved for one invocation.
 *
 * <p>This value is engine-local and invocation-scoped. It keeps exact runtime entries strongly
 * reachable and is neither a public domain model nor a persisted artifact.
 *
 * @author yusu
 */
public final class ProcessCallGraph {
    private final ProcessNode root;
    private final Map<CallSite, ProcessNode> targetsByCallSite;

    public ProcessCallGraph(ProcessNode root, Map<CallSite, ProcessNode> calls) {
        this.root = Objects.requireNonNull(root, "root");
        this.targetsByCallSite = Map.copyOf(Objects.requireNonNull(calls, "calls"));
    }

    public ProcessNode root() {
        return root;
    }

    public ProcessNode requireTarget(ProcessNode caller, String callSiteId) {
        ProcessNode target = targetsByCallSite.get(new CallSite(caller.id(), callSiteId));
        if (target == null) {
            throw new IllegalStateException(
                    "Resolved Process call is unavailable: caller=" + caller.id() + ", callSiteId=" + callSiteId);
        }
        return target;
    }

    public BoundCall bind(ProcessNode caller, String callSiteId, Map<String, Object> sourceInput) {
        ProcessNode source = Objects.requireNonNull(caller, "caller");
        String nodeId = ProcessIdentifiers.requireNodeId(callSiteId);
        ProcessNode target = requireTarget(source, nodeId);
        ProcessSemanticPlan.NodePlan node = source.runtimeEntry().getRuntime().getSemanticPlan().requireNode(nodeId);
        if (!(node.operation() instanceof ProcessCallPlan call)) {
            throw new IllegalStateException("Resolved call site is not a Process call: " + nodeId);
        }
        Map<String, Object> supplied = Objects.requireNonNull(sourceInput, "sourceInput");
        Set<String> expectedSources = call
            .inputs()
            .stream()
            .filter(input -> input.sourceExpression() != null)
            .map(ProcessCallPlan.Input::target)
            .collect(Collectors.toSet());
        if (!supplied.keySet().equals(expectedSources)) {
            throw new IllegalArgumentException(
                    "Process call source input does not match the admitted mapping: " + nodeId);
        }
        LinkedHashMap<String, Object> boundInput = new LinkedHashMap<>(supplied);
        ProcessSemanticPlan targetPlan = target.runtimeEntry().getRuntime().getSemanticPlan();
        ClassLoader targetClassLoader = target.runtimeEntry().getRuntimeIdentity().getClassLoader();
        call
            .inputs()
            .stream()
            .filter(input -> input.defaultValue() != null)
            .forEach(input -> {
                ProcessSemanticPlan.VariablePlan parameter = targetPlan.requireVariable(input.target());
                Class<?> parameterType = DataTypes.getJavaClass(parameter.dataType(), targetClassLoader);
                boundInput.put(input.target(), DataTypes.parseDefaultValue(parameterType, input.defaultValue()));
            });
        return new BoundCall(target, Collections.unmodifiableMap(boundInput));
    }

    /**
     * One exact executable Process node in the call graph.
     */
    public record ProcessNode(String id, String namespace, String code, ProcessRef.Version version,
            ProcessRuntimeEntry runtimeEntry) {
        public ProcessNode {
            id = SemanticText.requireIdentity(id, "id");
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            code = ProcessIdentifiers.requireCode(code);
            runtimeEntry = Objects.requireNonNull(runtimeEntry, "runtimeEntry");
            if (version != null && (!namespace.equals(version.namespace()) || !code.equals(version.code()))) {
                throw new IllegalArgumentException("version must identify the Process node");
            }
        }
    }

    /**
     * Identity of one call edge within one exact caller node.
     */
    public record CallSite(String callerId, String callSiteId) {
        public CallSite {
            callerId = SemanticText.requireIdentity(callerId, "callerId");
            callSiteId = ProcessIdentifiers.requireNodeId(callSiteId);
        }
    }

    public record BoundCall(ProcessNode target, Map<String, Object> input) {
        public BoundCall {
            target = Objects.requireNonNull(target, "target");
            input = Objects.requireNonNull(input, "input");
        }
    }
}
