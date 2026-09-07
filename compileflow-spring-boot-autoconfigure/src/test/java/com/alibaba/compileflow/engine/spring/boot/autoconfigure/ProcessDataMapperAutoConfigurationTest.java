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
import com.alibaba.compileflow.engine.ProcessDataMapper;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.mapping.JacksonProcessDataMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

class ProcessDataMapperAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                CompileFlowEngineAutoConfiguration.class))
        .withPropertyValues("compileflow.engine.enabled=true");

    @Test
    void explicitProcessDataMapperBeanTakesPriorityOverTheSpringObjectMapper() {
        runner
            .withUserConfiguration(CustomMapperBeans.class)
            .run(context -> {
                ProcessEngineConfig config = context.getBean(ProcessEngineConfig.class);

                assertThat(config.getDataMapper()).isSameAs(context.getBean(ProcessDataMapper.class));
                assertThat(config.getDataMapper()).isNotInstanceOf(JacksonProcessDataMapper.class);
            });
    }

    @Test
    void springObjectMapperIsAdaptedWhenNoProcessMapperBeanExists() {
        runner
            .withUserConfiguration(ObjectMapperBean.class)
            .run(context -> {
                ProcessDataMapper mapper = context.getBean(ProcessDataMapper.class);

                assertThat(mapper).isInstanceOf(JacksonProcessDataMapper.class);
                assertThat(context.getBean(ProcessEngineConfig.class).getDataMapper()).isSameAs(mapper);
                assertThat(mapper.toVariables(new Input("created"))).containsEntry("status", "created");
            });
    }

    @Test
    void defaultMapperIsBothPublicAndUsedByTheEngineConfiguration() {
        runner.run(context -> {
            ProcessDataMapper mapper = context.getBean(ProcessDataMapper.class);

            assertThat(mapper).isInstanceOf(JacksonProcessDataMapper.class);
            assertThat(context.getBean(ProcessEngineConfig.class).getDataMapper()).isSameAs(mapper);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomMapperBeans {
        @Bean
        ProcessDataMapper processDataMapper() {
            return new MarkerMapper();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ObjectMapperBean {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private record Input(String status) {}

    private static final class MarkerMapper implements ProcessDataMapper {
        @Override
        public Map<String, Object> toVariables(Object value) {
            return Map.of("mapper", "custom");
        }

        @Override
        public <T> T fromVariables(Map<String, Object> variables, Class<T> targetType) {
            return null;
        }
    }
}
