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

import com.alibaba.compileflow.durable.api.validation.DurablePayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Null-tolerant immutable snapshots used at Machine Turn boundaries.
 *
 * @author yusu
 */
final class DurableValueSnapshots {
    private DurableValueSnapshots() {
    }

    static Map<String, Object> immutableMap(Map<String, Object> source) {
        return DurablePayload.immutablePayload(Objects.requireNonNull(source, "source"), "Durable");
    }

    static Map<String, Object> mutableMap(Map<String, Object> source) {
        return new LinkedHashMap<>(immutableMap(source));
    }

    /**
     * Creates a detached read-only top-level view after callers have established that all values
     * are already deeply immutable. This keeps the boundary snapshot contract without walking the
     * same value graph for every pure action or wait mapping.
     */
    static Map<String, Object> readOnlyMap(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(source, "source")));
    }

    static List<Object> immutableList(List<?> source) {
        Object snapshot = immutableValue(Objects.requireNonNull(source, "source"));
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) snapshot;
        return result;
    }

    static Object immutableValue(Object source) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("value", source);
        return immutableMap(wrapper).get("value");
    }

    /**
     * Detaches mutable leaves while structurally sharing the immutable snapshot around them.
     */
    static Object detachedValue(Object source) {
        if (source instanceof byte[] bytes) {
            return bytes.clone();
        }
        if (source instanceof List<?> list) {
            List<Object> detached = null;
            for (int index = 0; index < list.size(); index++) {
                Object value = list.get(index);
                Object copy = detachedValue(value);
                if (detached != null) {
                    detached.add(copy);
                } else if (copy != value) {
                    detached = new ArrayList<>(list.size());
                    detached.addAll(list.subList(0, index));
                    detached.add(copy);
                }
            }
            return detached == null ? source : Collections.unmodifiableList(detached);
        }
        if (source instanceof Set<?> set) {
            Set<Object> detached = null;
            int index = 0;
            for (Object value : set) {
                Object copy = detachedValue(value);
                if (detached != null) {
                    detached.add(copy);
                } else if (copy != value) {
                    detached = new LinkedHashSet<>(set.size());
                    int precedingIndex = 0;
                    for (Object preceding : set) {
                        if (precedingIndex++ == index) {
                            break;
                        }
                        detached.add(preceding);
                    }
                    detached.add(copy);
                }
                index++;
            }
            return detached == null ? source : Collections.unmodifiableSet(detached);
        }
        if (source instanceof Map<?, ?> map) {
            Map<Object, Object> detached = null;
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = detachedValue(entry.getKey());
                Object value = detachedValue(entry.getValue());
                if (detached != null) {
                    detached.put(key, value);
                } else if (key != entry.getKey() || value != entry.getValue()) {
                    detached = new LinkedHashMap<>(map.size());
                    int precedingIndex = 0;
                    for (Map.Entry<?, ?> preceding : map.entrySet()) {
                        if (precedingIndex++ == index) {
                            break;
                        }
                        detached.put(preceding.getKey(), preceding.getValue());
                    }
                    detached.put(key, value);
                }
                index++;
            }
            return detached == null ? source : Collections.unmodifiableMap(detached);
        }
        return source;
    }
}
