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
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineRegistry;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import com.alibaba.compileflow.deploy.control.DefaultProcessDeploymentService;
import com.alibaba.compileflow.deploy.control.RolloutControlService;
import com.alibaba.compileflow.deploy.control.RoutingActivation;
import com.alibaba.compileflow.deploy.control.VersionPublicationService;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.control.projection.ProcessArtifactProjector;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxAdminService;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxScheduler;
import com.alibaba.compileflow.deploy.control.projection.RoutingProjectionReconciler;
import com.alibaba.compileflow.deploy.control.repository.JdbcRolloutRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.RolloutRepository;
import com.alibaba.compileflow.deploy.control.repository.RoutingOutboxRepository;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidator;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidation;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentArtifactProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentRoutingProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.ReconciliationProperties;
import java.util.List;
import javax.sql.DataSource;
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
@AutoConfiguration(after = {CompileFlowCoreAutoConfiguration.class, CompileFlowRepositoryAutoConfiguration.class,
        CompileFlowDeployOutboxAutoConfiguration.class, CompileFlowDeployRoutingAutoConfiguration.class})
@ConditionalOnClass(DefaultProcessDeploymentService.class)
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "control-plane-enabled", havingValue = "true", matchIfMissing = true)
public class CompileFlowDeployControlPlaneAutoConfiguration {
    private static ProcessPreflightReport unsupportedModelTypeReport(ProcessModelType modelType,
            ProcessDefinition.Inline definition) {
        return ProcessPreflightReport
            .builder()
            .code(definition.code())
            .addItem(ProcessPreflightReport.ItemType.LINT, ProcessPreflightReport.ItemStatus.FAIL, 0L,
                    "No process engine is configured for model type " + modelType)
            .build();
    }

    @Bean
    @ConditionalOnBean(DeploymentSyncChannel.class)
    @ConditionalOnProperty(prefix = "compileflow.deploy.artifact", name = "mode", havingValue = "CHANNEL")
    @ConditionalOnMissingBean(ProcessArtifactProjector.class)
    public ProcessArtifactProjector processArtifactProjector(DeploymentSyncChannel channel,
            DeploymentArtifactProperties artifact) {
        return new ProcessArtifactProjector(channel, artifact.getKeyPrefix(), artifact.getOperationTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(RolloutRepository.class)
    public RolloutRepository rolloutRepository(DataSource dataSource, DeploymentRoutingProperties routing) {
        return new JdbcRolloutRepository(dataSource, routing.getKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean(RolloutControlService.class)
    public RolloutControlService rolloutControlService(RolloutRepository rolloutRepository,
            ProcessVersionRepository versionRepository, ArtifactProjectionCoordinator artifactProjectionCoordinator,
            ProcessDeploymentOperationMetrics operationMetrics) {
        return new RolloutControlService(rolloutRepository, versionRepository, artifactProjectionCoordinator,
                operationMetrics);
    }

    @Bean
    @ConditionalOnMissingBean(RoutingOutboxAdminService.class)
    public RoutingOutboxAdminService routingOutboxAdminService(RoutingOutboxRepository outboxRepository,
            RoutingOutboxScheduler scheduler) {
        return new RoutingOutboxAdminService(outboxRepository, scheduler);
    }

    @Bean
    @ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
    @ConditionalOnMissingBean(RoutingActivation.class)
    public RoutingActivation distributedRoutingActivation(RoutingOutboxScheduler scheduler) {
        return ignored -> scheduler.requestDispatch();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessPublicationValidator.class)
    public ProcessPublicationValidator processPublicationValidator(ProcessEngine processEngine,
            ProcessEngineConfig engineConfig, ObjectProvider<ProcessEngineRegistry> registryProvider) {
        return (ref, modelType, definition) -> validatePublication(ref, modelType, definition, processEngine,
                engineConfig, registryProvider.getIfAvailable());
    }

    private static ProcessPublicationValidation validatePublication(ProcessRef.Version ref, ProcessModelType modelType,
            ProcessDefinition.Inline definition, ProcessEngine processEngine, ProcessEngineConfig engineConfig,
            ProcessEngineRegistry registry) {
        if (registry != null) {
            ProcessEngine engine = registry.get(modelType);
            ProcessPreflightReport report = registry.preflight(modelType, definition, ProcessPreflightOptions.fast());
            return validateCalls(ref, definition, engine, report);
        }
        if (modelType != engineConfig.getModelType()) {
            return new ProcessPublicationValidation(unsupportedModelTypeReport(modelType, definition), List.of());
        }
        ProcessPreflightReport report = processEngine.tooling().preflight(definition, ProcessPreflightOptions.fast());
        return validateCalls(ref, definition, processEngine, report);
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
            ProcessVersionRepository versionRepository, ProcessPublicationValidator publicationValidator) {
        return new VersionPublicationService(versionRepository, publicationValidator,
                engineConfig.getDefinitionConfig().getMaxBytes());
    }

    @Bean
    @ConditionalOnMissingBean(ArtifactProjectionCoordinator.class)
    public ArtifactProjectionCoordinator artifactProjectionCoordinator(
            ObjectProvider<ProcessArtifactProjector> artifactProjectorProvider, DeploymentArtifactProperties artifact) {
        switch (artifact.getMode()) {
            case DATABASE:
                return ArtifactProjectionCoordinator.database();
            case CHANNEL:
                return ArtifactProjectionCoordinator.channel(artifactProjectorProvider.getIfAvailable());
            default:
                throw new IllegalStateException("Unsupported deployment artifact mode: " + artifact.getMode());
        }
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDeploymentService.class)
    public ProcessDeploymentService processDeploymentService(VersionPublicationService publicationService,
            ArtifactProjectionCoordinator artifactProjectionCoordinator,
            ProcessDeploymentOperationMetrics operationMetrics, RolloutControlService rolloutControlService,
            ProcessVersionRepository versionRepository, ProcessAliasRepository aliasRepository,
            RoutingActivation routingActivation) {
        return new DefaultProcessDeploymentService(publicationService, artifactProjectionCoordinator, operationMetrics,
                rolloutControlService, versionRepository, aliasRepository, routingActivation);
    }

    @Bean
    @ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
    @ConditionalOnMissingBean(RoutingProjectionReconciler.class)
    public RoutingProjectionReconciler routingProjectionReconciler(ProcessAliasRepository aliasRepository,
            ProcessVersionRepository versionRepository, ArtifactProjectionCoordinator artifactProjectionCoordinator,
            DeploymentSyncChannel channel, RoutingOutboxRepository outboxRepository, DeploymentRoutingProperties routing,
            ReconciliationProperties reconciliation) {
        return new RoutingProjectionReconciler(aliasRepository, versionRepository, artifactProjectionCoordinator,
                channel, outboxRepository, routing.getKeyPrefix(),
                reconciliation.getMode() == ReconciliationProperties.Mode.REPAIR, routing.getOperationTimeout());
    }

    @Bean(name = "scheduledRoutingProjectionReconciler", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
    @ConditionalOnMissingBean(RoutingProjectionReconciler.ScheduledReconciler.class)
    public RoutingProjectionReconciler.ScheduledReconciler scheduledRoutingProjectionReconciler(
            RoutingProjectionReconciler task, ReconciliationProperties reconciliation) {
        return new RoutingProjectionReconciler.ScheduledReconciler(task, reconciliation.getInterval());
    }

    @Bean(name = "routingProjectionReconcilerLifecycle")
    @ConditionalOnProperty(prefix = "compileflow.deploy", name = "topology", havingValue = "DISTRIBUTED")
    @ConditionalOnMissingBean(name = "routingProjectionReconcilerLifecycle")
    public SmartLifecycle routingProjectionReconcilerLifecycle(
            RoutingProjectionReconciler.ScheduledReconciler reconciler, ReconciliationProperties reconciliation) {
        return new ActionBackedLifecycle(() -> {
            if (reconciliation.getMode() != ReconciliationProperties.Mode.DISABLED) {
                reconciler.start();
            }
        }, reconciler::stop, CompileFlowLifecyclePhases.DEPLOY_RECONCILIATION);
    }
}
