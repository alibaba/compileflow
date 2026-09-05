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
package com.alibaba.compileflow.engine.core.classloader;

/**
 * Resolves the application class loader used for process compilation and execution.
 *
 * @author yusu
 */
public final class ProcessClassLoaderResolver {
    private ProcessClassLoaderResolver() {
    }

    public static ClassLoader resolveEffectiveClassLoader(ClassLoader provided, Class<?> referrer) {
        if (provided != null) {
            return provided;
        }

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            return tccl;
        }

        if (referrer != null && referrer.getClassLoader() != null) {
            return referrer.getClassLoader();
        }

        ClassLoader resolverClassLoader = ProcessClassLoaderResolver.class.getClassLoader();
        if (resolverClassLoader != null) {
            return resolverClassLoader;
        }

        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (systemClassLoader == null) {
            throw new IllegalStateException("No effective application ClassLoader is available");
        }
        return systemClassLoader;
    }
}
