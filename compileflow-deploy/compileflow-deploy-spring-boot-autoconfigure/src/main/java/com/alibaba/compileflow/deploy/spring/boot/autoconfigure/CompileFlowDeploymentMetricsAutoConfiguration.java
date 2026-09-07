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

import com.alibaba.compileflow.deploy.runtime.DeploymentRuntime;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics;
import com.alibaba.compileflow.deploy.control.observability.DeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.control.projection.DeploymentProjectionReconciler;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.observability.CompileFlowDeploymentMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides instance-scoped deployment counters and optional Micrometer export.
 *
 * @author yusu
 */
@AutoConfiguration(before = {CompileFlowDeployControlPlaneAutoConfiguration.class,
        CompileFlowDeployRuntimeAutoConfiguration.class})
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
public class CompileFlowDeploymentMetricsAutoConfiguration {
    @Bean
    @ConditionalOnClass(DeploymentRuntimeMetrics.class)
    @ConditionalOnMissingBean
    public DeploymentRuntimeMetrics flowDeploymentMetrics() {
        return new DeploymentRuntimeMetrics();
    }

    @Bean
    @ConditionalOnClass(DeploymentOperationMetrics.class)
    @ConditionalOnMissingBean
    public DeploymentOperationMetrics flowDeploymentOpsMetrics() {
        return new DeploymentOperationMetrics();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({MeterRegistry.class, DeploymentRuntimeMetrics.class, DeploymentOperationMetrics.class})
    static class MicrometerConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "compileFlowDeploymentMetricsBinder")
        MeterBinder compileFlowDeploymentMetricsBinder(DeploymentRuntimeMetrics runtimeMetrics,
                DeploymentOperationMetrics controlMetrics,
                ObjectProvider<VersionRuntimeManager> versionRuntimeManagerProvider,
                ObjectProvider<LocalRoutingReconciler> localReadyProvider,
                ObjectProvider<DeploymentRuntime> deploymentRuntimeProvider,
                ObjectProvider<DeploymentProjectionReconciler> reconciliationProvider) {
            return new CompileFlowDeploymentMetricsBinder(runtimeMetrics, controlMetrics,
                    versionRuntimeManagerProvider::getIfAvailable,
                    () -> {
                        LocalRoutingReconciler localReady = localReadyProvider.getIfAvailable();
                        if (localReady != null) {
                            return localReady.snapshot();
                        }
                        DeploymentRuntime runtime = deploymentRuntimeProvider.getIfAvailable();
                        return runtime == null ? null : runtime.snapshot().getRouting();
                    }, reconciliationProvider::getIfAvailable);
        }
    }
}
