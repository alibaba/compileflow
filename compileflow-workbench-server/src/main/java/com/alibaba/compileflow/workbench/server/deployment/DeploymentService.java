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
package com.alibaba.compileflow.workbench.server.deployment;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.command.AbortRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.RollbackRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.UpdateCanaryWeightCommand;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutCursor;
import com.alibaba.compileflow.deploy.api.rollout.RolloutConstraints;
import com.alibaba.compileflow.deploy.api.rollout.RolloutEvent;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPage;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPhase;
import com.alibaba.compileflow.deploy.api.rollout.RolloutQuery;
import com.alibaba.compileflow.deploy.api.rollout.RolloutStrategy;
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import com.alibaba.compileflow.workbench.server.security.ServerIdentity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Adapts Workbench deployment requests to the authoritative deploy control plane.
 *
 * @author yusu
 */
@Service
public class DeploymentService {
    private final ProcessDeploymentService processDeploymentService;
    private final ServerIdentity identity;

    public DeploymentService(ProcessDeploymentService processDeploymentService, ServerIdentity identity) {
        this.processDeploymentService = processDeploymentService;
        this.identity = identity;
    }

    private static RolloutStrategy parseStrategy(String value) {
        String strategy = StringUtils.defaultIfBlank(StringUtils.trimToNull(value), "all_at_once");
        if ("all_at_once".equalsIgnoreCase(strategy)) {
            return RolloutStrategy.ALL_AT_ONCE;
        }
        if ("canary".equalsIgnoreCase(strategy)) {
            return RolloutStrategy.CANARY;
        }
        throw new IllegalArgumentException("strategy must be all_at_once or canary");
    }

    private static RolloutPhase parseOptionalPhase(String value) {
        String status = StringUtils.trimToNull(value);
        if (status == null) {
            return null;
        }
        switch (status.toLowerCase(Locale.ROOT)) {
            case "in_progress":
                return RolloutPhase.IN_PROGRESS;
            case "completed":
                return RolloutPhase.COMPLETED;
            case "aborted":
                return RolloutPhase.ABORTED;
            default:
                throw new IllegalArgumentException("Unsupported deployment status: " + value);
        }
    }

    private static String apiStatus(RolloutPhase phase) {
        switch (phase) {
            case IN_PROGRESS:
                return "in_progress";
            case COMPLETED:
                return "completed";
            case ABORTED:
                return "aborted";
            default:
                throw new IllegalStateException("Unsupported rollout phase: " + phase);
        }
    }

    private static String requireText(String value, String name) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }

    private static String requestText(String value, String name) {
        try {
            return requireText(value, name);
        } catch (IllegalArgumentException failure) {
            throw invalidRequest(failure);
        }
    }

    private static InvalidDeploymentRequestException invalidRequest(IllegalArgumentException failure) {
        return new InvalidDeploymentRequestException(failure.getMessage(), failure);
    }

    public DeploymentPage listDeployments(String processCode, String keyword, String alias, String status, String cursor,
            int limit) {
        RolloutQuery query;
        try {
            query = new RolloutQuery(ProcessRef.DEFAULT_NAMESPACE, StringUtils.trimToNull(processCode),
                    StringUtils.trimToNull(keyword), StringUtils.trimToNull(alias), parseOptionalPhase(status),
                    StringUtils.isBlank(cursor) ? null : new RolloutCursor(cursor), limit);
        } catch (IllegalArgumentException failure) {
            throw invalidRequest(failure);
        }
        RolloutPage rollouts;
        try {
            rollouts = processDeploymentService.listRollouts(query);
        } catch (IllegalArgumentException failure) {
            throw invalidRequest(failure);
        }
        List<DeploymentView> views = new ArrayList<>(rollouts.getRecords().size());
        for (ProcessRollout rollout : rollouts.getRecords()) {
            views.add(toView(rollout));
        }
        return new DeploymentPage(views, rollouts.getNextCursor() == null ? null : rollouts.getNextCursor().value());
    }

    public Optional<DeploymentView> getDeployment(String deploymentId) {
        return processDeploymentService.getRollout(requestText(deploymentId, "deploymentId")).map(this::toView);
    }

    public Optional<ProcessAliasState> getRoute(String processCode, String alias) {
        ProcessRef.Alias aliasRef;
        try {
            aliasRef = ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, requireText(processCode, "processCode"),
                    DeploymentRequestValidation.requireAlias(alias));
        } catch (IllegalArgumentException failure) {
            throw invalidRequest(failure);
        }
        return processDeploymentService.getAlias(aliasRef);
    }

    public DeploymentView createDeployment(CreateDeploymentCommand request) {
        CreateRolloutCommand command;
        try {
            String idempotencyKey = RolloutConstraints.requireIdempotencyKey(request.idempotencyKey());
            String processCode = requireText(request.processCode(), "processCode");
            ProcessRef.Alias alias = ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, processCode,
                    DeploymentRequestValidation.requireAlias(request.alias()));
            ProcessRef.Version target =
                    ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, processCode,
                            requireText(request.version(), "version"));
            RolloutStrategy strategy = parseStrategy(request.strategy());
            if (strategy == RolloutStrategy.CANARY) {
                Integer weightBps = request.canaryWeightBps();
                if (weightBps == null) {
                    throw new IllegalArgumentException("canaryWeightBps is required for canary strategy");
                }
                command = CreateRolloutCommand.canary(idempotencyKey, alias, target, request.expectedRouteRevision(),
                        weightBps, request.targeting(), identity.principal(), request.notes());
            } else {
                if (request.canaryWeightBps() != null || request.targeting() != null) {
                    throw new IllegalArgumentException(
                            "canaryWeightBps and targeting must be absent for all-at-once strategy");
                }
                command = CreateRolloutCommand.allAtOnce(idempotencyKey, alias, target, request.expectedRouteRevision(),
                        identity.principal(), request.notes());
            }
        } catch (IllegalArgumentException failure) {
            throw invalidRequest(failure);
        }
        ProcessRollout rollout = processDeploymentService.createRollout(command);
        return toView(rollout);
    }

    public DeploymentView updateCanary(String deploymentId, int weightBps, long expectedRevision) {
        UpdateCanaryWeightCommand command;
        try {
            command = new UpdateCanaryWeightCommand(deploymentId, weightBps, expectedRevision, identity.principal());
        } catch (IllegalArgumentException failure) {
            throw invalidRequest(failure);
        }
        ProcessRollout rollout = processDeploymentService.updateCanaryWeight(command);
        return toView(rollout);
    }

    public DeploymentView promoteCanary(String deploymentId, long expectedRevision) {
        PromoteRolloutCommand command;
        try {
            command = new PromoteRolloutCommand(deploymentId, expectedRevision, identity.principal());
        } catch (IllegalArgumentException failure) {
            throw invalidRequest(failure);
        }
        ProcessRollout rollout = processDeploymentService.promoteRollout(command);
        return toView(rollout);
    }

    public DeploymentView abortCanary(String deploymentId, long expectedRevision, String reason) {
        AbortRolloutCommand command;
        try {
            command = new AbortRolloutCommand(deploymentId, expectedRevision, identity.principal(), reason);
        } catch (IllegalArgumentException failure) {
            throw invalidRequest(failure);
        }
        ProcessRollout rollout = processDeploymentService.abortRollout(command);
        return toView(rollout);
    }

    public DeploymentView rollbackDeployment(String deploymentId, String idempotencyKey, long expectedRouteRevision) {
        RollbackRolloutCommand command;
        try {
            command = new RollbackRolloutCommand(requireText(deploymentId, "deploymentId"),
                    RolloutConstraints.requireIdempotencyKey(idempotencyKey), expectedRouteRevision,
                    identity.principal());
        } catch (IllegalArgumentException failure) {
            throw invalidRequest(failure);
        }
        ProcessRollout rollback = processDeploymentService.rollbackRollout(command);
        return toView(rollback);
    }

    public Optional<List<DeploymentEventView>> getDeploymentEvents(String deploymentId) {
        String normalizedId = requestText(deploymentId, "deploymentId");
        if (processDeploymentService.getRollout(normalizedId).isEmpty()) {
            return Optional.empty();
        }
        List<RolloutEvent> events = processDeploymentService.listRolloutEvents(normalizedId);
        List<DeploymentEventView> views = new ArrayList<>(events.size());
        for (RolloutEvent event : events) {
            views.add(
                    new DeploymentEventView(event.getId(), event.getSequence(), event.getType(),
                            event.getFromPhase() == null ? null : event
                                .getFromPhase()
                                .name()
                                .toLowerCase(Locale.ROOT), event.getToPhase().name().toLowerCase(Locale.ROOT),
                            event.getActor(), event.getReason(), event.getCreatedAt().toString()));
        }
        return Optional.of(List.copyOf(views));
    }

    private DeploymentView toView(ProcessRollout rollout) {
        String createdAt = rollout.getCreatedAt().toString();
        String deployedAt = rollout.getCompletedAt() == null ? null : rollout.getCompletedAt().toString();
        Long duration = rollout.getCompletedAt() == null
                ? null
                : Long.valueOf(Duration.between(rollout.getCreatedAt(), rollout.getCompletedAt()).toMillis());
        Integer canaryWeightBps =
                rollout.getStrategy() == RolloutStrategy.CANARY ? Integer.valueOf(rollout.getTargetWeightBps()) : null;
        return new DeploymentView(rollout.getId(), rollout.getAlias().code(), rollout.getTargetVersion().version(),
                rollout.getBaselineVersion() == null ? null : rollout.getBaselineVersion().version(),
                rollout.getAlias().alias(), rollout.getOperationKind().name().toLowerCase(Locale.ROOT),
                apiStatus(rollout.getPhase()), rollout.getStrategy().name().toLowerCase(Locale.ROOT), canaryWeightBps,
                rollout.getTargeting(), rollout.getRolloutRevision(), rollout.getBaseAliasRevision(),
                rollout.getAliasRevision(), createdAt, deployedAt, rollout.getCreatedBy(), rollout.getNotes(), duration);
    }
}
