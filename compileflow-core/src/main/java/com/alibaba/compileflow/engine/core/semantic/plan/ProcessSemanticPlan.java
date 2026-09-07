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
package com.alibaba.compileflow.engine.core.semantic.plan;

import static com.alibaba.compileflow.engine.core.semantic.CanonicalEncoding.append;
import static com.alibaba.compileflow.engine.core.semantic.CanonicalEncoding.sha256;
import static com.alibaba.compileflow.engine.core.semantic.SemanticText.optionalExpression;
import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireIdentity;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Immutable, source-format-neutral Process meaning.
 *
 * <p>The plan stores only declarative Process semantics. Source AST, generated classes, target
 * state, and graph facts that can be derived from this data are intentionally absent. The private
 * indexes are construction details and never contribute to the digest.
 *
 * @author yusu
 */
public final class ProcessSemanticPlan {
    /**
     * Reserved identity of the outermost Process scope.
     */
    public static final String ROOT_SCOPE_ID = "$root";
    private static final String CANONICAL_FORMAT = "semantic-v3";
    private final String processCode;
    private final Map<String, VariablePlan> variables;
    private final Map<String, NodePlan> nodes;
    private final Map<String, List<NodePlan>> nodesByScope;
    private final Map<String, Integer> incomingCounts;
    private final Map<String, String> parentScopes;
    private final String digest;

    /**
     * Creates and validates one complete immutable Process meaning.
     */
    public ProcessSemanticPlan(String processCode, Map<String, VariablePlan> variables, Map<String, NodePlan> nodes) {
        this.processCode = ProcessIdentifiers.requireCode(processCode);
        this.variables = immutableByIdentity(variables, VariablePlan::name, "variables");
        this.nodes = immutableByIdentity(nodes, NodePlan::id, "nodes");
        if (this.nodes.containsKey(ROOT_SCOPE_ID)) {
            throw new IllegalArgumentException("node id '" + ROOT_SCOPE_ID + "' is reserved");
        }
        this.nodesByScope = deriveNodesByScope(this.nodes);
        this.incomingCounts = deriveIncomingCounts(this.nodes);
        this.parentScopes = deriveParentScopes(this.nodes, this.nodesByScope);
        validateReferences();
        this.digest = sha256(canonicalForm());
    }

    /**
     * Returns the stable Process code identity.
     */
    public String getProcessCode() {
        return processCode;
    }

    /**
     * Returns immutable Process variable definitions in declaration order, keyed by name.
     */
    public Map<String, VariablePlan> getVariables() {
        return variables;
    }

    /**
     * Returns immutable executable node definitions in frontend order, keyed by node id.
     */
    public Map<String, NodePlan> getNodes() {
        return nodes;
    }

    /**
     * Returns the deterministic digest of irreducible semantic facts.
     */
    public String getDigest() {
        return digest;
    }

    /**
     * Returns one node or fails with a precise semantic identifier.
     */
    public NodePlan requireNode(String nodeId) {
        NodePlan node = nodes.get(Objects.requireNonNull(nodeId, "nodeId"));
        if (node == null) {
            throw new IllegalArgumentException("Unknown semantic node '" + nodeId + "'");
        }
        return node;
    }

    /**
     * Returns one Process variable or fails with a precise semantic identifier.
     */
    public VariablePlan requireVariable(String variableName) {
        VariablePlan variable = variables.get(Objects.requireNonNull(variableName, "variableName"));
        if (variable == null) {
            throw new IllegalArgumentException("Unknown Process variable '" + variableName + "'");
        }
        return variable;
    }

    /**
     * Returns nodes owned by a scope in frontend order.
     */
    public List<NodePlan> nodesInScope(String scopeId) {
        String exact = ProcessIdentifiers.requireNodeId(scopeId);
        return nodesByScope.getOrDefault(exact, List.of());
    }

    /**
     * Returns whether a node owns a nested scope.
     */
    public boolean ownsScope(String nodeId) {
        return nodesByScope.containsKey(ProcessIdentifiers.requireNodeId(nodeId));
    }

    /**
     * Returns the lexical parent of a scope, or {@code null} for the root scope.
     */
    public String parentScope(String scopeId) {
        String exact = ProcessIdentifiers.requireNodeId(scopeId);
        if (!parentScopes.containsKey(exact)) {
            throw new IllegalArgumentException("Unknown semantic scope '" + scopeId + "'");
        }
        return parentScopes.get(exact);
    }

    /**
     * Returns the number of incoming semantic transitions for one node.
     */
    public int incomingCount(String nodeId) {
        requireNode(nodeId);
        return incomingCounts.getOrDefault(nodeId, 0);
    }

    /**
     * Derives variables visible while executing a node.
     *
     * <p>Process variables are added first. Each enclosing iteration then contributes its lexical
     * variables. Construction rejects lexical shadowing, so every visible name has one unambiguous
     * meaning. The returned map is derived data and is not part of the semantic digest.
     */
    public Map<String, VisibleVariable> visibleVariables(String nodeId) {
        NodePlan node = requireNode(nodeId);
        Map<String, VisibleVariable> visible = visibleVariablesBeforeIteration(node);
        addIterationVariables(visible, node.iteration());
        return Collections.unmodifiableMap(visible);
    }

    private Map<String, VisibleVariable> visibleVariablesBeforeIteration(NodePlan node) {
        Map<String, VisibleVariable> visible = new LinkedHashMap<>();
        variables
            .values()
            .forEach(variable -> visible.put(variable.name(),
                    new VisibleVariable(variable.name(), variable.dataType(), VisibleVariable.Origin.PROCESS)));

        List<String> scopes = new ArrayList<>();
        for (String scope = node.scopeId(); scope != null && !ROOT_SCOPE_ID.equals(scope); scope = parentScopes.get(
                scope)) {
            scopes.add(scope);
        }
        Collections.reverse(scopes);
        for (String scope : scopes) {
            addIterationVariables(visible, requireNode(scope).iteration());
        }
        return visible;
    }

    private static void addIterationVariables(Map<String, VisibleVariable> visible, IterationPlan iteration) {
        if (iteration instanceof IterationPlan.While whilePlan) {
            addIterationVariable(visible, whilePlan.indexVariable(), "int");
        } else if (iteration instanceof IterationPlan.ForEach forEach) {
            addIterationVariable(visible, forEach.itemVariable(), forEach.itemType());
            addIterationVariable(visible, forEach.indexVariable(), "int");
        }
    }

    private static void addIterationVariable(Map<String, VisibleVariable> visible, String name, String typeName) {
        if (name != null) {
            visible.put(name, new VisibleVariable(name, typeName, VisibleVariable.Origin.ITERATION));
        }
    }

    /**
     * Returns the canonical serialization used only for deterministic digesting and golden tests.
     */
    public String canonicalForm() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, "format", CANONICAL_FORMAT);
        append(canonical, "process.code", processCode);
        variables
            .values()
            .forEach(variable -> appendVariable(canonical, variable));
        sorted(nodes).forEach(entry -> appendNode(canonical, entry.getValue()));
        return canonical.toString();
    }

    private void validateReferences() {
        validateScopeGraph();
        for (NodePlan node : nodes.values()) {
            for (TransitionPlan transition : node.outgoingTransitions()) {
                if (!nodes.containsKey(transition.targetId())) {
                    throw new IllegalArgumentException(
                            "Node '" + node.id() + "' references unknown transition target '" + transition.targetId() + "'");
                }
            }
            validateNodeBindings(node);
        }
    }

    private void validateScopeGraph() {
        for (NodePlan node : nodes.values()) {
            if (!ROOT_SCOPE_ID.equals(node.scopeId()) && !nodes.containsKey(node.scopeId())) {
                throw new IllegalArgumentException(
                        "Node '" + node.id() + "' references unknown scope owner '" + node.scopeId() + "'");
            }
        }
        for (String scope : parentScopes.keySet()) {
            Set<String> visited = new LinkedHashSet<>();
            for (String current = scope; current != null; current = parentScopes.get(current)) {
                if (!visited.add(current)) {
                    throw new IllegalArgumentException("Semantic scope ownership contains a cycle at '" + current + "'");
                }
            }
        }
        for (NodePlan owner : nodes.values()) {
            boolean ownsScope = nodesByScope.containsKey(owner.id());
            if (ownsScope != (owner.scopeBoundary() != null)) {
                throw new IllegalArgumentException(
                        "Node '" + owner.id() + "' "
                        + (ownsScope
                        ? "owns a nested scope but declares no boundary"
                        : "declares a boundary but owns no nested scope"));
            }
            if (owner.scopeBoundary() != null) {
                requireScopeMember(owner.id(), owner.scopeBoundary().startNodeId(), "start");
                requireScopeMember(owner.id(), owner.scopeBoundary().endNodeId(), "end");
            }
        }
    }

    private void requireScopeMember(String ownerId, String boundaryNodeId, String boundaryName) {
        NodePlan boundaryNode = nodes.get(boundaryNodeId);
        if (boundaryNode == null) {
            throw new IllegalArgumentException(
                    "Node '" + ownerId + "' declares unknown nested-scope " + boundaryName + " node '" + boundaryNodeId + "'");
        }
        if (!ownerId.equals(boundaryNode.scopeId())) {
            throw new IllegalArgumentException(
                    "Node '" + ownerId + "' declares " + boundaryName + " node '" + boundaryNodeId + "' outside its owned scope");
        }
    }

    private void validateNodeBindings(NodePlan node) {
        Map<String, VisibleVariable> visible = visibleVariables(node.id());
        validateIteration(node, visibleVariablesBeforeIteration(node));
        if (node.operation() instanceof ActionPlan action) {
            validateActionBindings(node.id(), action, visible);
        } else if (node.operation() instanceof ProcessCallPlan call) {
            call.inputs().forEach(input -> {
                if (ProcessNames.isIdentifier(input.sourceExpression())) {
                    requireVisible(node.id(), input.sourceExpression(), visible);
                }
            });
            call.outputs().forEach(output -> {
                VariablePlan target = requireVariable(output.target());
                requireSameType("Process call output at node '" + node.id() + "'", output.targetType(),
                        target.dataType());
            });
        }
    }

    private void validateIteration(NodePlan node, Map<String, VisibleVariable> visible) {
        if (node.iteration() instanceof IterationPlan.While whilePlan) {
            validateIterationVariable(node.id(), whilePlan.indexVariable(), visible);
        } else if (node.iteration() instanceof IterationPlan.ForEach forEach) {
            VisibleVariable collection = requireVisible(node.id(), forEach.collectionVariable(), visible);
            validateForEachInputType(node.id(), collection.typeName(), forEach.itemType());
            validateIterationVariable(node.id(), forEach.itemVariable(), visible);
            validateIterationVariable(node.id(), forEach.indexVariable(), visible);
            if (Objects.equals(forEach.itemVariable(), forEach.indexVariable())) {
                throw new IllegalArgumentException(
                        "Node '" + node.id() + "' declares the same item and index variable '" + forEach.itemVariable() + "'");
            }
            if (forEach.outputTargetVariable() != null) {
                VariablePlan outputSource = requireVariable(forEach.outputSourceVariable());
                VariablePlan outputTarget = requireVariable(forEach.outputTargetVariable());
                validateForEachOutputTypes(node.id(), outputSource, outputTarget);
            }
        }
    }

    private static void validateForEachInputType(String nodeId, String collectionType, String itemType) {
        Class<?> collectionClass = DataTypes.getJavaClass(collectionType);
        Class<?> declaredItemClass = boxedClass(DataTypes.getJavaClass(itemType));
        if (collectionClass.isArray()) {
            Class<?> componentClass = boxedClass(collectionClass.getComponentType());
            if (!declaredItemClass.isAssignableFrom(componentClass)) {
                throw new IllegalArgumentException(
                        "Foreach node '" + nodeId + "' declares item type '" + itemType
                        + "' incompatible with array type '" + collectionType + "'");
            }
            return;
        }
        if (!Iterable.class.isAssignableFrom(collectionClass)) {
            throw new IllegalArgumentException(
                    "Foreach node '" + nodeId + "' collection variable must be an Iterable or array, found '" + collectionType + "'");
        }

        List<String> arguments = DataTypes.getTypeArguments(collectionType);
        if (arguments.size() == 1 && !genericInputCompatible(arguments.get(0), declaredItemClass, itemType)) {
            throw new IllegalArgumentException(
                    "Foreach node '" + nodeId + "' declares item type '" + itemType
                    + "' incompatible with collection type '" + collectionType + "'");
        }
    }

    private static boolean genericInputCompatible(String argument, Class<?> itemClass, String itemType) {
        if (argument.equals("?") || argument.startsWith("?super")) {
            return itemClass == Object.class;
        }
        String candidate = argument.startsWith("?extends") ? argument.substring("?extends".length()) : argument;
        Class<?> candidateClass = boxedClass(DataTypes.getJavaClass(candidate));
        if (!itemClass.isAssignableFrom(candidateClass)) {
            return false;
        }
        return DataTypes.getTypeArguments(itemType).isEmpty() || sameDeclaredType(itemType, candidate);
    }

    private static void validateForEachOutputTypes(String nodeId, VariablePlan source, VariablePlan target) {
        if (source.name().equals(target.name())) {
            throw new IllegalArgumentException(
                    "Foreach node '" + nodeId + "' output source and target variables must be different");
        }
        if (DataTypes.getJavaClass(target.dataType()) != List.class) {
            throw new IllegalArgumentException(
                    "Foreach node '" + nodeId + "' output target variable '" + target.name()
                    + "' must declare java.util.List; aggregation does not promise a concrete List implementation");
        }
        if (source.role() != VariableRole.INNER) {
            throw new IllegalArgumentException(
                    "Foreach node '" + nodeId + "' output source variable '" + source.name() + "' must be an inner process variable");
        }
        List<String> arguments = DataTypes.getTypeArguments(target.dataType());
        if (arguments.size() == 1 && !sameDeclaredType(arguments.get(0), source.dataType())) {
            throw new IllegalArgumentException(
                    "Foreach node '" + nodeId + "' output target type '" + target.dataType()
                    + "' must contain output source type '" + source.dataType() + "'");
        }
    }

    private static boolean sameDeclaredType(String left, String right) {
        if (left.startsWith("?") || right.startsWith("?")) {
            return false;
        }
        if (!DataTypes.getJavaClass(left).equals(DataTypes.getJavaClass(right))) {
            return false;
        }
        List<String> leftArguments = DataTypes.getTypeArguments(left);
        List<String> rightArguments = DataTypes.getTypeArguments(right);
        if (leftArguments.size() != rightArguments.size()) {
            return false;
        }
        for (int index = 0; index < leftArguments.size(); index++) {
            if (!sameDeclaredType(leftArguments.get(index), rightArguments.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxedClass(Class<?> type) {
        return type.isPrimitive() ? DataTypes.getWrapperClass(type) : type;
    }

    private void validateActionBindings(String nodeId, ActionPlan action, Map<String, VisibleVariable> visible) {
        action.inputs().forEach(input -> {
            if (input.source() instanceof ActionPlan.InputSource.Expression expression) {
                validateActionInput(nodeId, expression.value(), visible);
            }
        });
        if (action.output() != null) {
            VariablePlan target = requireVariable(action.output().target());
            requireSameType("Action output at node '" + nodeId + "'", action.output().targetType(), target.dataType());
        }
    }

    private static void validateActionInput(String nodeId, String sourceExpression,
            Map<String, VisibleVariable> visible) {
        if (ProcessNames.isIdentifier(sourceExpression)) {
            requireVisible(nodeId, sourceExpression, visible);
        }
    }

    private static VisibleVariable requireVisible(String nodeId, String variableName,
            Map<String, VisibleVariable> visible) {
        VisibleVariable variable = visible.get(variableName);
        if (variable == null) {
            throw new IllegalArgumentException(
                    "Node '" + nodeId + "' references variable '" + variableName + "' outside its visible scope");
        }
        return variable;
    }

    private static void validateIterationVariable(String nodeId, String variableName,
            Map<String, VisibleVariable> visible) {
        if (variableName == null) {
            return;
        }
        requireUserVariableName(variableName, "iteration variable");
        if (visible.containsKey(variableName)) {
            throw new IllegalArgumentException(
                    "Iteration variable '" + variableName + "' at node '" + nodeId + "' shadows a visible variable");
        }
    }

    private static void requireSameType(String binding, String declaredType, String semanticType) {
        if (!declaredType.equals(semanticType)) {
            throw new IllegalArgumentException(
                    binding + " declares target type '" + declaredType + "' but Process variable type is '" + semanticType + "'");
        }
    }

    private static Map<String, List<NodePlan>> deriveNodesByScope(Map<String, NodePlan> nodes) {
        Map<String, List<NodePlan>> result = new LinkedHashMap<>();
        result.put(ROOT_SCOPE_ID, new ArrayList<>());
        nodes
            .values()
            .forEach(node -> result
                .computeIfAbsent(node.scopeId(), ignored -> new ArrayList<>())
                .add(node));
        result.replaceAll((scope, members) -> List.copyOf(members));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Integer> deriveIncomingCounts(Map<String, NodePlan> nodes) {
        Map<String, Integer> result = new HashMap<>();
        nodes
            .values()
            .forEach(node -> node
                .outgoingTransitions()
                .forEach(transition -> result.merge(transition.targetId(), 1, Integer::sum)));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> deriveParentScopes(Map<String, NodePlan> nodes,
            Map<String, List<NodePlan>> nodesByScope) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(ROOT_SCOPE_ID, null);
        for (String scope : nodesByScope.keySet()) {
            if (!ROOT_SCOPE_ID.equals(scope)) {
                NodePlan owner = nodes.get(scope);
                if (owner != null) {
                    result.put(scope, owner.scopeId());
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static <T> Map<String, T> immutableByIdentity(Map<String, T> source, Function<T, String> identity,
            String name) {
        Objects.requireNonNull(source, name);
        Map<String, T> exactValues = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String exactKey = requireIdentity(key, name + " key");
            T nonNullValue = Objects.requireNonNull(value, name + " value");
            String valueIdentity = requireIdentity(identity.apply(nonNullValue), name + " identity");
            if (!exactKey.equals(valueIdentity)) {
                throw new IllegalArgumentException(
                        name + " key '" + exactKey + "' does not match value identity '" + valueIdentity + "'");
            }
            if (exactValues.putIfAbsent(exactKey, nonNullValue) != null) {
                throw new IllegalArgumentException("Duplicate " + name + " key '" + exactKey + "'");
            }
        });
        return Collections.unmodifiableMap(exactValues);
    }

    private static <T> List<Map.Entry<String, T>> sorted(Map<String, T> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
    }

    private static void appendVariable(StringBuilder canonical, VariablePlan variable) {
        append(canonical, "variable.name", variable.name());
        append(canonical, "variable.type", variable.dataType());
        append(canonical, "variable.role", variable.role().name());
        append(canonical, "variable.default", variable.defaultValue());
    }

    private static void appendNode(StringBuilder canonical, NodePlan node) {
        append(canonical, "node.id", node.id());
        append(canonical, "node.kind", node.kind().name());
        append(canonical, "node.scope", node.scopeId());
        if (node.scopeBoundary() == null) {
            append(canonical, "node.scope.start", null);
            append(canonical, "node.scope.end", null);
        } else {
            append(canonical, "node.scope.start", node.scopeBoundary().startNodeId());
            append(canonical, "node.scope.end", node.scopeBoundary().endNodeId());
        }
        append(canonical, "node.controlCondition", node.controlCondition());
        for (TransitionPlan transition : node.outgoingTransitions()) {
            append(canonical, "transition.target", transition.targetId());
            append(canonical, "transition.condition", transition.condition());
            append(canonical, "transition.default", Boolean.toString(transition.defaultFlow()));
        }
        appendOperation(canonical, node.operation());
        appendIteration(canonical, node.iteration());
    }

    private static void appendOperation(StringBuilder canonical, OperationPlan operation) {
        if (operation == null) {
            append(canonical, "operation.kind", null);
            return;
        }
        if (operation instanceof ActionPlan action) {
            append(canonical, "operation.kind", "action");
            appendAction(canonical, action);
        } else if (operation instanceof AwaitPlan await) {
            append(canonical, "operation.kind", "await");
            append(canonical, "await.event", await.event());
            append(canonical, "await.timeout", duration(await.timeout()));
        } else if (operation instanceof TimerPlan timer) {
            append(canonical, "operation.kind", "timer");
            append(canonical, "timer.kind", timer.kind().name());
            append(canonical, "timer.value", timer.value());
        } else if (operation instanceof ProcessCallPlan call) {
            append(canonical, "operation.kind", "process-call");
            append(canonical, "process-call.code", call.code());
            if (call.target() instanceof ProcessCallTarget.Classpath classpath) {
                append(canonical, "process-call.target-kind", "classpath");
                append(canonical, "process-call.target", classpath.resourcePath());
            } else {
                ProcessCallTarget.Version version = (ProcessCallTarget.Version) call.target();
                append(canonical, "process-call.target-kind", "version");
                append(canonical, "process-call.target", version.version());
            }
            call.inputs().forEach(input -> {
                append(canonical, "process-call.input.source", input.sourceExpression());
                append(canonical, "process-call.input.target", input.target());
                append(canonical, "process-call.input.default", input.defaultValue());
            });
            call.outputs().forEach(output -> {
                append(canonical, "process-call.output.source", output.source());
                append(canonical, "process-call.output.target", output.target());
                append(canonical, "process-call.output.type", output.targetType());
            });
        } else {
            throw new IllegalStateException("Unsupported OperationPlan variant " + operation.getClass().getName());
        }
    }

    private static void appendAction(StringBuilder canonical, ActionPlan action) {
        append(canonical, "action.execution", action.execution() == null ? null : action.execution().name());
        appendInvocation(canonical, action.invocation());
        action.inputs().forEach(input -> {
            appendInputSource(canonical, input.source());
            append(canonical, "action.input.target", input.target());
            append(canonical, "action.input.type", input.declaredType());
        });
        if (action.output() == null) {
            append(canonical, "action.output", null);
        } else {
            append(canonical, "action.output.target", action.output().target());
            append(canonical, "action.output.resultType", action.output().resultType());
            append(canonical, "action.output.targetType", action.output().targetType());
        }
        appendInvocationPolicy(canonical, action.invocationPolicy());
        appendEffectPolicy(canonical, action.effectPolicy());
    }

    private static void appendInvocation(StringBuilder canonical, ActionInvocation invocation) {
        if (invocation instanceof ActionInvocation.Java java) {
            append(canonical, "action.invocation.kind", "java");
            append(canonical, "action.java.class", java.className());
            append(canonical, "action.java.method", java.method());
        } else if (invocation instanceof ActionInvocation.SpringBean spring) {
            append(canonical, "action.invocation.kind", "spring-bean");
            append(canonical, "action.spring.bean", spring.beanName());
            append(canonical, "action.spring.class", spring.declaredClass());
            append(canonical, "action.spring.method", spring.method());
        } else if (invocation instanceof ActionInvocation.Script script) {
            append(canonical, "action.invocation.kind", "script");
            append(canonical, "action.script.language", script.language());
            append(canonical, "action.script.source", script.source());
        } else {
            throw new IllegalStateException("Unsupported ActionInvocation variant " + invocation.getClass().getName());
        }
    }

    private static void appendInvocationPolicy(StringBuilder canonical, EffectiveInvocationPolicy policy) {
        append(canonical, "action.invocationPolicy.timeoutMs", Long.toString(policy.getTimeoutMs()));
        append(canonical, "action.invocationPolicy.attemptTimeoutMs", Long.toString(policy.getAttemptTimeoutMs()));
        append(canonical, "action.invocationPolicy.maxAttempts", Integer.toString(policy.getMaxAttempts()));
        append(canonical, "action.invocationPolicy.initialBackoffMs", Long.toString(policy.getInitialBackoffMs()));
        append(canonical, "action.invocationPolicy.backoffMultiplier", Double.toString(policy.getBackoffMultiplier()));
        append(canonical, "action.invocationPolicy.maxBackoffMs", Long.toString(policy.getMaxBackoffMs()));
        append(canonical, "action.invocationPolicy.jitter", policy.getJitter().name());
        append(canonical, "action.invocationPolicy.retryOn", policy.getRetryOn());
        append(canonical, "action.invocationPolicy.onFailure", policy.getOnFailure());
    }

    private static void appendEffectPolicy(StringBuilder canonical, EffectPolicyPlan policy) {
        if (policy == null) {
            append(canonical, "action.effectPolicy", null);
            return;
        }
        append(canonical, "action.effectPolicy.recoveryPlanVariable", policy.recoveryPlanVariable());
        append(canonical, "action.effectPolicy.recovery", policy.recovery() == null ? null : policy.recovery().name());
        append(canonical, "action.effectPolicy.maxAttempts", Integer.toString(policy.maxAttempts()));
        append(canonical, "action.effectPolicy.maxReconcileAttempts", Integer.toString(policy.maxReconcileAttempts()));
        append(canonical, "action.effectPolicy.recoveryDelay", duration(policy.recoveryDelay()));
        append(canonical, "action.effectPolicy.maxRecoveryDuration", duration(policy.maxRecoveryDuration()));
        if (policy.reconcileAction() == null) {
            append(canonical, "action.effectPolicy.reconcileAction", null);
        } else {
            append(canonical, "action.effectPolicy.reconcileAction", "reconcile");
            appendReconcile(canonical, policy.reconcileAction());
        }
    }

    private static void appendReconcile(StringBuilder canonical, ReconcilePlan reconcile) {
        appendInvocation(canonical, reconcile.invocation());
        reconcile.inputs().forEach(input -> {
            if (input.source() instanceof ReconcilePlan.InputSource.RequestField field) {
                append(canonical, "action.effectPolicy.reconcile.input.source-kind", "request-field");
                append(canonical, "action.effectPolicy.reconcile.input.source", field.name());
            } else if (input.source() instanceof ReconcilePlan.InputSource.EffectId) {
                append(canonical, "action.effectPolicy.reconcile.input.source-kind", "effect-id");
                append(canonical, "action.effectPolicy.reconcile.input.source", null);
            } else {
                throw new IllegalStateException("Unsupported Reconcile input source " + input.source().getClass());
            }
            append(canonical, "action.effectPolicy.reconcile.input.target", input.target());
            append(canonical, "action.effectPolicy.reconcile.input.type", input.declaredType());
        });
    }

    private static void appendInputSource(StringBuilder canonical, ActionPlan.InputSource source) {
        if (source instanceof ActionPlan.InputSource.Expression expression) {
            append(canonical, "action.input.source-kind", "expression");
            append(canonical, "action.input.source", expression.value());
        } else if (source instanceof ActionPlan.InputSource.Literal literal) {
            append(canonical, "action.input.source-kind", "literal");
            append(canonical, "action.input.source", literal.value());
        } else if (source instanceof ActionPlan.InputSource.EffectId) {
            append(canonical, "action.input.source-kind", "effect-id");
            append(canonical, "action.input.source", null);
        } else {
            throw new IllegalStateException("Unsupported Action input source " + source.getClass());
        }
    }

    private static void appendIteration(StringBuilder canonical, IterationPlan iteration) {
        if (iteration == null) {
            append(canonical, "iteration.kind", null);
        } else if (iteration instanceof IterationPlan.While whilePlan) {
            append(canonical, "iteration.kind", "while");
            append(canonical, "iteration.condition", whilePlan.condition());
            append(canonical, "iteration.timing", whilePlan.timing().name());
            append(canonical, "iteration.max",
                    whilePlan.maxIterations() == null ? null : whilePlan.maxIterations().toString());
            append(canonical, "iteration.limitBehavior", whilePlan.limitBehavior().name());
            append(canonical, "iteration.index", whilePlan.indexVariable());
        } else if (iteration instanceof IterationPlan.ForEach forEach) {
            append(canonical, "iteration.kind", "foreach");
            append(canonical, "iteration.collection", forEach.collectionVariable());
            append(canonical, "iteration.item", forEach.itemVariable());
            append(canonical, "iteration.itemType", forEach.itemType());
            append(canonical, "iteration.index", forEach.indexVariable());
            append(canonical, "iteration.execution", forEach.execution().name());
            append(canonical, "iteration.output.source", forEach.outputSourceVariable());
            append(canonical, "iteration.output.target", forEach.outputTargetVariable());
        } else {
            throw new IllegalStateException("Unsupported IterationPlan variant " + iteration.getClass().getName());
        }
    }

    private static String duration(Duration duration) {
        return duration == null ? null : duration.toString();
    }

    private static String requireUserVariableName(String value, String role) {
        String exact = requireIdentity(value, role);
        if (!ProcessNames.isIdentifier(exact) || ProcessNames.isReserved(exact)) {
            throw new IllegalArgumentException("Invalid " + role + " name: " + value);
        }
        return exact;
    }

    /**
     * Supported executable control-flow categories.
     */
    public enum NodeKind {
        START,
        END,
        ACTIVITY,
        EXCLUSIVE_GATEWAY,
        PARALLEL_GATEWAY,
        INCLUSIVE_GATEWAY,
        BREAK,
        CONTINUE
    }

    /**
     * One Process variable's stable semantic declaration.
     */
    public record VariablePlan(String name, String dataType, VariableRole role, String defaultValue) {
        public VariablePlan {
            name = requireUserVariableName(name, "Process variable");
            dataType = requireIdentity(dataType, "dataType");
            role = Objects.requireNonNull(role, "role");
            // Literal whitespace and the empty String are semantically significant defaults.
        }
    }

    /**
     * Process-variable role independent of a source-format string token.
     */
    public enum VariableRole {
        PARAM,
        INNER,
        RETURN
    }

    /**
     * One executable node and its outgoing control-flow semantics.
     */
    public record NodePlan(String id, NodeKind kind, String scopeId, ScopeBoundary scopeBoundary,
            List<TransitionPlan> outgoingTransitions, String controlCondition, OperationPlan operation,
            IterationPlan iteration) {
        public NodePlan {
            id = ProcessIdentifiers.requireNodeId(id);
            kind = Objects.requireNonNull(kind, "kind");
            scopeId = ProcessIdentifiers.requireNodeId(scopeId);
            outgoingTransitions = List.copyOf(Objects.requireNonNull(outgoingTransitions, "outgoingTransitions"));
            controlCondition = optionalExpression(controlCondition, "controlCondition");
            long defaultFlowCount = outgoingTransitions.stream().filter(TransitionPlan::defaultFlow).count();
            if (defaultFlowCount > 1) {
                throw new IllegalArgumentException("Node '" + id + "' declares more than one default flow");
            }
            if (kind == NodeKind.BREAK || kind == NodeKind.CONTINUE) {
                if (operation != null || iteration != null) {
                    throw new IllegalArgumentException(kind + " node '" + id + "' cannot own an operation or iteration");
                }
            } else if (controlCondition != null) {
                throw new IllegalArgumentException("controlCondition is only valid for BREAK and CONTINUE nodes");
            }
            if (iteration != null && kind != NodeKind.ACTIVITY) {
                throw new IllegalArgumentException("iteration is only valid for ACTIVITY nodes");
            }
            if (operation != null && kind != NodeKind.ACTIVITY) {
                throw new IllegalArgumentException("operation is only valid for ACTIVITY nodes");
            }
            if (scopeBoundary != null && kind != NodeKind.ACTIVITY) {
                throw new IllegalArgumentException("only an ACTIVITY node can own a nested scope boundary");
            }
        }
    }

    /**
     * Irreducible entry and exit identities of the nested scope owned by one activity.
     */
    public record ScopeBoundary(String startNodeId, String endNodeId) {
        public ScopeBoundary {
            startNodeId = ProcessIdentifiers.requireNodeId(startNodeId);
            endNodeId = ProcessIdentifiers.requireNodeId(endNodeId);
        }
    }

    /**
     * An ordered outgoing transition. Its containing node provides the source identity.
     */
    public record TransitionPlan(String targetId, String condition, boolean defaultFlow) {
        public TransitionPlan {
            targetId = ProcessIdentifiers.requireNodeId(targetId);
            condition = optionalExpression(condition, "condition");
            if (defaultFlow && condition != null) {
                throw new IllegalArgumentException("a default flow cannot also declare a condition");
            }
        }
    }

    /**
     * A derived variable binding visible at an executable node.
     */
    public record VisibleVariable(String name, String typeName, Origin origin) {
        public VisibleVariable {
            name = requireIdentity(name, "name");
            typeName = requireIdentity(typeName, "typeName");
            origin = Objects.requireNonNull(origin, "origin");
        }

        /**
         * Origin of a visible binding.
         */
        public enum Origin {
            PROCESS,
            ITERATION
        }
    }
}
