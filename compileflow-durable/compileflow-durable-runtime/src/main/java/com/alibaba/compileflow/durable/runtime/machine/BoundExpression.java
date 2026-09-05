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

import com.alibaba.compileflow.engine.core.semantic.SemanticText;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Exact expression source and lexical bindings shared by every Durable backend.
 *
 * @author yusu
 */
public record BoundExpression(String source, ResultKind resultKind, List<Binding> bindings) {
    public BoundExpression {
        source = SemanticText.requireExpression(source, "source");
        resultKind = Objects.requireNonNull(resultKind, "resultKind");
        bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
        Set<String> names = new HashSet<>();
        for (Binding binding : bindings) {
            Binding value = Objects.requireNonNull(binding, "binding");
            if (!names.add(value.name())) {
                throw new IllegalArgumentException("Duplicate expression binding '" + value.name() + "'");
            }
        }
    }

    public enum ResultKind {
        BOOLEAN,
        DURATION,
        INSTANT
    }

    /**
     * One expression identifier and the Durable storage location that owns its value.
     */
    public record Binding(String name, String typeName, Source source) {
        public Binding {
            name = SemanticText.requireIdentity(name, "name");
            typeName = SemanticText.requireIdentity(typeName, "typeName");
            source = Objects.requireNonNull(source, "source");
        }

        public enum Source {
            STATE,
            FRAME
        }
    }
}
