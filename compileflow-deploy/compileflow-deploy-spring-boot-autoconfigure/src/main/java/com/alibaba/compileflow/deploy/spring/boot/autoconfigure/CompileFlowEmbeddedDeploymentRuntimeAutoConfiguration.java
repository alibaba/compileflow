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

import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEngineAutoConfiguration;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasRouteConverger;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.control.RoutingStatePropagator;
import com.alibaba.compileflow.deploy.control.outbox.RoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasStore;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingConvergence;
import com.alibaba.compileflow.deploy.runtime.version.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.artifact.ArtifactSourceResolver;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.routing.DesiredRoutingState;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing.EmbeddedAliasRouteConverger;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing.LocalReadyRoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
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
@AutoConfiguration(after = {CompileFlowEngineAutoConfiguration.class,
        CompileFlowDeployStoreAutoConfiguration.class, CompileFlowDeployRoutingAutoConfiguration.class})
@ConditionalOnClass({LocalRoutingReconciler.class, RoutingStateDeliveryTarget.class})
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "EMBEDDED", matchIfMissing = true)
public class CompileFlowEmbeddedDeploymentRuntimeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProcessArtifactRuntimeLoader.class)
    public ProcessArtifactRuntimeLoader embeddedProcessRuntimeLoader(ProcessEngine processEngine,
            ObjectProvider<DeploymentRuntimeMetrics> metricsProvider) {
        DeploymentRuntimeMetrics metrics = metricsProvider.getIfAvailable(DeploymentRuntimeMetrics::new);
        return new ProcessArtifactRuntimeLoader(processEngine, metrics);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessArtifactResolver.class)
    public ProcessArtifactResolver embeddedProcessArtifactResolver(ProcessArtifactSource artifactSource) {
        return new ArtifactSourceResolver(artifactSource);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(VersionRuntimeManager.class)
    public VersionRuntimeManager embeddedVersionRuntimeManager(ProcessArtifactResolver resolver,
            ProcessArtifactRuntimeLoader loader, LocalRoutingState localRoutingState, ProcessEngineConfig engineConfig,
            CompileFlowDeploymentProperties properties, DeploymentRuntimeMetrics metrics) {
        var runtime = properties.getRuntime();
        // The manager's semaphore has an int ceiling; valid engine limits can sum beyond it.
        int admissionCapacity = (int) Math.min(Integer.MAX_VALUE,
                (long) engineConfig.getExecutorConfig().getRuntimeLoadMaxConcurrency()
                + engineConfig.getExecutorConfig().getRuntimeLoadMaxPending());
        return VersionRuntimeManager.withOwnedRetryScheduler(resolver, loader, Runnable::run, localRoutingState,
                runtime.getFailureBackoff(), admissionCapacity, metrics);
    }

    @Bean
    @ConditionalOnMissingBean(LocalRoutingReconciler.class)
    public LocalRoutingReconciler localRoutingReconciler(VersionRuntimeManager versionRuntimeManager,
            LocalRoutingState localRoutingState, ProcessEngineConfig engineConfig) {
        return new LocalRoutingReconciler(versionRuntimeManager, localRoutingState,
                engineConfig.getAliasTargetingPolicies().keySet());
    }

    @Bean
    @ConditionalOnMissingBean(RoutingStatePropagator.class)
    public RoutingStatePropagator embeddedRoutingStatePropagator(LocalRoutingConvergence convergence,
            CompileFlowDeploymentProperties properties) {
        return alias -> convergence.applyAndAwait(DesiredRoutingState.from(alias),
                properties.getRuntime().getConvergenceTimeout());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(LocalRoutingConvergence.class)
    public LocalRoutingConvergence embeddedLocalRoutingConvergence(LocalRoutingReconciler localRoutingReconciler,
            CompileFlowDeploymentProperties properties) {
        return new LocalRoutingConvergence(localRoutingReconciler, properties
            .getRuntime()
            .getInstallationConcurrency());
    }

    @Bean
    @ConditionalOnMissingBean(RoutingStateDeliveryTarget.class)
    public RoutingStateDeliveryTarget embeddedRoutingStateDeliveryTarget(LocalRoutingConvergence convergence,
            CompileFlowDeploymentProperties properties) {
        return new LocalReadyRoutingStateDeliveryTarget(convergence, properties.getRuntime().getConvergenceTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(AliasRouteConverger.class)
    public AliasRouteConverger embeddedAliasRouteConverger(ProcessAliasStore aliasRepository,
            LocalRoutingConvergence convergence, CompileFlowDeploymentProperties properties) {
        return new EmbeddedAliasRouteConverger(aliasRepository, convergence,
                properties.getRuntime().getConvergenceTimeout());
    }
}
