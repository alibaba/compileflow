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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntime;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntimeSnapshot;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.routing.RoutingConvergenceSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CompileFlowDeploymentOptionalMetricsAutoConfigurationTest {
    @Test
    void exportsDistributedRuntimeRoutingSnapshotWithoutAnEmbeddedReconcilerBean() {
        DeploymentRuntime runtime = mock(DeploymentRuntime.class);
        DeploymentRuntimeSnapshot snapshot = mock(DeploymentRuntimeSnapshot.class);
        RoutingConvergenceSnapshot routing = mock(RoutingConvergenceSnapshot.class);
        when(runtime.snapshot()).thenReturn(snapshot);
        when(snapshot.getRouting()).thenReturn(routing);
        when(routing.getDesiredAliasCount()).thenReturn(5);
        when(routing.getLocalReadyAliasCount()).thenReturn(4);
        when(routing.getPendingAliasCount()).thenReturn(1);

        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowDeploymentMetricsAutoConfiguration.class))
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED")
            .withBean(DeploymentRuntime.class, () -> runtime)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(LocalRoutingReconciler.class);
                SimpleMeterRegistry registry = new SimpleMeterRegistry();
                try {
                    context.getBean("compileFlowDeploymentMetricsBinder", MeterBinder.class).bindTo(registry);
                    assertThat(registry.get("compileflow.deploy.alias.desired.count").gauge().value()).isEqualTo(5.0);
                    assertThat(registry.get("compileflow.deploy.alias.local_ready.count").gauge().value()).isEqualTo(
                            4.0);
                    assertThat(registry.get("compileflow.deploy.alias.pending.count").gauge().value()).isEqualTo(1.0);
                } finally {
                    registry.close();
                }
            });
    }

    @Test
    void exporterDoesNotDependOnRegistryRegistrationOrder() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowDeploymentMetricsAutoConfiguration.class))
            .withPropertyValues("compileflow.deploy.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("compileFlowDeploymentMetricsBinder");
                assertThat(context).doesNotHaveBean(MeterRegistry.class);
            });
    }

    @Test
    void registersExporterWithDefaultDeploymentCounters() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowDeploymentMetricsAutoConfiguration.class))
            .withPropertyValues("compileflow.deploy.enabled=true")
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("flowDeploymentMetrics");
                assertThat(context).hasBean("flowDeploymentOpsMetrics");
                assertThat(context).hasBean("compileFlowDeploymentMetricsBinder");
            });
    }

    @Test
    void runtimeMetricsDoNotRequireControlPlaneClasses() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("com.alibaba.compileflow.deploy.control"))
            .withConfiguration(AutoConfigurations.of(CompileFlowDeploymentMetricsAutoConfiguration.class))
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.control-plane-enabled=false",
                    "compileflow.deploy.runtime-worker-enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.routing.codes[0]=metrics.flow")
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("flowDeploymentMetrics");
                assertThat(context).doesNotHaveBean("flowDeploymentOpsMetrics");
                assertThat(context).doesNotHaveBean("compileFlowDeploymentMetricsBinder");
            });
    }

    @Test
    void controlPlaneMetricsDoNotRequireRuntimeClasses() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("com.alibaba.compileflow.deploy.runtime"))
            .withConfiguration(AutoConfigurations.of(CompileFlowDeploymentMetricsAutoConfiguration.class))
            .withPropertyValues("compileflow.deploy.enabled=true")
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("flowDeploymentOpsMetrics");
                assertThat(context).doesNotHaveBean("flowDeploymentMetrics");
                assertThat(context).doesNotHaveBean("compileFlowDeploymentMetricsBinder");
            });
    }
}
