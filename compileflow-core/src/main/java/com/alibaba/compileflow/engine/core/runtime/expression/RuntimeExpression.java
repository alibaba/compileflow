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

import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireExpression;
import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireIdentity;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One exact Java expression and its source-neutral lexical binding contract.
 *
 * @author yusu
 */
public record RuntimeExpression(String source, Kind kind, List<Binding> bindings) {
    public RuntimeExpression {
        source = requireExpression(source, "source");
        kind = Objects.requireNonNull(kind, "kind");
        bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
        Set<String> names = new HashSet<>();
        for (Binding binding : bindings) {
            Binding value = Objects.requireNonNull(binding, "binding");
            if (!names.add(value.name())) {
                throw new IllegalArgumentException("Duplicate expression binding '" + value.name() + "'");
            }
        }
    }

    public enum Kind {
        VALUE,
        CONDITION
    }

    public record Binding(String name, String typeName) {
        public Binding {
            name = requireIdentity(name, "name");
            typeName = requireIdentity(typeName, "typeName");
        }
    }
}
