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

import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.control.projection.RoutingProjectionReconciler;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.observability.CompileFlowDeploymentMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
        CompileFlowDeployDataPlaneAutoConfiguration.class})
@ConditionalOnClass(ProcessDeploymentMetrics.class)
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
public class CompileFlowDeploymentMetricsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ProcessDeploymentMetrics flowDeploymentMetrics() {
        return new ProcessDeploymentMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDeploymentOperationMetrics flowDeploymentOpsMetrics() {
        return new ProcessDeploymentOperationMetrics();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerConfiguration {
        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnMissingBean(name = "compileFlowDeploymentMetricsBinder")
        MeterBinder compileFlowDeploymentMetricsBinder(ProcessDeploymentMetrics runtimeMetrics,
                ProcessDeploymentOperationMetrics controlMetrics, ObjectProvider<RuntimeInstaller> installerProvider,
                ObjectProvider<LocalRoutingReconciler> localReadyProvider,
                ObjectProvider<RoutingProjectionReconciler> reconciliationProvider) {
            return new CompileFlowDeploymentMetricsBinder(runtimeMetrics, controlMetrics,
                    installerProvider::getIfAvailable, localReadyProvider::getIfAvailable,
                    reconciliationProvider::getIfAvailable);
        }
    }
}
