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
package com.alibaba.compileflow.deploy.runtime.routing;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converges desired Alias state into executable node-local routing state.
 *
 * @author yusu
 */
public final class LocalRoutingReconciler {
    private static final int MAX_FAILURE_REASON_LENGTH = 160;
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalRoutingReconciler.class);
    private final VersionRuntimeManager versionRuntimeManager;
    private final LocalRoutingState localRoutingState;
    private final Set<String> targetingPolicyNames;
    private final ConcurrentHashMap<String, DesiredAliasState> desiredAliases = new ConcurrentHashMap<>();

    public LocalRoutingReconciler(VersionRuntimeManager versionRuntimeManager, LocalRoutingState localRoutingState,
            Set<String> targetingPolicyNames) {
        this.versionRuntimeManager = Objects.requireNonNull(versionRuntimeManager, "versionRuntimeManager");
        this.localRoutingState = Objects.requireNonNull(localRoutingState, "localRoutingState");
        this.targetingPolicyNames = Set.copyOf(Objects.requireNonNull(targetingPolicyNames, "targetingPolicyNames"));
    }

    private static boolean sameRevisionContent(DesiredRoutingState current, DesiredRoutingState change) {
        return current.getUpdatedAt() == change.getUpdatedAt() && current.getActor().equals(change.getActor())
                && current.isDeleted() == change.isDeleted()
                && Objects.equals(current.getStableVersion(), change.getStableVersion())
                && Objects.equals(current.getCandidateVersion(), change.getCandidateVersion())
                && current.getCandidateWeightBps() == change.getCandidateWeightBps()
                && Objects.equals(current.getTargeting(), change.getTargeting());
    }

    private static RoutingConvergenceSnapshot.ConvergenceState convergenceState(DesiredAliasState state) {
        if (state.localReadyGeneration == state.generation) {
            return RoutingConvergenceSnapshot.ConvergenceState.LOCAL_READY;
        }
        if (state.convergence.isCompletedExceptionally() || state.convergence.isCancelled()) {
            return RoutingConvergenceSnapshot.ConvergenceState.FAILED;
        }
        return RoutingConvergenceSnapshot.ConvergenceState.PENDING;
    }

    private static DeploymentException convergenceFailure(DesiredRoutingState change, String message, Throwable cause) {
        return DeploymentException
            .builder(DeploymentErrorCode.CONVERGENCE_FAILED, message, cause)
            .namespace(change.getNamespace())
            .code(change.getCode())
            .alias(change.getAlias())
            .build();
    }

    private static String aliasId(DesiredRoutingState state) {
        ProcessRef.Alias ref = ProcessRef.alias(state.getNamespace(), state.getCode(), state.getAlias());
        return ref.namespace() + "/" + ref.code() + "@" + ref.alias();
    }

    private static String boundedFailureReason(Throwable failure) {
        Throwable cause = Objects.requireNonNull(failure, "failure");
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String type = cause.getClass().getName();
        String reason =
                cause instanceof DeploymentException deploymentFailure ? type + "[" + deploymentFailure.getErrorCode()
                + "]" : type;
        return ProcessText.truncateCodePoints(reason, MAX_FAILURE_REASON_LENGTH);
    }

    private static void observeConvergence(DesiredAliasState desired, long generation,
            CompletableFuture<Void> convergence) {
        desired.failureReason = null;
        desired.convergence = convergence;
        convergence.whenComplete((ignored, failure) -> {
            synchronized (desired) {
                if (desired.generation == generation && desired.convergence == convergence) {
                    desired.failureReason = failure == null ? null : boundedFailureReason(failure);
                }
            }
        });
    }

    /**
     * Accepts one authoritative desired alias revision.
     *
     * @param change validated desired alias state
     * @return completion that resolves only after this or a newer desired state is local-ready
     */
    public CompletableFuture<Void> apply(DesiredRoutingState change) {
        DesiredRoutingState desiredChange = Objects.requireNonNull(change, "change");
        String aliasId = aliasId(desiredChange);
        DesiredAliasState desired = desiredAliases.computeIfAbsent(aliasId, ignored -> new DesiredAliasState());
        synchronized (desired) {
            DesiredRoutingState current = desired.desired;
            if (current != null && desiredChange.getRevision() <= current.getRevision()) {
                if (desiredChange.getRevision() < current.getRevision()) {
                    return desired.convergence;
                }
                if (!sameRevisionContent(current, desiredChange)) {
                    throw new IllegalStateException(
                            "Conflicting alias route for the same revision: alias=" + aliasId + " revision=" + current.getRevision());
                }
                return retryFailedConvergence(aliasId, desired);
            }
            requireTargetingPolicy(desiredChange);
            AliasVersionDemand decision = AliasVersionDemand.from(desiredChange);
            DesiredRoutingState previousDesired = desired.desired;
            AliasVersionDemand previousDemand = desired.demand;
            long previousGeneration = desired.generation;
            CompletableFuture<Void> previousConvergence = desired.convergence;
            String previousFailureReason = desired.failureReason;
            desired.desired = desiredChange;
            desired.demand = decision;
            long generation = ++desired.generation;
            try {
                CompletableFuture<Void> convergence = versionRuntimeManager.ensureInstalled(decision, () -> publishLocalReady(aliasId,
                        desired, desiredChange, generation));
                observeConvergence(desired, generation, convergence);
                return convergence;
            } catch (RuntimeException | Error failure) {
                desired.desired = previousDesired;
                desired.demand = previousDemand;
                desired.generation = previousGeneration;
                desired.convergence = previousConvergence;
                desired.failureReason = previousFailureReason;
                throw failure;
            }
        }
    }

    private void requireTargetingPolicy(DesiredRoutingState change) {
        if (change.getTargeting() != null && !targetingPolicyNames.contains(change.getTargeting().policy())) {
            throw convergenceFailure(change,
                    "Alias targeting policy is not registered on this node: " + change.getTargeting().policy(), null);
        }
    }

    /**
     * Returns current desired and local-ready state for node diagnostics.
     *
     * @return immutable point-in-time alias convergence snapshot
     */
    public RoutingConvergenceSnapshot snapshot() {
        List<RoutingConvergenceSnapshot.AliasState> aliases = new ArrayList<>();
        int desiredAliasCount = 0;
        int localReadyAliasCount = 0;
        int pendingAliasCount = 0;
        int failedAliasCount = 0;
        for (DesiredAliasState state : desiredAliases.values()) {
            synchronized (state) {
                DesiredRoutingState desired = state.desired;
                if (desired == null) {
                    continue;
                }
                boolean desiredDeleted = desired.isDeleted();
                RoutingConvergenceSnapshot.ConvergenceState convergenceState = convergenceState(state);
                if (!desiredDeleted) {
                    desiredAliasCount++;
                }
                if (state.localReadyRevision > 0L && !state.localReadyDeleted) {
                    localReadyAliasCount++;
                }
                if (convergenceState == RoutingConvergenceSnapshot.ConvergenceState.PENDING) {
                    pendingAliasCount++;
                } else if (convergenceState == RoutingConvergenceSnapshot.ConvergenceState.FAILED) {
                    failedAliasCount++;
                }
                aliases.add(
                        new RoutingConvergenceSnapshot.AliasState(desired.getNamespace(), desired.getCode(),
                                desired.getAlias(), desired.getRevision(), desiredDeleted, state.localReadyRevision,
                                state.localReadyDeleted, convergenceState, state.failureReason));
            }
        }
        aliases.sort(Comparator
            .comparing(RoutingConvergenceSnapshot.AliasState::getNamespace)
            .thenComparing(RoutingConvergenceSnapshot.AliasState::getCode)
            .thenComparing(RoutingConvergenceSnapshot.AliasState::getAlias));
        return new RoutingConvergenceSnapshot(desiredAliasCount, localReadyAliasCount, pendingAliasCount,
                failedAliasCount, aliases);
    }

    private CompletableFuture<Void> retryFailedConvergence(String aliasId, DesiredAliasState desired) {
        if (desired.localReadyGeneration == desired.generation) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> current = desired.convergence;
        if (!current.isCompletedExceptionally() && !current.isCancelled()) {
            return current;
        }

        DesiredRoutingState expected = desired.desired;
        AliasVersionDemand demand = desired.demand;
        long generation = desired.generation;
        CompletableFuture<Void> retried = versionRuntimeManager.ensureInstalled(demand, () -> publishLocalReady(aliasId,
                desired, expected, generation));
        observeConvergence(desired, generation, retried);
        return retried;
    }

    private void publishLocalReady(String aliasId, DesiredAliasState desired, DesiredRoutingState expected,
            long expectedGeneration) {
        synchronized (desired) {
            if (desired.generation != expectedGeneration || desired.desired != expected) {
                return;
            }
            boolean applied;
            if (expected.isDeleted()) {
                applied = localRoutingState.applyAliasTombstone(ProcessRef.alias(expected.getNamespace(),
                                expected.getCode(), expected.getAlias()), expected.getRevision());
            } else {
                applied = localRoutingState.applyAliasRoute(expected.toAliasRoute());
            }
            if (!applied) {
                throw new IllegalStateException(
                        "Local-ready snapshot rejected authoritative alias revision: alias=" + aliasId + " revision="
                        + expected.getRevision());
            }
            desired.localReadyGeneration = expectedGeneration;
            desired.localReadyRevision = expected.getRevision();
            desired.localReadyDeleted = expected.isDeleted();
            desired.failureReason = null;
            LOGGER.info("Published local-ready alias state: alias={} revision={} generation={}", aliasId,
                    expected.getRevision(), expectedGeneration);
        }
    }

    private static final class DesiredAliasState {
        private long generation;
        private long localReadyGeneration;
        private long localReadyRevision;
        private boolean localReadyDeleted;
        private DesiredRoutingState desired;
        private AliasVersionDemand demand;
        private CompletableFuture<Void> convergence = CompletableFuture.completedFuture(null);
        private String failureReason;
    }
}
