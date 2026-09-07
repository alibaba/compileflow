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
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.command.AbortRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PublishProcessVersionCommand;
import com.alibaba.compileflow.deploy.api.command.RollbackRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.UpdateCanaryWeightCommand;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.control.observability.DeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.control.observability.DeploymentOperationMetrics.Operation;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutEvent;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPage;
import com.alibaba.compileflow.deploy.api.rollout.RolloutQuery;
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import com.alibaba.compileflow.deploy.api.version.PublishedProcessVersion;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionCursor;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionPage;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionQuery;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.control.repository.DeploymentCursorCodec;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasStore;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionStore;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default deployment facade backed by immutable version and rollout control services.
 *
 * @author yusu
 */
public final class DefaultProcessDeploymentService implements ProcessDeploymentService {
    private final VersionPublicationService publicationService;
    private final ArtifactProjectionCoordinator artifactProjectionCoordinator;
    private final DeploymentOperationMetrics operationMetrics;
    private final RolloutService rolloutService;
    private final ProcessVersionStore versionRepository;
    private final ProcessAliasStore aliasRepository;
    private final RoutingStatePropagator routingStatePropagator;

    public DefaultProcessDeploymentService(VersionPublicationService publicationService,
            ArtifactProjectionCoordinator artifactProjectionCoordinator, DeploymentOperationMetrics operationMetrics,
            RolloutService rolloutService, ProcessVersionStore versionRepository, ProcessAliasStore aliasRepository,
            RoutingStatePropagator routingStatePropagator) {
        this.publicationService = Objects.requireNonNull(publicationService, "publicationService");
        this.artifactProjectionCoordinator = Objects.requireNonNull(artifactProjectionCoordinator,
                "artifactProjectionCoordinator");
        this.operationMetrics = Objects.requireNonNull(operationMetrics, "operationMetrics");
        this.rolloutService = Objects.requireNonNull(rolloutService, "rolloutService");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository");
        this.aliasRepository = Objects.requireNonNull(aliasRepository, "aliasRepository");
        this.routingStatePropagator = Objects.requireNonNull(routingStatePropagator, "routingStatePropagator");
    }

    private static PublishedProcessVersion toPublishedVersion(ProcessVersionRecord record) {
        return new PublishedProcessVersion(record.getRef(), record.getModelType(), record.getArtifactDigest(),
                record.getMetadata(), record.getActor(), Instant.ofEpochMilli(record.getCreatedAt()));
    }

    private static ProcessAliasState toAliasState(ProcessAliasRecord record) {
        return new ProcessAliasState(ProcessRef.alias(record.getNamespace(), record.getCode(), record.getAlias()),
                ProcessRef.version(record.getNamespace(), record.getCode(), record.getStableVersion()),
                record.getCandidateVersion() == null
                ? null
                : ProcessRef.version(record.getNamespace(), record.getCode(), record.getCandidateVersion()),
                record.getCandidateWeightBps(), record.getTargeting(), record.getRevision(), record.getUpdatedBy(),
                Instant.ofEpochMilli(record.getUpdatedAt()));
    }

    @Override
    public Optional<PublishedProcessVersion> getVersion(ProcessRef.Version ref) {
        ProcessRef.Version versionRef = Objects.requireNonNull(ref, "ref");
        return versionRepository
            .find(versionRef.namespace(), versionRef.code(), versionRef.version())
            .map(DefaultProcessDeploymentService::toPublishedVersion);
    }

    @Override
    public PublishedVersionPage listVersions(PublishedVersionQuery query) {
        PublishedVersionQuery versionQuery = Objects.requireNonNull(query, "query");
        int limit = versionQuery.getLimit();
        List<PublishedProcessVersion> candidates = versionRepository
            .list(versionQuery.getNamespace(), versionQuery.getCode(), versionQuery.getVersionPrefix(),
                    DeploymentCursorCodec.publishedVersion(versionQuery.getCursor()), limit + 1)
            .stream()
            .map(DefaultProcessDeploymentService::toPublishedVersion)
            .collect(Collectors.toUnmodifiableList());
        boolean hasMore = candidates.size() > limit;
        List<PublishedProcessVersion> versions = hasMore ? candidates.subList(0, limit) : candidates;
        PublishedVersionCursor nextCursor = null;
        if (hasMore) {
            PublishedProcessVersion last = versions.get(versions.size() - 1);
            nextCursor = DeploymentCursorCodec.publishedVersion(last.getCreatedAt().toEpochMilli(),
                    last.getRef().version());
        }
        return new PublishedVersionPage(versions, nextCursor);
    }

    @Override
    public long countProcessesWithAliases(String namespace) {
        return aliasRepository.countDistinctProcesses(namespace);
    }

    @Override
    public Optional<ProcessAliasState> getAlias(ProcessRef.Alias ref) {
        ProcessRef.Alias aliasRef = Objects.requireNonNull(ref, "ref");
        return aliasRepository
            .resolve(aliasRef.namespace(), aliasRef.code(), aliasRef.alias())
            .map(DefaultProcessDeploymentService::toAliasState);
    }

    @Override
    public ProcessRollout createRollout(CreateRolloutCommand command) {
        return propagateCommittedAlias(rolloutService.create(command));
    }

    @Override
    public Optional<ProcessRollout> getRollout(String rolloutId) {
        return rolloutService.find(rolloutId);
    }

    @Override
    public RolloutPage listRollouts(RolloutQuery query) {
        return rolloutService.list(query);
    }

    @Override
    public List<RolloutEvent> listRolloutEvents(String rolloutId) {
        return rolloutService.listEvents(rolloutId);
    }

    @Override
    public ProcessRollout updateCanaryWeight(UpdateCanaryWeightCommand command) {
        return propagateCommittedAlias(rolloutService.updateCanary(command));
    }

    @Override
    public ProcessRollout promoteRollout(PromoteRolloutCommand command) {
        return propagateCommittedAlias(rolloutService.promote(command));
    }

    @Override
    public ProcessRollout abortRollout(AbortRolloutCommand command) {
        return propagateCommittedAlias(rolloutService.abort(command));
    }

    @Override
    public ProcessRollout rollbackRollout(RollbackRolloutCommand command) {
        return propagateCommittedAlias(rolloutService.rollback(command));
    }

    @Override
    public PublishedProcessVersion publish(PublishProcessVersionCommand command) {
        PublishProcessVersionCommand publication = Objects.requireNonNull(command, "command");
        try {
            ProcessVersionRecord published = publicationService.publish(publication.getRef(),
                    publication.getDefinition(), publication.getExpectedArtifactDigest(), publication.getMetadata(),
                    publication.getActor());
            artifactProjectionCoordinator.ensureProjected(published);
            operationMetrics.recordSuccess(Operation.PUBLISH);
            return toPublishedVersion(published);
        } catch (DeploymentException failure) {
            operationMetrics.recordFailure(Operation.PUBLISH);
            throw failure;
        } catch (Exception failure) {
            operationMetrics.recordFailure(Operation.PUBLISH);
            throw DeploymentException.fromRef(DeploymentErrorCode.INTERNAL_ERROR, "Unexpected error during publish",
                    publication.getRef(), failure);
        }
    }

    private ProcessRollout propagateCommittedAlias(ProcessRollout rollout) {
        ProcessAliasRecord current = aliasRepository
            .resolve(rollout.getAlias().namespace(), rollout.getAlias().code(), rollout.getAlias().alias())
            .orElseThrow(() -> new IllegalStateException(
                    "Committed rollout has no authoritative alias: rollout=" + rollout.getId()));
        routingStatePropagator.propagate(toAliasState(current));
        return rollout;
    }
}
