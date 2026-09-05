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
package com.alibaba.compileflow.engine.core.runtime.expression;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable dispatcher for one runtime's compiled expressions.
 *
 * @author yusu
 */
public final class CompiledExpressionEvaluator {
    private final Map<RuntimeExpression, Integer> indexes;
    private final RuntimeExpressionProgram program;

    CompiledExpressionEvaluator(Map<RuntimeExpression, Integer> indexes, RuntimeExpressionProgram program) {
        this.indexes = Map.copyOf(Objects.requireNonNull(indexes, "indexes"));
        this.program = program;
    }

    public Object evaluate(RuntimeExpression expression, Map<String, Object> state, Map<String, Object> lexicalBindings) {
        RuntimeExpression requested = Objects.requireNonNull(expression, "expression");
        Integer index = indexes.get(requested);
        if (index == null || program == null) {
            throw new IllegalArgumentException("Expression does not belong to this Process runtime");
        }
        Map<String, Object> processState = Objects.requireNonNull(state, "state");
        Map<String, Object> lexical = Objects.requireNonNull(lexicalBindings, "lexicalBindings");
        Object[] arguments = new Object[requested.bindings().size()];
        for (int argument = 0; argument < arguments.length; argument++) {
            String name = requested.bindings().get(argument).name();
            if (lexical.containsKey(name)) {
                arguments[argument] = lexical.get(name);
            } else if (processState.containsKey(name)) {
                arguments[argument] = processState.get(name);
            } else {
                throw new IllegalArgumentException("Expression binding is unavailable: " + name);
            }
        }
        return program.evaluate(index, arguments);
    }

    public boolean evaluateCondition(RuntimeExpression expression, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        Object result = evaluate(expression, state, lexicalBindings);
        if (!(result instanceof Boolean condition)) {
            throw new IllegalStateException("Condition expression did not return boolean: " + expression.source());
        }
        return condition;
    }
}
