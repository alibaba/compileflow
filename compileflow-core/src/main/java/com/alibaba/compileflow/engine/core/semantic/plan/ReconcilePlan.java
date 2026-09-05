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

import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireIdentity;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable query adapter used to reconcile an uncertain Durable Effect.
 *
 * @author yusu
 */
public record ReconcilePlan(ActionInvocation invocation, List<Input> inputs) {
    public ReconcilePlan {
        invocation = Objects.requireNonNull(invocation, "invocation");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        if (new HashSet<>(inputs.stream().map(Input::target).toList()).size() != inputs.size()) {
            throw new IllegalArgumentException("reconcile inputs must not contain duplicate targets");
        }
    }

    /**
     * Derives the complete provider program specification for a script reconcile adapter.
     */
    public ScriptProgramSpec scriptProgramSpec() {
        if (!(invocation instanceof ActionInvocation.Script script)) {
            throw new IllegalStateException("Reconcile adapter does not invoke a ScriptExecutor");
        }
        return new ScriptProgramSpec(script.language(), script.source(),
                inputs
                    .stream()
                    .map(input -> new ScriptProgramSpec.Input(input.target(), input.declaredType()))
                    .toList(), null);
    }

    /**
     * One argument mapped from a persisted Effect request field or recovery metadata.
     */
    public record Input(InputSource source, String target, String declaredType) {
        public Input {
            source = Objects.requireNonNull(source, "source");
            target = requireIdentity(target, "target");
            declaredType = requireIdentity(declaredType, "declaredType");
            if (!ProcessNames.isIdentifier(target) || ProcessNames.isReserved(target)) {
                throw new IllegalArgumentException("target must be a valid Java identifier: " + target);
            }
        }

        public static Input requestField(String field, String target, String declaredType) {
            return new Input(new InputSource.RequestField(field), target, declaredType);
        }

        public static Input effectId(String target, String declaredType) {
            return new Input(new InputSource.EffectId(), target, declaredType);
        }
    }

    /**
     * Value source available to a recovery-only reconcile adapter.
     */
    public sealed interface InputSource {
        /**
         * Field from the persisted original Effect request.
         */
        record RequestField(String name) implements InputSource {
            public RequestField {
                name = requireIdentity(name, "name");
                if (!ProcessNames.isIdentifier(name) || ProcessNames.isReserved(name)) {
                    throw new IllegalArgumentException("name must be a valid Effect request field: " + name);
                }
            }
        }

        /**
         * Stable identity of the Effect being reconciled.
         */
        record EffectId() implements InputSource {}
    }
}
