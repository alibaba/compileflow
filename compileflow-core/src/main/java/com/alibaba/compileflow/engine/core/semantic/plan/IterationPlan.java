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

import static com.alibaba.compileflow.engine.core.semantic.SemanticText.optionalIdentity;
import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireExpression;
import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireIdentity;
import java.util.Objects;

/**
 * Immutable iteration semantics attached to an activity node.
 *
 * @author yusu
 */
public sealed interface IterationPlan permits IterationPlan.While, IterationPlan.ForEach {
    /**
     * Repeats a node operation or owned scope based on a condition.
     */
    record While(String condition, ConditionTiming timing, Integer maxIterations, LimitBehavior limitBehavior,
            String indexVariable) implements IterationPlan {
        public While {
            condition = requireExpression(condition, "condition");
            timing = Objects.requireNonNull(timing, "timing");
            if (maxIterations != null && maxIterations <= 0) {
                throw new IllegalArgumentException("maxIterations must be positive when declared");
            }
            limitBehavior = Objects.requireNonNull(limitBehavior, "limitBehavior");
            indexVariable = optionalIdentity(indexVariable, "indexVariable");
        }
    }

    /**
     * Repeats a node operation or owned scope for a finite collection.
     */
    record ForEach(String collectionVariable, String itemVariable, String itemType, String indexVariable,
            Execution execution, String outputSourceVariable, String outputTargetVariable) implements IterationPlan {
        public ForEach {
            collectionVariable = requireIdentity(collectionVariable, "collectionVariable");
            itemVariable = requireIdentity(itemVariable, "itemVariable");
            itemType = requireIdentity(itemType, "itemType");
            indexVariable = optionalIdentity(indexVariable, "indexVariable");
            execution = Objects.requireNonNull(execution, "execution");
            outputSourceVariable = optionalIdentity(outputSourceVariable, "outputSourceVariable");
            outputTargetVariable = optionalIdentity(outputTargetVariable, "outputTargetVariable");
            if ((outputTargetVariable == null) != (outputSourceVariable == null)) {
                throw new IllegalArgumentException(
                        "outputTargetVariable and outputSourceVariable must be declared together");
            }
        }
    }

    /**
     * Whether a while condition is evaluated before or after the first body execution.
     */
    enum ConditionTiming {
        BEFORE,
        AFTER
    }

    /**
     * Meaning of reaching {@link While#maxIterations()} while its condition still holds.
     */
    enum LimitBehavior {
        STOP,
        FAIL
    }

    /**
     * Source-defined iteration execution ordering.
     */
    enum Execution {
        SEQUENTIAL,
        PARALLEL
    }
}
