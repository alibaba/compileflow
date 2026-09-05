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
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import com.alibaba.compileflow.deploy.runtime.DeployRuntime;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ChannelProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.artifact.RepositoryProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.state.DeploymentSyncRoutingStateSubscriber;
import com.alibaba.compileflow.deploy.runtime.state.RoutingStateSubscriber;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing.RoutingStateKeysBuilder;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeployRuntimeProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentArtifactProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentRoutingProperties;
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
 * Auto-configures the transport-independent deployment data plane.
 *
 * @author yusu
 */
@AutoConfiguration(after = {CompileFlowCoreAutoConfiguration.class, CompileFlowRepositoryAutoConfiguration.class})
@ConditionalOnClass(DeployRuntime.class)
@ConditionalOnProperty(prefix = "compileflow.deploy", name = {"enabled", "runtime-worker-enabled"}, havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
public class CompileFlowDeployDataPlaneAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompileFlowDeployDataPlaneAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ProcessArtifactRuntimeLoader.class)
    public ProcessArtifactRuntimeLoader processRuntimeLoader(ProcessEngine processEngine,
            ProcessEngineConfig engineConfig, ObjectProvider<ProcessEngineRegistry> registryProvider,
            ObjectProvider<ProcessDeploymentMetrics> metricsProvider) {
        ProcessEngineRegistry registry = registryProvider.getIfAvailable();
        ProcessDeploymentMetrics metrics = metricsProvider.getIfAvailable(ProcessDeploymentMetrics::new);
        return registry == null
                ? new ProcessArtifactRuntimeLoader(processEngine, engineConfig.getModelType(), metrics)
                : new ProcessArtifactRuntimeLoader(registry.getEngines(), metrics);
    }

    @Bean(name = "deployRuntimeExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "deployRuntimeExecutor")
    public ExecutorService deployRuntimeExecutor(DeployRuntimeProperties properties) {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(properties.getConcurrency(), properties.getConcurrency(), 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(properties.getQueueCapacity()),
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
    public ProcessArtifactResolver processArtifactResolver(DeploymentArtifactProperties artifact,
            DeploymentSyncChannel channel, ObjectProvider<ProcessArtifactSource> sourceProvider) {
        switch (artifact.getMode()) {
            case DATABASE:
                ProcessArtifactSource source = sourceProvider.getIfAvailable();
                if (source == null) {
                    throw new IllegalStateException(
                            "compileflow.deploy.artifact.mode=DATABASE requires a ProcessArtifactSource bean");
                }
                return new RepositoryProcessArtifactResolver(source);
            case CHANNEL:
                return new ChannelProcessArtifactResolver(channel, artifact.getKeyPrefix(),
                        artifact.getOperationTimeout());
            default:
                throw new IllegalStateException("Unsupported deployment artifact mode: " + artifact.getMode());
        }
    }

    @Bean
    @ConditionalOnMissingBean(RoutingStateSubscriber.class)
    public RoutingStateSubscriber routingStateSubscriber(DeploymentSyncChannel channel,
            DeploymentRoutingProperties routing) {
        List<String> keys = RoutingStateKeysBuilder.build(routing);
        return new DeploymentSyncRoutingStateSubscriber(channel, keys, routing.getOperationTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(VersionDemandPlanner.class)
    public VersionDemandPlanner versionDemandPlanner() {
        return new VersionDemandPlanner();
    }

    @Bean
    @ConditionalOnMissingBean(RuntimeInstaller.class)
    public RuntimeInstaller runtimeInstaller(ProcessArtifactResolver resolver, ProcessArtifactRuntimeLoader loader,
            @Qualifier("deployRuntimeExecutor") ExecutorService executor,
            @Qualifier("deployRuntimeRetryScheduler") ScheduledExecutorService retryScheduler,
            LocalRoutingState localRoutingState, DeployRuntimeProperties properties, ProcessDeploymentMetrics metrics) {
        int admissionCapacity = Math.addExact(properties.getConcurrency(), properties.getQueueCapacity());
        return new RuntimeInstaller(resolver, loader, executor, retryScheduler, localRoutingState,
                properties.getFailureBackoff(), admissionCapacity, metrics);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(DeployRuntime.class)
    public DeployRuntime deployRuntime(RoutingStateSubscriber subscriber, VersionDemandPlanner planner,
            RuntimeInstaller installer, LocalRoutingState localRoutingState, ProcessEngineConfig engineConfig) {
        return new DeployRuntime(subscriber, planner, installer, localRoutingState,
                engineConfig.getAliasTargetingPolicies().keySet());
    }

    @Bean
    @ConditionalOnMissingBean(name = "deployRuntimeLifecycle")
    public SmartLifecycle deployRuntimeLifecycle(DeployRuntime deployRuntime) {
        return new ActionBackedLifecycle(() -> {
            deployRuntime.start();
            LOGGER.info("CompileFlow deployment runtime started");
        }, () -> {
            deployRuntime.stop();
            LOGGER.info("CompileFlow deployment runtime stopped");
        }, CompileFlowLifecyclePhases.DEPLOY_DATA_PLANE);
    }
}
