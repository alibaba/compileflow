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

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEnginePropertiesAutoConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import com.alibaba.compileflow.deploy.testkit.InMemoryDeploymentProjectionStore;
import com.alibaba.compileflow.deploy.control.DefaultProcessDeploymentService;
import com.alibaba.compileflow.deploy.control.RolloutService;
import com.alibaba.compileflow.deploy.control.VersionPublicationService;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.control.projection.ProcessArtifactProjector;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxAdminService;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxDispatcher;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxScheduler;
import com.alibaba.compileflow.deploy.control.outbox.RoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.control.projection.DeploymentProjectionReconciler;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionStore;
import com.alibaba.compileflow.deploy.spi.store.RolloutStore;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxStore;
import com.alibaba.compileflow.deploy.spi.store.DeployStore;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidator;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntime;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics;
import com.alibaba.compileflow.deploy.runtime.version.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.routing.AliasVersionDemand;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentRuntimeProperties;
import com.alibaba.compileflow.deploy.runtime.routing.DesiredRoutingStateSubscriber;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing.LocalReadyRoutingStateDeliveryTarget;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CompileFlowDeploySubAutoConfigurationsTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                CompileFlowDeployPropertiesAutoConfiguration.class, CompileFlowDeploymentMetricsAutoConfiguration.class,
                CompileFlowDeployStoreAutoConfiguration.class, CompileFlowDeployControlPlaneAutoConfiguration.class,
                CompileFlowDeployOutboxAutoConfiguration.class, CompileFlowDeployRuntimeAutoConfiguration.class,
                CompileFlowEmbeddedDeploymentRuntimeAutoConfiguration.class,
                CompileFlowDeployRoutingAutoConfiguration.class))
        .withPropertyValues("compileflow.deploy.outbox.retention=0ms")
        .withBean(ProcessEngineConfig.class, ProcessEngineConfig::defaults)
        .withBean(ProcessArtifactRuntimeLoader.class, () -> mock(ProcessArtifactRuntimeLoader.class));

    @Test
    void registersPipelineHealthAfterControlPlaneAutoConfiguration() {
        contextRunner
            .withConfiguration(AutoConfigurations.of(CompileFlowDeployActuatorAutoConfiguration.class))
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=SOURCE", "compileflow.deploy.runtime-worker-enabled=false")
            .withUserConfiguration(TestEngineConfiguration.class, JdbcControlPlaneConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(RoutingOutboxAdminService.class);
                assertThat(context).hasBean("deploymentPipelineHealthIndicator");
            });
    }

    @Test
    void shouldFailControlPlaneStartupWithoutAuthoritativeRepositories() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=SOURCE", "compileflow.deploy.runtime-worker-enabled=false",
                    "spring.main.allow-bean-definition-overriding=true")
            .withUserConfiguration(TestEngineConfiguration.class, ControlPlaneWithoutRepositoriesConfig.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailControlPlaneStartupWithoutEngineServices() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=SOURCE", "compileflow.deploy.runtime-worker-enabled=false")
            .withUserConfiguration(ControlPlaneWithoutEngineServicesConfig.class)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(ProcessEngine.class.getName());
            });
    }

    @Test
    void shouldCreateJdbcOutboxInfrastructureWhenDataSourceAndProjectionStoreExist() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=SOURCE", "compileflow.deploy.runtime-worker-enabled=false",
                    "spring.main.allow-bean-definition-overriding=true")
            .withUserConfiguration(TestEngineConfiguration.class, JdbcControlPlaneConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(RoutingOutboxStore.class);
                assertThat(context).hasSingleBean(RoutingOutboxAdminService.class);
                assertThat(context).hasSingleBean(RolloutStore.class);
                assertThat(context).hasSingleBean(RoutingOutboxDispatcher.class);
                assertThat(context).hasSingleBean(ProcessPublicationValidator.class);
                assertThat(context).hasSingleBean(VersionPublicationService.class);
                assertThat(context).hasSingleBean(RolloutService.class);
                assertThat(context).hasSingleBean(ProcessDeploymentService.class);
                assertThat(context).hasSingleBean(DefaultProcessDeploymentService.class);
                assertThat(context).hasBean("routingOutboxSchedulerLifecycle");
                assertThat(context).hasBean("deploymentProjectionReconcilerLifecycle");
                assertThat(context
                    .getBean("routingOutboxSchedulerLifecycle", org.springframework.context.SmartLifecycle.class)
                    .isRunning())
                    .isTrue();
                assertThat(context
                    .getBean("deploymentProjectionReconcilerLifecycle", org.springframework.context.SmartLifecycle.class)
                    .isRunning())
                    .isTrue();
            });
    }

    @Test
    void channelModeWiresArtifactProjectionIntoTheSharedReconciler() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=PROJECTION_STORE",
                    "compileflow.deploy.runtime-worker-enabled=false",
                    "spring.main.allow-bean-definition-overriding=true")
            .withUserConfiguration(TestEngineConfiguration.class, JdbcControlPlaneConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ProcessArtifactProjector.class);
                assertThat(context).hasSingleBean(DeploymentProjectionReconciler.class);
                assertThat(context.getBean(ArtifactProjectionCoordinator.class).isProjectionRequired()).isTrue();
            });
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicationValidatorUsesStructuralPreflightWithoutCompilation() {
        ProcessEngine processEngine =
                mock(ProcessEngine.class, withSettings().extraInterfaces(ProcessCallInspector.class));
        ProcessToolingService toolingService = mock(ProcessToolingService.class);
        when(processEngine.tooling()).thenReturn(toolingService);
        when(((ProcessCallInspector) processEngine).inspectProcessCalls(any())).thenReturn(List.of());
        ProcessDefinition.Inline definition =
                ProcessDefinition.inline(ProcessModelType.TBBPM, "order.process", "<definitions/>");
        ProcessPreflightReport expected = ProcessPreflightReport
            .builder()
            .code(definition.code())
            .addItem(ProcessPreflightReport.ItemType.LINT, ProcessPreflightReport.ItemStatus.PASS, 0L, "lint ok")
            .build();
        AtomicReference<ProcessPreflightOptions> observedOptions = new AtomicReference<>();
        AtomicReference<ProcessDefinition> observedDefinition = new AtomicReference<>();
        when(toolingService.preflight(any(ProcessDefinition.class), any(ProcessPreflightOptions.class)))
            .thenAnswer(invocation -> {
                observedDefinition.set(invocation.getArgument(0));
                observedOptions.set(invocation.getArgument(1));
                return expected;
            });

        ProcessPublicationValidator validator =
                new CompileFlowDeployControlPlaneAutoConfiguration().processPublicationValidator(processEngine);

        assertThat(validator.validate(ProcessRef.version("default", definition.code(), "v1"), definition).report())
            .isSameAs(expected);
        assertThat(observedOptions.get().isLintEnabled()).isTrue();
        assertThat(observedOptions.get().isCompileEnabled()).isFalse();
        assertThat(observedDefinition.get()).isSameAs(definition);
    }

    @Test
    void shouldCreateEmbeddedOutboxInfrastructureWithoutSyncProjectionStore() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=EMBEDDED",
                    "compileflow.deploy.artifact.mode=SOURCE", "compileflow.deploy.runtime-worker-enabled=false",
                    "compileflow.deploy.runtime.installation-concurrency=11")
            .withUserConfiguration(TestEngineConfiguration.class, JdbcEmbeddedControlPlaneConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(LocalReadyRoutingStateDeliveryTarget.class);
                assertThat(context).hasSingleBean(VersionRuntimeManager.class);
                assertThat(context).hasSingleBean(RoutingOutboxDispatcher.class);
                assertThat(context).hasSingleBean(RoutingOutboxAdminService.class);
                assertThat(context).doesNotHaveBean(DeploymentProjectionStore.class);
                assertThat(context)
                    .doesNotHaveBean(
                            com.alibaba.compileflow.deploy.control.projection.DeploymentProjectionReconciler.class);
                ProcessEngineConfig engineConfig = context.getBean(ProcessEngineConfig.class);
                assertThat(context.getBean(VersionRuntimeManager.class).snapshot().getInflightCapacity())
                    .isEqualTo(
                                    engineConfig.getExecutorConfig().getRuntimeLoadMaxConcurrency()
                            + engineConfig.getExecutorConfig().getRuntimeLoadMaxPending());
            });
    }

    @ParameterizedTest
    @CsvSource({"2, 3, 5", "2147483647, 0, 2147483647", "2147483646, 1, 2147483647", "2147483647, 1, 2147483647",
            "1, 2147483647, 2147483647", "2147483647, 2147483647, 2147483647"})
    void embeddedAdmissionSaturatesAtTheSemaphoreRepresentationLimit(int concurrency, int pending, int expected) {
        ProcessEngineConfig engineConfig = ProcessEngineConfig
            .builder()
            .executors(ProcessExecutorConfig
                .builder()
                .runtimeLoadMaxConcurrency(concurrency)
                .runtimeLoadMaxPending(pending)
                .build())
            .build();
        CompileFlowDeploymentProperties properties = mock(CompileFlowDeploymentProperties.class);
        when(properties.getRuntime())
            .thenReturn(new DeploymentRuntimeProperties(Duration.ofMinutes(5), Duration.ofSeconds(30), 1));
        try (VersionRuntimeManager manager = new CompileFlowEmbeddedDeploymentRuntimeAutoConfiguration()
            .embeddedVersionRuntimeManager(mock(ProcessArtifactResolver.class), mock(ProcessArtifactRuntimeLoader.class),
                    new LocalRoutingState(), engineConfig, properties, new DeploymentRuntimeMetrics())) {
            assertThat(manager.snapshot().getInflightCapacity()).isEqualTo(expected);
        }
    }

    @Test
    void shouldFailDistributedControlPlaneWithoutSyncProjectionStore() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=SOURCE", "compileflow.deploy.runtime-worker-enabled=false")
            .withUserConfiguration(TestEngineConfiguration.class, JdbcEmbeddedControlPlaneConfig.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldCreateDataPlaneWithCustomSyncTransport() {
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime-worker-enabled=true",
                    "compileflow.deploy.control-plane-enabled=false", "compileflow.deploy.enabled=true",
                    "compileflow.deploy.topology=DISTRIBUTED", "compileflow.deploy.artifact.mode=SOURCE",
                    "compileflow.deploy.routing.codes[0]=order.flow",
                    "compileflow.deploy.runtime.installation-concurrency=3")
            .withUserConfiguration(RuntimeConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ProcessArtifactResolver.class);
                assertThat(context).hasSingleBean(DesiredRoutingStateSubscriber.class);
                assertThat(context).hasSingleBean(VersionRuntimeManager.class);
                assertThat(context).hasSingleBean(DeploymentRuntime.class);
                assertThat(context).doesNotHaveBean(ProcessDeploymentService.class);
                assertThat(context).doesNotHaveBean(RoutingOutboxDispatcher.class);
                assertThat(context.getBean(VersionRuntimeManager.class).snapshot().getInflightCapacity()).isEqualTo(3);
            });
    }

    @Test
    void installationCompletionHandsOffPendingDemandWithoutExecutorRejection() throws Exception {
        CompileFlowDeploymentProperties properties = mock(CompileFlowDeploymentProperties.class);
        when(properties.getRuntime())
            .thenReturn(new DeploymentRuntimeProperties(Duration.ofMinutes(5), Duration.ofSeconds(30), 1));
        ExecutorService executor = new CompileFlowDeployRuntimeAutoConfiguration().deployRuntimeExecutor(properties);
        ProcessArtifactResolver resolver = mock(ProcessArtifactResolver.class);
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(resolver.resolve(any(ProcessRef.Version.class))).thenAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            ProcessRef.Version ref = invocation.getArgument(0);
            ProcessDefinition.Inline definition =
                    ProcessDefinition.inline(ProcessModelType.TBBPM, ref.code(), "<flow/>");
            return new ProcessArtifact(ref, definition, ProcessArtifactDigest.compute(definition, Map.of()));
        });
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        VersionRuntimeManager manager = VersionRuntimeManager.withOwnedRetryScheduler(resolver, loader, executor,
                new LocalRoutingState(), Duration.ofMinutes(5), 1);
        try {
            List<CompletableFuture<Void>> installed = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                ProcessRef.Version ref = ProcessRef.version("default", "handoff", "v" + i);
                installed.add(manager.ensureInstalled(AliasVersionDemand.forAlias("default", "handoff", "alias" + i,
                        Set.of(ref))));
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(manager.snapshot().getInflightCount()).isEqualTo(1);
            assertThat(installed).allMatch(future -> !future.isDone());
            release.countDown();
            CompletableFuture.allOf(installed.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
            assertThat(manager.snapshot().getRetainedRuntimeCount()).isEqualTo(32);
        } finally {
            release.countDown();
            manager.close();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldCreateDatabaseBackedDataPlaneWithoutControlPlaneInfrastructure() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.control-plane-enabled=false", "compileflow.deploy.runtime-worker-enabled=true",
                    "compileflow.deploy.artifact.mode=SOURCE", "compileflow.deploy.routing.codes[0]=order.flow")
            .withUserConfiguration(DatabaseRuntimeConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ProcessArtifactResolver.class);
                assertThat(context).hasSingleBean(DeploymentRuntime.class);
                assertThat(context).doesNotHaveBean(ProcessDeploymentService.class);
                assertThat(context).doesNotHaveBean(RoutingOutboxStore.class);
                assertThat(context).doesNotHaveBean(RoutingOutboxDispatcher.class);
                assertThat(context).doesNotHaveBean(RolloutStore.class);
            });
    }

    @Test
    void shouldFailWhenEnabledRuntimeHasNoRoutingSubscriptions() {
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime-worker-enabled=true", "compileflow.deploy.enabled=true",
                    "compileflow.deploy.topology=DISTRIBUTED", "compileflow.deploy.artifact.mode=SOURCE")
            .withUserConfiguration(RuntimeConfig.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldApplyCanonicalAliasRoutingDuringAdmission() {
        ApplicationContextRunner routingRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowDeployPropertiesAutoConfiguration.class, CompileFlowDeployRoutingAutoConfiguration.class))
            .withBean(ProcessEngineConfig.class, ProcessEngineConfig::defaults);

        routingRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.control-plane-enabled=true", "compileflow.deploy.runtime-worker-enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                LocalRoutingState localRoutingState = context.getBean(LocalRoutingState.class);
                ProcessRef.Alias alias = ProcessRef.alias("default", "order.process", "production");
                localRoutingState.applyAliasRoute(ProcessAliasRoute.canary(alias, "v1", "v2", 5_000, 7L));

                AliasSelection selection = context
                    .getBean(AliasAdmission.class)
                    .admit(ProcessRef.alias("default", "order.process", "production"), new AliasRoutingOptions("user-1"));

                assertThat(selection.version().version()).isEqualTo("v2");
                assertThat(selection.target()).isEqualTo(ProcessAliasTarget.CANDIDATE);
                assertThat(selection.aliasRevision()).isEqualTo(7L);
            });
    }

    @Test
    void shouldUseTheSingleApplicationAliasRouteSource() {
        ProcessAliasRouteSource routeSource = alias -> Optional.of(ProcessAliasRoute.stable(alias, "v9", 9L));
        ApplicationContextRunner routingRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowDeployPropertiesAutoConfiguration.class, CompileFlowDeployRoutingAutoConfiguration.class))
            .withBean(ProcessEngineConfig.class, ProcessEngineConfig::defaults)
            .withBean(ProcessAliasRouteSource.class, () -> routeSource);

        routingRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.control-plane-enabled=true", "compileflow.deploy.runtime-worker-enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ProcessAliasRouteSource.class);

                ProcessRef.Alias alias = ProcessRef.alias("default", "order.process", "production");
                AliasSelection selection =
                        context.getBean(AliasAdmission.class).admit(alias, AliasRoutingOptions.defaults());

                assertThat(context.getBean(ProcessAliasRouteSource.class)).isSameAs(routeSource);
                assertThat(selection.version().version()).isEqualTo("v9");
                assertThat(selection.aliasRevision()).isEqualTo(9L);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestEngineConfiguration {
        @Bean
        ProcessEngine processEngine() {
            ProcessEngine engine = mock(ProcessEngine.class, withSettings().extraInterfaces(ProcessCallInspector.class));
            when(engine.tooling()).thenReturn(mock(ProcessToolingService.class));
            return engine;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ControlPlaneWithoutRepositoriesConfig {
        @Bean
        RoutingStateDeliveryTarget routingStateDeliveryTarget() {
            return mock(RoutingStateDeliveryTarget.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ControlPlaneWithoutEngineServicesConfig {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        DeployStore deployStore() {
            DeployStore store = mock(DeployStore.class);
            when(store.listDistinctProcesses()).thenReturn(List.of());
            return store;
        }

        @Bean
        RoutingOutboxScheduler routingOutboxScheduler() {
            return mock(RoutingOutboxScheduler.class);
        }

        @Bean
        DeploymentProjectionStore deploymentProjectionStore() {
            return mock(DeploymentProjectionStore.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcEmbeddedControlPlaneConfig {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        DeployStore deployStore() {
            return mock(DeployStore.class);
        }

        @Bean
        RoutingOutboxScheduler routingOutboxScheduler() {
            return mock(RoutingOutboxScheduler.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcControlPlaneConfig {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        DeployStore deployStore() {
            DeployStore store = mock(DeployStore.class);
            when(store.listDistinctProcesses()).thenReturn(List.of());
            return store;
        }

        @Bean
        RoutingOutboxScheduler routingOutboxScheduler() {
            return mock(RoutingOutboxScheduler.class);
        }

        @Bean
        DeploymentProjectionStore deploymentProjectionStore() {
            return mock(DeploymentProjectionStore.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeConfig {
        @Bean
        ProcessArtifactSource flowArtifactSource() {
            return mock(ProcessArtifactSource.class);
        }

        @Bean
        DeploymentProjectionStore deploymentProjectionStore() {
            return new InMemoryDeploymentProjectionStore();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DatabaseRuntimeConfig {
        @Bean
        ProcessRuntimeManager processRuntimeManager() {
            return mock(ProcessRuntimeManager.class);
        }

        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        ProcessVersionStore versionRepository() {
            return mock(ProcessVersionStore.class);
        }

        @Bean
        DeploymentProjectionStore deploymentProjectionStore() {
            return new InMemoryDeploymentProjectionStore();
        }
    }
}
