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

import static com.alibaba.compileflow.engine.core.semantic.SemanticText.optionalExpression;
import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireIdentity;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable action semantics owned by an activity node.
 *
 * @author yusu
 */
public record ActionPlan(ActionExecution execution, ActionInvocation invocation, List<Input> inputs, Output output,
        EffectiveInvocationPolicy invocationPolicy, EffectPolicyPlan effectPolicy) implements OperationPlan {
    public ActionPlan {
        invocation = Objects.requireNonNull(invocation, "invocation");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        if (new HashSet<>(inputs.stream().map(Input::target).toList()).size() != inputs.size()) {
            throw new IllegalArgumentException("inputs must not contain duplicate targets");
        }
        invocationPolicy = Objects.requireNonNull(invocationPolicy, "invocationPolicy");
        if ((execution == ActionExecution.EFFECT) != (effectPolicy != null)) {
            throw new IllegalArgumentException("execution=EFFECT requires exactly one resolved effectPolicy");
        }
        if (execution != ActionExecution.EFFECT
                && inputs.stream().map(Input::source).anyMatch(InputSource.EffectId.class::isInstance)) {
            throw new IllegalArgumentException("Effect ID input requires execution=EFFECT");
        }
    }

    /**
     * Derives the complete provider program specification for a script action.
     *
     * <p>This remains disposable runtime metadata. It is derived from immutable semantic facts
     * and is deliberately not a separate Process IR.
     *
     * @return specification used to validate, compile, and cache the script
     */
    public ScriptProgramSpec scriptProgramSpec() {
        if (!(invocation instanceof ActionInvocation.Script script)) {
            throw new IllegalStateException("Action does not invoke a ScriptExecutor");
        }
        return new ScriptProgramSpec(script.language(), script.source(),
                inputs
                    .stream()
                    .map(input -> new ScriptProgramSpec.Input(input.target(), input.declaredType()))
                    .toList(), output == null ? null : output.resultType());
    }

    /**
     * One borrowed, read-only action argument mapped from Process state or a source literal.
     *
     * <p>An Action must not mutate any object reachable from this input. Process state changes are
     * expressed only through declared outputs and explicit Process constructs.
     */
    public record Input(InputSource source, String target, String declaredType) {
        public Input {
            source = Objects.requireNonNull(source, "source");
            target = requireIdentity(target, "target");
            if (!ProcessNames.isIdentifier(target) || ProcessNames.isReserved(target)) {
                throw new IllegalArgumentException("target must be a valid Java identifier: " + target);
            }
            declaredType = requireIdentity(declaredType, "declaredType");
        }

        public static Input expression(String expression, String target, String declaredType) {
            return new Input(new InputSource.Expression(expression), target, declaredType);
        }

        public static Input literal(String value, String target, String declaredType) {
            return new Input(new InputSource.Literal(value), target, declaredType);
        }

        public static Input effectId(String target, String declaredType) {
            return new Input(new InputSource.EffectId(), target, declaredType);
        }
    }

    /**
     * Value source for one Action argument after frontend normalization.
     */
    public sealed interface InputSource {
        /**
         * Process expression evaluated against the node's visible state.
         */
        record Expression(String value) implements InputSource {
            public Expression {
                value = optionalExpression(value, "expression");
                if (value == null) {
                    throw new IllegalArgumentException("expression is required");
                }
            }
        }

        /**
         * Source literal converted according to the declared argument type.
         */
        record Literal(String value) implements InputSource {
            public Literal {
                value = Objects.requireNonNull(value, "value");
            }
        }

        /**
         * Stable identity of the current Durable Effect occurrence.
         */
        record EffectId() implements InputSource {}
    }

    /**
     * The Process-state destination for an action result.
     */
    public record Output(String target, String resultType, String targetType) {
        public Output {
            target = requireIdentity(target, "target");
            resultType = requireIdentity(resultType, "resultType");
            targetType = requireIdentity(targetType, "targetType");
        }
    }
}
