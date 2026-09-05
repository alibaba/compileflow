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
package com.alibaba.compileflow.durable.spi.admission;

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads the authoritative exact immutable definition for a Process Version during admission.
 *
 * <p>Implementations must be thread-safe. External I/O must be bounded and preserve interruption.
 * Absence or failure fails admission without fallback to another definition authority. The
 * application or dependency-injection container owns the source lifecycle. Run recovery never
 * consults this source.
 *
 * @author yusu
 */
@FunctionalInterface
public interface DurableVersionDefinitionSource {
    /**
     * Finds the immutable definition selected by an exact Version.
     *
     * @param version exact Version identity
     * @return authoritative format and exact definition, or empty when absent
     */
    Optional<VersionDefinition> find(ProcessRef.Version version);

    /**
     * Returns an empty Version-definition source.
     *
     * @return empty Version definition source
     */
    static DurableVersionDefinitionSource empty() {
        return version -> Optional.empty();
    }

    /**
     * Immutable Process-owned facts required to register an exact recovery replica.
     *
     * @param modelType authoritative definition format
     * @param definition exact inline definition
     * @param callBindings verified exact Version selected for every direct Process call
     */
    record VersionDefinition(ProcessModelType modelType, ProcessDefinition.Inline definition,
            Map<String, ProcessRef.Version> callBindings) {
        public VersionDefinition(ProcessModelType modelType, ProcessDefinition.Inline definition) {
            this(modelType, definition, Map.of());
        }

        public VersionDefinition {
            modelType = Objects.requireNonNull(modelType, "modelType");
            definition = Objects.requireNonNull(definition, "definition");
            Map<String, ProcessRef.Version> bindings = new LinkedHashMap<>();
            for (Map.Entry<String, ProcessRef.Version> entry : Objects
                .requireNonNull(callBindings, "callBindings")
                .entrySet()) {
                bindings.put(ProcessIdentifiers.requireNodeId(entry.getKey()),
                        Objects.requireNonNull(entry.getValue(), "callBindings contains null"));
            }
            callBindings = Map.copyOf(bindings);
        }
    }
}
