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
import com.alibaba.compileflow.durable.api.DurableProcessEngine;
import com.alibaba.compileflow.durable.runtime.program.DurableInterpretedProgramCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableProgramCompiler;
import com.alibaba.compileflow.durable.runtime.worker.DurableRetentionWorker;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasStateSource;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CompileFlowDurableOptionalObservabilityAutoConfigurationTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowDurableAutoConfiguration.class))
            .withPropertyValues("compileflow.durable.enabled=true", "compileflow.durable.worker.enabled=false")
            .withBean(ProcessEngineConfig.class, ProcessEngineConfig::tbbpm)
            .withBean(DurableStore.class, () -> mock(DurableStore.class));
    }

    @Test
    void doesNotComposeWithoutStore() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowDurableAutoConfiguration.class))
            .withPropertyValues("compileflow.durable.enabled=true")
            .withBean(ProcessEngineConfig.class, ProcessEngineConfig::tbbpm)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(DurableStore.class);
                assertThat(context).doesNotHaveBean(DurableProcessEngine.class);
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
            assertThat(context).hasSingleBean(DurableProgramCompiler.class);
            assertThat(context.getBean(DurableProgramCompiler.class)).isInstanceOf(DurableJavaProgramCompiler.class);
        });
        runner()
            .withPropertyValues("compileflow.durable.runtime-mode=interpreted")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(DurableProgramCompiler.class);
                assertThat(context.getBean(DurableProgramCompiler.class)).isInstanceOf(
                        DurableInterpretedProgramCompiler.class);
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
            assertThat(context).doesNotHaveBean(DurableRetentionWorker.class);
        });
        runner()
            .withPropertyValues("compileflow.durable.retention.terminal-run=30d")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(DurableRetentionWorker.class);
            });
        runner()
            .withPropertyValues("compileflow.durable.retention.unused-process=90d")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(DurableRetentionWorker.class);
            });
        runner()
            .withPropertyValues("compileflow.durable.retention.consumed-occurrence=7d")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(DurableRetentionWorker.class);
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
