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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ProcessClassLoaderResolverTest {
    @Test
    void explicitLoaderWinsOverEveryFallback() {
        ClassLoader explicit = new ClassLoader(null) {
        };

        assertThat(ProcessClassLoaderResolver.resolveEffectiveClassLoader(explicit, ProcessClassLoaderResolverTest.class))
            .isSameAs(explicit);
    }

    @Test
    void contextLoaderWinsWhenNoLoaderIsExplicitlyConfigured() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        ClassLoader context = new ClassLoader(null) {
        };
        thread.setContextClassLoader(context);
        try {
            assertThat(ProcessClassLoaderResolver.resolveEffectiveClassLoader(null, ProcessClassLoaderResolverTest.class))
                .isSameAs(context);
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Test
    void referrerLoaderIsUsedWhenTheContextLoaderIsAbsent() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try {
            assertThat(ProcessClassLoaderResolver.resolveEffectiveClassLoader(null, ProcessClassLoaderResolverTest.class))
                .isSameAs(ProcessClassLoaderResolverTest.class.getClassLoader());
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
