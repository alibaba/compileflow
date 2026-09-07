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
package com.alibaba.compileflow.engine.core.java.codegen;

import static com.alibaba.compileflow.engine.core.java.codegen.JavaSourceFormatting.line;
import static com.alibaba.compileflow.engine.core.java.codegen.JavaSourceFormatting.literal;
import static com.alibaba.compileflow.engine.core.java.codegen.JavaSourceFormatting.logicalExpressionLines;
import static com.alibaba.compileflow.engine.core.java.codegen.JavaSourceFormatting.readableStringExpression;
import static com.alibaba.compileflow.engine.core.java.codegen.JavaSourceFormatting.safeComment;
import com.alibaba.compileflow.engine.core.controlflow.GatewayBranchKey;
import com.alibaba.compileflow.engine.core.controlflow.GatewayBranchPlan;
import com.alibaba.compileflow.engine.core.controlflow.GatewayPlan;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowPlan;
import com.alibaba.compileflow.engine.core.java.expression.JavaExpressionInspector;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Generates one specialized Java realization from source-neutral Process semantics.
 *
 * <p>Source-format ASTs end at their semantic frontends. Control flow, actions, calls,
 * triggers, iterations, and branch isolation are emitted exclusively from
 * {@link ProcessSemanticPlan} plus derived {@link StructuredControlFlowPlan} facts.</p>
 *
 * @author yusu
 */
public final class JavaProcessCodeGenerator {
    public static final String GENERATOR_VERSION = "compileflow.process-java/v2";
    private static final int MAX_NODES_PER_FLOW_METHOD = 200;
    private static final String NORMAL = "_CF_NORMAL";
    private static final String BREAK = "_CF_BREAK";
    private static final String CONTINUE = "_CF_CONTINUE";
    private static final String PAUSED = "_CF_PAUSED";
    private static final Set<String> GENERATOR_TYPES = Set.of("com.alibaba.compileflow.engine.ProcessRef",
            "com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy",
            "com.alibaba.compileflow.engine.core.model.action.RetryJitter",
            "com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder",
            "com.alibaba.compileflow.engine.core.runtime.executable.ExecutableProcess",
            "com.alibaba.compileflow.engine.core.runtime.executable.TriggerableProcess",
            "com.alibaba.compileflow.engine.core.runtime.execution.ActionExecutor",
            "com.alibaba.compileflow.engine.core.runtime.execution.ConditionSemantics",
            "com.alibaba.compileflow.engine.core.runtime.execution.GatewayExecutor",
            "com.alibaba.compileflow.engine.core.runtime.execution.LoopSemantics",
            "com.alibaba.compileflow.engine.core.runtime.execution.ProcessCallOutputs",
            "com.alibaba.compileflow.engine.core.runtime.execution.TriggerValidation",
            "com.alibaba.compileflow.engine.core.type.DataTypes",
            "com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec", "java.util.ArrayList",
            "java.util.Collections", "java.util.LinkedHashMap", "java.util.List", "java.util.Map", "java.util.Objects",
            "javax.annotation.processing.Generated");
    private final ProcessSemanticPlan semantics;
    private final StructuredControlFlowPlan structure;
    private final Map<String, String> nodeNames;
    private final String className;
    private final String simpleName;
    private final Map<String, String> activityMethodNames;
    private final Map<String, String> actionMethodNames;
    private final Map<String, String> scriptSpecFieldNames;
    private final Map<String, String> policyFieldNames;
    private final Map<String, String> gatewayMethodNames;
    private Map<String, String> typeReplacements;
    private final Map<FlowKey, String> flowMethodNames = new LinkedHashMap<>();
    private final List<FlowKey> flowMethodQueue = new ArrayList<>();
    private final Map<String, String> flowMethodOwners = new LinkedHashMap<>();

    public JavaProcessCodeGenerator(ProcessSemanticPlan semantics, StructuredControlFlowPlan structure) {
        this(semantics, structure, Map.of());
    }

    public JavaProcessCodeGenerator(ProcessSemanticPlan semantics, StructuredControlFlowPlan structure,
            Map<String, String> nodeNames) {
        this.semantics = Objects.requireNonNull(semantics, "semantics");
        this.structure = Objects.requireNonNull(structure, "structure");
        this.nodeNames = Map.copyOf(Objects.requireNonNull(nodeNames, "nodeNames"));
        this.className = GeneratedProcessNames.className(semantics.getProcessCode());
        this.simpleName = className.substring(className.lastIndexOf('.') + 1);
        this.activityMethodNames = collectActivityMethodNames();
        this.actionMethodNames = collectActionMethodNames();
        this.scriptSpecFieldNames = collectScriptSpecFieldNames();
        this.policyFieldNames = collectPolicyFieldNames();
        this.gatewayMethodNames = collectGatewayMethodNames();
    }

    public String generateCode() {
        typeReplacements = null;
        flowMethodNames.clear();
        flowMethodQueue.clear();
        flowMethodOwners.clear();
        StringBuilder code = new StringBuilder(Math.max(8_192, semantics.getNodes().size() * 1_024));
        int separator = className.lastIndexOf('.');
        line(code, 0, "package " + className.substring(0, separator) + ";");
        line(code, 0, "");
        line(code, 0, "// Generated CompileFlow process: " + safeComment(semantics.getProcessCode()));
        line(code, 0, "// Semantic digest: " + semantics.getDigest());
        line(code, 0, "");
        String processInterface = triggerable()
                ? "com.alibaba.compileflow.engine.core.runtime.executable.TriggerableProcess"
                : "com.alibaba.compileflow.engine.core.runtime.executable.ExecutableProcess";
        String generatedValue = literal(GENERATOR_VERSION);
        String generatedComments = literal("process=" + semantics.getProcessCode());
        String generatedAnnotation = "@Generated(value = " + generatedValue + ", comments = " + generatedComments + ")";
        if (generatedAnnotation.length() <= 120) {
            line(code, 0,
                    "@javax.annotation.processing.Generated(value = " + generatedValue + ", comments = " + generatedComments + ")");
        } else {
            line(code, 0, "@javax.annotation.processing.Generated(");
            line(code, 1, "value = " + generatedValue + ",");
            line(code, 1, "comments = " + generatedComments + ")");
        }
        line(code, 0, "public final class " + simpleName + " implements " + processInterface + " {");
        fields(code);
        entryMethods(code);
        branchFrameFactory(code);
        activityMethods(code);
        actionMethods(code);
        gatewayMethods(code);
        flowMethods(code);
        removeTrailingBlankLines(code);
        line(code, 0, "}");
        return organizeImports(code.toString());
    }

    private static void removeTrailingBlankLines(StringBuilder code) {
        while (code.length() >= 2 && code.charAt(code.length() - 1) == '\n' && code.charAt(code.length() - 2) == '\n') {
            code.setLength(code.length() - 1);
        }
    }

    public String getClassFullName() {
        return className;
    }

    private void fields(StringBuilder code) {
        line(code, 1, "private static final int " + NORMAL + " = 0;");
        if (hasNodeKind(ProcessSemanticPlan.NodeKind.BREAK)) {
            line(code, 1, "private static final int " + BREAK + " = 1;");
        }
        if (hasNodeKind(ProcessSemanticPlan.NodeKind.CONTINUE)) {
            line(code, 1, "private static final int " + CONTINUE + " = 2;");
        }
        if (triggerable()) {
            line(code, 1, "private static final int " + PAUSED + " = 3;");
        }
        line(code, 1, "");
        scriptSpecFields(code);
        policyFields(code);
        for (ProcessSemanticPlan.VariablePlan variable : semantics.getVariables().values()) {
            DataTypes.DefaultValueCode value =
                    DataTypes.generateDefaultValueCode(DataTypes.getJavaClass(variable.dataType()),
                            variable.defaultValue());
            String defaultValue = value.expression();
            String initializer = "null".equals(defaultValue) ? "" : " = " + defaultValue;
            line(code, 1, "private " + variable.dataType() + " " + variable.name() + initializer + ";");
        }
        if (triggerable()) {
            if (!semantics.getVariables().isEmpty()) {
                line(code, 1, "");
            }
            if (hasAwaitEvent()) {
                line(code, 1, "private String _cf$triggerEvent;");
            }
            line(code, 1, "private boolean _cf$triggerPending;");
        }
        line(code, 1, "");
        if (hasConcurrentGateway()) {
            line(code, 1, "private record _cf$BranchOutcome(" + simpleName + " branchFrame, int signal) {}");
            line(code, 1, "");
        }
    }

    private void scriptSpecFields(StringBuilder code) {
        Set<String> emitted = new LinkedHashSet<>();
        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            if (!(node.operation() instanceof ActionPlan action)
                    || !(action.invocation() instanceof ActionInvocation.Script script)) {
                continue;
            }
            String field = scriptSpecFieldNames.get(node.id());
            if (!emitted.add(field)) {
                continue;
            }
            line(code, 1,
                    "private static final com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec " + field + " =");
            line(code, 2, "new com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec(");
            line(code, 3, literal(script.language()) + ",");
            line(code, 3, readableStringExpression(script.source(), 3) + ",");
            if (action.inputs().isEmpty()) {
                line(code, 3, "java.util.List.of(),");
            } else {
                line(code, 3, "java.util.List.of(");
                for (int index = 0; index < action.inputs().size(); index++) {
                    ActionPlan.Input input = action.inputs().get(index);
                    String suffix = index + 1 == action.inputs().size() ? "" : ",";
                    String source = "new ScriptProgramSpec.Input(" + literal(input.target()) + ", "
                            + literal(input.declaredType()) + ")" + suffix;
                    if (source.length() + 16 <= 120) {
                        line(code, 4, source);
                    } else {
                        line(code, 4, "new ScriptProgramSpec.Input(");
                        line(code, 5, literal(input.target()) + ",");
                        line(code, 5, literal(input.declaredType()) + ")" + suffix);
                    }
                }
                line(code, 3, "),");
            }
            line(code, 3, (action.output() == null ? "null" : literal(action.output().resultType())) + ");");
            line(code, 1, "");
        }
    }

    private void policyFields(StringBuilder code) {
        Set<String> emitted = new LinkedHashSet<>();
        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            if (!(node.operation() instanceof ActionPlan action) || defaultPolicy(action.invocationPolicy())) {
                continue;
            }
            String field = policyFieldNames.get(node.id());
            if (emitted.add(field)) {
                line(code, 1,
                        "private static final com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy " + field + " =");
                line(code, 2, policySource(action.invocationPolicy()) + ";");
                line(code, 1, "");
            }
        }
    }

    private void entryMethods(StringBuilder code) {
        String rootStart = structure.getEntryNodeId();
        String rootFlow = requestFlow(rootStart, null);
        String mapType = "java.util.Map<String, Object>";
        String triggerExecutorType = "com.alibaba.compileflow.engine.core.runtime.execution.TriggerValidation";
        line(code, 1, "@Override");
        line(code, 1, "public " + mapType + " execute(" + mapType + " context) throws Exception {");
        if (triggerable() || hasParameters()) {
            line(code, 2, "_cf$loadParameters(context);");
        } else {
            line(code, 2, "java.util.Objects.requireNonNull(context, \"context\");");
        }
        line(code, 2, rootFlow + "();");
        line(code, 2, "return _cf$buildResult();");
        line(code, 1, "}");
        line(code, 1, "");

        if (triggerable()) {
            line(code, 1, "@Override");
            line(code, 1,
                    "public java.util.Map<String, Object> trigger(String nodeId, java.util.Map<String, Object> context) throws Exception {");
            line(code, 2, "return trigger(nodeId, null, context);");
            line(code, 1, "}");
            line(code, 1, "");
            line(code, 1, "@Override");
            line(code, 1,
                    "public java.util.Map<String, Object> trigger(String nodeId, String event, java.util."
                    + "Map<String, Object> context) throws Exception {");
            line(code, 2, "_cf$restoreState(context);");
            if (hasAwaitEvent()) {
                line(code, 2, "this._cf$triggerEvent = event;");
            }
            line(code, 2, "this._cf$triggerPending = true;");
            line(code, 2, "switch (nodeId) {");
            semantics
                .getNodes()
                .values()
                .stream()
                .filter(node -> node.operation() instanceof AwaitPlan)
                .forEach(node -> line(code, 3,
                        "case " + literal(node.id()) + " -> " + requestFlow(node.id(), null) + "();"));
            line(code, 3, "default -> throw " + triggerExecutorType + ".unknownNodeId(nodeId);");
            line(code, 2, "}");
            line(code, 2, "return _cf$buildResult();");
            line(code, 1, "}");
            line(code, 1, "");
        }

        if (triggerable() || hasParameters()) {
            line(code, 1, "private void _cf$loadParameters(java.util.Map<String, Object> context) {");
            line(code, 2, "java.util.Objects.requireNonNull(context, \"context\");");
            for (ProcessSemanticPlan.VariablePlan variable : semantics.getVariables().values()) {
                if (variable.role() != ProcessSemanticPlan.VariableRole.PARAM) {
                    continue;
                }
                contextAssignment(code, variable, 2);
            }
            line(code, 1, "}");
            line(code, 1, "");
        }

        if (triggerable()) {
            line(code, 1, "private void _cf$restoreState(java.util.Map<String, Object> context) {");
            line(code, 2, "_cf$loadParameters(context);");
            semantics
                .getVariables()
                .values()
                .stream()
                .filter(variable -> variable.role() != ProcessSemanticPlan.VariableRole.PARAM)
                .forEach(variable -> contextAssignment(code, variable, 2));
            line(code, 1, "}");
            line(code, 1, "");
        }

        line(code, 1, "private java.util.Map<String, Object> _cf$buildResult() {");
        List<ProcessSemanticPlan.VariablePlan> returns = semantics
            .getVariables()
            .values()
            .stream()
            .filter(variable -> variable.role() == ProcessSemanticPlan.VariableRole.RETURN)
            .toList();
        if (returns.isEmpty()) {
            line(code, 2, "return java.util.Collections.emptyMap();");
        } else if (returns.size() == 1) {
            ProcessSemanticPlan.VariablePlan returned = returns.get(0);
            line(code, 2,
                    "return java.util.Collections.singletonMap(" + literal(returned.name()) + ", this." + returned.name() + ");");
        } else {
            line(code, 2, "java.util.Map<String, Object> _cf$result = new java.util.LinkedHashMap<>();");
            returns.forEach(variable -> line(code, 2,
                    "_cf$result.put(" + literal(variable.name()) + ", this." + variable.name() + ");"));
            line(code, 2, "return _cf$result;");
        }
        line(code, 1, "}");
        line(code, 1, "");
    }

    private void contextAssignment(StringBuilder code, ProcessSemanticPlan.VariablePlan variable, int indent) {
        String key = literal(variable.name());
        String value = hasDefaultValue(variable)
                ? "context.getOrDefault(" + key + ", this." + variable.name() + ")"
                : "context.get(" + key + ")";
        String assignment = "this." + variable.name() + " = " + convertFromObject(value, variable.dataType()) + ";";
        line(code, indent, assignment);
    }

    private void branchFrameFactory(StringBuilder code) {
        if (!hasConcurrentGateway()) {
            return;
        }
        line(code, 1, "private " + simpleName + " _cf$forkBranchFrame() {");
        line(code, 2, simpleName + " _cf$branchFrame = new " + simpleName + "();");
        Set<String> copiedVariables = concurrentBranchVariables();
        semantics
            .getVariables()
            .values()
            .stream()
            .filter(variable -> copiedVariables.contains(variable.name()))
            .forEach(variable -> line(code, 2, "_cf$branchFrame." + variable.name() + " = this." + variable.name() + ";"));
        line(code, 2, "return _cf$branchFrame;");
        line(code, 1, "}");
        line(code, 1, "");
    }

    private void flowMethods(StringBuilder code) {
        for (int index = 0; index < flowMethodQueue.size(); index++) {
            FlowKey flow = flowMethodQueue.get(index);
            line(code, 1,
                    "private int " + flowMethodNames.get(flow) + parameterDeclaration(flow.startNodeId()) + " throws Exception {");
            flowBody(code, flow, 2);
            line(code, 1, "}");
            line(code, 1, "");
        }
    }

    private void flowBody(StringBuilder code, FlowKey flow, int indent) {
        String current = flow.startNodeId();
        Set<String> visited = new LinkedHashSet<>();
        int emittedNodes = 0;
        boolean signalDeclared = false;
        while (current != null && !current.equals(flow.boundaryNodeId())) {
            if (emittedNodes == MAX_NODES_PER_FLOW_METHOD) {
                line(code, indent, "return " + flowInvocation(current, flow.boundaryNodeId(), null) + ";");
                return;
            }
            if (!visited.add(current)) {
                throw new IllegalArgumentException(
                        "Cycle reached while generating structured flow at node '" + current + "'");
            }
            ProcessSemanticPlan.NodePlan node = semantics.requireNode(current);
            if (node.kind() == ProcessSemanticPlan.NodeKind.END) {
                line(code, indent, "return " + NORMAL + ";");
                return;
            }
            GatewayPlan gateway = structure.getGatewayPlan(current);
            if (gateway != null) {
                if (gateway.isJoin()) {
                    current = nextNode(node);
                    continue;
                }
                if (isNoOpParallel(gateway)) {
                    current = gateway.getConvergenceNodeId();
                    emittedNodes++;
                    continue;
                }
                String invocation = gatewayInvocation(node.id());
                if (isNormalTail(gateway.getConvergenceNodeId(), flow.boundaryNodeId())) {
                    line(code, indent, "return " + invocation + ";");
                    return;
                }
                signalDeclared = propagateSignal(code, indent, invocation, signalDeclared);
                current = gateway.getConvergenceNodeId();
                emittedNodes++;
                continue;
            }
            if (node.kind() == ProcessSemanticPlan.NodeKind.BREAK
                    || node.kind() == ProcessSemanticPlan.NodeKind.CONTINUE) {
                String signal = node.kind() == ProcessSemanticPlan.NodeKind.BREAK ? BREAK : CONTINUE;
                if (node.controlCondition() == null) {
                    line(code, indent, "return " + signal + ";");
                    return;
                }
                line(code, indent,
                        "if (com.alibaba.compileflow.engine.core.runtime.execution.ConditionSemantics.isTrue("
                        + node.controlCondition() + ")) {");
                line(code, indent + 1, "return " + signal + ";");
                line(code, indent, "}");
                current = node.outgoingTransitions().isEmpty() ? null : nextNode(node);
                emittedNodes++;
                continue;
            }
            if (node.kind() == ProcessSemanticPlan.NodeKind.ACTIVITY) {
                if (!activityReturnsSignal(node)) {
                    line(code, indent, activityInvocation(node) + ";");
                } else {
                    boolean normalTail = node.outgoingTransitions().isEmpty()
                            ? isScopeEnd(node)
                            : isNormalTail(nextNode(node), flow.boundaryNodeId());
                    if (normalTail) {
                        line(code, indent, "return " + activityInvocation(node) + ";");
                        return;
                    }
                    signalDeclared = propagateSignal(code, indent, activityInvocation(node), signalDeclared);
                }
                emittedNodes++;
            }
            if (node.outgoingTransitions().isEmpty()) {
                if (isScopeEnd(node)) {
                    line(code, indent, "return " + NORMAL + ";");
                    return;
                }
                throw new IllegalArgumentException(
                        "Node '" + node.id() + "' has no outgoing transition before its scope end");
            }
            current = nextNode(node);
        }
        line(code, indent, "return " + NORMAL + ";");
    }

    private boolean isNormalTail(String startNodeId, String boundaryNodeId) {
        String current = startNodeId;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && !current.equals(boundaryNodeId)) {
            if (!visited.add(current)) {
                return false;
            }
            ProcessSemanticPlan.NodePlan node = semantics.requireNode(current);
            if (node.kind() == ProcessSemanticPlan.NodeKind.END) {
                return true;
            }
            GatewayPlan gateway = structure.getGatewayPlan(current);
            if (gateway != null) {
                if (gateway.isJoin()) {
                    current = nextNode(node);
                    continue;
                }
                if (isNoOpParallel(gateway)) {
                    current = gateway.getConvergenceNodeId();
                    continue;
                }
                return false;
            }
            if (node.kind() == ProcessSemanticPlan.NodeKind.ACTIVITY
                    || node.kind() == ProcessSemanticPlan.NodeKind.BREAK
                    || node.kind() == ProcessSemanticPlan.NodeKind.CONTINUE) {
                return false;
            }
            if (node.outgoingTransitions().isEmpty()) {
                return isScopeEnd(node);
            }
            current = nextNode(node);
        }
        return true;
    }

    private boolean propagateSignal(StringBuilder code, int indent, String invocation, boolean declared) {
        line(code, indent, (declared ? "_cf$signal = " : "int _cf$signal = ") + invocation + ";");
        line(code, indent, "if (_cf$signal != " + NORMAL + ") {");
        line(code, indent + 1, "return _cf$signal;");
        line(code, indent, "}");
        return true;
    }

    private String nextNode(ProcessSemanticPlan.NodePlan node) {
        if (node.outgoingTransitions().size() != 1) {
            throw new IllegalArgumentException("Node '" + node.id() + "' must have exactly one outgoing transition");
        }
        return node.outgoingTransitions().get(0).targetId();
    }

    private void gatewayMethods(StringBuilder code) {
        for (GatewayPlan gateway : structure.getGatewayPlans().values()) {
            if (!gateway.isSplit() || isNoOpParallel(gateway)) {
                continue;
            }
            ProcessSemanticPlan.NodePlan node = semantics.requireNode(gateway.getGatewayId());
            line(code, 1,
                    "private int " + gatewayMethodNames.get(node.id()) + parameterDeclaration(node.id()) + " throws Exception {");
            if (node.kind() == ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY) {
                exclusiveGateway(code, node, gateway, 2);
            } else {
                concurrentGateway(code, node, gateway, 2);
                line(code, 2, "return " + NORMAL + ";");
            }
            line(code, 1, "}");
            line(code, 1, "");
        }
    }

    private boolean isScopeEnd(ProcessSemanticPlan.NodePlan node) {
        if (ProcessSemanticPlan.ROOT_SCOPE_ID.equals(node.scopeId())) {
            return false;
        }
        ProcessSemanticPlan.ScopeBoundary boundary = semantics.requireNode(node.scopeId()).scopeBoundary();
        return boundary != null && boundary.endNodeId().equals(node.id());
    }

    private void exclusiveGateway(StringBuilder code, ProcessSemanticPlan.NodePlan node, GatewayPlan gateway,
            int indent) {
        ProcessSemanticPlan.TransitionPlan fallback = null;
        List<ProcessSemanticPlan.TransitionPlan> conditions = new ArrayList<>();
        for (ProcessSemanticPlan.TransitionPlan transition : node.outgoingTransitions()) {
            if (transition.defaultFlow() || transition.condition() == null) {
                if (fallback == null) {
                    fallback = transition;
                }
            } else {
                conditions.add(transition);
            }
        }

        for (ProcessSemanticPlan.TransitionPlan transition : conditions) {
            String condition = "if (com.alibaba.compileflow.engine.core.runtime.execution.ConditionSemantics.isTrue("
                    + transition.condition() + ")) {";
            int organizedLength = condition.length() - "com.alibaba.compileflow.engine.core.runtime.execution."
                .length();
            if (organizedLength + indent * 4 <= 120) {
                line(code, indent, condition);
            } else {
                line(code, indent,
                        "if (com.alibaba.compileflow.engine.core.runtime.execution.ConditionSemantics.isTrue(");
                List<String> expressionLines = logicalExpressionLines(transition.condition());
                for (int index = 0; index < expressionLines.size(); index++) {
                    String suffix = index + 1 == expressionLines.size() ? ")) {" : "";
                    line(code, indent + 2, expressionLines.get(index) + suffix);
                }
            }
            line(code, indent + 1,
                    "return " + flowInvocation(transition.targetId(), gateway.getConvergenceNodeId(), null) + ";");
            line(code, indent, "}");
        }
        gatewayFallback(code, node, gateway, fallback, indent);
    }

    private void gatewayFallback(StringBuilder code, ProcessSemanticPlan.NodePlan node, GatewayPlan gateway,
            ProcessSemanticPlan.TransitionPlan fallback, int indent) {
        if (fallback != null) {
            line(code, indent,
                    "return " + flowInvocation(fallback.targetId(), gateway.getConvergenceNodeId(), null) + ";");
            return;
        }
        line(code, indent,
                "throw com.alibaba.compileflow.engine.core.runtime.execution.GatewayExecutor.noBranchMatched("
                + literal(node.id()) + ");");
    }

    private void concurrentGateway(StringBuilder code, ProcessSemanticPlan.NodePlan node, GatewayPlan gateway,
            int indent) {
        boolean inclusive = node.kind() == ProcessSemanticPlan.NodeKind.INCLUSIVE_GATEWAY;
        String factory = inclusive ? "inclusive" : "parallel";
        line(code, indent, "var _cf$branches =");
        line(code, indent + 1,
                "com.alibaba.compileflow.engine.core.runtime.execution.GatewayExecutor.<_cf$BranchOutcome>" + factory
                + "(" + literal(node.id()) + ");");
        ProcessSemanticPlan.TransitionPlan fallback = null;
        int ordinal = 0;
        for (ProcessSemanticPlan.TransitionPlan transition : node.outgoingTransitions()) {
            if (inclusive && (transition.defaultFlow() || transition.condition() == null)) {
                if (fallback == null) {
                    fallback = transition;
                }
                ordinal++;
                continue;
            }
            String operation = inclusive
                    ? "when(com.alibaba.compileflow.engine.core.runtime.execution.ConditionSemantics.isTrue("
                    + transition.condition() + "), "
                    : "branch(";
            branchRegistration(code, operation, ordinal, transition, gateway, indent);
            ordinal++;
        }
        if (inclusive && fallback != null) {
            int fallbackOrdinal = node.outgoingTransitions().indexOf(fallback);
            branchRegistration(code, "otherwise(", fallbackOrdinal, fallback, gateway, indent);
        }
        line(code, indent, "for (var _cf$branchResult : _cf$branches.run()) {");
        line(code, indent + 1, "_cf$BranchOutcome _cf$branchOutcome = _cf$branchResult.value();");
        line(code, indent + 1, "if (_cf$branchOutcome.signal() != " + NORMAL + ") {");
        line(code, indent + 2, "return _cf$branchOutcome.signal();");
        line(code, indent + 1, "}");
        line(code, indent + 1, "switch (_cf$branchResult.branchId().targetId()) {");
        Set<String> emitted = new LinkedHashSet<>();
        for (ProcessSemanticPlan.TransitionPlan transition : node.outgoingTransitions()) {
            if (!emitted.add(transition.targetId())) {
                continue;
            }
            GatewayBranchPlan branch = gateway.requireBranch(GatewayBranchKey.of(node.id(), transition.targetId()));
            line(code, indent + 2, "case " + literal(transition.targetId()) + " -> {");
            for (String variable : branch.getWrites()) {
                line(code, indent + 3, "this." + variable + " = _cf$branchOutcome.branchFrame()." + variable + ";");
            }
            line(code, indent + 2, "}");
        }
        line(code, indent + 2, "default -> throw new IllegalStateException(\"Unexpected gateway branch\");");
        line(code, indent + 1, "}");
        line(code, indent, "}");
    }

    private void branchRegistration(StringBuilder code, String operation, int ordinal,
            ProcessSemanticPlan.TransitionPlan transition, GatewayPlan gateway, int indent) {
        line(code, indent, "_cf$branches." + operation + ordinal + ", " + literal(transition.targetId()) + ", () -> {");
        line(code, indent + 1, simpleName + " _cf$branchFrame = _cf$forkBranchFrame();");
        line(code, indent + 1,
                "int _cf$signal = " + flowInvocation(transition.targetId(), gateway.getConvergenceNodeId(),
                        "_cf$branchFrame") + ";");
        line(code, indent + 1, "return new _cf$BranchOutcome(_cf$branchFrame, _cf$signal);");
        line(code, indent, "});");
    }

    private void activityMethods(StringBuilder code) {
        Set<String> emitted = new LinkedHashSet<>();
        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            if (node.kind() != ProcessSemanticPlan.NodeKind.ACTIVITY || !emitted.add(activityMethod(node.id()))) {
                continue;
            }
            boolean reusable = isReusableActionActivity(node);
            boolean returnsSignal = activityReturnsSignal(node);
            String parameters =
                    reusable ? reusableActionParameterDeclaration(node.id()) : parameterDeclaration(node.id());
            String throwsClause = node.operation() instanceof AwaitPlan ? "" : " throws Exception";
            line(code, 1,
                    "private " + (returnsSignal ? "int " : "void ") + activityMethod(node.id()) + parameters + throwsClause + " {");
            if (node.operation() instanceof AwaitPlan await) {
                line(code, 2, "if (!this._cf$triggerPending) {");
                line(code, 3, "return " + PAUSED + ";");
                line(code, 2, "}");
                line(code, 2, "this._cf$triggerPending = false;");
                if (await.event() != null) {
                    line(code, 2,
                            "com.alibaba.compileflow.engine.core.runtime.execution.TriggerValidation.requireEvent("
                            + literal(node.id()) + ", " + literal(await.event()) + ", this._cf$triggerEvent);");
                }
            }
            if (node.iteration() instanceof IterationPlan.While loop) {
                whileActivity(code, node, loop);
            } else if (node.iteration() instanceof IterationPlan.ForEach loop) {
                forEachActivity(code, node, loop);
            } else if (reusable) {
                operation(code, node, 2, "_cf$nodeId");
            } else if (returnsSignal) {
                unit(code, node, 2);
            } else {
                operation(code, node, 2);
            }
            line(code, 1, "}");
            line(code, 1, "");
        }
    }

    private void whileActivity(StringBuilder code, ProcessSemanticPlan.NodePlan node, IterationPlan.While loop) {
        line(code, 2, "int _cf$completedIterations = 0;");
        if (loop.indexVariable() != null) {
            line(code, 2, "int " + loop.indexVariable() + " = _cf$completedIterations;");
        }
        line(code, 2, "boolean _cf$firstIteration = true;");
        line(code, 2, "while (true) {");
        String evaluationGuard = loop.timing() == IterationPlan.ConditionTiming.BEFORE ? "true" : "!_cf$firstIteration";
        line(code, 3, "if (" + evaluationGuard + ") {");
        if (loop.limitBehavior() == IterationPlan.LimitBehavior.STOP && loop.maxIterations() != null) {
            line(code, 4, "if (_cf$completedIterations >= " + loop.maxIterations() + ") {");
            line(code, 5, "return " + NORMAL + ";");
            line(code, 4, "}");
        }
        line(code, 4,
                "if (!com.alibaba.compileflow.engine.core.runtime.execution.ConditionSemantics.isTrue(" + loop.condition() + ")) {");
        line(code, 5, "return " + NORMAL + ";");
        line(code, 4, "}");
        if (loop.limitBehavior() == IterationPlan.LimitBehavior.FAIL && loop.maxIterations() != null) {
            line(code, 4,
                    "com.alibaba.compileflow.engine.core.runtime.execution.LoopSemantics.requireIterationAllowed("
                    + literal(node.id()) + ", _cf$completedIterations, " + loop.maxIterations() + ");");
        }
        line(code, 3, "}");
        if (loop.timing() == IterationPlan.ConditionTiming.AFTER
                && loop.limitBehavior() == IterationPlan.LimitBehavior.STOP && loop.maxIterations() != null) {
            line(code, 3, "else if (_cf$completedIterations >= " + loop.maxIterations() + ") {");
            line(code, 4, "return " + NORMAL + ";");
            line(code, 3, "}");
        }
        line(code, 3, "_cf$firstIteration = false;");
        if (requiresLoopSignal()) {
            unitToSignal(code, node, 3);
        } else {
            unitWithoutSignal(code, node, 3);
        }
        if (triggerable()) {
            line(code, 3, "if (_cf$signal == " + PAUSED + ") {");
            line(code, 4, "return _cf$signal;");
            line(code, 3, "}");
        }
        if (hasNodeKind(ProcessSemanticPlan.NodeKind.BREAK)) {
            line(code, 3, "if (_cf$signal == " + BREAK + ") {");
            line(code, 4, "return " + NORMAL + ";");
            line(code, 3, "}");
        }
        line(code, 3, "_cf$completedIterations++;");
        if (loop.indexVariable() != null) {
            line(code, 3, loop.indexVariable() + " = _cf$completedIterations;");
        }
        line(code, 2, "}");
    }

    private void forEachActivity(StringBuilder code, ProcessSemanticPlan.NodePlan node, IterationPlan.ForEach loop) {
        String itemType = loop.itemType();
        line(code, 2, "java.util.List<" + boxedSourceType(itemType) + "> _cf$iterationValues =");
        line(code, 3,
                "com.alibaba.compileflow.engine.core.runtime.execution.LoopSemantics.<" + boxedSourceType(itemType)
                + ">snapshot(" + expression(node.id(), loop.collectionVariable()) + ", " + literal(node.id()) + ", "
                + boxedSourceType(itemType) + ".class);");
        if (loop.outputTargetVariable() != null) {
            line(code, 2, "java.util.List<Object> _cf$collectedOutputs =");
            line(code, 3, "new java.util.ArrayList<>(_cf$iterationValues.size());");
        }
        line(code, 2, "for (int _cf$position = 0; _cf$position < _cf$iterationValues.size(); _cf$position++) {");
        if (loop.outputSourceVariable() != null) {
            ProcessSemanticPlan.VariablePlan output = semantics.requireVariable(loop.outputSourceVariable());
            line(code, 3, "this." + output.name() + " = " + defaultValue(output.dataType(), output.defaultValue()) + ";");
        }
        line(code, 3, itemType + " " + loop.itemVariable() + " = _cf$iterationValues.get(_cf$position);");
        if (loop.indexVariable() != null) {
            line(code, 3, "int " + loop.indexVariable() + " = _cf$position;");
        }
        if (requiresLoopSignal()) {
            unitToSignal(code, node, 3);
        } else {
            unitWithoutSignal(code, node, 3);
        }
        if (triggerable()) {
            line(code, 3, "if (_cf$signal == " + PAUSED + ") {");
            line(code, 4, "return _cf$signal;");
            line(code, 3, "}");
        }
        if (loop.outputTargetVariable() != null) {
            line(code, 3,
                    "_cf$collectedOutputs.add("
                    + expression(node.scopeBoundary() == null ? node.id() : node.scopeBoundary().startNodeId(),
                            loop.outputSourceVariable()) + ");");
        }
        if (hasNodeKind(ProcessSemanticPlan.NodeKind.BREAK)) {
            line(code, 3, "if (_cf$signal == " + BREAK + ") {");
            line(code, 4, "break;");
            line(code, 3, "}");
        }
        line(code, 2, "}");
        if (loop.outputTargetVariable() != null) {
            String type = semantics.requireVariable(loop.outputTargetVariable()).dataType();
            line(code, 2,
                    "this." + loop.outputTargetVariable() + " = " + convertFromObject("_cf$collectedOutputs", type) + ";");
        }
        line(code, 2, "return " + NORMAL + ";");
    }

    private void unitToSignal(StringBuilder code, ProcessSemanticPlan.NodePlan node, int indent) {
        if (node.scopeBoundary() == null) {
            operation(code, node, indent);
            line(code, indent, "int _cf$signal = " + NORMAL + ";");
            return;
        }
        operation(code, node, indent);
        line(code, indent, "int _cf$signal = " + flowInvocation(node.scopeBoundary().startNodeId(), null, null) + ";");
    }

    private void unitWithoutSignal(StringBuilder code, ProcessSemanticPlan.NodePlan node, int indent) {
        operation(code, node, indent);
        if (node.scopeBoundary() != null) {
            line(code, indent, flowInvocation(node.scopeBoundary().startNodeId(), null, null) + ";");
        }
    }

    private boolean requiresLoopSignal() {
        return triggerable() || hasNodeKind(ProcessSemanticPlan.NodeKind.BREAK);
    }

    private void unit(StringBuilder code, ProcessSemanticPlan.NodePlan node, int indent) {
        operation(code, node, indent);
        if (node.scopeBoundary() != null) {
            line(code, indent, "return " + flowInvocation(node.scopeBoundary().startNodeId(), null, null) + ";");
        } else {
            line(code, indent, "return " + NORMAL + ";");
        }
    }

    private void operation(StringBuilder code, ProcessSemanticPlan.NodePlan node, int indent) {
        operation(code, node, indent, literal(node.id()));
    }

    private void operation(StringBuilder code, ProcessSemanticPlan.NodePlan node, int indent, String nodeIdSource) {
        if (node.operation() instanceof ActionPlan action) {
            actionCall(code, node.id(), nodeIdSource, action, indent);
        } else if (node.operation() instanceof ProcessCallPlan call) {
            processCall(code, node.id(), call, indent);
        } else if (node.operation() != null && !(node.operation() instanceof AwaitPlan)) {
            throw new IllegalArgumentException(
                    "Unsupported compiled Process operation at node '" + node.id() + "': " + node
                        .operation()
                        .getClass()
                        .getName());
        }
    }

    private void actionCall(StringBuilder code, String nodeId, String nodeIdSource, ActionPlan action, int indent) {
        String invocation = actionInvocation(nodeId);
        EffectiveInvocationPolicy policy = action.invocationPolicy();
        if (defaultPolicy(policy)) {
            if (action.output() != null) {
                line(code, indent, rawSourceType(action.output().targetType()) + " _cf$actionResult;");
            }
            line(code, indent,
                    "com.alibaba.compileflow.engine.core.runtime.execution.ActionExecutor.ActionScope _cf$actionScope = com.alibaba.compileflow.engine.core.runtime.execution.ActionExecutor.open("
                    + nodeIdSource + ");");
            line(code, indent, "try (_cf$actionScope) {");
            line(code, indent + 1, action.output() == null ? invocation + ";" : "_cf$actionResult = " + invocation + ";");
            line(code, indent, "} catch (Exception _cf$actionFailure) {");
            line(code, indent + 1, "throw _cf$actionScope.failure(_cf$actionFailure);");
            line(code, indent, "}");
            if (action.output() != null) {
                line(code, indent, "this." + action.output().target() + " = _cf$actionResult;");
            }
            return;
        }
        String policySource = policyFieldNames.get(nodeId);
        if (action.output() == null) {
            line(code, indent, "com.alibaba.compileflow.engine.core.runtime.execution.ActionExecutor.run(");
            line(code, indent + 1, nodeIdSource + ",");
            line(code, indent + 1, "() -> " + invocation + ",");
            line(code, indent + 1, policySource + ");");
        } else {
            line(code, indent, "com.alibaba.compileflow.engine.core.runtime.execution.ActionExecutor.callAndCommit(");
            line(code, indent + 1, nodeIdSource + ",");
            line(code, indent + 1, "() -> " + invocation + ",");
            line(code, indent + 1, policySource + ",");
            line(code, indent + 1, "_cf$actionResult -> this." + action.output().target() + " = _cf$actionResult);");
        }
    }

    private void processCall(StringBuilder code, String nodeId, ProcessCallPlan call, int indent) {
        List<ProcessCallPlan.Input> sourceInputs =
                call
            .inputs()
            .stream()
            .filter(input -> input.sourceExpression() != null)
            .toList();
        String inputSource;
        if (sourceInputs.isEmpty()) {
            inputSource = "java.util.Collections.emptyMap()";
        } else if (sourceInputs.size() == 1) {
            ProcessCallPlan.Input input = sourceInputs.get(0);
            String value = expression(nodeId, input.sourceExpression());
            inputSource = "java.util.Collections.singletonMap(" + literal(input.target()) + ", " + value + ")";
        } else {
            line(code, indent, "java.util.Map<String, Object> _cf$processCallInput = new java.util.LinkedHashMap<>();");
            for (ProcessCallPlan.Input input : sourceInputs) {
                String value = expression(nodeId, input.sourceExpression());
                line(code, indent, "_cf$processCallInput.put(" + literal(input.target()) + ", " + value + ");");
            }
            inputSource = "_cf$processCallInput";
        }
        String continuation = "\n" + "    ".repeat(indent + 1);
        String invocation = ".callProcess(" + literal(nodeId) + ", " + inputSource + ")";
        if (invocation.length() + (indent + 1) * 4 > 120) {
            String argumentIndent = "\n" + "    ".repeat(indent + 2);
            invocation = ".callProcess(" + argumentIndent + literal(nodeId) + "," + argumentIndent + inputSource + ")";
        }
        String processCall = "com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder.requireCurrent()"
                + continuation + invocation + continuation + ".orElseThrow()";
        if (call.outputs().isEmpty()) {
            line(code, indent, processCall + ";");
            return;
        }
        line(code, indent, "java.util.Map<String, Object> _cf$processCallOutput = " + processCall + ";");
        int outputIndex = 0;
        List<String> convertedOutputs = new ArrayList<>();
        Set<String> outputNames = new LinkedHashSet<>();
        for (ProcessCallPlan.Output output : call.outputs()) {
            String convertedOutput = "_cf$converted" + JavaIdentifiers.toMethodSuffix(output.source());
            if (!outputNames.add(convertedOutput)) {
                convertedOutput += outputIndex;
                outputNames.add(convertedOutput);
            }
            outputIndex++;
            convertedOutputs.add(convertedOutput);
            String requiredOutput = "com.alibaba.compileflow.engine.core.runtime.execution.ProcessCallOutputs.requireOutput(_cf$processCallOutput, "
                    + literal(call.code()) + ", " + literal(nodeId) + ", " + literal(output.source()) + ")";
            String assignment = output.targetType() + " " + convertedOutput + " = "
                    + convertFromObject(requiredOutput, output.targetType()) + ";";
            if (assignment.length() + indent * 4 <= 120) {
                line(code, indent, assignment);
            } else {
                emitProcessCallOutputAssignment(code, indent, output.targetType(), convertedOutput, call.code(), nodeId,
                        output.source());
            }
        }
        outputIndex = 0;
        for (ProcessCallPlan.Output output : call.outputs()) {
            line(code, indent, "this." + output.target() + " = " + convertedOutputs.get(outputIndex++) + ";");
        }
    }

    private void emitProcessCallOutputAssignment(StringBuilder code, int indent, String targetType, String target,
            String calledProcessCode, String nodeId, String calledVariableName) {
        Class<?> type = DataTypes.getJavaClass(targetType);
        Class<?> raw = type.isPrimitive() ? DataTypes.getWrapperClass(type) : type;
        String suffix = type.isPrimitive() ? "." + DataTypes.getUnboxingMethodName(type) : "";
        line(code, indent, targetType + " " + target + " =");
        line(code, indent + 1, "com.alibaba.compileflow.engine.core.type.DataTypes.transfer(");
        line(code, indent + 2, "com.alibaba.compileflow.engine.core.runtime.execution.ProcessCallOutputs.requireOutput(");
        line(code, indent + 3, "_cf$processCallOutput,");
        line(code, indent + 3, literal(calledProcessCode) + ",");
        line(code, indent + 3, literal(nodeId) + ",");
        line(code, indent + 3, literal(calledVariableName) + "),");
        line(code, indent + 2, sourceType(raw) + ".class)" + suffix + ";");
    }

    private void actionMethods(StringBuilder code) {
        Set<String> emitted = new LinkedHashSet<>();
        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            if (!(node.operation() instanceof ActionPlan action) || !emitted.add(actionMethod(node.id()))) {
                continue;
            }
            String returnType = action.output() == null ? "void" : action.output().targetType();
            line(code, 1,
                    "private " + returnType + " " + actionMethod(node.id()) + actionParameterDeclaration(node.id())
                    + " throws Exception {");
            actionBody(code, node.id(), action, 2);
            line(code, 1, "}");
            line(code, 1, "");
        }
    }

    private void actionBody(StringBuilder code, String nodeId, ActionPlan action, int indent) {
        if (action.invocation() instanceof ActionInvocation.Script) {
            scriptAction(code, nodeId, action, indent);
            return;
        }
        List<String> arguments = action
            .inputs()
            .stream()
            .map(input -> actionInput(nodeId, input))
            .toList();
        String invocation;
        if (action.invocation() instanceof ActionInvocation.Java java) {
            String target = "new " + sourceClassName(java.className()) + "()";
            String methodCall = "." + java.method() + "(" + String.join(", ", arguments) + ")";
            invocation = target + methodCall;
            String statement =
                    action.output() == null
                    ? invocation + ";"
                    : "return " + convertedResult(invocation, action.output()) + ";";
            String renderedStatement = shortenQualifiedTypes(statement, typeReplacements(), new LinkedHashSet<>());
            if (indent * 4 + renderedStatement.length() > 120) {
                invocation = target + "\n" + "    ".repeat(indent + 1) + methodCall;
            }
        } else if (action.invocation() instanceof ActionInvocation.SpringBean spring) {
            String continuation = "\n" + "    ".repeat(indent + 1);
            invocation = "com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder"
                    + continuation + ".component(" + literal(spring.beanName()) + ", "
                    + sourceClassName(spring.declaredClass()) + ".class)" + continuation + "." + spring.method() + "("
                    + String.join(", ", arguments) + ")";
        } else {
            throw new IllegalArgumentException(
                    "Unsupported compiled Action at node '" + nodeId + "': " + action
                        .invocation()
                        .getClass()
                        .getName());
        }
        if (action.output() == null) {
            line(code, indent, invocation + ";");
        } else {
            line(code, indent, "return " + convertedResult(invocation, action.output()) + ";");
        }
    }

    private void scriptAction(StringBuilder code, String nodeId, ActionPlan action, int indent) {
        String inputs;
        if (action.inputs().isEmpty()) {
            inputs = "java.util.Collections.emptyMap()";
        } else if (action.inputs().size() == 1) {
            ActionPlan.Input input = action.inputs().get(0);
            inputs = "java.util.Collections.singletonMap(" + literal(input.target()) + ", " + actionInput(nodeId, input)
                    + ")";
        } else {
            line(code, indent, "java.util.Map<String, Object> _cf$scriptInputs = new java.util.LinkedHashMap<>();");
            for (ActionPlan.Input input : action.inputs()) {
                line(code, indent,
                        "_cf$scriptInputs.put(" + literal(input.target()) + ", " + actionInput(nodeId, input) + ");");
            }
            inputs = "java.util.Collections.unmodifiableMap(_cf$scriptInputs)";
        }
        String invocation = "com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder.evaluateScript("
                + scriptSpecFieldNames.get(nodeId) + ", " + inputs + ")";
        if (action.output() == null) {
            emitInvocation(code, indent, invocation + ";");
        } else {
            line(code, indent, "Object _cf$scriptResult =");
            emitInvocation(code, indent + 1, invocation + ";");
            String typedResult = convertFromObject("_cf$scriptResult", action.output().resultType());
            line(code, indent, "return " + convertedResult(typedResult, action.output()) + ";");
        }
    }

    private void emitInvocation(StringBuilder code, int indent, String invocation) {
        if (invocation.length() + indent * 4 <= 120) {
            line(code, indent, invocation);
            return;
        }
        int arguments = invocation.indexOf('(');
        int separator = invocation.indexOf(", ", arguments);
        if (arguments < 0 || separator < 0) {
            line(code, indent, invocation);
            return;
        }
        line(code, indent, invocation.substring(0, arguments + 1));
        line(code, indent + 1, invocation.substring(arguments + 1, separator + 1));
        line(code, indent + 1, invocation.substring(separator + 2));
    }

    private String actionInput(String nodeId, ActionPlan.Input input) {
        if (input.source() instanceof ActionPlan.InputSource.Literal literal) {
            return defaultValue(input.declaredType(), literal.value());
        }
        if (input.source() instanceof ActionPlan.InputSource.Expression expression) {
            String source = expression(nodeId, expression.value());
            ProcessSemanticPlan.VisibleVariable visible = visibleVariable(nodeId, expression.value());
            if (visible == null) {
                return source;
            }
            return conversion(visible.typeName(), input.declaredType(), source);
        }
        throw new IllegalStateException(
                "Compiled ProcessRuntime cannot materialize Action input source " + input.source().getClass());
    }

    private String convertedResult(String invocation, ActionPlan.Output output) {
        return conversion(output.resultType(), output.targetType(), invocation);
    }

    private String expression(String nodeId, String source) {
        ProcessSemanticPlan.VisibleVariable visible = visibleVariable(nodeId, source);
        if (visible == null) {
            return source;
        }
        return visible.origin() == ProcessSemanticPlan.VisibleVariable.Origin.PROCESS ? "this." + source : source;
    }

    private ProcessSemanticPlan.VisibleVariable visibleVariable(String nodeId, String name) {
        return semantics.visibleVariables(nodeId).get(name);
    }

    private String conversion(String sourceType, String targetType, String expression) {
        Class<?> source = DataTypes.getJavaClass(sourceType);
        Class<?> target = DataTypes.getJavaClass(targetType);
        if (DataTypes.isJavaAssignmentCompatible(source, target)) {
            return expression;
        }
        if (target.isPrimitive()) {
            Class<?> wrapper = DataTypes.getWrapperClass(target);
            return "com.alibaba.compileflow.engine.core.type.DataTypes.transfer(" + expression + ", "
                    + sourceType(wrapper) + ".class)." + DataTypes.getUnboxingMethodName(target);
        }
        return "com.alibaba.compileflow.engine.core.type.DataTypes.transfer(" + expression + ", " + sourceType(target)
                + ".class)";
    }

    private String convertFromObject(String expression, String targetType) {
        Class<?> type = DataTypes.getJavaClass(targetType);
        Class<?> raw = type.isPrimitive() ? DataTypes.getWrapperClass(type) : type;
        String conversion = "com.alibaba.compileflow.engine.core.type.DataTypes.transfer(" + expression + ", "
                + sourceType(raw) + ".class)";
        return type.isPrimitive() ? conversion + "." + DataTypes.getUnboxingMethodName(type) : conversion;
    }

    private String defaultValue(String typeName, String value) {
        return DataTypes.generateDefaultValueCode(DataTypes.getJavaClass(typeName), value).expression();
    }

    private String policySource(EffectiveInvocationPolicy policy) {
        String continuation = "\n            ";
        return "com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy.of(" + continuation
                + policy.getTimeoutMs() + "L, " + policy.getAttemptTimeoutMs() + "L, " + policy.getMaxAttempts() + ", "
                + policy.getInitialBackoffMs() + "L, " + policy.getBackoffMultiplier() + "d, "
                + policy.getMaxBackoffMs() + "L," + continuation
                + "com.alibaba.compileflow.engine.core.model.action.RetryJitter." + policy.getJitter().name() + ", "
                + literal(policy.getRetryOn()) + ", " + literal(policy.getOnFailure()) + ")";
    }

    private static boolean defaultPolicy(EffectiveInvocationPolicy policy) {
        return EffectiveInvocationPolicy.defaults().equals(policy);
    }

    private List<DataTypes.DefaultValueCode> defaultValueCodes() {
        List<DataTypes.DefaultValueCode> result = new ArrayList<>();
        semantics
            .getVariables()
            .values()
            .forEach(variable -> result.add(DataTypes.generateDefaultValueCode(DataTypes.getJavaClass(variable.dataType()),
                    variable.defaultValue())));
        semantics.getNodes().values().forEach(node -> {
            if (node.operation() instanceof ActionPlan action) {
                action
                    .inputs()
                    .stream()
                    .filter(input -> input.source() instanceof ActionPlan.InputSource.Literal)
                    .map(input -> DataTypes.generateDefaultValueCode(DataTypes.getJavaClass(input.declaredType()),
                            ((ActionPlan.InputSource.Literal) input.source()).value()))
                    .forEach(result::add);
            }
        });
        return List.copyOf(result);
    }

    private static String simpleTypeName(String typeName) {
        int separator = typeName.lastIndexOf('.');
        return separator < 0 ? typeName : typeName.substring(separator + 1);
    }

    private String organizeImports(String source) {
        Map<String, String> replacements = typeReplacements();
        Set<String> usedTypes = new LinkedHashSet<>();
        String body = shortenQualifiedTypes(source, replacements, usedTypes);
        Set<String> organizedImports = new TreeSet<>();
        String generatedPackage = className.substring(0, className.lastIndexOf('.'));
        usedTypes
            .stream()
            .filter(type -> !packageName(type).equals("java.lang"))
            .filter(type -> !packageName(type).equals(generatedPackage))
            .forEach(organizedImports::add);
        if (organizedImports.isEmpty()) {
            return body;
        }

        int packageEnd = body.indexOf('\n');
        StringBuilder result = new StringBuilder(body.length() + organizedImports.size() * 48);
        result.append(body, 0, packageEnd + 1).append('\n');
        organizedImports.forEach(type -> result.append("import ").append(type).append(";\n"));
        result.append(body, packageEnd + 1, body.length());
        return result.toString();
    }

    private Map<String, String> typeReplacements() {
        if (typeReplacements != null) {
            return typeReplacements;
        }
        Set<String> candidates = typeCandidates();
        Map<String, Set<String>> bySimpleName = new TreeMap<>();
        candidates.forEach(type -> bySimpleName
            .computeIfAbsent(simpleTypeName(type), ignored -> new LinkedHashSet<>())
            .add(type));

        Set<String> variableNames = new LinkedHashSet<>(semantics.getVariables().keySet());
        semantics.getNodes().values().forEach(node -> {
            if (node.iteration() instanceof IterationPlan.ForEach loop) {
                variableNames.add(loop.itemVariable());
                variableNames.add(loop.indexVariable());
            } else if (node.iteration() instanceof IterationPlan.While loop) {
                variableNames.add(loop.indexVariable());
            }
        });
        Map<String, String> replacements = new LinkedHashMap<>();
        bySimpleName.forEach((simpleName, types) -> {
            if (simpleName.equals(this.simpleName) || variableNames.contains(simpleName)) {
                return;
            }
            if (types.size() == 1) {
                replacements.put(types.iterator().next(), simpleName);
            }
        });
        typeReplacements = Map.copyOf(replacements);
        return typeReplacements;
    }

    private Set<String> typeCandidates() {
        Set<String> result = new LinkedHashSet<>(GENERATOR_TYPES);
        defaultValueCodes()
            .stream()
            .flatMap(value -> value.referencedTypes().stream())
            .filter(type -> !type.isPrimitive())
            .map(JavaProcessCodeGenerator::sourceType)
            .forEach(type -> addTypeCandidates(result, type));
        semantics
            .getVariables()
            .values()
            .forEach(variable -> addTypeCandidates(result, variable.dataType()));
        semantics.getNodes().values().forEach(node -> {
            if (node.iteration() instanceof IterationPlan.ForEach forEach) {
                addTypeCandidates(result, forEach.itemType());
            }
            if (node.operation() instanceof ActionPlan action) {
                action
                    .inputs()
                    .forEach(input -> addTypeCandidates(result, input.declaredType()));
                if (action.output() != null) {
                    addTypeCandidates(result, action.output().resultType());
                    addTypeCandidates(result, action.output().targetType());
                }
                if (action.invocation() instanceof ActionInvocation.Java java) {
                    result.add(java.className());
                } else if (action.invocation() instanceof ActionInvocation.SpringBean spring) {
                    result.add(spring.declaredClass());
                }
            } else if (node.operation() instanceof ProcessCallPlan call) {
                call
                    .outputs()
                    .forEach(output -> addTypeCandidates(result, output.targetType()));
            }
        });
        return result;
    }

    private static void addTypeCandidates(Set<String> target, String sourceName) {
        JavaTypeName
            .of(sourceName)
            .getReferencedTypes()
            .stream()
            .map(JavaTypeName::getImportName)
            .filter(Objects::nonNull)
            .forEach(target::add);
    }

    private static String shortenQualifiedTypes(String source, Map<String, String> replacements, Set<String> usedTypes) {
        List<String> qualifiedNames = replacements
            .keySet()
            .stream()
            .sorted((left, right) -> Integer.compare(right.length(), left.length()))
            .toList();
        StringBuilder result = new StringBuilder(source.length());
        int cursor = 0;
        while (cursor < source.length()) {
            if (source.startsWith("//", cursor)) {
                cursor = copyThroughLine(source, cursor, result);
                continue;
            }
            if (source.startsWith("/*", cursor)) {
                cursor = copyThrough(source, cursor, "*/", 2, result);
                continue;
            }
            if (source.startsWith("\"\"\"", cursor)) {
                cursor = copyThrough(source, cursor, "\"\"\"", 3, result);
                continue;
            }
            char current = source.charAt(cursor);
            if (current == '\"' || current == '\'') {
                cursor = copyQuoted(source, cursor, current, result);
                continue;
            }
            String match = null;
            for (String candidate : qualifiedNames) {
                if (source.startsWith(candidate, cursor) && typeBoundary(source, cursor - 1)
                        && typeBoundary(source, cursor + candidate.length())) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                result.append(current);
                cursor++;
            } else {
                result.append(replacements.get(match));
                usedTypes.add(match);
                cursor += match.length();
            }
        }
        return result.toString();
    }

    private static boolean typeBoundary(String source, int index) {
        return index < 0 || index >= source.length()
                || (!Character.isJavaIdentifierPart(source.charAt(index)) && source.charAt(index) != '$');
    }

    private static int copyThroughLine(String source, int start, StringBuilder target) {
        int end = source.indexOf('\n', start + 2);
        int exclusiveEnd = end < 0 ? source.length() : end + 1;
        target.append(source, start, exclusiveEnd);
        return exclusiveEnd;
    }

    private static int copyThrough(String source, int start, String delimiter, int delimiterLength,
            StringBuilder target) {
        int end = source.indexOf(delimiter, start + delimiterLength);
        int exclusiveEnd = end < 0 ? source.length() : end + delimiterLength;
        target.append(source, start, exclusiveEnd);
        return exclusiveEnd;
    }

    private static int copyQuoted(String source, int start, char delimiter, StringBuilder target) {
        int cursor = start + 1;
        while (cursor < source.length()) {
            char current = source.charAt(cursor++);
            if (current == '\\' && cursor < source.length()) {
                cursor++;
            } else if (current == delimiter) {
                break;
            }
        }
        target.append(source, start, cursor);
        return cursor;
    }

    private static String packageName(String typeName) {
        int separator = typeName.lastIndexOf('.');
        return separator < 0 ? "" : typeName.substring(0, separator);
    }

    private boolean triggerable() {
        return semantics
            .getNodes()
            .values()
            .stream()
            .anyMatch(node -> node.operation() instanceof AwaitPlan);
    }

    private String activityMethod(String nodeId) {
        return Objects.requireNonNull(activityMethodNames.get(nodeId), "Unknown activity node: " + nodeId);
    }

    private String activityInvocation(ProcessSemanticPlan.NodePlan node) {
        return activityMethod(node.id())
                + (isReusableActionActivity(node) ? reusableActionArgumentList(node.id()) : argumentList(node.id()));
    }

    private boolean isReusableActionActivity(ProcessSemanticPlan.NodePlan node) {
        return node.operation() instanceof ActionPlan && node.iteration() == null && node.scopeBoundary() == null;
    }

    private boolean activityReturnsSignal(ProcessSemanticPlan.NodePlan node) {
        return node.operation() instanceof AwaitPlan || node.iteration() != null || node.scopeBoundary() != null;
    }

    private String actionMethod(String nodeId) {
        return Objects.requireNonNull(actionMethodNames.get(nodeId), "Unknown Action node: " + nodeId);
    }

    private String actionInvocation(String nodeId) {
        return actionMethod(nodeId) + actionArgumentList(nodeId);
    }

    private String gatewayInvocation(String nodeId) {
        return gatewayMethodNames.get(nodeId) + argumentList(nodeId);
    }

    private String flowInvocation(String startNodeId, String boundaryNodeId, String receiver) {
        if (startNodeId.equals(boundaryNodeId)) {
            return NORMAL;
        }
        String prefix = receiver == null ? "" : receiver + ".";
        return prefix + requestFlow(startNodeId, boundaryNodeId) + argumentList(startNodeId);
    }

    private String actionParameterDeclaration(String nodeId) {
        return parameterDeclaration(actionLexicalParameters(nodeId));
    }

    private String reusableActionParameterDeclaration(String nodeId) {
        List<String> parameters = new ArrayList<>(List.of("String _cf$nodeId"));
        actionLexicalParameters(nodeId).forEach(parameter -> parameters.add(
                parameter.typeName() + " " + parameter.name()));
        return parenthesized(parameters);
    }

    private String actionArgumentList(String nodeId) {
        return argumentList(actionLexicalParameters(nodeId));
    }

    private String reusableActionArgumentList(String nodeId) {
        List<String> arguments = new ArrayList<>(List.of(literal(nodeId)));
        actionLexicalParameters(nodeId).stream().map(LexicalParameter::name).forEach(arguments::add);
        return parenthesized(arguments);
    }

    private String parameterDeclaration(String nodeId) {
        return parameterDeclaration(flowLexicalParameters(nodeId));
    }

    private static String parameterDeclaration(List<LexicalParameter> parameters) {
        return parenthesized(parameters
            .stream()
            .map(parameter -> parameter.typeName() + " " + parameter.name())
            .toList());
    }

    private String argumentList(String nodeId) {
        return argumentList(flowLexicalParameters(nodeId));
    }

    private static String argumentList(List<LexicalParameter> parameters) {
        return parenthesized(parameters.stream().map(LexicalParameter::name).toList());
    }

    private static String parenthesized(List<String> values) {
        return "(" + String.join(", ", values) + ")";
    }

    private List<LexicalParameter> lexicalParameters(String nodeId) {
        Map<String, LexicalParameter> parameters = new LinkedHashMap<>();
        semantics
            .visibleVariables(nodeId)
            .values()
            .stream()
            .filter(variable -> variable.origin() == ProcessSemanticPlan.VisibleVariable.Origin.ITERATION)
            .forEach(variable -> parameters.put(variable.name(),
                    new LexicalParameter(variable.name(), variable.typeName())));
        return List.copyOf(parameters.values());
    }

    private List<LexicalParameter> flowLexicalParameters(String nodeId) {
        Map<String, LexicalParameter> parameters = new LinkedHashMap<>();
        lexicalParameters(nodeId).forEach(parameter -> parameters.put(parameter.name(), parameter));
        removeOwnedIterationParameters(parameters, semantics.requireNode(nodeId).iteration());
        return List.copyOf(parameters.values());
    }

    private List<LexicalParameter> actionLexicalParameters(String nodeId) {
        ProcessSemanticPlan.NodePlan node = semantics.requireNode(nodeId);
        ActionPlan action = (ActionPlan) node.operation();
        List<LexicalParameter> visible = lexicalParameters(nodeId);
        Set<String> candidates = visible
            .stream()
            .map(LexicalParameter::name)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> referenced = new LinkedHashSet<>();
        action
            .inputs()
            .stream()
            .map(ActionPlan.Input::source)
            .filter(ActionPlan.InputSource.Expression.class::isInstance)
            .map(ActionPlan.InputSource.Expression.class::cast)
            .forEach(source -> referenced.addAll(JavaExpressionInspector.referencedIdentifiers(source.value(),
                    candidates)));
        return visible
            .stream()
            .filter(parameter -> referenced.contains(parameter.name()))
            .toList();
    }

    private static void removeOwnedIterationParameters(Map<String, LexicalParameter> parameters,
            IterationPlan iteration) {
        if (iteration instanceof IterationPlan.While loop) {
            parameters.remove(loop.indexVariable());
        } else if (iteration instanceof IterationPlan.ForEach loop) {
            parameters.remove(loop.itemVariable());
            parameters.remove(loop.indexVariable());
        }
    }

    private Map<String, String> collectActivityMethodNames() {
        Map<String, String> names = new LinkedHashMap<>();
        Map<ActionTemplate, String> reusable = new LinkedHashMap<>();
        Map<String, String> owners = new LinkedHashMap<>();
        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            if (node.kind() != ProcessSemanticPlan.NodeKind.ACTIVITY) {
                continue;
            }
            String readableIdentity = readableNodeIdentity(node);
            String prefix;
            if (node.operation() instanceof AwaitPlan) {
                prefix = "_cf$await";
            } else if (node.operation() instanceof ProcessCallPlan) {
                prefix = "_cf$call";
                if (readableIdentity.length() > "call".length() && startsWithIgnoreCase(readableIdentity, "call")) {
                    readableIdentity = readableIdentity.substring("call".length());
                }
                if (readableIdentity.length() > "call".length() && endsWithIgnoreCase(readableIdentity, "call")) {
                    readableIdentity = readableIdentity.substring(0, readableIdentity.length() - "call".length());
                }
            } else if (node.iteration() instanceof IterationPlan.ForEach) {
                prefix = "_cf$foreach";
                if (startsWithIgnoreCase(readableIdentity, "foreach-")) {
                    readableIdentity = readableIdentity.substring("foreach-".length());
                }
            } else if (node.iteration() instanceof IterationPlan.While) {
                prefix = "_cf$loop";
                if ("while-loop".equalsIgnoreCase(readableIdentity)) {
                    readableIdentity = "";
                }
            } else if (node.scopeBoundary() != null) {
                prefix = "_cf$run";
            } else {
                prefix = node.operation() instanceof ActionPlan ? "_cf$execute" : "_cf$activity";
            }
            String methodIdentity = readableIdentity;
            String name = isReusableActionActivity(node)
                    ? reusable.computeIfAbsent(new ActionTemplate((ActionPlan) node.operation(),
                    actionLexicalParameters(node.id())), ignored -> allocateReadableMethod(owners, prefix,
                    methodIdentity, node.id()))
                    : allocateReadableMethod(owners, prefix, methodIdentity, node.id());
            names.put(node.id(), name);
        }
        return Map.copyOf(names);
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        return value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length());
    }

    private Map<String, String> collectActionMethodNames() {
        Map<String, String> names = new LinkedHashMap<>();
        Map<ActionTemplate, String> reusable = new LinkedHashMap<>();
        Map<String, String> owners = new LinkedHashMap<>();
        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            if (!(node.operation() instanceof ActionPlan action)) {
                continue;
            }
            String readableIdentity = readableNodeIdentity(node);
            names.put(node.id(),
                    reusable.computeIfAbsent(new ActionTemplate(action, actionLexicalParameters(node.id())), ignored -> allocateReadableMethod(owners,
                            "_cf$invoke", readableIdentity, node.id())));
        }
        return Map.copyOf(names);
    }

    private Map<String, String> collectScriptSpecFieldNames() {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, String> fieldsByActionMethod = new LinkedHashMap<>();
        semantics.getNodes().values().forEach(node -> {
            if (node.operation() instanceof ActionPlan action && action.invocation() instanceof ActionInvocation.Script) {
                String actionMethod = actionMethodNames.get(node.id());
                String field = fieldsByActionMethod.computeIfAbsent(actionMethod, method -> "_cf$script"
                        + method.substring("_cf$invoke".length()));
                names.put(node.id(), field);
            }
        });
        return Map.copyOf(names);
    }

    private Map<String, String> collectPolicyFieldNames() {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, String> fieldsByActionMethod = new LinkedHashMap<>();
        semantics.getNodes().values().forEach(node -> {
            if (node.operation() instanceof ActionPlan action && !defaultPolicy(action.invocationPolicy())) {
                String actionMethod = actionMethodNames.get(node.id());
                String field = fieldsByActionMethod.computeIfAbsent(actionMethod, method -> "_cf$policy"
                        + method.substring("_cf$invoke".length()));
                names.put(node.id(), field);
            }
        });
        return Map.copyOf(names);
    }

    private String allocateReadableMethod(Map<String, String> owners, String prefix, String readableIdentity,
            String uniqueIdentity) {
        String suffix = readableIdentity == null || readableIdentity.isBlank()
                ? ""
                : JavaIdentifiers.toMethodSuffix(readableIdentity);
        String base = prefix + suffix;
        if (base.length() > 96) {
            base = base.substring(0, 96);
        }
        String owner = owners.putIfAbsent(base, uniqueIdentity);
        if (owner == null || owner.equals(uniqueIdentity)) {
            return base;
        }
        String readableCandidate = base + "At" + JavaIdentifiers.toMethodSuffix(uniqueIdentity);
        String readableOwner = owners.putIfAbsent(readableCandidate, uniqueIdentity);
        if (readableOwner == null || readableOwner.equals(uniqueIdentity)) {
            return readableCandidate;
        }
        String stableSuffix = "$" + JavaIdentifiers.toStableMethodSuffix(uniqueIdentity);
        String allocated = base.substring(0, Math.min(base.length(), 96 - stableSuffix.length())) + stableSuffix;
        String collision = owners.putIfAbsent(allocated, uniqueIdentity);
        if (collision != null && !collision.equals(uniqueIdentity)) {
            throw new IllegalStateException("Generated method collision for '" + uniqueIdentity + "'");
        }
        return allocated;
    }

    private String readableNodeIdentity(ProcessSemanticPlan.NodePlan node) {
        if (!isStructuralIdentity(node.id())) {
            return node.id();
        }
        if (node.operation() instanceof ProcessCallPlan call) {
            return simpleProcessCode(call.code());
        }
        String nodeName = readableNodeName(node.id());
        if (nodeName != null) {
            return nodeName;
        }
        if (node.operation() instanceof AwaitPlan await && await.event() != null) {
            return await.event();
        }
        if (node.operation() instanceof ActionPlan action) {
            if (action.invocation() instanceof ActionInvocation.SpringBean spring) {
                String beanName = spring.beanName();
                return beanName.endsWith("Activity") && beanName.length() > "Activity".length()
                        ? beanName.substring(0, beanName.length() - "Activity".length())
                        : beanName;
            }
            if (action.invocation() instanceof ActionInvocation.Java java) {
                String className = java.className();
                int separator = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
                String simpleClassName = className.substring(separator + 1);
                return "execute".equals(java.method()) ? simpleClassName : java.method();
            }
            if (action.invocation() instanceof ActionInvocation.Script) {
                return action.output() == null ? "execute-script" : "compute-" + action.output().target();
            }
        }
        if (node.iteration() instanceof IterationPlan.ForEach loop) {
            return "foreach-" + loop.itemVariable();
        }
        if (node.iteration() instanceof IterationPlan.While) {
            return "while-loop";
        }
        if (node.id().codePoints().noneMatch(Character::isLetter) && node.operation() instanceof AwaitPlan
                && node.outgoingTransitions().size() == 1) {
            String target = node.outgoingTransitions().get(0).targetId();
            if (startsWithIgnoreCase(target, "apply") && endsWithIgnoreCase(target, "callback")) {
                return target.substring("apply".length());
            }
            return "before-" + target;
        }
        return node.id();
    }

    private String readableNodeName(String nodeId) {
        String name = nodeNames.get(nodeId);
        if (name == null || !isAsciiText(name) || isStructuralIdentity(name)) {
            return null;
        }
        return name;
    }

    private static boolean isAsciiText(String value) {
        boolean hasLetter = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < 32 || current > 126) {
                return false;
            }
            hasLetter |= Character.isLetter(current);
        }
        return hasLetter;
    }

    private static boolean isStructuralIdentity(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < 128 && Character.isLetterOrDigit(current)) {
                normalized.append(Character.toLowerCase(current));
            }
        }
        if (normalized.chars().noneMatch(Character::isLetter)) {
            return true;
        }
        String identity = normalized.toString();
        for (String stem :
                List.of("activity", "action", "autotask", "callactivity", "exclusive", "exclusivegateway", "foreach",
                        "gateway", "inclusive", "inclusivegateway", "parallel", "parallelgateway", "receivetask",
                        "scripttask", "servicetask", "subprocess", "task", "waiteventtask", "waittask", "while")) {
            if (identity.equals(stem)
                    || (identity.startsWith(stem)
                    && identity.substring(stem.length()).chars().allMatch(Character::isDigit))) {
                return true;
            }
        }
        return false;
    }

    private static String simpleProcessCode(String code) {
        int separator = Math.max(code.lastIndexOf('.'), code.lastIndexOf('-'));
        return code.substring(separator + 1);
    }

    private Map<String, String> collectGatewayMethodNames() {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, String> owners = new LinkedHashMap<>();
        structure
            .getGatewayPlans()
            .values()
            .stream()
            .filter(GatewayPlan::isSplit)
            .forEach(gateway -> {
                ProcessSemanticPlan.NodePlan node = semantics.requireNode(gateway.getGatewayId());
                String prefix = switch (node.kind()) {
                    case EXCLUSIVE_GATEWAY -> "_cf$route";
                    case INCLUSIVE_GATEWAY -> "_cf$forkInclusive";
                    case PARALLEL_GATEWAY -> "_cf$forkParallel";
                    default -> throw new IllegalArgumentException("Unsupported split gateway: " + node.kind());
                };
                String identity = gatewayIdentity(node);
                names.put(node.id(), allocateReadableMethod(owners, prefix, identity, node.id()));
            });
        return Map.copyOf(names);
    }

    private String gatewayIdentity(ProcessSemanticPlan.NodePlan node) {
        String result = readableNodeIdentity(node);
        List<String> prefixes = switch (node.kind()) {
            case EXCLUSIVE_GATEWAY -> List.of("exclusiveGateway", "gateway", "choice");
            case INCLUSIVE_GATEWAY -> List.of("inclusiveGateway", "inclusiveFork", "inclusive");
            case PARALLEL_GATEWAY -> List.of("parallelGateway", "parallelFork");
            default -> List.of();
        };
        for (String prefix : prefixes) {
            if (startsWithIgnoreCase(result, prefix)) {
                result = result.substring(prefix.length());
                break;
            }
        }
        if (node.kind() == ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY && "fork".equalsIgnoreCase(result)) {
            return "";
        }
        if (!isStructuralIdentity(result)) {
            return result;
        }

        Set<String> conditionVariables = new LinkedHashSet<>();
        node
            .outgoingTransitions()
            .stream()
            .map(ProcessSemanticPlan.TransitionPlan::condition)
            .forEach(condition -> conditionVariables.addAll(JavaExpressionInspector.referencedIdentifiers(condition,
                    semantics.getVariables().keySet())));
        if (!conditionVariables.isEmpty() && conditionVariables.size() <= 2) {
            return "by-" + String.join("-and-", conditionVariables);
        }

        List<String> branchTargets = node
            .outgoingTransitions()
            .stream()
            .map(ProcessSemanticPlan.TransitionPlan::targetId)
            .map(semantics::requireNode)
            .map(this::readableNodeIdentity)
            .distinct()
            .toList();
        if (branchTargets.size() == 2) {
            return "to-" + String.join("-or-", branchTargets);
        }
        return result;
    }

    private String requestFlow(String startNodeId, String boundaryNodeId) {
        semantics.requireNode(startNodeId);
        if (boundaryNodeId != null) {
            ProcessSemanticPlan.NodePlan boundary = semantics.requireNode(boundaryNodeId);
            if (boundary.kind() == ProcessSemanticPlan.NodeKind.END) {
                boundaryNodeId = null;
            }
        }
        FlowKey key = new FlowKey(startNodeId, boundaryNodeId);
        String existing = flowMethodNames.get(key);
        if (existing != null) {
            return existing;
        }
        String readable;
        boolean numericStart = startNodeId.codePoints().noneMatch(Character::isLetter);
        if (boundaryNodeId == null && numericStart && startNodeId.equals(structure.getEntryNodeId())) {
            readable = "_cf$runProcess";
        } else if (boundaryNodeId == null && numericStart
                && semantics.requireNode(startNodeId).operation() instanceof AwaitPlan) {
            String identity = readableNodeIdentity(semantics.requireNode(startNodeId));
            readable = startsWithIgnoreCase(identity, "before-")
                    ? "_cf$resumeWith" + JavaIdentifiers.toMethodSuffix(identity.substring("before-".length()))
                    : "_cf$resume" + JavaIdentifiers.toMethodSuffix(identity);
        } else {
            String startIdentity = numericStart ? readableNodeIdentity(semantics.requireNode(startNodeId)) : startNodeId;
            readable = "_cf$run" + JavaIdentifiers.toMethodSuffix(startIdentity);
        }
        if (boundaryNodeId != null) {
            ProcessSemanticPlan.NodePlan boundary = semantics.requireNode(boundaryNodeId);
            if (boundary.kind() != ProcessSemanticPlan.NodeKind.END) {
                String boundaryIdentity =
                        boundaryNodeId.codePoints().noneMatch(Character::isLetter)
                        ? readableNodeIdentity(boundary)
                        : boundaryNodeId;
                readable += "To" + JavaIdentifiers.toMethodSuffix(boundaryIdentity);
            }
        }
        if (readable.length() > 96) {
            readable = readable.substring(0, 96);
        }
        String identity = startNodeId + '\0' + Objects.toString(boundaryNodeId, "");
        String owner = flowMethodOwners.putIfAbsent(readable, identity);
        String allocated = readable;
        if (owner != null && !owner.equals(identity)) {
            allocated = readable + "$" + JavaIdentifiers.toStableMethodSuffix(identity);
            String collision = flowMethodOwners.putIfAbsent(allocated, identity);
            if (collision != null && !collision.equals(identity)) {
                throw new IllegalStateException("Generated flow-method collision for '" + identity + "'");
            }
        }
        flowMethodNames.put(key, allocated);
        flowMethodQueue.add(key);
        return allocated;
    }

    private boolean hasConcurrentGateway() {
        return structure
            .getGatewayPlans()
            .values()
            .stream()
            .filter(GatewayPlan::isConcurrent)
            .anyMatch(gateway -> semantics.requireNode(gateway.getGatewayId()).kind() != ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY
                    || gateway.getBranches().values().stream().anyMatch(branch -> !branch.getNodeIds().isEmpty()));
    }

    private boolean isNoOpParallel(GatewayPlan gateway) {
        return semantics.requireNode(gateway.getGatewayId()).kind() == ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY
                && gateway.getBranches().values().stream().allMatch(branch -> branch.getNodeIds().isEmpty());
    }

    private boolean hasNodeKind(ProcessSemanticPlan.NodeKind kind) {
        return semantics
            .getNodes()
            .values()
            .stream()
            .anyMatch(node -> node.kind() == kind);
    }

    private boolean hasAwaitEvent() {
        return semantics
            .getNodes()
            .values()
            .stream()
            .map(ProcessSemanticPlan.NodePlan::operation)
            .filter(AwaitPlan.class::isInstance)
            .map(AwaitPlan.class::cast)
            .anyMatch(await -> await.event() != null);
    }

    private boolean hasParameters() {
        return semantics
            .getVariables()
            .values()
            .stream()
            .anyMatch(variable -> variable.role() == ProcessSemanticPlan.VariableRole.PARAM);
    }

    private boolean hasDefaultValue(ProcessSemanticPlan.VariablePlan variable) {
        DataTypes.DefaultValueCode value =
                DataTypes.generateDefaultValueCode(DataTypes.getJavaClass(variable.dataType()), variable.defaultValue());
        return !"null".equals(value.expression());
    }

    private Set<String> concurrentBranchVariables() {
        Set<String> variables = new LinkedHashSet<>();
        structure
            .getGatewayPlans()
            .values()
            .stream()
            .filter(GatewayPlan::isConcurrent)
            .flatMap(gateway -> gateway.getBranches().values().stream())
            .forEach(branch -> {
                variables.addAll(branch.getReads());
                variables.addAll(branch.getWrites());
            });
        return variables;
    }

    private String rawSourceType(String typeName) {
        return sourceType(DataTypes.getJavaClass(typeName));
    }

    private String boxedSourceType(String typeName) {
        Class<?> type = DataTypes.getJavaClass(typeName);
        return sourceType(type.isPrimitive() ? DataTypes.getWrapperClass(type) : type);
    }

    private static String sourceType(Class<?> type) {
        String canonical = type.getCanonicalName();
        return canonical == null ? type.getName().replace('$', '.') : canonical;
    }

    private static String sourceClassName(String binaryName) {
        try {
            return sourceType(Class.forName(binaryName, false, Thread.currentThread().getContextClassLoader()));
        } catch (ClassNotFoundException | LinkageError unavailable) {
            // The compiler will report a missing application dependency with its standard diagnostics.
            return binaryName.replace('$', '.');
        }
    }

    private record LexicalParameter(String name, String typeName) {}

    private record ActionTemplate(ActionPlan action, List<LexicalParameter> parameters) {}

    private record FlowKey(String startNodeId, String boundaryNodeId) {}
}
