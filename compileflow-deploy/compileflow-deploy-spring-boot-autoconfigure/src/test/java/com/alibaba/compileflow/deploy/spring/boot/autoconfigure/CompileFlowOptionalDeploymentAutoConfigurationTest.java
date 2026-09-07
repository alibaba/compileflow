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

import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEnginePropertiesAutoConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxDispatcher;
import com.alibaba.compileflow.deploy.spi.store.DeployStore;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CompileFlowOptionalDeploymentAutoConfigurationTest {
    @Test
    void baseConfigurationDoesNotLinkOptionalDeployClasses() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("com.alibaba.compileflow.deploy"))
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(CompileFlowDeploymentProperties.class);
            });
    }

    @Test
    void controlPlaneConfigurationsBackOffWithoutTheControlPlaneModule() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("com.alibaba.compileflow.deploy.control"))
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowDeployPropertiesAutoConfiguration.class,
                    CompileFlowDeployControlPlaneAutoConfiguration.class, CompileFlowDeployOutboxAutoConfiguration.class))
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.runtime-worker-enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(ProcessDeploymentService.class);
                assertThat(context).doesNotHaveBean(RoutingOutboxDispatcher.class);
            });
    }

    @Test
    void distributedOutboxDoesNotRequireTheRuntimeModule() throws Exception {
        DeployStore repository = mock(DeployStore.class);
        when(repository.claimPending(anyInt(), anyString(), anyLong())).thenReturn(List.of());
        DeploymentProjectionStore projectionStore = mock(DeploymentProjectionStore.class);
        when(projectionStore.read(anyString(), any(Duration.class))).thenReturn(null);

        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("com.alibaba.compileflow.deploy.runtime"))
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowDeployPropertiesAutoConfiguration.class, CompileFlowDeployOutboxAutoConfiguration.class))
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.runtime-worker-enabled=false", "compileflow.deploy.outbox.retention=0ms")
            .withBean(DeployStore.class, () -> repository)
            .withBean(DeploymentProjectionStore.class, () -> projectionStore)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(RoutingOutboxDispatcher.class);
            });
    }
}
