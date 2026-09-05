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
package com.alibaba.compileflow.durable.spi.wait;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable inputs for describing one outward-facing Durable Wait.
 *
 * @param processCode lexical Process identity
 * @param semanticDigest exact Process semantic digest
 * @param nodeId exact Wait node identity
 * @param state detached Process state
 * @param lexicalBindings detached enclosing-scope bindings
 * @author yusu
 */
public record DurableWaitDescriptionContext(String processCode, String semanticDigest, String nodeId,
        Map<String, Object> state, Map<String, Object> lexicalBindings) {
    public DurableWaitDescriptionContext {
        processCode = ProcessIdentifiers.requireCode(processCode);
        semanticDigest = ProcessIdentifiers.requireSha256(semanticDigest, "semanticDigest");
        nodeId = ProcessIdentifiers.requireNodeId(nodeId);
        state = immutableCopy(state, "state");
        lexicalBindings = immutableCopy(lexicalBindings, "lexicalBindings");
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source, String name) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(source, name)));
    }
}
