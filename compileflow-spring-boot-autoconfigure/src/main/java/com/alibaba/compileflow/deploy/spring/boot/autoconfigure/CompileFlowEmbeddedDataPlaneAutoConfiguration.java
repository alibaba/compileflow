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

import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowCoreAutoConfiguration;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineRegistry;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasRouteConverger;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.control.RoutingActivation;
import com.alibaba.compileflow.deploy.control.routing.RoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRepository;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.artifact.RepositoryProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.state.DesiredRoutingState;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing.EmbeddedAliasRouteConverger;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing.LocalReadyRoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeployRuntimeProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures synchronous local-ready convergence for the embedded topology.
 *
 * @author yusu
 */
@AutoConfiguration(after = {CompileFlowCoreAutoConfiguration.class,
        CompileFlowRepositoryAutoConfiguration.class, CompileFlowDeployRoutingAutoConfiguration.class})
@ConditionalOnClass({LocalRoutingReconciler.class, RoutingStateDeliveryTarget.class})
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "EMBEDDED", matchIfMissing = true)
public class CompileFlowEmbeddedDataPlaneAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProcessArtifactRuntimeLoader.class)
    public ProcessArtifactRuntimeLoader embeddedProcessRuntimeLoader(ProcessEngine processEngine,
            ProcessEngineConfig engineConfig, ObjectProvider<ProcessEngineRegistry> registryProvider,
            ObjectProvider<ProcessDeploymentMetrics> metricsProvider) {
        ProcessEngineRegistry registry = registryProvider.getIfAvailable();
        ProcessDeploymentMetrics metrics = metricsProvider.getIfAvailable(ProcessDeploymentMetrics::new);
        return registry == null
                ? new ProcessArtifactRuntimeLoader(processEngine, engineConfig.getModelType(), metrics)
                : new ProcessArtifactRuntimeLoader(registry.getEngines(), metrics);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessArtifactResolver.class)
    public ProcessArtifactResolver embeddedProcessArtifactResolver(ProcessArtifactSource artifactSource) {
        return new RepositoryProcessArtifactResolver(artifactSource);
    }

    @Bean
    @ConditionalOnMissingBean(VersionDemandPlanner.class)
    public VersionDemandPlanner embeddedVersionDemandPlanner() {
        return new VersionDemandPlanner();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(RuntimeInstaller.class)
    public RuntimeInstaller embeddedRuntimeInstaller(ProcessArtifactResolver resolver,
            ProcessArtifactRuntimeLoader loader, LocalRoutingState localRoutingState, ProcessEngineConfig engineConfig,
            DeployRuntimeProperties properties, ProcessDeploymentMetrics metrics) {
        int admissionCapacity = Math.addExact(engineConfig.getExecutorConfig().getRuntimeLoadMaxConcurrency(),
                engineConfig.getExecutorConfig().getRuntimeLoadMaxPending());
        return RuntimeInstaller.withOwnedRetryScheduler(resolver, loader, Runnable::run, localRoutingState,
                properties.getFailureBackoff(), admissionCapacity, metrics);
    }

    @Bean
    @ConditionalOnMissingBean(LocalRoutingReconciler.class)
    public LocalRoutingReconciler localReadyRoutingStateApplier(VersionDemandPlanner planner, RuntimeInstaller installer,
            LocalRoutingState localRoutingState, ProcessEngineConfig engineConfig) {
        return new LocalRoutingReconciler(planner, installer, localRoutingState,
                engineConfig.getAliasTargetingPolicies().keySet());
    }

    @Bean
    @ConditionalOnMissingBean(RoutingActivation.class)
    public RoutingActivation embeddedRoutingActivation(LocalRoutingReconciler localRoutingReconciler,
            DeployRuntimeProperties properties) {
        return alias -> localRoutingReconciler.applyAndAwait(DesiredRoutingState.from(alias),
                properties.getConvergenceTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(RoutingStateDeliveryTarget.class)
    public RoutingStateDeliveryTarget embeddedRoutingStateDeliveryTarget(LocalRoutingReconciler localRoutingReconciler,
            DeployRuntimeProperties runtimeProperties) {
        return new LocalReadyRoutingStateDeliveryTarget(localRoutingReconciler,
                runtimeProperties.getConvergenceTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(AliasRouteConverger.class)
    public AliasRouteConverger embeddedAliasRouteConverger(ProcessAliasRepository aliasRepository,
            LocalRoutingReconciler localRoutingReconciler, DeployRuntimeProperties properties) {
        return new EmbeddedAliasRouteConverger(aliasRepository, localRoutingReconciler,
                properties.getConvergenceTimeout());
    }
}
