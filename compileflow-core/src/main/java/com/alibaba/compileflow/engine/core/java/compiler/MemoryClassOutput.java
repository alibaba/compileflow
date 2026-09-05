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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Collects compiled class bytes into an in-memory map.
 *
 * @author yusu
 */
final class MemoryClassOutput implements ClassOutput {
    private final String primaryClassName;
    private final Map<String, byte[]> classes = new HashMap<>();
    private boolean finished;

    MemoryClassOutput(String primaryClassName) {
        this.primaryClassName = JavaNames.requireValidClassName(primaryClassName);
    }

    @Override
    public synchronized void writeClass(String className, byte[] bytes) {
        ensureOpen();
        String validatedName = JavaNames.requireValidClassName(className);
        byte[] validatedBytes = Objects.requireNonNull(bytes, "class bytes must not be null");
        if (validatedBytes.length == 0) {
            throw new IllegalArgumentException("class bytes must not be empty");
        }
        if (classes.putIfAbsent(validatedName, validatedBytes.clone()) != null) {
            throw new IllegalStateException("Duplicate compiled class: " + validatedName);
        }
    }

    synchronized CompiledClasses finish() {
        ensureOpen();
        if (!classes.containsKey(primaryClassName)) {
            throw new IllegalStateException("Compiler did not emit primary class: " + primaryClassName);
        }
        finished = true;
        return CompiledClasses.copyOf(classes);
    }

    private void ensureOpen() {
        if (finished) {
            throw new IllegalStateException("Compiled class output is already finished");
        }
    }
}
