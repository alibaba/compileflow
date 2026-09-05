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

import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireExpression;
import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireIdentity;
import com.alibaba.compileflow.engine.core.java.expression.JavaExpressionInspector;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.runtime.expression.RuntimeExpression;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Exact expression inventory shared by runtime construction and interpreted execution.
 *
 * @author yusu
 */
final class InterpretedExpressionCatalog {
    private final ProcessSemanticPlan semanticPlan;
    private final Map<ExpressionKey, RuntimeExpression> expressions = new LinkedHashMap<>();
    private final Map<DefaultKey, RuntimeExpression> defaults = new LinkedHashMap<>();

    InterpretedExpressionCatalog(ProcessSemanticPlan semanticPlan) {
        this.semanticPlan = Objects.requireNonNull(semanticPlan, "semanticPlan");
        collect();
    }

    List<RuntimeExpression> expressions() {
        List<RuntimeExpression> result = new ArrayList<>(expressions.size() + defaults.size());
        result.addAll(expressions.values());
        result.addAll(defaults.values());
        return List.copyOf(result);
    }

    RuntimeExpression condition(String nodeId, String source) {
        return require(new ExpressionKey(nodeId, source, RuntimeExpression.Kind.CONDITION, false));
    }

    RuntimeExpression loopCondition(String nodeId, String source) {
        return require(new ExpressionKey(nodeId, source, RuntimeExpression.Kind.CONDITION, true));
    }

    RuntimeExpression value(String nodeId, String source) {
        return require(new ExpressionKey(nodeId, source, RuntimeExpression.Kind.VALUE, false));
    }

    RuntimeExpression defaultValue(String typeName, String sourceValue) {
        RuntimeExpression expression = defaults.get(new DefaultKey(typeName, sourceValue));
        if (expression == null) {
            throw new IllegalArgumentException("Unknown cataloged default for type '" + typeName + "'");
        }
        return expression;
    }

    private RuntimeExpression require(ExpressionKey key) {
        RuntimeExpression expression = expressions.get(key);
        if (expression == null) {
            throw new IllegalArgumentException(
                    "Unknown cataloged expression at node '" + key.nodeId() + "': " + key.source());
        }
        return expression;
    }

    private void collect() {
        for (ProcessSemanticPlan.VariablePlan variable : semanticPlan.getVariables().values()) {
            addDefault(variable.dataType(), variable.defaultValue());
        }
        for (ProcessSemanticPlan.NodePlan node : semanticPlan.getNodes().values()) {
            for (ProcessSemanticPlan.TransitionPlan transition : node.outgoingTransitions()) {
                if (transition.condition() != null) {
                    add(node.id(), transition.condition(), RuntimeExpression.Kind.CONDITION, false);
                }
            }
            if (node.controlCondition() != null) {
                add(node.id(), node.controlCondition(), RuntimeExpression.Kind.CONDITION, false);
            }
            if (node.iteration() instanceof IterationPlan.While loop) {
                add(node.id(), loop.condition(), RuntimeExpression.Kind.CONDITION, true);
            }
            if (node.operation() instanceof ActionPlan action) {
                for (ActionPlan.Input input : action.inputs()) {
                    if (input.source() instanceof ActionPlan.InputSource.Expression expression) {
                        add(node.id(), expression.value(), RuntimeExpression.Kind.VALUE, false);
                    } else if (input.source() instanceof ActionPlan.InputSource.Literal literal) {
                        addDefault(input.declaredType(), literal.value());
                    }
                }
            } else if (node.operation() instanceof ProcessCallPlan call) {
                for (ProcessCallPlan.Input input : call.inputs()) {
                    if (input.sourceExpression() != null) {
                        add(node.id(), input.sourceExpression(), RuntimeExpression.Kind.VALUE, false);
                    }
                }
            }
        }
    }

    private void add(String nodeId, String source, RuntimeExpression.Kind kind, boolean includeIterationBindings) {
        ExpressionKey key = new ExpressionKey(nodeId, source, kind, includeIterationBindings);
        expressions.computeIfAbsent(key, ignored -> new RuntimeExpression(source, kind,
                bindings(nodeId, source, includeIterationBindings)));
    }

    private List<RuntimeExpression.Binding> bindings(String nodeId, String source, boolean includeIterationBindings) {
        Map<String, String> visibleTypes = new LinkedHashMap<>();
        semanticPlan
            .visibleVariables(nodeId)
            .forEach((name, variable) -> visibleTypes.put(name, variable.typeName()));
        if (includeIterationBindings) {
            IterationPlan iteration = semanticPlan.requireNode(nodeId).iteration();
            if (iteration instanceof IterationPlan.While loop && loop.indexVariable() != null) {
                visibleTypes.put(loop.indexVariable(), "int");
            }
        }
        Set<String> referenced = JavaExpressionInspector.referencedIdentifiers(source, visibleTypes.keySet());
        List<RuntimeExpression.Binding> result = new ArrayList<>();
        visibleTypes.forEach((name, type) -> {
            if (referenced.contains(name)) {
                result.add(new RuntimeExpression.Binding(name, type));
            }
        });
        return List.copyOf(result);
    }

    private void addDefault(String typeName, String sourceValue) {
        DefaultKey key = new DefaultKey(typeName, sourceValue);
        defaults.computeIfAbsent(key, ignored -> {
            Class<?> type = DataTypes.getJavaClass(typeName);
            String source = DataTypes.generateDefaultValueCode(type, sourceValue).expression();
            return new RuntimeExpression(source, RuntimeExpression.Kind.VALUE, List.of());
        });
    }

    private record ExpressionKey(String nodeId, String source, RuntimeExpression.Kind kind,
            boolean includeIterationBindings) {
        private ExpressionKey {
            nodeId = requireIdentity(nodeId, "nodeId");
            source = requireExpression(source, "source");
            kind = Objects.requireNonNull(kind, "kind");
        }
    }

    private record DefaultKey(String typeName, String sourceValue) {
        private DefaultKey {
            typeName = requireIdentity(typeName, "typeName");
        }
    }
}
