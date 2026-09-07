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
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.control.observability.DeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import com.alibaba.compileflow.deploy.control.DefaultProcessDeploymentService;
import com.alibaba.compileflow.deploy.control.RolloutService;
import com.alibaba.compileflow.deploy.control.RoutingStatePropagator;
import com.alibaba.compileflow.deploy.control.VersionPublicationService;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.control.projection.ProcessArtifactProjector;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxAdminService;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxScheduler;
import com.alibaba.compileflow.deploy.control.projection.DeploymentProjectionReconciler;
import com.alibaba.compileflow.deploy.spi.store.DeployStore;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasStore;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionStore;
import com.alibaba.compileflow.deploy.spi.store.RolloutStore;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxStore;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidator;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidation;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentArtifactProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.ReconciliationProperties;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the deployment control plane: publication, rollout, and reconciliation.
 *
 * @author yusu
 */
@AutoConfiguration(after = {CompileFlowEngineAutoConfiguration.class,
        CompileFlowDeployOutboxAutoConfiguration.class, CompileFlowDeployRoutingAutoConfiguration.class}, afterName = {"c"
        + "om.alibaba.compileflow.deploy.spring.boot.autoconfigure.postgres."
        + "CompileFlowDeployPostgresAutoConfiguration",
        "com.alibaba.compileflow.deploy.spring.boot.autoconfigure.mysql.CompileFlowDeployMySqlAutoConfiguration"})
@ConditionalOnClass(DefaultProcessDeploymentService.class)
@ConditionalOnBean(DeployStore.class)
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "control-plane-enabled", havingValue = "true", matchIfMissing = true)
public class CompileFlowDeployControlPlaneAutoConfiguration {
    @Bean
    @ConditionalOnBean(DeploymentProjectionStore.class)
    @ConditionalOnProperty(prefix = "compileflow.deploy.artifact", name = "mode", havingValue = "PROJECTION_STORE")
    @ConditionalOnMissingBean(ProcessArtifactProjector.class)
    public ProcessArtifactProjector processArtifactProjector(DeploymentProjectionStore projectionStore,
            CompileFlowDeploymentProperties properties) {
        DeploymentArtifactProperties artifact = properties.getArtifact();
        return new ProcessArtifactProjector(projectionStore, artifact.getKeyPrefix(), artifact.getOperationTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(RolloutService.class)
    public RolloutService rolloutService(RolloutStore rolloutRepository, ProcessVersionStore versionRepository,
            ArtifactProjectionCoordinator artifactProjectionCoordinator, DeploymentOperationMetrics operationMetrics) {
        return new RolloutService(rolloutRepository, versionRepository, artifactProjectionCoordinator, operationMetrics);
    }

    @Bean
    @ConditionalOnMissingBean(RoutingOutboxAdminService.class)
    public RoutingOutboxAdminService routingOutboxAdminService(RoutingOutboxStore outboxRepository,
            RoutingOutboxScheduler scheduler) {
        return new RoutingOutboxAdminService(outboxRepository, scheduler);
    }

    @Bean
    @ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
    @ConditionalOnMissingBean(RoutingStatePropagator.class)
    public RoutingStatePropagator distributedRoutingStatePropagator(RoutingOutboxScheduler scheduler) {
        return ignored -> scheduler.requestDispatch();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessPublicationValidator.class)
    public ProcessPublicationValidator processPublicationValidator(ProcessEngine processEngine) {
        return (ref, definition) -> validateCalls(ref, definition, processEngine,
                processEngine.tooling().preflight(definition, ProcessPreflightOptions.fast()));
    }

    private static ProcessPublicationValidation validateCalls(ProcessRef.Version ref,
            ProcessDefinition.Inline definition, ProcessEngine engine, ProcessPreflightReport report) {
        if (report.getOverallStatus() != ProcessPreflightReport.OverallStatus.PASS) {
            return new ProcessPublicationValidation(report, List.of());
        }
        List<ProcessCallBinding> bindings =
                ProcessCallInspector
            .require(engine)
            .inspectProcessCalls(definition)
            .stream()
            .map(call -> {
                if (!(call.target() instanceof ProcessCallTarget.Version version)) {
                    throw new IllegalArgumentException(
                            "Published Process call '" + call.callSiteId() + "' must declare an exact called-Process version");
                }
                return new ProcessCallBinding(call.callSiteId(),
                        ProcessRef.version(ref.namespace(), call.code(), version.version()));
            })
            .toList();
        return new ProcessPublicationValidation(report, bindings);
    }

    @Bean
    @ConditionalOnMissingBean(VersionPublicationService.class)
    public VersionPublicationService versionPublicationService(ProcessEngineConfig engineConfig,
            ProcessVersionStore versionRepository, ProcessPublicationValidator publicationValidator) {
        return new VersionPublicationService(versionRepository, publicationValidator,
                engineConfig.getDefinitionConfig().getMaxBytes());
    }

    @Bean
    @ConditionalOnMissingBean(ArtifactProjectionCoordinator.class)
    public ArtifactProjectionCoordinator artifactProjectionCoordinator(
            ObjectProvider<ProcessArtifactProjector> artifactProjectorProvider,
            CompileFlowDeploymentProperties properties) {
        DeploymentArtifactProperties artifact = properties.getArtifact();
        switch (artifact.getMode()) {
            case SOURCE:
                return ArtifactProjectionCoordinator.source();
            case PROJECTION_STORE:
                return ArtifactProjectionCoordinator.projectionStore(artifactProjectorProvider.getIfAvailable());
            default:
                throw new IllegalStateException("Unsupported deployment artifact mode: " + artifact.getMode());
        }
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDeploymentService.class)
    public ProcessDeploymentService processDeploymentService(VersionPublicationService publicationService,
            ArtifactProjectionCoordinator artifactProjectionCoordinator, DeploymentOperationMetrics operationMetrics,
            RolloutService rolloutService, ProcessVersionStore versionRepository, ProcessAliasStore aliasRepository,
            RoutingStatePropagator routingStatePropagator) {
        return new DefaultProcessDeploymentService(publicationService, artifactProjectionCoordinator, operationMetrics,
                rolloutService, versionRepository, aliasRepository, routingStatePropagator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
    @ConditionalOnMissingBean(DeploymentProjectionReconciler.class)
    public DeploymentProjectionReconciler deploymentProjectionReconciler(ProcessAliasStore aliasRepository,
            ProcessVersionStore versionRepository, ArtifactProjectionCoordinator artifactProjectionCoordinator,
            DeploymentProjectionStore projectionStore, RoutingOutboxStore outboxRepository,
            CompileFlowDeploymentProperties properties) {
        var routing = properties.getRouting();
        var reconciliation = properties.getReconciliation();
        return new DeploymentProjectionReconciler(aliasRepository, versionRepository, artifactProjectionCoordinator,
                projectionStore, outboxRepository, routing.getKeyPrefix(),
                reconciliation.getMode() == ReconciliationProperties.Mode.REPAIR, routing.getOperationTimeout());
    }

    @Bean(name = "scheduledDeploymentProjectionReconciler", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
    @ConditionalOnMissingBean(DeploymentProjectionReconciler.ScheduledReconciler.class)
    public DeploymentProjectionReconciler.ScheduledReconciler scheduledDeploymentProjectionReconciler(
            DeploymentProjectionReconciler task, CompileFlowDeploymentProperties properties) {
        return new DeploymentProjectionReconciler.ScheduledReconciler(task, properties
                    .getReconciliation()
                    .getInterval());
    }

    @Bean(name = "deploymentProjectionReconcilerLifecycle")
    @ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
    @ConditionalOnMissingBean(name = "deploymentProjectionReconcilerLifecycle")
    public SmartLifecycle deploymentProjectionReconcilerLifecycle(
            DeploymentProjectionReconciler.ScheduledReconciler reconciler, CompileFlowDeploymentProperties properties) {
        return new ActionBackedLifecycle(() -> {
            if (properties.getReconciliation().getMode() != ReconciliationProperties.Mode.DISABLED) {
                reconciler.start();
            }
        }, reconciler::stop, CompileFlowLifecyclePhases.DEPLOY_RECONCILIATION);
    }
}
