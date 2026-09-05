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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory class loader that defines classes from compiled bytecode.
 *
 * @author yusu
 */
final class CompiledClassLoader extends ClassLoader {
    static {
        if (!registerAsParallelCapable()) {
            throw new ExceptionInInitializerError("CompiledClassLoader could not enable parallel class loading");
        }
    }

    private final Map<String, byte[]> classBytes;

    CompiledClassLoader(ClassLoader parent, CompiledClasses compiledClasses) {
        super(parent);
        this.classBytes = new ConcurrentHashMap<>(compiledClasses.copyClassBytes());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null && classBytes.containsKey(name)) {
                loadedClass = findClass(name);
            }
            if (loadedClass == null) {
                loadedClass = super.loadClass(name, false);
            }
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classBytes.get(name);
        if (bytes == null) {
            return super.findClass(name);
        }
        Class<?> definedClass = defineClass(name, bytes, 0, bytes.length);
        classBytes.remove(name, bytes);
        return definedClass;
    }
}
