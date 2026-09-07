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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ProcessServiceBeansAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                CompileFlowEngineAutoConfiguration.class))
        .withPropertyValues("compileflow.engine.enabled=true");

    @Test
    void exposesOneEngineWithRuntimeAndToolingViews() {
        runner.run(context -> {
            ProcessEngine engine = context.getBean(ProcessEngine.class);

            assertThat(engine.runtime()).isSameAs(engine);
            assertThat(engine.tooling()).isSameAs(engine);
            assertThat(context.getBeansOfType(ProcessEngine.class)).hasSize(1);
        });
    }

    @Test
    void backsOffForACustomEngineWithoutPublishingDuplicateServiceBeans() {
        runner
            .withUserConfiguration(CustomEngineConfiguration.class)
            .run(context -> {
                ProcessEngine engine = context.getBean(ProcessEngine.class);

                assertThat(engine).isSameAs(context.getBean("customProcessEngine"));
                assertThat(context.getBeansOfType(ProcessEngine.class)).hasSize(1);
                assertThat(context).doesNotHaveBean(ProcessRuntimeManager.class);
                assertThat(context).doesNotHaveBean(ProcessToolingService.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomEngineConfiguration {
        @Bean
        ProcessEngine customProcessEngine() {
            return mock(ProcessEngine.class);
        }
    }
}
