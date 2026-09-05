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

import com.alibaba.compileflow.engine.core.java.naming.JavaNames;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable in-memory Java compilation unit.
 *
 * @author yusu
 */
public final class JavaSource {
    private final String javaSourceCode;
    private final String targetFullClassName;
    private final Map<String, String> debugMetadata;

    private JavaSource(String javaSourceCode, String targetFullClassName, Map<String, String> debugMetadata) {
        this.javaSourceCode = Objects.requireNonNull(javaSourceCode, "javaSourceCode must not be null");
        this.targetFullClassName = JavaNames.requireValidClassName(targetFullClassName);
        TreeMap<String, String> metadata =
                new TreeMap<>(Objects.requireNonNull(debugMetadata, "debugMetadata must not be null"));
        metadata.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("debug metadata key must not be blank");
            }
            Objects.requireNonNull(value, "debug metadata value must not be null");
        });
        this.debugMetadata = Map.copyOf(metadata);
    }

    /**
     * Creates an in-memory compilation unit.
     *
     * @param javaSourceCode      complete Java source text
     * @param targetFullClassName binary name of the primary class
     * @return validated immutable compilation unit
     */
    public static JavaSource of(String javaSourceCode, String targetFullClassName) {
        return of(javaSourceCode, targetFullClassName, Map.of());
    }

    public static JavaSource of(String javaSourceCode, String targetFullClassName, Map<String, String> debugMetadata) {
        return new JavaSource(javaSourceCode, targetFullClassName, debugMetadata);
    }

    /**
     * Returns the complete Java source text.
     *
     * @return source text supplied at construction time
     */
    public String getJavaSourceCode() {
        return javaSourceCode;
    }

    /**
     * Returns the primary class binary name.
     *
     * @return validated binary class name
     */
    public String getTargetFullClassName() {
        return targetFullClassName;
    }

    public Map<String, String> getDebugMetadata() {
        return debugMetadata;
    }
}
