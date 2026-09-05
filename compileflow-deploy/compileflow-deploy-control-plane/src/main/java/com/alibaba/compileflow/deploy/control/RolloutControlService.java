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
package com.alibaba.compileflow.deploy.control;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.command.AbortRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.RollbackRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.UpdateCanaryWeightCommand;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics.Operation;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutEvent;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPage;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPhase;
import com.alibaba.compileflow.deploy.api.rollout.RolloutQuery;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.RolloutCreateRequest;
import com.alibaba.compileflow.deploy.control.repository.RolloutRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Application service for durable rollout commands and queries.
 *
 * @author yusu
 */
public final class RolloutControlService {
    private final RolloutRepository repository;
    private final ProcessVersionRepository versionRepository;
    private final ArtifactProjectionCoordinator artifactProjectionCoordinator;
    private final ProcessDeploymentOperationMetrics operationMetrics;

    public RolloutControlService(RolloutRepository repository, ProcessVersionRepository versionRepository,
            ArtifactProjectionCoordinator artifactProjectionCoordinator,
            ProcessDeploymentOperationMetrics operationMetrics) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository");
        this.artifactProjectionCoordinator = Objects.requireNonNull(artifactProjectionCoordinator,
                "artifactProjectionCoordinator");
        this.operationMetrics = Objects.requireNonNull(operationMetrics, "operationMetrics");
    }

    public ProcessRollout create(CreateRolloutCommand command) {
        Objects.requireNonNull(command, "command");
        return executeOperation(Operation.CREATE_ROLLOUT, () -> {
            ensureArtifactAvailable(command.getTargetVersion());
            return repository.create(RolloutCreateRequest.deploy(command));
        });
    }

    public Optional<ProcessRollout> find(String rolloutId) {
        return repository.find(rolloutId);
    }

    public RolloutPage list(RolloutQuery query) {
        return repository.list(query);
    }

    public List<RolloutEvent> listEvents(String rolloutId) {
        return repository.listEvents(rolloutId);
    }

    public ProcessRollout updateCanary(UpdateCanaryWeightCommand command) {
        return executeOperation(Operation.UPDATE_CANARY, () -> repository.updateCanary(command));
    }

    public ProcessRollout promote(PromoteRolloutCommand command) {
        return executeOperation(Operation.PROMOTE, () -> repository.promote(command));
    }

    public ProcessRollout abort(AbortRolloutCommand command) {
        return executeOperation(Operation.ABORT, () -> repository.abort(command));
    }

    public ProcessRollout rollback(RollbackRolloutCommand command) {
        Objects.requireNonNull(command, "command");
        return executeOperation(Operation.ROLLBACK, () -> {
            ProcessRollout source = repository
                .find(command.getSourceRolloutId())
                .orElseThrow(() -> DeploymentException.of(DeploymentErrorCode.ROLLOUT_NOT_FOUND,
                        "Source rollout not found: " + command.getSourceRolloutId()));
            if (source.getPhase() != RolloutPhase.COMPLETED) {
                throw DeploymentException.of(DeploymentErrorCode.ROLLOUT_CONFLICT,
                        "Only a completed rollout can be rolled back: " + source.getId());
            }
            ProcessRef.Version baseline = source.getBaselineVersion();
            if (baseline == null) {
                throw DeploymentException.of(DeploymentErrorCode.ROLLOUT_CONFLICT,
                        "Source rollout has no captured baseline: " + source.getId());
            }
            if (command.getExpectedAliasRevision() != source.getAliasRevision()) {
                throw DeploymentException.of(DeploymentErrorCode.CONCURRENT_MODIFICATION,
                        "Source rollout no longer owns the expected route revision: " + source.getId());
            }
            ensureArtifactAvailable(baseline);
            return repository.create(RolloutCreateRequest.rollback(command.getIdempotencyKey(), source.getAlias(),
                    baseline, command.getExpectedAliasRevision(), command.getActor(), "Rollback of " + source.getId()));
        });
    }

    private void ensureArtifactAvailable(ProcessRef.Version version) {
        ProcessVersionRecord record = versionRepository
            .find(version.namespace(), version.code(), version.version())
            .orElseThrow(() -> DeploymentException
                .builder(DeploymentErrorCode.VERSION_NOT_FOUND, "Target version is not published")
                .namespace(version.namespace())
                .code(version.code())
                .version(version.version())
                .build());
        artifactProjectionCoordinator.ensureProjected(record);
    }

    private <T> T executeOperation(Operation operation, Supplier<T> action) {
        try {
            T result = action.get();
            operationMetrics.recordSuccess(operation);
            return result;
        } catch (RuntimeException exception) {
            operationMetrics.recordFailure(operation);
            throw exception;
        }
    }
}
