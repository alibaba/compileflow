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
package com.alibaba.compileflow.deploy.control.projection;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.protocol.RoutingStateKeys;
import com.alibaba.compileflow.deploy.protocol.RoutingStateCodec;
import com.alibaba.compileflow.deploy.protocol.RoutingStateUpdate;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import com.alibaba.compileflow.deploy.spi.store.ProcessKey;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasStore;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionStore;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxRecord;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciles authoritative Alias state and active artifacts with their runtime projections.
 *
 * @author yusu
 */
public final class DeploymentProjectionReconciler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentProjectionReconciler.class);
    private final ProcessAliasStore aliasRepository;
    private final ProcessVersionStore versionRepository;
    private final ArtifactProjectionCoordinator artifactCoordinator;
    private final DeploymentProjectionStore projectionStore;
    private final RoutingOutboxStore outboxRepository;
    private final String keyPrefix;
    private final boolean autoFix;
    private final Duration routingOperationTimeout;
    private final AtomicLong totalReconciliations = new AtomicLong();
    private final AtomicLong totalMismatches = new AtomicLong();
    private final AtomicLong totalAutoFixed = new AtomicLong();
    private final AtomicLong totalArtifactChecks = new AtomicLong();
    private final AtomicLong totalArtifactMissing = new AtomicLong();
    private final AtomicLong totalArtifactRepairs = new AtomicLong();
    private final AtomicLong totalArtifactConflicts = new AtomicLong();
    private final AtomicLong totalArtifactFailures = new AtomicLong();
    private final AtomicLong lastCheckedProcesses = new AtomicLong();

    public DeploymentProjectionReconciler(ProcessAliasStore aliasRepository, ProcessVersionStore versionRepository,
            ArtifactProjectionCoordinator artifactCoordinator, DeploymentProjectionStore projectionStore,
            RoutingOutboxStore outboxRepository, String keyPrefix, boolean autoFix, Duration routingOperationTimeout) {
        this.aliasRepository = Objects.requireNonNull(aliasRepository, "aliasRepository");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository");
        this.artifactCoordinator = Objects.requireNonNull(artifactCoordinator, "artifactCoordinator");
        this.projectionStore = Objects.requireNonNull(projectionStore, "projectionStore");
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.keyPrefix = keyPrefix;
        this.autoFix = autoFix;
        this.routingOperationTimeout = Objects.requireNonNull(routingOperationTimeout, "routingOperationTimeout");
        if (routingOperationTimeout.isZero() || routingOperationTimeout.isNegative()) {
            throw new IllegalArgumentException("routingOperationTimeout must be positive");
        }
    }

    private static boolean isArtifactConflict(DeploymentErrorCode errorCode) {
        return errorCode == DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH
                || errorCode == DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH
                || errorCode == DeploymentErrorCode.INVALID_ARGUMENT;
    }

    private static boolean matches(ProcessAliasRecord alias, RoutingStateUpdate channelState) {
        if (channelState == null || channelState.isDeleted()) {
            return false;
        }
        int candidateWeightBps = alias.getCandidateWeightBps() == null ? 0 : alias.getCandidateWeightBps().intValue();
        return matchesIdentity(alias, channelState) && alias
                    .getStableVersion()
                    .equals(channelState.getStableVersion())
                && Objects.equals(alias.getCandidateVersion(), channelState.getCandidateVersion())
                && candidateWeightBps == channelState.getCandidateWeightBps()
                && Objects.equals(alias.getTargeting(), channelState.getTargeting())
                && alias.getRevision() == channelState.getAliasRevision()
                && alias.getUpdatedBy().equals(channelState.getActor())
                && alias.getUpdatedAt() == channelState.getUpdatedAt();
    }

    private static boolean matchesIdentity(ProcessAliasRecord alias, RoutingStateUpdate channelState) {
        return alias.getNamespace().equals(channelState.getNamespace())
                && alias.getCode().equals(channelState.getCode()) && alias.getAlias().equals(channelState.getAlias());
    }

    private static String mismatchDescription(ProcessAliasRecord alias, RoutingStateUpdate channelState,
            boolean repairable) {
        if (channelState == null) {
            return "ProjectionStore alias state is absent";
        }
        if (!matchesIdentity(alias, channelState)) {
            return "ProjectionStore alias state conflicts with the repository identity";
        }
        String category = repairable
                ? "ProjectionStore alias state is behind the repository"
                : "ProjectionStore alias state conflicts at the same or a newer revision";
        return category + ": repositoryRevision=" + alias.getRevision() + ", projectionRevision="
                + channelState.getAliasRevision();
    }

    public ReconciliationResult reconcile() {
        List<ProcessKey> aliasedProcesses = aliasRepository.listDistinctProcesses();
        long startedAtNanos = System.nanoTime();
        int checkedProcesses = 0;
        int checkedItems = 0;
        int mismatches = 0;
        int fixed = 0;
        int failedProcesses = 0;
        int checkedArtifacts = 0;
        int missingArtifacts = 0;
        int repairedArtifacts = 0;
        int conflictingArtifacts = 0;
        int failedArtifacts = 0;
        List<ReconciliationMismatch> details = new ArrayList<>();

        for (ProcessKey process : aliasedProcesses) {
            checkedProcesses++;
            List<ProcessAliasRecord> aliases;
            try {
                aliases = aliasRepository.listAll(process.namespace(), process.code());
            } catch (Exception failure) {
                failedProcesses++;
                LOGGER.error("Failed to read authoritative Aliases: process={}", process, failure);
                continue;
            }
            AliasReconciliationResult aliasResult = reconcileAliases(process.namespace(), process.code(), aliases);
            checkedItems += aliasResult.checked;
            if (aliasResult.failed) {
                failedProcesses++;
            }
            mismatches += aliasResult.mismatches.size();
            for (ReconciliationMismatch mismatch : aliasResult.mismatches) {
                if (mismatch.isAutoFixed()) {
                    fixed++;
                }
            }
            details.addAll(aliasResult.mismatches);
            if (artifactCoordinator.isProjectionRequired()) {
                ArtifactReconciliationResult artifacts = reconcileArtifacts(aliases);
                checkedArtifacts += artifacts.checked;
                missingArtifacts += artifacts.missing;
                repairedArtifacts += artifacts.repairs;
                conflictingArtifacts += artifacts.conflicts;
                failedArtifacts += artifacts.failures;
            }
        }

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        totalReconciliations.incrementAndGet();
        totalMismatches.addAndGet(mismatches);
        totalAutoFixed.addAndGet(fixed);
        totalArtifactChecks.addAndGet(checkedArtifacts);
        totalArtifactMissing.addAndGet(missingArtifacts);
        totalArtifactRepairs.addAndGet(repairedArtifacts);
        totalArtifactConflicts.addAndGet(conflictingArtifacts);
        totalArtifactFailures.addAndGet(failedArtifacts);
        lastCheckedProcesses.set(checkedProcesses);
        return new ReconciliationResult(checkedProcesses, checkedItems, mismatches, fixed, failedProcesses,
                checkedArtifacts, missingArtifacts, repairedArtifacts, conflictingArtifacts, failedArtifacts, durationMs,
                details);
    }

    public Map<String, Long> getStatistics() {
        Map<String, Long> statistics = new LinkedHashMap<>();
        statistics.put("totalReconciliations", totalReconciliations.get());
        statistics.put("totalMismatches", totalMismatches.get());
        statistics.put("totalAutoFixed", totalAutoFixed.get());
        statistics.put("totalArtifactChecks", totalArtifactChecks.get());
        statistics.put("totalArtifactMissing", totalArtifactMissing.get());
        statistics.put("totalArtifactRepairs", totalArtifactRepairs.get());
        statistics.put("totalArtifactConflicts", totalArtifactConflicts.get());
        statistics.put("totalArtifactFailures", totalArtifactFailures.get());
        statistics.put("lastCheckedProcesses", lastCheckedProcesses.get());
        return Collections.unmodifiableMap(statistics);
    }

    public long getTotalReconciliations() {
        return totalReconciliations.get();
    }

    public long getTotalRoutingMismatches() {
        return totalMismatches.get();
    }

    public long getTotalRoutingRepairs() {
        return totalAutoFixed.get();
    }

    public long getTotalArtifactChecks() {
        return totalArtifactChecks.get();
    }

    public long getTotalArtifactMissing() {
        return totalArtifactMissing.get();
    }

    public long getTotalArtifactRepairs() {
        return totalArtifactRepairs.get();
    }

    public long getTotalArtifactConflicts() {
        return totalArtifactConflicts.get();
    }

    public long getTotalArtifactFailures() {
        return totalArtifactFailures.get();
    }

    private ArtifactReconciliationResult reconcileArtifacts(List<ProcessAliasRecord> aliases) {
        Set<ProcessRef.Version> activeVersions = new LinkedHashSet<>();
        for (ProcessAliasRecord alias : aliases) {
            activeVersions.add(ProcessRef.version(alias.getNamespace(), alias.getCode(), alias.getStableVersion()));
            if (alias.getCandidateVersion() != null) {
                activeVersions.add(ProcessRef.version(alias.getNamespace(), alias.getCode(), alias.getCandidateVersion()));
            }
        }

        int missing = 0;
        int repairs = 0;
        int conflicts = 0;
        int failures = 0;
        Set<ProcessRef.Version> checkedVersions = new LinkedHashSet<>();
        List<ProcessRef.Version> pending = new ArrayList<>(activeVersions);
        while (!pending.isEmpty()) {
            ProcessRef.Version ref = pending.remove(pending.size() - 1);
            if (!checkedVersions.add(ref)) {
                continue;
            }
            try {
                ProcessVersionRecord version = versionRepository
                    .find(ref.namespace(), ref.code(), ref.version())
                    .orElseThrow(() -> new IllegalStateException(
                            "Active Alias references a missing published version: " + ref));
                version
                    .getCallBindings()
                    .stream()
                    .map(binding -> binding.target())
                    .forEach(pending::add);
                ArtifactProjectionStatus status =
                        autoFix ? artifactCoordinator.repair(version) : artifactCoordinator.inspect(version);
                if (status == ArtifactProjectionStatus.MISSING || status == ArtifactProjectionStatus.REPAIRED) {
                    missing++;
                }
                if (status == ArtifactProjectionStatus.REPAIRED) {
                    repairs++;
                }
            } catch (DeploymentException failure) {
                failures++;
                if (isArtifactConflict(failure.getErrorCode())) {
                    conflicts++;
                    LOGGER.error("Artifact projection reconciliation conflict: version={} errorCode={} message={}", ref,
                            failure.getErrorCode(), failure.getMessage());
                } else {
                    LOGGER.error("Artifact projection reconciliation failed: version={} errorCode={}", ref,
                            failure.getErrorCode(), failure);
                }
            } catch (RuntimeException failure) {
                failures++;
                LOGGER.error("Artifact projection reconciliation failed: version={}", ref, failure);
            }
        }
        return new ArtifactReconciliationResult(checkedVersions.size(), missing, repairs, conflicts, failures);
    }

    private AliasReconciliationResult reconcileAliases(String namespace, String code, List<ProcessAliasRecord> aliases) {
        List<ReconciliationMismatch> mismatches = new ArrayList<>();
        int checked = 0;
        boolean failed = false;
        for (ProcessAliasRecord alias : aliases) {
            String routingKey = RoutingStateKeys.aliasState(keyPrefix, namespace, code, alias.getAlias());
            try {
                String projectionPayload = projectionStore.read(routingKey, routingOperationTimeout);
                RoutingStateUpdate channelState = RoutingStateCodec.parse(projectionPayload);
                checked++;
                if (matches(alias, channelState)) {
                    continue;
                }
                long projectionRevision = channelState == null ? 0L : channelState.getAliasRevision();
                boolean repairable =
                        channelState == null
                        || (matchesIdentity(alias, channelState) && projectionRevision < alias.getRevision());
                boolean repaired = autoFix && repairable && insertCorrection(namespace, code, alias, routingKey);
                mismatches.add(
                        new ReconciliationMismatch(namespace, code, alias.getAlias(), alias.getRevision(),
                                projectionRevision, mismatchDescription(alias, channelState, repairable), repaired));
            } catch (Exception failure) {
                failed = true;
                LOGGER.error("Routing Alias reconciliation failed: ns={} code={} alias={}", namespace, code,
                        alias.getAlias(), failure);
            }
        }
        return new AliasReconciliationResult(checked, failed, mismatches);
    }

    private boolean insertCorrection(String namespace, String code, ProcessAliasRecord alias, String routingKey) {
        try {
            return outboxRepository.ensurePending(RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE, namespace, code,
                    alias.getAlias(), routingKey,
                    RoutingStateCodec.aliasStateJson(namespace, code, alias.getAlias(), alias.getStableVersion(),
                            alias.getCandidateVersion(), alias.getCandidateWeightBps(), alias.getTargeting(),
                            alias.getRevision(), alias.getUpdatedBy(), alias.getUpdatedAt()));
        } catch (Exception failure) {
            LOGGER.warn("Failed to enqueue route correction: ns={} code={} alias={}", namespace, code, alias.getAlias(),
                    failure);
            return false;
        }
    }

    private static final class AliasReconciliationResult {
        private final int checked;
        private final boolean failed;
        private final List<ReconciliationMismatch> mismatches;

        private AliasReconciliationResult(int checked, boolean failed, List<ReconciliationMismatch> mismatches) {
            this.checked = checked;
            this.failed = failed;
            this.mismatches = mismatches;
        }
    }

    /**
     * Summary of one reconciliation cycle.
     */
    public static final class ReconciliationResult {
        private final int checkedProcesses;
        private final int checkedItems;
        private final int mismatchesFound;
        private final int autoFixedCount;
        private final int failedProcessesCount;
        private final int checkedArtifactVersions;
        private final int missingArtifactProjections;
        private final int repairedArtifactProjections;
        private final int conflictingArtifactProjections;
        private final int failedArtifactProjections;
        private final long durationMs;
        private final List<ReconciliationMismatch> mismatchDetails;

        public ReconciliationResult(int checkedProcesses, int checkedItems, int mismatchesFound, int autoFixedCount,
                int failedProcessesCount, int checkedArtifactVersions, int missingArtifactProjections,
                int repairedArtifactProjections, int conflictingArtifactProjections, int failedArtifactProjections,
                long durationMs, List<ReconciliationMismatch> mismatchDetails) {
            this.checkedProcesses = checkedProcesses;
            this.checkedItems = checkedItems;
            this.mismatchesFound = mismatchesFound;
            this.autoFixedCount = autoFixedCount;
            this.failedProcessesCount = failedProcessesCount;
            this.checkedArtifactVersions = checkedArtifactVersions;
            this.missingArtifactProjections = missingArtifactProjections;
            this.repairedArtifactProjections = repairedArtifactProjections;
            this.conflictingArtifactProjections = conflictingArtifactProjections;
            this.failedArtifactProjections = failedArtifactProjections;
            this.durationMs = durationMs;
            this.mismatchDetails = Collections.unmodifiableList(new ArrayList<>(mismatchDetails));
        }

        public int getCheckedProcesses() {
            return checkedProcesses;
        }

        public int getCheckedItems() {
            return checkedItems;
        }

        public int getMismatchesFound() {
            return mismatchesFound;
        }

        public int getAutoFixedCount() {
            return autoFixedCount;
        }

        public int getFailedProcessesCount() {
            return failedProcessesCount;
        }

        public int getCheckedArtifactVersions() {
            return checkedArtifactVersions;
        }

        public int getMissingArtifactProjections() {
            return missingArtifactProjections;
        }

        public int getRepairedArtifactProjections() {
            return repairedArtifactProjections;
        }

        public int getConflictingArtifactProjections() {
            return conflictingArtifactProjections;
        }

        public int getFailedArtifactProjections() {
            return failedArtifactProjections;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public List<ReconciliationMismatch> getMismatchDetails() {
            return mismatchDetails;
        }
    }

    /**
     * Description of one Alias routing drift.
     */
    public static final class ReconciliationMismatch {
        private final String namespace;
        private final String code;
        private final String aliasName;
        private final long repositoryRevision;
        private final long projectionRevision;
        private final String reason;
        private final boolean autoFixed;

        public ReconciliationMismatch(String namespace, String code, String aliasName, long repositoryRevision,
                long projectionRevision, String reason, boolean autoFixed) {
            this.namespace = namespace;
            this.code = code;
            this.aliasName = aliasName;
            this.repositoryRevision = repositoryRevision;
            this.projectionRevision = projectionRevision;
            this.reason = reason;
            this.autoFixed = autoFixed;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getCode() {
            return code;
        }

        public String getAliasName() {
            return aliasName;
        }

        public long getRepositoryRevision() {
            return repositoryRevision;
        }

        public long getProjectionRevision() {
            return projectionRevision;
        }

        public String getReason() {
            return reason;
        }

        public boolean isAutoFixed() {
            return autoFixed;
        }
    }

    private static final class ArtifactReconciliationResult {
        private final int checked;
        private final int missing;
        private final int repairs;
        private final int conflicts;
        private final int failures;

        private ArtifactReconciliationResult(int checked, int missing, int repairs, int conflicts, int failures) {
            this.checked = checked;
            this.missing = missing;
            this.repairs = repairs;
            this.conflicts = conflicts;
            this.failures = failures;
        }
    }

    /**
     * Lifecycle wrapper for scheduled reconciliation.
     */
    public static final class ScheduledReconciler implements AutoCloseable {
        private static final long SHUTDOWN_TIMEOUT_SECONDS = 10L;
        private static final long FORCE_SHUTDOWN_SECONDS = 1L;
        private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledReconciler.class);
        private final DeploymentProjectionReconciler task;
        private final long intervalMs;
        private final ScheduledExecutorService executor;
        private ScheduledFuture<?> future;
        private boolean closed;

        public ScheduledReconciler(DeploymentProjectionReconciler task, Duration interval) {
            this.task = Objects.requireNonNull(task, "task");
            Duration value = Objects.requireNonNull(interval, "interval");
            long millis;
            try {
                millis = value.toMillis();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("interval must be a positive whole-millisecond duration", exception);
            }
            if (millis <= 0 || !value.equals(Duration.ofMillis(millis))) {
                throw new IllegalArgumentException("interval must be a positive whole-millisecond duration");
            }
            this.intervalMs = millis;
            this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "compileflow-projection-reconciler");
                thread.setDaemon(true);
                return thread;
            });
        }

        public synchronized void start() {
            if (closed) {
                throw new IllegalStateException("ScheduledReconciler is closed");
            }
            if (future != null) {
                return;
            }
            future = executor.scheduleWithFixedDelay(this::runOnce, 0L, intervalMs, TimeUnit.MILLISECONDS);
        }

        private void runOnce() {
            try {
                task.reconcile();
            } catch (Exception failure) {
                LOGGER.error("Unhandled routing reconciliation failure", failure);
            }
        }

        public synchronized void stop() {
            if (future == null) {
                return;
            }
            future.cancel(false);
            future = null;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            stop();
            closed = true;
            executor.shutdown();
            boolean interrupted = false;
            try {
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS - FORCE_SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(FORCE_SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                        LOGGER.warn("Projection reconciliation task remains active after forced shutdown");
                    }
                }
            } catch (InterruptedException gracefulInterrupted) {
                interrupted = true;
                executor.shutdownNow();
                try {
                    if (!executor.awaitTermination(FORCE_SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                        LOGGER.warn("Projection reconciliation task remains active after interrupted shutdown");
                    }
                } catch (InterruptedException forceInterrupted) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
