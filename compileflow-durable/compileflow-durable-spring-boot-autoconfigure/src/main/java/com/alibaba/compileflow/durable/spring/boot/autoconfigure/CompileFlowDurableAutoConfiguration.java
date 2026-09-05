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
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.machine.DurableProcessCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableInterpretedProgramCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableProgramCompiler;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.service.DefaultDurableOperatorService;
import com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngine;
import com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorkerOptions;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer;
import com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisher;
import com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisherOptions;
import com.alibaba.compileflow.durable.runtime.worker.DurableProcessRuntimeLoadWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableRetentionWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerOptions;
import com.alibaba.compileflow.durable.runtime.worker.SecureWaitTokenIssuer;
import com.alibaba.compileflow.durable.runtime.worker.WaitTokenIssuer;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasStateSource;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.durable.spi.outbox.DurableOutboxSink;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime.DurableHealthIndicator;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime.DurableWorkerIdentity;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.observability.DurableRuntimeMetricsBinder;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime.DurableWorkerCoordinator;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.properties.CompileFlowDurableProperties;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEnginePropertiesAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Direct opt-in wiring for the CompileFlow 2.0 Durable Runtime.
 *
 * @author yusu
 */
@AutoConfiguration(after = CompileFlowEnginePropertiesAutoConfiguration.class)
@ConditionalOnClass(DurableProcessEngine.class)
@ConditionalOnBean(DurableStore.class)
@ConditionalOnProperty(prefix = "compileflow.durable", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CompileFlowDurableProperties.class)
public class CompileFlowDurableAutoConfiguration {
    private static final Duration NOT_READY_BACKOFF = Duration.ofSeconds(1);

    @Bean
    @ConditionalOnMissingBean(DurableRuntimeMetrics.class)
    public DurableRuntimeMetrics durableRuntimeMetrics() {
        return new DurableRuntimeMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(DurableWorkerIdentity.class)
    public DurableWorkerIdentity durableWorkerIdentity(CompileFlowDurableProperties properties) {
        return DurableWorkerIdentity.create(properties.getWorker().getId());
    }

    @Bean
    @ConditionalOnMissingBean(DurableWaitDescriptionProvider.class)
    public DurableWaitDescriptionProvider durableWaitDescriptionProvider() {
        return DurableWaitDescriptionProvider.defaults();
    }

    @Bean
    @ConditionalOnMissingBean(DurableProcessRuntimeCache.class)
    public DurableProcessRuntimeCache durableProcessRuntimeCache(CompileFlowDurableProperties properties) {
        return new InMemoryDurableProcessRuntimeCache(properties.getCache().getRuntimeMaxSize());
    }

    @Bean
    @ConditionalOnMissingBean(ScriptExecutorRegistry.class)
    public ScriptExecutorRegistry durableScriptExecutorRegistry(ProcessEngineConfig config) {
        return ScriptExecutorRegistry.configured(config, config.getScriptExecutors());
    }

    @Bean
    @ConditionalOnMissingBean(DurableProcessCompiler.class)
    public DurableProcessCompiler durableProcessCompiler(ScriptExecutorRegistry scripts) {
        return new DurableProcessCompiler(scripts);
    }

    @Bean
    @ConditionalOnMissingBean(DurableProgramCompiler.class)
    public DurableProgramCompiler durableProgramCompiler(CompileFlowDurableProperties properties) {
        return properties.getRuntimeMode() == ProcessRuntimeMode.INTERPRETED
                ? new DurableInterpretedProgramCompiler()
                : new DurableJavaProgramCompiler();
    }

    @Bean
    @ConditionalOnMissingBean(DurableActionInvoker.class)
    public DurableActionInvoker durableActionInvoker(ProcessEngineConfig config, ScriptExecutorRegistry scripts) {
        return new DurableActionInvoker(config.getComponentResolver(), scripts, config.getClassLoader());
    }

    @Bean
    @ConditionalOnMissingBean(DurableProcessRuntimeManager.class)
    public DurableProcessRuntimeManager durableProcessRuntimeManager(DurableStore store,
            DurableProcessRuntimeCache runtimeCache, DurableProcessCompiler processCompiler,
            DurableProgramCompiler programCompiler, ProcessEngineConfig config,
            ObjectProvider<DurableVersionDefinitionSource> versionSources) {
        return new DurableProcessRuntimeManager(store, runtimeCache, processCompiler, programCompiler, config,
                versionSources.getIfAvailable(DurableVersionDefinitionSource::empty));
    }

    @Bean
    @ConditionalOnMissingBean(DurableProcessEngine.class)
    public DurableProcessEngine durableProcessEngine(DurableStore store,
            DurableProcessRuntimeManager processRuntimeManager,
            ObjectProvider<DurableAliasStateSource> aliasStateSources, ProcessEngineConfig engineConfig) {
        DurableAliasStateSource aliasStateSource = aliasStateSources.getIfAvailable();
        if (aliasStateSource == null) {
            return new DefaultDurableProcessEngine(store, processRuntimeManager);
        }
        return new DefaultDurableProcessEngine(store, processRuntimeManager, aliasStateSource,
                engineConfig.getAliasTargetingPolicies());
    }

    @Bean
    @ConditionalOnMissingBean(DurableOperatorService.class)
    public DurableOperatorService durableOperatorService(DurableStore store,
            DurableProcessRuntimeManager processRuntimeManager) {
        return new DefaultDurableOperatorService(store, processRuntimeManager);
    }

    /**
     * Keeps optional Deploy types out of the primary Durable configuration's reflective surface.
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

    @Bean
    @ConditionalOnMissingBean(WaitTokenIssuer.class)
    public WaitTokenIssuer durableWaitTokenIssuer() {
        return new SecureWaitTokenIssuer();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(DurableLeaseRenewer.class)
    public DurableLeaseRenewer durableLeaseRenewer(DurableStore store, DurableRuntimeMetrics metrics,
            CompileFlowDurableProperties properties) {
        return new DurableLeaseRenewer(store, properties.getWorker().getLeaseDuration(), metrics);
    }

    @Bean
    @ConditionalOnMissingBean(DurableTurnWorker.class)
    public DurableTurnWorker durableTurnWorker(DurableStore store, DurableProcessRuntimeManager processRuntimeManager,
            DurableProcessRuntimeCache runtimeCache, DurableActionInvoker actions, DurableWaitDescriptionProvider waits,
            WaitTokenIssuer tokens, DurableLeaseRenewer leases, DurableRuntimeMetrics metrics,
            CompileFlowDurableProperties properties, DurableWorkerIdentity identity) {
        return new DurableTurnWorker(store, processRuntimeManager, runtimeCache, actions, waits, tokens,
                new DurableTurnWorkerOptions(identity.worker("turn"), properties.getWorker().getTurnFaultBackoff(),
                        properties.getWorker().getTurnMaxSteps(), properties.getWorker().getMaxActiveIterations()),
                leases, metrics);
    }

    @Bean
    @ConditionalOnMissingBean(DurableEffectWorker.class)
    public DurableEffectWorker durableEffectWorker(DurableStore store, DurableProcessRuntimeCache runtimeCache,
            DurableActionInvoker actions, DurableLeaseRenewer leases, DurableRuntimeMetrics metrics,
            CompileFlowDurableProperties properties, DurableWorkerIdentity identity) {
        return new DurableEffectWorker(store, runtimeCache, actions,
                new DurableEffectWorkerOptions(identity.worker("effect"), NOT_READY_BACKOFF), leases, metrics);
    }

    @Bean
    @ConditionalOnMissingBean(DurableProcessRuntimeLoadWorker.class)
    public DurableProcessRuntimeLoadWorker durableProcessRuntimeLoadWorker(DurableStore store,
            DurableProcessRuntimeManager processRuntimeManager, DurableProcessRuntimeCache runtimeCache,
            DurableRuntimeMetrics metrics, CompileFlowDurableProperties properties) {
        return new DurableProcessRuntimeLoadWorker(store, processRuntimeManager, runtimeCache, NOT_READY_BACKOFF,
                metrics);
    }

    @Bean
    @ConditionalOnMissingBean(DurableOutboxPublisher.class)
    @ConditionalOnBean(DurableOutboxSink.class)
    @ConditionalOnProperty(prefix = "compileflow.durable.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DurableOutboxPublisher durableOutboxPublisher(DurableStore store, DurableOutboxSink sink,
            DurableLeaseRenewer leases, DurableRuntimeMetrics metrics, CompileFlowDurableProperties properties,
            DurableWorkerIdentity identity) {
        CompileFlowDurableProperties.Outbox.Retry retry = properties.getOutbox().getRetry();
        return new DurableOutboxPublisher(store, sink,
                new DurableOutboxPublisherOptions(identity.worker("outbox"), retry.getInitialDelay(),
                        retry.getMaxDelay(), retry.getMaxAttempts()), leases, metrics);
    }

    @Bean
    @Conditional(RetentionConfiguredCondition.class)
    @ConditionalOnMissingBean(DurableRetentionWorker.class)
    public DurableRetentionWorker durableRetentionWorker(DurableStore store, DurableRuntimeMetrics metrics,
            CompileFlowDurableProperties properties) {
        return new DurableRetentionWorker(store, properties.getRetention().getTerminalRun(),
                properties.getRetention().getUnusedProcess(), properties.getRetention().getConsumedOccurrence(), metrics);
    }

    static final class RetentionConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getEnvironment().containsProperty("compileflow.durable.retention.terminal-run")
                    || context.getEnvironment().containsProperty("compileflow.durable.retention.unused-process")
                    || context.getEnvironment().containsProperty("compileflow.durable.retention.consumed-occurrence");
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "compileflow.durable.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(DurableWorkerCoordinator.class)
    public DurableWorkerCoordinator durableWorkerCoordinator(DurableStore store,
            DurableProcessRuntimeLoadWorker programLoads, DurableTurnWorker turns, DurableEffectWorker effects,
            ObjectProvider<DurableOutboxPublisher> outbox, ObjectProvider<DurableRetentionWorker> retention,
            DurableRuntimeMetrics metrics, CompileFlowDurableProperties properties) {
        return new DurableWorkerCoordinator(store, programLoads, turns, effects,
                Optional.ofNullable(outbox.getIfAvailable()), Optional.ofNullable(retention.getIfAvailable()),
                properties.getWorker().getIdlePollDelay(), properties.getMaintenance().getInterval(),
                properties.getRetention().getInterval(), properties.getMaintenance().getBatchSize(),
                properties.getWorker().getTurnConcurrency(), properties.getWorker().getEffectConcurrency(),
                properties.getOutbox().getConcurrency(), metrics);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {
        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnMissingBean(name = "compileFlowDurableMetricsBinder")
        MeterBinder compileFlowDurableMetricsBinder(DurableRuntimeMetrics metrics,
                DurableProcessRuntimeCache runtimeCache) {
            return new DurableRuntimeMetricsBinder(metrics, runtimeCache);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    static class HealthConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "compileFlowDurableHealthIndicator")
        DurableHealthIndicator compileFlowDurableHealthIndicator(DurableStore store,
                DurableProcessRuntimeCache runtimeCache, ObjectProvider<DurableWorkerCoordinator> coordinators,
                ObjectProvider<DurableLeaseRenewer> renewers, ObjectProvider<DurableOutboxSink> sinks) {
            return new DurableHealthIndicator(store, runtimeCache, coordinators::getIfAvailable,
                    renewers::getIfAvailable, sinks.getIfAvailable() != null);
        }
    }
}
