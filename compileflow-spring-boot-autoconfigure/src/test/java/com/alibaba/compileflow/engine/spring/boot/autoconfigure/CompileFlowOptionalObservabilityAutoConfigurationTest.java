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

import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.CompileFlowDeployActuatorAutoConfiguration;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.CompileFlowDeploymentMetricsAutoConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.observability.CompileFlowMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CompileFlowOptionalObservabilityAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                CompileFlowCoreAutoConfiguration.class, CompileFlowDeployActuatorAutoConfiguration.class,
                CompileFlowMetricsAutoConfiguration.class))
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void disablingTheEngineAlsoDisablesEngineMetrics() {
        runner
            .withPropertyValues("compileflow.engine.enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(ProcessEngine.class);
                assertThat(context).doesNotHaveBean(HealthIndicator.class);
                assertThat(context).doesNotHaveBean(MeterBinder.class);
            });
    }

    @Test
    void userProvidedEngineRetainsOptionalMetricsWithoutSyntheticHealth() {
        runner
            .withPropertyValues("compileflow.engine.enabled=false")
            .withBean(ProcessEngine.class, () -> mock(ProcessEngine.class))
            .withBean(ProcessEngineConfig.class, ProcessEngineConfig::tbbpm)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(HealthIndicator.class);
                assertThat(context).hasBean("compileFlowMetricsBinder");
                SimpleMeterRegistry registry = new SimpleMeterRegistry();
                context.getBean("compileFlowMetricsBinder", MeterBinder.class).bindTo(registry);
                assertThat(registry
                    .find("compileflow.engine.executor.runtime.load.max.concurrency")
                    .tag("model.type", "TBBPM")
                    .gauge())
                    .isNotNull();
            });
    }

    @Test
    void metricsRepresentEveryConfiguredModelType() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new CompileFlowMetricsBinder(List.of(ProcessEngineConfig.tbbpm(), ProcessEngineConfig.bpmn())).bindTo(registry);

        assertThat(registry
            .find("compileflow.engine.executor.runtime.load.max.concurrency")
            .tag("model.type", "TBBPM")
            .gauge())
            .isNotNull();
        assertThat(registry
            .find("compileflow.engine.executor.runtime.load.max.concurrency")
            .tag("model.type", "BPMN")
            .gauge())
            .isNotNull();
        assertThat(registry.find("compileflow.engine.events.dropped").functionCounter()).isNotNull();
    }

    @Test
    void runtimeMetricsDoNotRequireControlPlaneClasses() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("com.alibaba.compileflow.deploy.control"))
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowDeploymentMetricsAutoConfiguration.class))
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.control-plane-enabled=false",
                    "compileflow.deploy.runtime-worker-enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.routing.codes[0]=metrics.flow")
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("flowDeploymentMetrics");
                assertThat(context).hasBean("compileFlowDeploymentMetricsBinder");
                context.getBean("compileFlowDeploymentMetricsBinder", MeterBinder.class).bindTo(
                        new SimpleMeterRegistry());
            });
    }

    @Test
    void controlPlaneMetricsDoNotRequireRuntimeClasses() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("com.alibaba.compileflow.deploy.runtime"))
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowDeploymentMetricsAutoConfiguration.class))
            .withPropertyValues("compileflow.deploy.enabled=true")
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("flowDeploymentOpsMetrics");
                assertThat(context).hasBean("compileFlowDeploymentMetricsBinder");
                context.getBean("compileFlowDeploymentMetricsBinder", MeterBinder.class).bindTo(
                        new SimpleMeterRegistry());
            });
    }
}
