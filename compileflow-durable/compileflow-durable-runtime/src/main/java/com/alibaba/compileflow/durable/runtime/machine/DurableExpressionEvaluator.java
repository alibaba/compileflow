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
package com.alibaba.compileflow.durable.runtime.machine;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Evaluator bound for the exact bound expressions of one Durable machine.
 *
 * @author yusu
 */
@FunctionalInterface
public interface DurableExpressionEvaluator {
    Object evaluate(BoundExpression expression, Map<String, Object> state, Map<String, Object> frames);

    default boolean evaluateBoolean(BoundExpression expression, Map<String, Object> state, Map<String, Object> frames) {
        Object value = evaluate(expression, state, frames);
        if (value == null) {
            return false;
        }
        if (!(value instanceof Boolean result)) {
            throw new IllegalStateException("Durable expression did not return boolean: " + expression.source());
        }
        return result;
    }

    default Duration evaluateDuration(BoundExpression expression, Map<String, Object> state, Map<String, Object> frames) {
        Object value = evaluate(expression, state, frames);
        if (!(value instanceof Duration result)) {
            throw new IllegalStateException("Durable expression did not return Duration: " + expression.source());
        }
        return result;
    }

    default Instant evaluateInstant(BoundExpression expression, Map<String, Object> state, Map<String, Object> frames) {
        Object value = evaluate(expression, state, frames);
        if (!(value instanceof Instant result)) {
            throw new IllegalStateException("Durable expression did not return Instant: " + expression.source());
        }
        return result;
    }
}
