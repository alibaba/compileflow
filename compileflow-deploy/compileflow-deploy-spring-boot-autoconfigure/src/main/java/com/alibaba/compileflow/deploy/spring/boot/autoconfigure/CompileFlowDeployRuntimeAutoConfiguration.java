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
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntime;
import com.alibaba.compileflow.deploy.runtime.version.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ProjectionStoreArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.artifact.ArtifactSourceResolver;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.routing.ProjectionStoreRoutingStateSubscriber;
import com.alibaba.compileflow.deploy.runtime.routing.DesiredRoutingStateSubscriber;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing.RoutingStateKeysBuilder;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentArtifactProperties;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the transport-independent deployment runtime.
 *
 * @author yusu
 */
@AutoConfiguration(after = {CompileFlowEngineAutoConfiguration.class, CompileFlowDeployStoreAutoConfiguration.class})
@ConditionalOnClass(DeploymentRuntime.class)
@ConditionalOnProperty(prefix = "compileflow.deploy", name = {"enabled", "runtime-worker-enabled"}, havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
public class CompileFlowDeployRuntimeAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompileFlowDeployRuntimeAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ProcessArtifactRuntimeLoader.class)
    public ProcessArtifactRuntimeLoader processRuntimeLoader(ProcessEngine processEngine,
            ObjectProvider<DeploymentRuntimeMetrics> metricsProvider) {
        DeploymentRuntimeMetrics metrics = metricsProvider.getIfAvailable(DeploymentRuntimeMetrics::new);
        return new ProcessArtifactRuntimeLoader(processEngine, metrics);
    }

    @Bean(name = "deployRuntimeExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "deployRuntimeExecutor")
    public ExecutorService deployRuntimeExecutor(CompileFlowDeploymentProperties properties) {
        var runtime = properties.getRuntime();
        AtomicInteger sequence = new AtomicInteger();
        // Completion callbacks admit the next installation before the current executor task returns.
        return new ThreadPoolExecutor(runtime.getInstallationConcurrency(), runtime.getInstallationConcurrency(), 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(runtime.getInstallationConcurrency()),
                runnable -> {
                    Thread thread = new Thread(runnable, "compileflow-deploy-runtime-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "deployRuntimeRetryScheduler", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "deployRuntimeRetryScheduler")
    public ScheduledExecutorService deployRuntimeRetryScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "compileflow-deploy-runtime-retry");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnMissingBean(ProcessArtifactResolver.class)
    public ProcessArtifactResolver processArtifactResolver(CompileFlowDeploymentProperties properties,
            DeploymentProjectionStore projectionStore, ObjectProvider<ProcessArtifactSource> sourceProvider) {
        DeploymentArtifactProperties artifact = properties.getArtifact();
        switch (artifact.getMode()) {
            case SOURCE:
                ProcessArtifactSource source = sourceProvider.getIfAvailable();
                if (source == null) {
                    throw new IllegalStateException(
                            "compileflow.deploy.artifact.mode=SOURCE requires a ProcessArtifactSource bean");
                }
                return new ArtifactSourceResolver(source);
            case PROJECTION_STORE:
                return new ProjectionStoreArtifactResolver(projectionStore, artifact.getKeyPrefix(),
                        artifact.getOperationTimeout());
            default:
                throw new IllegalStateException("Unsupported deployment artifact mode: " + artifact.getMode());
        }
    }

    @Bean
    @ConditionalOnMissingBean(DesiredRoutingStateSubscriber.class)
    public DesiredRoutingStateSubscriber routingStateSubscriber(DeploymentProjectionStore projectionStore,
            CompileFlowDeploymentProperties properties) {
        var routing = properties.getRouting();
        List<String> keys = RoutingStateKeysBuilder.build(routing);
        return new ProjectionStoreRoutingStateSubscriber(projectionStore, keys, routing.getOperationTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(VersionRuntimeManager.class)
    public VersionRuntimeManager versionRuntimeManager(ProcessArtifactResolver resolver,
            ProcessArtifactRuntimeLoader loader, @Qualifier("deployRuntimeExecutor") ExecutorService executor,
            @Qualifier("deployRuntimeRetryScheduler") ScheduledExecutorService retryScheduler,
            LocalRoutingState localRoutingState, CompileFlowDeploymentProperties properties,
            DeploymentRuntimeMetrics metrics) {
        var runtime = properties.getRuntime();
        int admissionCapacity = runtime.getInstallationConcurrency();
        return new VersionRuntimeManager(resolver, loader, executor, retryScheduler, localRoutingState,
                runtime.getFailureBackoff(), admissionCapacity, metrics);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(DeploymentRuntime.class)
    public DeploymentRuntime deploymentRuntime(DesiredRoutingStateSubscriber subscriber,
            VersionRuntimeManager versionRuntimeManager, LocalRoutingState localRoutingState,
            ProcessEngineConfig engineConfig) {
        return new DeploymentRuntime(subscriber, versionRuntimeManager, localRoutingState,
                engineConfig.getAliasTargetingPolicies().keySet());
    }

    @Bean
    @ConditionalOnMissingBean(name = "deployRuntimeLifecycle")
    public SmartLifecycle deploymentRuntimeLifecycle(DeploymentRuntime deploymentRuntime) {
        return new ActionBackedLifecycle(() -> {
            deploymentRuntime.start();
            LOGGER.info("CompileFlow deployment runtime started");
        }, () -> {
            deploymentRuntime.stop();
            LOGGER.info("CompileFlow deployment runtime stopped");
        }, CompileFlowLifecyclePhases.DEPLOY_RUNTIME);
    }
}
