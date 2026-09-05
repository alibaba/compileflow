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

import java.util.Objects;

/**
 * Java class and primitive-wrapper relationship for one registered type alias.
 *
 * @author yusu
 */
final class JavaTypeDescriptor {
    private final Class<?> javaClass;
    private final Class<?> primitiveClass;

    JavaTypeDescriptor(Class<?> javaClass, Class<?> primitiveClass) {
        this.javaClass = Objects.requireNonNull(javaClass, "javaClass");
        this.primitiveClass = primitiveClass;
    }

    Class<?> getJavaClass() {
        return javaClass;
    }

    boolean isPrimitive() {
        return javaClass.isPrimitive();
    }

    boolean isWrapper() {
        return primitiveClass != null && !javaClass.isPrimitive();
    }
}
