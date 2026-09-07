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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import com.alibaba.compileflow.durable.api.DurableOperatorService;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.runtime.DurableProcessEngineFactory;
import com.alibaba.compileflow.durable.spi.outbox.DurableOutboxSink;
import com.alibaba.compileflow.durable.api.DurableProcessEngine;
import com.alibaba.compileflow.durable.runtime.program.DurableProgramCompiler;
import com.alibaba.compileflow.durable.runtime.worker.DurableRetentionWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer;
import com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableProcessRuntimeLoadWorker;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasStateSource;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.durable.runtime.DurableProcessEngineConfig;
import com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngine;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CompileFlowDurableOptionalObservabilityAutoConfigurationTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowDurablePropertiesAutoConfiguration.class,
                    CompileFlowDurableAutoConfiguration.class, CompileFlowDurableObservabilityAutoConfiguration.class))
            .withPropertyValues("compileflow.durable.enabled=true", "compileflow.durable.worker.enabled=false")
            .withBean(DurableStore.class, () -> mock(DurableStore.class));
    }

    @Test
    void doesNotComposeWithoutStore() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowDurablePropertiesAutoConfiguration.class,
                    CompileFlowDurableAutoConfiguration.class, CompileFlowDurableObservabilityAutoConfiguration.class))
            .withPropertyValues("compileflow.durable.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(DurableStore.class);
                assertThat(context).doesNotHaveBean(DurableProcessEngine.class);
            });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void operatorAndHealthUseTheActualEngineAuthority(boolean customEngine) {
        DurableStore authority = mock(DurableStore.class);
        when(authority.listProcessRuntimeDemand(any())).thenReturn(
                new DurableStore.ProcessRuntimeDemandPage(List.of(), null));
        DurableProcessEngineConfig config =
                DurableProcessEngineConfig.builder(authority).outboxSink(mock(DurableOutboxSink.class)).build();
        ApplicationContextRunner configured = customEngine
                ? runner()
            .withBean(DefaultDurableProcessEngine.class, () -> DurableProcessEngineFactory.create(config))
                : runner().withBean(DurableProcessEngineConfig.class, () -> config);

        configured.run(context -> {
            assertThat(context).hasNotFailed();
            ProcessRunId runId = ProcessRunId.random();
            assertThat(context.getBean(DurableProcessEngine.class).getRun(runId)).isEmpty();
            assertThat(context.getBean(DurableOperatorService.class).getRun(runId)).isEmpty();
            verify(authority, times(2)).findRun(runId);
            var health = context.getBean("compileFlowDurableHealthIndicator", HealthIndicator.class).health();
            assertThat(health.getStatus().getCode()).isEqualTo("UP");
            assertThat(health.getDetails()).containsEntry("outboxSink", "configured");
            verify(authority).listProcessRuntimeDemand(any());
            verifyNoInteractions(context.getBean(DurableStore.class));
        });
    }

    @Test
    void registersOptionalMetricsAndAuthorityHealth() {
        runner()
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("compileFlowDurableMetricsBinder");
                assertThat(context).hasBean("compileFlowDurableHealthIndicator");
                assertThat(context.getBean("compileFlowDurableMetricsBinder")).isInstanceOf(MeterBinder.class);
                assertThat(context.getBean("compileFlowDurableHealthIndicator")).isInstanceOf(HealthIndicator.class);
            });
    }

    @Test
    void selectsOneDurableProgramRealization() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ProcessEngine.class).doesNotHaveBean(ProcessEngineConfig.class);
            assertThat(context).doesNotHaveBean(DurableProgramCompiler.class);
            assertThat(context.getBean(DurableProcessEngineConfig.class).getRuntimeMode()).isEqualTo(
                    ProcessRuntimeMode.COMPILED);
        });
        runner()
            .withPropertyValues("compileflow.durable.runtime-mode=interpreted")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(DurableProgramCompiler.class);
                assertThat(context.getBean(DurableProcessEngineConfig.class).getRuntimeMode())
                    .isEqualTo(ProcessRuntimeMode.INTERPRETED);
            });
    }

    @Test
    void startsWithoutOptionalObservabilityLibraries() {
        runner()
            .withClassLoader(
                    new FilteredClassLoader("io.micrometer.core.instrument",
                            "org.springframework.boot.health.contributor"))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(MeterBinder.class);
                assertThat(context).doesNotHaveBean(HealthIndicator.class);
            });
    }

    @Test
    void startsWithoutOptionalDeployLibrary() {
        runner()
            .withClassLoader(new FilteredClassLoader("com.alibaba.compileflow.deploy.api"))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(DurableAliasStateSource.class);
                assertThat(context).doesNotHaveBean(DurableVersionDefinitionSource.class);
            });
    }

    @Test
    void customStoreComposesWithoutPostgresOrJdbc() {
        runner()
            .withClassLoader(new FilteredClassLoader("com.alibaba.compileflow.durable.postgres", "javax.sql"))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(DurableStore.class);
                assertThat(context).hasBean("compileFlowDurableHealthIndicator");
            });
    }

    @Test
    void terminalRunRetentionIsStrictlyOptIn() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DurableLeaseRenewer.class);
            assertThat(context).doesNotHaveBean(
                    com.alibaba.compileflow.durable.runtime.worker.DurableWorkerCoordinator.class);
            assertThat(context)
                .doesNotHaveBean(
                        com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime.DurableWorkerLifecycle.class);
            assertThat(context).doesNotHaveBean(DurableTurnWorker.class);
            assertThat(context).doesNotHaveBean(DurableEffectWorker.class);
            assertThat(context).doesNotHaveBean(DurableProcessRuntimeLoadWorker.class);
            assertThat(context).doesNotHaveBean(DurableRetentionWorker.class);
        });
        runner()
            .withPropertyValues("compileflow.durable.retention.terminal-run=30d")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(DurableRetentionWorker.class);
            });
        runner()
            .withPropertyValues("compileflow.durable.worker.enabled=true",
                    "compileflow.durable.retention.unused-process=90d")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(DurableRetentionWorker.class);
                assertThat(context.getBean(DurableProcessEngineConfig.class).getRetention().enabled()).isTrue();
                assertThat(context.getBean(DefaultDurableProcessEngine.class).getWorkerCoordinator()).isNotNull();
            });
        runner()
            .withPropertyValues("compileflow.durable.worker.enabled=true",
                    "compileflow.durable.retention.consumed-occurrence=7d")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(DurableRetentionWorker.class);
                assertThat(context.getBean(DurableProcessEngineConfig.class).getRetention().enabled()).isTrue();
                assertThat(context.getBean(DefaultDurableProcessEngine.class).getWorkerCoordinator()).isNotNull();
            });
    }

    @Test
    void composesDeployThroughTheTwoNarrowAdmissionAdapters() {
        runner()
            .withBean(ProcessDeploymentService.class, () -> mock(ProcessDeploymentService.class))
            .withBean(com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource.class, () -> version -> Optional.empty())
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(DurableAliasStateSource.class)).isInstanceOf(
                        DeployAliasStateSourceAdapter.class);
                assertThat(context.getBean(DurableVersionDefinitionSource.class))
                    .isInstanceOf(DeployDurableVersionDefinitionSourceAdapter.class);
            });
    }
}
