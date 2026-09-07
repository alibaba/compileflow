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
package com.alibaba.compileflow.engine.core.type;

import com.google.common.primitives.Primitives;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of known process data type aliases.
 *
 * @author yusu
 */
final class TypeRegistry {
    private static final Map<String, Class<?>> TYPES = createTypes();

    private TypeRegistry() {
    }

    static Class<?> getJavaClass(String typeName) {
        return typeName == null ? null : TYPES.get(typeName);
    }

    private static Map<String, Class<?>> createTypes() {
        Map<String, Class<?>> types = new LinkedHashMap<>();
        for (Class<?> type :
                List.of(String.class, Object.class, short.class, int.class, long.class, double.class, float.class,
                        byte.class, char.class, boolean.class)) {
            Class<?> reference = Primitives.wrap(type);
            types.put(type.getName(), type);
            types.put(reference.getSimpleName(), reference);
            types.put(reference.getName(), reference);
        }
        return Map.copyOf(types);
    }
}
