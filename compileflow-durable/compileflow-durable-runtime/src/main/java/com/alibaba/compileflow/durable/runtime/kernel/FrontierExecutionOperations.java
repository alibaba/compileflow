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
package com.alibaba.compileflow.durable.runtime.kernel;

import com.alibaba.compileflow.engine.core.runtime.execution.LoopSemantics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Small deterministic primitives used by generated frontier code.
 *
 * @author yusu
 */
public final class FrontierExecutionOperations {
    private FrontierExecutionOperations() {
    }

    public static Map<String, Object> mutableState(Map<String, Object> source) {
        return DurableValueSnapshots.mutableMap(Objects.requireNonNull(source, "source"));
    }

    public static List<ScopeFrame> mutableFrames(List<ScopeFrame> source) {
        return new ArrayList<>(Objects.requireNonNull(source, "source"));
    }

    public static List<Object> snapshot(Object source, String loopId) {
        return LoopSemantics.snapshot(source, loopId, Object.class);
    }

    public static void applyUpdates(Map<String, Object> state, Map<String, Object> updates,
            Set<String> allowedStateFields) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(updates, "updates");
        Objects.requireNonNull(allowedStateFields, "allowedStateFields");
        for (String field : updates.keySet()) {
            if (!allowedStateFields.contains(field)) {
                throw new IllegalArgumentException("Pure action returned undeclared state field '" + field + "'");
            }
        }
        Map<String, Object> snapshot = DurableValueSnapshots.immutableMap(updates);
        state.putAll(snapshot);
    }

    public static Object scriptInput(Map<String, Object> state, Map<String, Object> lexical, String source,
            String nodeId) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(lexical, "lexical");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(nodeId, "nodeId");
        if (lexical.containsKey(source)) {
            return lexical.get(source);
        }
        if (state.containsKey(source)) {
            return state.get(source);
        }
        throw new IllegalArgumentException(
                "Script action on node '" + nodeId + "' has an absent input source: " + source);
    }

    public static Object loopCollection(Map<String, Object> state, Map<String, Object> lexical,
            String collectionVariable, String loopId) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(lexical, "lexical");
        Objects.requireNonNull(collectionVariable, "collectionVariable");
        Objects.requireNonNull(loopId, "loopId");
        if (lexical.containsKey(collectionVariable)) {
            return lexical.get(collectionVariable);
        }
        if (state.containsKey(collectionVariable)) {
            return state.get(collectionVariable);
        }
        throw new IllegalArgumentException(
                "Loop '" + loopId + "' has an absent collection source: " + collectionVariable);
    }

    public static Map<String, Object> scriptContext(Map<String, Object> context) {
        return DurableValueSnapshots.immutableMap(Objects.requireNonNull(context, "context"));
    }

    public static Map<String, Object> effectInput(Map<String, Object> state, Map<String, String> mappings) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(mappings, "mappings");
        Map<String, Object> input = new LinkedHashMap<>();
        mappings.forEach((effectField, stateField) -> {
            if (!state.containsKey(stateField)) {
                throw new IllegalArgumentException("Effect input source is absent: " + stateField);
            }
            input.put(effectField, state.get(stateField));
        });
        // EffectRequest owns the durable deep snapshot; this map is transient.
        return input;
    }

    public static void applyEffectOutput(Map<String, Object> state, Map<String, Object> output,
            Map<String, String> mappings, Set<String> allowedStateFields) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(mappings, "mappings");
        Objects.requireNonNull(allowedStateFields, "allowedStateFields");
        mappings.forEach((effectField, stateField) -> {
            if (!output.containsKey(effectField)) {
                throw new IllegalArgumentException("Effect output field is absent: " + effectField);
            }
            if (!allowedStateFields.contains(stateField)) {
                throw new IllegalArgumentException("Effect output targets undeclared state field: " + stateField);
            }
        });
        Map<String, Object> snapshot = DurableValueSnapshots.immutableMap(output);
        mappings.forEach((effectField, stateField) -> state.put(stateField, snapshot.get(effectField)));
    }

    public static ScopeFrame requireTopFrame(List<ScopeFrame> frames, String loopId) {
        Objects.requireNonNull(frames, "frames");
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("Loop '" + loopId + "' has no active control frame");
        }
        ScopeFrame frame = frames.get(frames.size() - 1);
        if (!frame.loopId().equals(loopId)) {
            throw new IllegalArgumentException(
                    "Expected top control frame for loop '" + loopId + "', found '" + frame.loopId() + "'");
        }
        return frame;
    }

    public static void replaceTopFrame(List<ScopeFrame> frames, String loopId, ScopeFrame replacement) {
        requireTopFrame(frames, loopId);
        if (!loopId.equals(replacement.loopId())) {
            throw new IllegalArgumentException("Replacement frame must keep loop identity");
        }
        frames.set(frames.size() - 1, replacement);
    }

    public static void popTopFrame(List<ScopeFrame> frames, String loopId) {
        requireTopFrame(frames, loopId);
        frames.remove(frames.size() - 1);
    }

    public static Map<String, Object> readOnlyState(Map<String, Object> state) {
        return DurableValueSnapshots.readOnlyMap(Objects.requireNonNull(state, "state"));
    }

    public static Map<String, Object> immutableLexical(Map<String, Object> lexical) {
        return DurableValueSnapshots.readOnlyMap(Objects.requireNonNull(lexical, "lexical"));
    }
}
