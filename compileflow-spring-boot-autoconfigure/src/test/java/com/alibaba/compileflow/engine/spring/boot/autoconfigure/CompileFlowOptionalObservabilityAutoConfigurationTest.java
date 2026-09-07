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
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.observability.CompileFlowMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CompileFlowOptionalObservabilityAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                CompileFlowEngineAutoConfiguration.class, CompileFlowEngineMetricsAutoConfiguration.class))
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void micrometerRemainsOptional() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("io.micrometer.core"))
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowEngineAutoConfiguration.class, CompileFlowEngineMetricsAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ProcessEngine.class);
                assertThat(context).doesNotHaveBean("compileFlowMetricsBinder");
            });
    }

    @Test
    void createsTheBinderWithoutDependingOnRegistryBeanRegistrationOrder() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowEngineAutoConfiguration.class, CompileFlowEngineMetricsAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(MeterRegistry.class);
                assertThat(context).hasBean("compileFlowMetricsBinder");
            });
    }

    @Test
    void disablingTheEngineAlsoDisablesEngineMetrics() {
        runner
            .withPropertyValues("compileflow.engine.enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(ProcessEngine.class);
                assertThat(context).doesNotHaveBean(MeterBinder.class);
            });
    }

    @Test
    void unknownEngineDoesNotBorrowCapacityFromAnUnrelatedConfiguration() {
        runner
            .withPropertyValues("compileflow.engine.enabled=false")
            .withBean(ProcessEngine.class, () -> mock(ProcessEngine.class))
            .withBean(ProcessEngineConfig.class, ProcessEngineConfig::defaults)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("compileFlowMetricsBinder");
                SimpleMeterRegistry registry = new SimpleMeterRegistry();
                context.getBean("compileFlowMetricsBinder", MeterBinder.class).bindTo(registry);
                assertThat(registry.find("compileflow.engine.executor.runtime.load.max.concurrency").gauge()).isNull();
                assertThat(registry.find("compileflow.engine.events.dropped").functionCounter()).isNotNull();
            });
    }

    @Test
    void customEngineCapacityComesFromItsOwnConfiguration() {
        ProcessEngineConfig actual = ProcessEngineConfig
            .builder()
            .executors(ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(7).build())
            .build();
        runner
            .withBean(ProcessEngine.class, () -> ProcessEngineFactory.create(actual))
            .withBean(ProcessEngineConfig.class, () -> ProcessEngineConfig
                .builder()
                .executors(ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(3).build())
                .build())
            .run(context -> {
                assertThat(context).hasNotFailed();
                SimpleMeterRegistry registry = new SimpleMeterRegistry();
                try {
                    context.getBean("compileFlowMetricsBinder", MeterBinder.class).bindTo(registry);
                    assertThat(registry
                        .get("compileflow.engine.executor.runtime.load.max.concurrency")
                        .gauge()
                        .value()).isEqualTo(7);
                } finally {
                    registry.close();
                }
            });
    }

    @Test
    void capacityMetricsHaveNoDefinitionTypeTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (ProcessEngine engine = ProcessEngineFactory.create()) {
            new CompileFlowMetricsBinder(engine).bindTo(registry);
            assertThat(registry.find("compileflow.engine.executor.runtime.load.max.concurrency").gauge()).isNotNull();
            assertThat(registry
                .find("compileflow.engine.executor.runtime.load.max.concurrency")
                .gauge()
                .getId()
                .getTags())
                .isEmpty();
            assertThat(registry.find("compileflow.engine.events.dropped").functionCounter()).isNotNull();
        } finally {
            registry.close();
        }
    }
}
