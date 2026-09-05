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

import com.alibaba.compileflow.engine.core.semantic.SemanticText;
import java.util.Objects;

/**
 * A timer boundary described independently of a source format.
 *
 * @author yusu
 */
public record TimerPlan(Kind kind, String value) implements OperationPlan {
    public TimerPlan {
        kind = Objects.requireNonNull(kind, "kind");
        value = isLiteral(kind)
                ? SemanticText.requireIdentity(value, "value")
                : SemanticText.requireExpression(value, "value");
    }

    /**
     * Returns whether this timer is fully specified by its serialized value.
     */
    public boolean isLiteral() {
        return isLiteral(kind);
    }

    /**
     * Supported timer value forms.
     */
    public enum Kind {
        DURATION_LITERAL,
        DURATION_EXPRESSION,
        WAKE_AT_LITERAL,
        WAKE_AT_EXPRESSION
    }

    private static boolean isLiteral(Kind kind) {
        return kind == Kind.DURATION_LITERAL || kind == Kind.WAKE_AT_LITERAL;
    }
}
