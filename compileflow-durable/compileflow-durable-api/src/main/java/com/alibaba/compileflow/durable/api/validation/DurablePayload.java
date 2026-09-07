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
package com.alibaba.compileflow.durable.api.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded snapshot validation for Durable portable values.
 *
 * <p>Container values and byte arrays are recursively detached. Other application objects are
 * retained by reference here; typed Durable serializers must detach them before they cross a
 * persisted execution boundary.</p>
 *
 * @author yusu
 */
public final class DurablePayload {
    public static final int MAX_INPUT_ENTRIES = 1_024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_COLLECTION_ENTRIES = 100_000;

    private DurablePayload() {
    }

    /**
     * Validates and recursively detaches a portable payload map.
     *
     * @param source source payload
     * @param name field name used in failures
     * @return immutable detached payload
     */
    public static Map<String, Object> immutablePayload(Map<String, ?> source, String name) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        if (source.size() > MAX_INPUT_ENTRIES) {
            throw new IllegalArgumentException(name + " must not contain more than " + MAX_INPUT_ENTRIES + " entries");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        IdentityHashMap<Object, Boolean> active = new IdentityHashMap<>();
        int[] remainingEntries = {MAX_COLLECTION_ENTRIES};
        consumeEntries(source.size(), remainingEntries, name);
        enterContainer(source, active, name);
        try {
            for (Map.Entry<String, ?> entry : source.entrySet()) {
                String key = DurableIdentifiers.requireIdentity(entry.getKey(), name + " key", 256);
                if (copy.containsKey(key)) {
                    throw new IllegalArgumentException(name + " contains duplicate key: " + key);
                }
                copy.put(key, immutableValue(entry.getValue(), active, remainingEntries, 1, name));
            }
        } finally {
            active.remove(source);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value, IdentityHashMap<Object, Boolean> active, int[] remainingEntries,
            int depth, String name) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(name + " exceeds the maximum nested depth of " + MAX_DEPTH);
        }
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        if (value instanceof List<?> list) {
            consumeEntries(list.size(), remainingEntries, name);
            enterContainer(list, active, name);
            try {
                List<Object> copy = new ArrayList<>(list.size());
                for (Object item : list) {
                    copy.add(immutableValue(item, active, remainingEntries, depth + 1, name));
                }
                return Collections.unmodifiableList(copy);
            } finally {
                active.remove(list);
            }
        }
        if (value instanceof Set<?> set) {
            consumeEntries(set.size(), remainingEntries, name);
            enterContainer(set, active, name);
            try {
                Set<Object> copy = new LinkedHashSet<>();
                for (Object item : set) {
                    copy.add(immutableValue(item, active, remainingEntries, depth + 1, name));
                }
                return Collections.unmodifiableSet(copy);
            } finally {
                active.remove(set);
            }
        }
        if (value instanceof Map<?, ?> map) {
            consumeEntries(map.size(), remainingEntries, name);
            enterContainer(map, active, name);
            try {
                Map<Object, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = immutableValue(entry.getKey(), active, remainingEntries, depth + 1, name);
                    if (copy.containsKey(key)) {
                        throw new IllegalArgumentException(name + " contains duplicate nested key");
                    }
                    copy.put(key, immutableValue(entry.getValue(), active, remainingEntries, depth + 1, name));
                }
                return Collections.unmodifiableMap(copy);
            } finally {
                active.remove(map);
            }
        }
        return value;
    }

    private static void enterContainer(Object value, IdentityHashMap<Object, Boolean> active, String name) {
        if (active.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Cyclic " + name + " value graph is not supported");
        }
    }

    private static void consumeEntries(int size, int[] remainingEntries, String name) {
        if (size > remainingEntries[0]) {
            throw new IllegalArgumentException(
                    name + " total nested collection entries exceed " + MAX_COLLECTION_ENTRIES + " entries");
        }
        remainingEntries[0] -= size;
    }
}
