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
package com.alibaba.compileflow.deploy.runtime;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.runtime.demand.AliasVersionDemand;
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeOwnerIds;
import com.alibaba.compileflow.deploy.runtime.state.DesiredRoutingState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converges desired Alias state into executable node-local routing state.
 *
 * @author yusu
 */
public final class LocalRoutingReconciler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalRoutingReconciler.class);
    private final VersionDemandPlanner planner;
    private final RuntimeInstaller installer;
    private final LocalRoutingState localRoutingState;
    private final Set<String> targetingPolicyNames;
    private final ConcurrentHashMap<String, DesiredAliasState> desiredAliases = new ConcurrentHashMap<>();

    public LocalRoutingReconciler(VersionDemandPlanner planner, RuntimeInstaller installer,
            LocalRoutingState localRoutingState, Set<String> targetingPolicyNames) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.installer = Objects.requireNonNull(installer, "installer");
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

    private static LocalReadyRoutingStateSnapshot.ConvergenceState convergenceState(DesiredAliasState state) {
        if (state.localReadyGeneration == state.generation) {
            return LocalReadyRoutingStateSnapshot.ConvergenceState.LOCAL_READY;
        }
        if (state.convergence.isCompletedExceptionally() || state.convergence.isCancelled()) {
            return LocalReadyRoutingStateSnapshot.ConvergenceState.FAILED;
        }
        return LocalReadyRoutingStateSnapshot.ConvergenceState.PENDING;
    }

    private static DeploymentException convergenceFailure(DesiredRoutingState change, String message, Throwable cause) {
        return DeploymentException
            .builder(DeploymentErrorCode.CONVERGENCE_FAILED, message, cause)
            .namespace(change.getNamespace())
            .code(change.getCode())
            .alias(change.getAlias())
            .build();
    }

    /**
     * Accepts one authoritative desired alias revision.
     *
     * @param change validated desired alias state
     * @return completion that resolves only after this or a newer desired state is local-ready
     */
    public CompletableFuture<Void> apply(DesiredRoutingState change) {
        DesiredRoutingState desiredChange = Objects.requireNonNull(change, "change");
        String aliasId =
                RuntimeOwnerIds.alias(desiredChange.getNamespace(), desiredChange.getCode(), desiredChange.getAlias());
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
            AliasVersionDemand decision = planner.plan(desiredChange);
            DesiredRoutingState previousDesired = desired.desired;
            AliasVersionDemand previousDemand = desired.demand;
            long previousGeneration = desired.generation;
            CompletableFuture<Void> previousConvergence = desired.convergence;
            desired.desired = desiredChange;
            desired.demand = decision;
            long generation = ++desired.generation;
            try {
                CompletableFuture<Void> convergence =
                        installer.ensureInstalled(decision, () -> publishLocalReady(aliasId, desired, desiredChange,
                        generation));
                desired.convergence = convergence;
                return convergence;
            } catch (RuntimeException | Error failure) {
                desired.desired = previousDesired;
                desired.demand = previousDemand;
                desired.generation = previousGeneration;
                desired.convergence = previousConvergence;
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
    public LocalReadyRoutingStateSnapshot snapshot() {
        List<LocalReadyRoutingStateSnapshot.AliasState> aliases = new ArrayList<>();
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
                LocalReadyRoutingStateSnapshot.ConvergenceState convergenceState = convergenceState(state);
                if (!desiredDeleted) {
                    desiredAliasCount++;
                }
                if (state.localReadyRevision > 0L && !state.localReadyDeleted) {
                    localReadyAliasCount++;
                }
                if (convergenceState == LocalReadyRoutingStateSnapshot.ConvergenceState.PENDING) {
                    pendingAliasCount++;
                } else if (convergenceState == LocalReadyRoutingStateSnapshot.ConvergenceState.FAILED) {
                    failedAliasCount++;
                }
                aliases.add(
                        new LocalReadyRoutingStateSnapshot.AliasState(desired.getNamespace(), desired.getCode(),
                                desired.getAlias(), desired.getRevision(), desiredDeleted, state.localReadyRevision,
                                state.localReadyDeleted, convergenceState));
            }
        }
        aliases.sort(Comparator
            .comparing(LocalReadyRoutingStateSnapshot.AliasState::getNamespace)
            .thenComparing(LocalReadyRoutingStateSnapshot.AliasState::getCode)
            .thenComparing(LocalReadyRoutingStateSnapshot.AliasState::getAlias));
        return new LocalReadyRoutingStateSnapshot(desiredAliasCount, localReadyAliasCount, pendingAliasCount,
                failedAliasCount, aliases);
    }

    /**
     * Converges one desired alias through the same pipeline and waits for local readiness.
     *
     * @param change  validated desired alias state
     * @param timeout maximum caller wait; convergence may continue after a timeout
     */
    public void applyAndAwait(DesiredRoutingState change, Duration timeout) {
        DesiredRoutingState desiredChange = Objects.requireNonNull(change, "change");
        Duration wait = Objects.requireNonNull(timeout, "timeout");
        long timeoutMillis;
        try {
            timeoutMillis = wait.toMillis();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("timeout must be representable in milliseconds", failure);
        }
        if (timeoutMillis <= 0 || !wait.equals(Duration.ofMillis(timeoutMillis))) {
            throw new IllegalArgumentException("timeout must be a positive whole-millisecond duration");
        }

        try {
            apply(desiredChange).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (DeploymentException failure) {
            throw failure;
        } catch (CancellationException failure) {
            throw convergenceFailure(desiredChange, "Local-ready convergence was cancelled", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw convergenceFailure(desiredChange, "Local-ready convergence was interrupted", failure);
        } catch (TimeoutException failure) {
            throw convergenceFailure(desiredChange, "Local-ready convergence timed out", failure);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw convergenceFailure(desiredChange, "Local-ready convergence failed", cause);
        } catch (RuntimeException failure) {
            throw convergenceFailure(desiredChange, "Local-ready convergence could not start", failure);
        }
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
        CompletableFuture<Void> retried =
                installer.ensureInstalled(demand, () -> publishLocalReady(aliasId, desired, expected, generation));
        desired.convergence = retried;
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
    }
}
