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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure;

import com.alibaba.compileflow.durable.api.DurableOperatorService;
import com.alibaba.compileflow.durable.api.DurableProcessEngine;
import com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngine;
import com.alibaba.compileflow.durable.runtime.DurableProcessEngineConfig;
import com.alibaba.compileflow.durable.runtime.DurableProcessEngineFactory;
import com.alibaba.compileflow.durable.runtime.service.DefaultDurableOperatorService;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasStateSource;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.durable.spi.outbox.DurableOutboxSink;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime.DurableWorkerLifecycle;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.properties.CompileFlowDurableProperties;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adapts the Spring environment to the canonical Durable engine assembly.
 *
 * @author yusu
 */
@AutoConfiguration(after = CompileFlowDurablePropertiesAutoConfiguration.class)
@ConditionalOnClass(DurableProcessEngine.class)
@ConditionalOnBean(DurableStore.class)
@ConditionalOnProperty(prefix = "compileflow.durable", name = "enabled", havingValue = "true")
public class CompileFlowDurableAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public DurableProcessEngineConfig durableProcessEngineConfig(DurableStore store,
            CompileFlowDurableProperties properties, ObjectProvider<ProcessComponentResolver> resolvers,
            ObjectProvider<ScriptExecutor> scripts, ObjectProvider<ProcessAliasTargetingPolicy> policies,
            ObjectProvider<DurableVersionDefinitionSource> versions, ObjectProvider<DurableAliasStateSource> aliases,
            ObjectProvider<DurableWaitDescriptionProvider> waits, ObjectProvider<DurableOutboxSink> sinks) {
        CompileFlowDurableProperties.Worker worker = properties.getWorker();
        CompileFlowDurableProperties.Outbox outbox = properties.getOutbox();
        CompileFlowDurableProperties.Retention retention = properties.getRetention();
        DurableProcessEngineConfig.Builder builder = DurableProcessEngineConfig
            .builder(store)
            .runtimeMode(properties.getRuntimeMode())
            .maxCallDepth(properties.getCall().getMaxDepth())
            .shutdownTimeout(properties.getShutdown().getTimeout())
            .definitions(properties.getDefinition().toConfig())
            .javaDiagnostics(properties.getJavaDiagnostics().toConfig())
            .cacheMaxSize(properties.getCache().getRuntimeMaxSize())
            .worker(
                    new DurableProcessEngineConfig.Worker(worker.isEnabled(), worker.getId(), worker.getLeaseDuration(),
                            worker.getIdlePollDelay(), worker.getTurnFaultBackoff(), worker.getTurnMaxSteps(),
                            worker.getMaxActiveIterations(), worker.getTurnConcurrency(), worker.getEffectConcurrency()))
            .outbox(
                    new DurableProcessEngineConfig.Outbox(outbox.getConcurrency(), outbox.getRetry().getInitialDelay(),
                            outbox.getRetry().getMaxDelay(), outbox.getRetry().getMaxAttempts()))
            .maintenance(
                    new DurableProcessEngineConfig.Maintenance(properties.getMaintenance().getInterval(),
                            properties.getMaintenance().getBatchSize()))
            .retention(
                    new DurableProcessEngineConfig.Retention(retention.getTerminalRun(), retention.getUnusedProcess(),
                            retention.getConsumedOccurrence(), retention.getInterval()))
            .componentResolver(resolvers.getIfAvailable(ProcessComponentResolver::disabled))
            .versionDefinitionSource(versions.getIfAvailable(DurableVersionDefinitionSource::empty))
            .aliasStateSource(aliases.getIfAvailable())
            .waitDescriptionProvider(waits.getIfAvailable(DurableWaitDescriptionProvider::defaults))
            .outboxSink(sinks.getIfAvailable());
        scripts.orderedStream().forEach(builder::scriptExecutor);
        policies.orderedStream().forEach(builder::aliasTargetingPolicy);
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(DurableProcessEngine.class)
    public DefaultDurableProcessEngine durableProcessEngine(DurableProcessEngineConfig config) {
        return DurableProcessEngineFactory.create(config);
    }

    @Bean
    @ConditionalOnBean(DefaultDurableProcessEngine.class)
    @ConditionalOnMissingBean(DurableOperatorService.class)
    public DurableOperatorService durableOperatorService(DefaultDurableProcessEngine engine) {
        return new DefaultDurableOperatorService(engine.getStore(), engine.getProcessRuntimeManager());
    }

    @Bean
    @ConditionalOnMissingBean(DurableWorkerLifecycle.class)
    @ConditionalOnProperty(prefix = "compileflow.durable.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DurableWorkerLifecycle durableWorkerLifecycle(DurableProcessEngine engine) {
        return new DurableWorkerLifecycle(engine);
    }

    /**
     * Optional Deploy admission adapters; never consulted during Run recovery.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"com.alibaba.compileflow.deploy.api.ProcessDeploymentService",
            "com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource"})
    static class DeployAdmissionConfiguration {
        @Bean
        @ConditionalOnBean(ProcessDeploymentService.class)
        @ConditionalOnMissingBean(DurableAliasStateSource.class)
        DurableAliasStateSource durableDeployAliasStateSource(ProcessDeploymentService deployments) {
            return new DeployAliasStateSourceAdapter(deployments);
        }

        @Bean
        @ConditionalOnBean(ProcessArtifactSource.class)
        @ConditionalOnMissingBean(DurableVersionDefinitionSource.class)
        DurableVersionDefinitionSource durableDeployVersionDefinitionSource(ProcessArtifactSource source) {
            return new DeployDurableVersionDefinitionSourceAdapter(source);
        }
    }
}
