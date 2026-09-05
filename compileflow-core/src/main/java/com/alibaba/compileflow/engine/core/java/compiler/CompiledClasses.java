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
package com.alibaba.compileflow.engine.core.java.compiler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of compiled class bytes keyed by binary name.
 *
 * @author yusu
 */
final class CompiledClasses {
    private final Map<String, byte[]> classBytes;

    private CompiledClasses(Map<String, byte[]> classBytes) {
        this.classBytes = deepCopy(classBytes);
    }

    static CompiledClasses copyOf(Map<String, byte[]> classBytes) {
        if (classBytes.isEmpty()) {
            throw new IllegalArgumentException("Compiled classes must not be empty");
        }
        return new CompiledClasses(classBytes);
    }

    private static Map<String, byte[]> deepCopy(Map<String, byte[]> source) {
        Map<String, byte[]> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, byte[]> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return copy;
    }

    Map<String, byte[]> copyClassBytes() {
        return Collections.unmodifiableMap(deepCopy(classBytes));
    }
}
