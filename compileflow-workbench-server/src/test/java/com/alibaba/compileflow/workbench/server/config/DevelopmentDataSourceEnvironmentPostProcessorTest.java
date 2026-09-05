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
package com.alibaba.compileflow.workbench.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class DevelopmentDataSourceEnvironmentPostProcessorTest {
    private final DevelopmentDataSourceEnvironmentPostProcessor processor =
            new DevelopmentDataSourceEnvironmentPostProcessor();

    private static StandardEnvironment developmentEnvironment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("dev");
        environment.getPropertySources().addFirst(new MapPropertySource("testProperties", properties));
        return environment;
    }

    @Test
    void shouldRunAfterConfigDataAndServerEnvironmentTranslation() {
        assertThat(processor.getOrder())
            .isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER)
            .isGreaterThan(new CompileFlowWorkbenchServerEnvironmentPostProcessor().getOrder());
    }

    @Test
    void shouldRejectMissingDevelopmentDatabasePassword() {
        StandardEnvironment environment = developmentEnvironment(Map.of());

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("spring.datasource.password")
            .hasMessageContaining("no embedded database fallback");
    }

    @Test
    void shouldRejectBlankDevelopmentDatabasePassword() {
        StandardEnvironment environment = developmentEnvironment(Map.of(DevelopmentDataSourceEnvironmentPostProcessor.DATASOURCE_PASSWORD_PROPERTY,
                "  "));

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non-empty");
    }

    @Test
    void shouldAcceptEffectivePasswordFromAnySpringPropertySource() {
        StandardEnvironment environment = developmentEnvironment(Map.of(DevelopmentDataSourceEnvironmentPostProcessor.DATASOURCE_PASSWORD_PROPERTY,
                "configured-secret"));

        assertThatCode(() -> processor.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptStandardDatasourcePasswordEnvironmentVariable() {
        StandardEnvironment environment = developmentEnvironment(Map.of());
        environment
            .getPropertySources()
            .addFirst(
                    new SystemEnvironmentPropertySource("testSystemEnvironment",
                            Map.of("SPRING_DATASOURCE_PASSWORD", "configured-secret")));

        assertThatCode(() -> processor.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotRequireDevelopmentCredentialsForOtherProfiles() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");

        assertThatCode(() -> processor.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }
}
