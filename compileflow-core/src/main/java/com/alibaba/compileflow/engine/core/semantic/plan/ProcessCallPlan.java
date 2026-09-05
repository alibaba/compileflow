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
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable Process-call semantics owned by a single node.
 *
 * @author yusu
 */
public record ProcessCallPlan(String code, ProcessCallTarget target, List<Input> inputs, List<Output> outputs)
        implements OperationPlan {
    public ProcessCallPlan {
        code = ProcessIdentifiers.requireCode(code);
        target = Objects.requireNonNull(target, "target");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        requireUnique(inputs.stream().map(Input::target).toList(), "input targets");
        requireUnique(outputs.stream().map(Output::source).toList(), "output sources");
        requireUnique(outputs.stream().map(Output::target).toList(), "output targets");
    }

    /**
     * Maps a caller value into a variable of the called Process.
     */
    public record Input(String sourceExpression, String target, String defaultValue) {
        public Input {
            sourceExpression = optionalExpression(sourceExpression, "sourceExpression");
            target = requireIdentity(target, "target");
            if ((sourceExpression == null) == (defaultValue == null)) {
                throw new IllegalArgumentException("exactly one of sourceExpression or defaultValue is required");
            }
        }
    }

    /**
     * Maps a variable returned by the called Process into caller state.
     */
    public record Output(String source, String target, String targetType) {
        public Output {
            source = requireIdentity(source, "source");
            target = requireIdentity(target, "target");
            targetType = requireIdentity(targetType, "targetType");
        }
    }

    private static void requireUnique(List<String> values, String role) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(role + " must not contain duplicates");
        }
    }
}
