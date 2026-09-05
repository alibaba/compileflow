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
package com.alibaba.compileflow.engine.test.quality.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

class ProcessEngineFactoryClassLoaderIntegrationTest {
    private static final String PROVIDER_RESOURCE =
            "META-INF/services/com.alibaba.compileflow.engine.spi.ProcessEngineProvider";

    @Test
    void discoversTheEngineProviderFromTheConfiguredClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader applicationClassLoader = getClass().getClassLoader();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        ClassLoader contextClassLoaderWithoutProviderMetadata = new ClassLoader(applicationClassLoader) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (PROVIDER_RESOURCE.equals(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getResources(name);
            }
        };
        ProcessEngineConfig config =
                ProcessEngineTestFactory
            .tbbpmBuilder()
            .classLoader(applicationClassLoader)
            .discoverPlugins(false)
            .build();

        thread.setContextClassLoader(contextClassLoaderWithoutProviderMetadata);
        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            assertThat(engine).isNotNull();
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }
}
