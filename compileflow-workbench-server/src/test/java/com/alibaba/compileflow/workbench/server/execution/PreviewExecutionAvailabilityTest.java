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
package com.alibaba.compileflow.workbench.server.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class PreviewExecutionAvailabilityTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PreviewExecutionTestConfiguration.class);

    @Test
    void doesNotExposePreviewExecutionByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(PreviewExecutionController.class));
    }

    @Test
    void exposesPreviewExecutionOnlyWhenExplicitlyEnabled() {
        contextRunner
            .withPropertyValues("compileflow.workbench.server.preview-execution.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(PreviewExecutionController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(PreviewExecutionController.class)
    static class PreviewExecutionTestConfiguration {
        @Bean
        PreviewExecutionService previewExecutionService() {
            return mock(PreviewExecutionService.class);
        }
    }
}
