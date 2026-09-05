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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class DataTypeClassLoaderTest {
    private static byte[] readClassBytes(String className) throws IOException {
        String resource = "/" + className.replace('.', '/') + ".class";
        try (InputStream input = DataTypeClassLoaderTest.class.getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return input.readAllBytes();
        }
    }

    @Test
    void resolvesSameNamedTypesAgainstTheCurrentContextClassLoader() throws IOException {
        String className = LoaderScopedTypeFixture.class.getName();
        byte[] classBytes = readClassBytes(className);
        ClassLoader parent = DataTypeClassLoaderTest.class.getClassLoader();
        ClassLoader firstLoader = new IsolatedTypeClassLoader(parent, className, classBytes);
        ClassLoader secondLoader = new IsolatedTypeClassLoader(parent, className, classBytes);

        Thread currentThread = Thread.currentThread();
        ClassLoader original = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(firstLoader);
            Class<?> first = DataTypes.getJavaClass(className);

            currentThread.setContextClassLoader(secondLoader);
            Class<?> second = DataTypes.getJavaClass(className);

            assertThat(first.getName()).isEqualTo(className);
            assertThat(second.getName()).isEqualTo(className);
            assertThat(first.getClassLoader()).isSameAs(firstLoader);
            assertThat(second.getClassLoader()).isSameAs(secondLoader);
            assertThat(second).isNotSameAs(first);
        } finally {
            currentThread.setContextClassLoader(original);
        }
    }

    @Test
    void explicitClassLoaderDoesNotDependOnTheCallingThreadContext() throws Exception {
        String className = LoaderScopedTypeFixture.class.getName();
        byte[] classBytes = readClassBytes(className);
        ClassLoader parent = DataTypeClassLoaderTest.class.getClassLoader();
        ClassLoader applicationLoader = new IsolatedTypeClassLoader(parent, className, classBytes);
        ClassLoader unrelatedContextLoader = new IsolatedTypeClassLoader(parent, className, classBytes);

        Thread currentThread = Thread.currentThread();
        ClassLoader original = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(unrelatedContextLoader);

            Class<?> resolved = DataTypes.getJavaClass(className, applicationLoader);

            assertThat(resolved.getClassLoader()).isSameAs(applicationLoader);
            assertThat(resolved).isNotSameAs(unrelatedContextLoader.loadClass(className));
        } finally {
            currentThread.setContextClassLoader(original);
        }
    }

    @Test
    void convertsLinkageFailuresIntoDefinitionErrors() {
        ClassLoader brokenLoader = new ClassLoader(DataTypeClassLoaderTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if ("broken.example.Type".equals(name)) {
                    throw new UnsupportedClassVersionError("unsupported bytecode");
                }
                return super.loadClass(name, resolve);
            }
        };

        Thread currentThread = Thread.currentThread();
        ClassLoader original = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(brokenLoader);
            assertThatThrownBy(() -> DataTypes.getJavaClass("broken.example.Type"))
                .isInstanceOf(DataTypeException.UnsupportedTypeException.class)
                .hasMessageContaining("cannot be linked");
        } finally {
            currentThread.setContextClassLoader(original);
        }
    }

    private static final class IsolatedTypeClassLoader extends ClassLoader {
        private final String isolatedClassName;
        private final byte[] classBytes;

        private IsolatedTypeClassLoader(ClassLoader parent, String isolatedClassName, byte[] classBytes) {
            super(parent);
            this.isolatedClassName = isolatedClassName;
            this.classBytes = classBytes.clone();
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (!isolatedClassName.equals(name)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = defineClass(name, classBytes, 0, classBytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
