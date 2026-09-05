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

import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowEnginePropertiesAutoConfiguration;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineRegistry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import com.alibaba.compileflow.deploy.api.sync.inmemory.InMemoryDeploymentSyncChannel;
import com.alibaba.compileflow.deploy.control.DefaultProcessDeploymentService;
import com.alibaba.compileflow.deploy.control.RolloutControlService;
import com.alibaba.compileflow.deploy.control.VersionPublicationService;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.control.projection.ProcessArtifactProjector;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxAdminService;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxDispatcher;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxScheduler;
import com.alibaba.compileflow.deploy.control.routing.RoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.control.projection.RoutingProjectionReconciler;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.RolloutRepository;
import com.alibaba.compileflow.deploy.control.repository.RoutingOutboxRepository;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidator;
import com.alibaba.compileflow.deploy.runtime.DeployRuntime;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.state.RoutingStateSubscriber;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing.LocalReadyRoutingStateDeliveryTarget;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CompileFlowDeploySubAutoConfigurationsTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                CompileFlowDeployPropertiesAutoConfiguration.class, CompileFlowDeploymentMetricsAutoConfiguration.class,
                CompileFlowDeployControlPlaneAutoConfiguration.class, CompileFlowDeployOutboxAutoConfiguration.class,
                CompileFlowDeployDataPlaneAutoConfiguration.class, CompileFlowEmbeddedDataPlaneAutoConfiguration.class,
                CompileFlowDeployRoutingAutoConfiguration.class))
        .withPropertyValues("spring.autoconfigure.exclude=" + CompileFlowRepositoryAutoConfiguration.class.getName(),
                "compileflow.deploy.outbox.retention=0ms")
        .withBean(ProcessEngineConfig.class, ProcessEngineConfig::tbbpm)
        .withBean(ProcessArtifactRuntimeLoader.class, () -> mock(ProcessArtifactRuntimeLoader.class));

    @Test
    void shouldFailControlPlaneStartupWithoutAuthoritativeRepositories() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=DATABASE", "compileflow.deploy.runtime-worker-enabled=false",
                    "spring.main.allow-bean-definition-overriding=true")
            .withUserConfiguration(TestEngineConfiguration.class, ControlPlaneWithoutRepositoriesConfig.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailControlPlaneStartupWithoutEngineServices() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=DATABASE", "compileflow.deploy.runtime-worker-enabled=false")
            .withUserConfiguration(ControlPlaneWithoutEngineServicesConfig.class)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(ProcessEngine.class.getName());
            });
    }

    @Test
    void shouldCreateJdbcOutboxInfrastructureWhenDataSourceAndChannelExist() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=DATABASE", "compileflow.deploy.runtime-worker-enabled=false",
                    "spring.main.allow-bean-definition-overriding=true")
            .withUserConfiguration(TestEngineConfiguration.class, JdbcControlPlaneConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(RoutingOutboxRepository.class);
                assertThat(context).hasSingleBean(RoutingOutboxAdminService.class);
                assertThat(context).hasSingleBean(RolloutRepository.class);
                assertThat(context).hasSingleBean(RoutingOutboxDispatcher.class);
                assertThat(context).hasSingleBean(ProcessPublicationValidator.class);
                assertThat(context).hasSingleBean(VersionPublicationService.class);
                assertThat(context).hasSingleBean(RolloutControlService.class);
                assertThat(context).hasSingleBean(ProcessDeploymentService.class);
                assertThat(context).hasSingleBean(DefaultProcessDeploymentService.class);
                assertThat(context).hasBean("routingOutboxSchedulerLifecycle");
                assertThat(context).hasBean("routingProjectionReconcilerLifecycle");
                assertThat(context
                    .getBean("routingOutboxSchedulerLifecycle", org.springframework.context.SmartLifecycle.class)
                    .isRunning())
                    .isTrue();
                assertThat(context
                    .getBean("routingProjectionReconcilerLifecycle", org.springframework.context.SmartLifecycle.class)
                    .isRunning())
                    .isTrue();
            });
    }

    @Test
    void channelModeWiresArtifactProjectionIntoTheSharedReconciler() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=CHANNEL", "compileflow.deploy.runtime-worker-enabled=false",
                    "spring.main.allow-bean-definition-overriding=true")
            .withUserConfiguration(TestEngineConfiguration.class, JdbcControlPlaneConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ProcessArtifactProjector.class);
                assertThat(context).hasSingleBean(RoutingProjectionReconciler.class);
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
        ObjectProvider<ProcessEngineRegistry> registryProvider = mock(ObjectProvider.class);
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order.process", "<definitions/>");
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

        ProcessPublicationValidator validator = new CompileFlowDeployControlPlaneAutoConfiguration()
            .processPublicationValidator(processEngine, ProcessEngineConfig.tbbpm(), registryProvider);

        assertThat(validator
            .validate(ProcessRef.version("default", definition.code(), "v1"), ProcessModelType.TBBPM, definition)
            .report())
            .isSameAs(expected);
        assertThat(observedOptions.get().isLintEnabled()).isTrue();
        assertThat(observedOptions.get().isCompileEnabled()).isFalse();
        assertThat(observedDefinition.get()).isSameAs(definition);
    }

    @Test
    void shouldCreateEmbeddedOutboxInfrastructureWithoutSyncChannel() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=EMBEDDED",
                    "compileflow.deploy.artifact.mode=DATABASE", "compileflow.deploy.runtime-worker-enabled=false",
                    "compileflow.deploy.runtime.concurrency=11", "compileflow.deploy.runtime.queue-capacity=99")
            .withUserConfiguration(TestEngineConfiguration.class, JdbcEmbeddedControlPlaneConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(LocalReadyRoutingStateDeliveryTarget.class);
                assertThat(context).hasSingleBean(RuntimeInstaller.class);
                assertThat(context).hasSingleBean(RoutingOutboxDispatcher.class);
                assertThat(context).hasSingleBean(RoutingOutboxAdminService.class);
                assertThat(context).doesNotHaveBean(DeploymentSyncChannel.class);
                assertThat(context)
                    .doesNotHaveBean(
                            com.alibaba.compileflow.deploy.control.projection.RoutingProjectionReconciler.class);
                ProcessEngineConfig engineConfig = context.getBean(ProcessEngineConfig.class);
                assertThat(context.getBean(RuntimeInstaller.class).snapshot().getInflightCapacity())
                    .isEqualTo(
                                    engineConfig.getExecutorConfig().getRuntimeLoadMaxConcurrency()
                            + engineConfig.getExecutorConfig().getRuntimeLoadMaxPending());
            });
    }

    @Test
    void shouldFailDistributedControlPlaneWithoutSyncChannel() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.artifact.mode=DATABASE", "compileflow.deploy.runtime-worker-enabled=false")
            .withUserConfiguration(TestEngineConfiguration.class, JdbcEmbeddedControlPlaneConfig.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldCreateDataPlaneWithCustomSyncTransport() {
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime-worker-enabled=true",
                    "compileflow.deploy.control-plane-enabled=false", "compileflow.deploy.enabled=true",
                    "compileflow.deploy.topology=DISTRIBUTED", "compileflow.deploy.artifact.mode=DATABASE",
                    "compileflow.deploy.routing.codes[0]=order.flow", "compileflow.deploy.runtime.concurrency=3",
                    "compileflow.deploy.runtime.queue-capacity=7")
            .withUserConfiguration(RuntimeConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ProcessArtifactResolver.class);
                assertThat(context).hasSingleBean(RoutingStateSubscriber.class);
                assertThat(context).hasSingleBean(RuntimeInstaller.class);
                assertThat(context).hasSingleBean(DeployRuntime.class);
                assertThat(context).doesNotHaveBean(ProcessDeploymentService.class);
                assertThat(context).doesNotHaveBean(RoutingOutboxDispatcher.class);
                assertThat(context.getBean(RuntimeInstaller.class).snapshot().getInflightCapacity()).isEqualTo(10);
            });
    }

    @Test
    void shouldCreateDatabaseBackedDataPlaneWithoutControlPlaneInfrastructure() {
        contextRunner
            .withPropertyValues("compileflow.deploy.enabled=true", "compileflow.deploy.topology=DISTRIBUTED",
                    "compileflow.deploy.control-plane-enabled=false", "compileflow.deploy.runtime-worker-enabled=true",
                    "compileflow.deploy.artifact.mode=DATABASE", "compileflow.deploy.routing.codes[0]=order.flow")
            .withUserConfiguration(DatabaseRuntimeConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ProcessArtifactResolver.class);
                assertThat(context).hasSingleBean(DeployRuntime.class);
                assertThat(context).doesNotHaveBean(ProcessDeploymentService.class);
                assertThat(context).doesNotHaveBean(RoutingOutboxRepository.class);
                assertThat(context).doesNotHaveBean(RoutingOutboxDispatcher.class);
                assertThat(context).doesNotHaveBean(RolloutRepository.class);
            });
    }

    @Test
    void shouldFailWhenEnabledRuntimeHasNoRoutingSubscriptions() {
        contextRunner
            .withPropertyValues("compileflow.deploy.runtime-worker-enabled=true", "compileflow.deploy.enabled=true",
                    "compileflow.deploy.topology=DISTRIBUTED", "compileflow.deploy.artifact.mode=DATABASE")
            .withUserConfiguration(RuntimeConfig.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldApplyCanonicalAliasRoutingDuringAdmission() {
        ApplicationContextRunner routingRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowEnginePropertiesAutoConfiguration.class,
                    CompileFlowDeployPropertiesAutoConfiguration.class, CompileFlowDeployRoutingAutoConfiguration.class))
            .withBean(ProcessEngineConfig.class, ProcessEngineConfig::tbbpm);

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
            .withBean(ProcessEngineConfig.class, ProcessEngineConfig::tbbpm)
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
        ProcessVersionRepository versionRepository() {
            return mock(ProcessVersionRepository.class);
        }

        @Bean
        ProcessAliasRepository processAliasRepository() {
            ProcessAliasRepository repository = mock(ProcessAliasRepository.class);
            when(repository.listDistinctProcesses()).thenReturn(List.of());
            return repository;
        }

        @Bean
        RoutingOutboxScheduler routingOutboxScheduler() {
            return mock(RoutingOutboxScheduler.class);
        }

        @Bean
        DeploymentSyncChannel deploymentSyncChannel() {
            return mock(DeploymentSyncChannel.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcEmbeddedControlPlaneConfig {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        ProcessVersionRepository versionRepository() {
            return mock(ProcessVersionRepository.class);
        }

        @Bean
        ProcessAliasRepository processAliasRepository() {
            return mock(ProcessAliasRepository.class);
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
        ProcessVersionRepository versionRepository() {
            return mock(ProcessVersionRepository.class);
        }

        @Bean
        ProcessAliasRepository processAliasRepository() {
            ProcessAliasRepository repository = mock(ProcessAliasRepository.class);
            when(repository.listDistinctProcesses()).thenReturn(List.of());
            return repository;
        }

        @Bean
        RoutingOutboxScheduler routingOutboxScheduler() {
            return mock(RoutingOutboxScheduler.class);
        }

        @Bean
        DeploymentSyncChannel deploymentSyncChannel() {
            return mock(DeploymentSyncChannel.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeConfig {
        @Bean
        ProcessArtifactSource flowArtifactSource() {
            return mock(ProcessArtifactSource.class);
        }

        @Bean
        DeploymentSyncChannel deploymentSyncChannel() {
            return new InMemoryDeploymentSyncChannel();
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
        ProcessVersionRepository versionRepository() {
            return mock(ProcessVersionRepository.class);
        }

        @Bean
        DeploymentSyncChannel deploymentSyncChannel() {
            return new InMemoryDeploymentSyncChannel();
        }
    }
}
