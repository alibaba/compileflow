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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of known process data type aliases.
 *
 * @author yusu
 */
final class TypeRegistry {
    private static final Map<String, JavaTypeDescriptor> TYPES = createTypes();

    private TypeRegistry() {
    }

    static JavaTypeDescriptor getDescriptor(String typeName) {
        return typeName == null ? null : TYPES.get(typeName);
    }

    static Class<?> getJavaClass(String typeName) {
        JavaTypeDescriptor descriptor = getDescriptor(typeName);
        return descriptor == null ? null : descriptor.getJavaClass();
    }

    private static Map<String, JavaTypeDescriptor> createTypes() {
        Map<String, JavaTypeDescriptor> types = new LinkedHashMap<>();

        register(types, String.class, null, "String", "java.lang.String");
        register(types, Object.class, null, "Object", "java.lang.Object");

        register(types, Short.class, short.class, "Short", "java.lang.Short");
        register(types, Integer.class, int.class, "Integer", "java.lang.Integer");
        register(types, Long.class, long.class, "Long", "java.lang.Long");
        register(types, Double.class, double.class, "Double", "java.lang.Double");
        register(types, Float.class, float.class, "Float", "java.lang.Float");
        register(types, Byte.class, byte.class, "Byte", "java.lang.Byte");
        register(types, Character.class, char.class, "Character", "java.lang.Character");
        register(types, Boolean.class, boolean.class, "Boolean", "java.lang.Boolean");

        register(types, short.class, short.class, "short");
        register(types, int.class, int.class, "int");
        register(types, long.class, long.class, "long");
        register(types, double.class, double.class, "double");
        register(types, float.class, float.class, "float");
        register(types, byte.class, byte.class, "byte");
        register(types, char.class, char.class, "char");
        register(types, boolean.class, boolean.class, "boolean");

        return Map.copyOf(types);
    }

    private static void register(Map<String, JavaTypeDescriptor> types, Class<?> javaClass, Class<?> primitiveClass,
            String... names) {
        JavaTypeDescriptor descriptor = new JavaTypeDescriptor(javaClass, primitiveClass);
        for (String name : names) {
            if (types.putIfAbsent(name, descriptor) != null) {
                throw new IllegalStateException("Duplicate built-in type name: " + name);
            }
        }
    }
}
