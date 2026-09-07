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
package com.alibaba.compileflow.deploy.runtime.version;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.engine.core.lifecycle.OperationGate;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.InstalledVersionState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics.AliasConvergenceReason;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics.AttemptOutcome;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics.RuntimeInstallReason;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.routing.AliasVersionDemand;
import java.io.Serial;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciles node-local process runtimes with Alias and execution demand.
 *
 * <p>Concurrency invariant: a path that needs both an {@link OwnerState} monitor and
 * {@link #ownershipLock} acquires the OwnerState monitor first. No path may acquire an
 * OwnerState monitor while holding ownershipLock. Operations that need only one lock do not
 * manufacture a second lock scope.</p>
 *
 * @author yusu
 */
public final class VersionRuntimeManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionRuntimeManager.class);
    private static final int MAX_FAILURE_REASON_LENGTH = 160;
    private static final Duration SHUTDOWN_GRACE_PERIOD = Duration.ofSeconds(10);
    private static final Runnable NO_READY_ACTION = () -> {};
    private static final Comparator<ProcessRef.Version> VERSION_ORDER = Comparator
        .comparing(ProcessRef.Version::namespace)
        .thenComparing(ProcessRef.Version::code)
        .thenComparing(ProcessRef.Version::version);
    private final ProcessArtifactResolver resolver;
    private final ProcessArtifactRuntimeLoader loader;
    private final InstalledVersionState installedVersionState;
    private final DeploymentRuntimeMetrics metrics;
    private final Executor executor;
    private final ScheduledExecutorService retryScheduler;
    private final boolean ownsRetryScheduler;
    private final long failureBackoffMs;
    private final int maxInFlight;
    private final Semaphore inflightSemaphore;
    private final Map<String, OwnerState> ownerStates = new ConcurrentHashMap<>();
    private final Map<ProcessRef.Version, CompletableFuture<Void>> inflight = new ConcurrentHashMap<>();
    private final Map<ProcessRef.Version, FailureBackoffEntry> failures = new ConcurrentHashMap<>();
    private final Object ownershipLock = new Object();
    private final Map<ProcessRef.Version, Set<String>> ownersByVersion = new HashMap<>();
    private final Map<ProcessRef.Version, Set<ProcessRef.Version>> dependenciesByVersion = new HashMap<>();
    private final Map<ProcessRef.Version, Integer> dependencyReferenceCounts = new HashMap<>();
    private final Map<ProcessRef.Version, Integer> dependencyReservationCounts = new HashMap<>();
    private final Set<ProcessRef.Version> managedVersions = new LinkedHashSet<>();
    private final Set<ProcessRef.Version> pendingReleaseVersions = new LinkedHashSet<>();
    private final Set<ProcessRef.Version> retryScheduled = ConcurrentHashMap.newKeySet();
    private final Set<ProcessRef.Version> orphanReleaseScheduled = ConcurrentHashMap.newKeySet();
    private final Set<String> reconciliationScheduled = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingOwnerIds = ConcurrentHashMap.newKeySet();
    private final Set<ScheduledFuture<?>> scheduledTasks = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> pendingOwners = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drainingPending = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong installationLeaseSequence = new AtomicLong();
    private final Object closeLock = new Object();
    private final OperationGate lifecycleGate = new OperationGate("VersionRuntimeManager");

    public VersionRuntimeManager(ProcessArtifactResolver resolver, ProcessArtifactRuntimeLoader loader,
            Executor executor, ScheduledExecutorService retryScheduler, LocalRoutingState localRoutingState,
            Duration failureBackoff, int maxInFlight) {
        this(resolver, loader, executor, retryScheduler, false, localRoutingState, failureBackoff, maxInFlight,
                new DeploymentRuntimeMetrics());
    }

    public VersionRuntimeManager(ProcessArtifactResolver resolver, ProcessArtifactRuntimeLoader loader,
            Executor executor, ScheduledExecutorService retryScheduler, LocalRoutingState localRoutingState,
            Duration failureBackoff, int maxInFlight, DeploymentRuntimeMetrics metrics) {
        this(resolver, loader, executor, retryScheduler, false, localRoutingState, failureBackoff, maxInFlight, metrics);
    }

    private VersionRuntimeManager(ProcessArtifactResolver resolver, ProcessArtifactRuntimeLoader loader,
            Executor executor, ScheduledExecutorService retryScheduler, boolean ownsRetryScheduler,
            LocalRoutingState localRoutingState, Duration failureBackoff, int maxInFlight,
            DeploymentRuntimeMetrics metrics) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.retryScheduler = Objects.requireNonNull(retryScheduler, "retryScheduler");
        this.ownsRetryScheduler = ownsRetryScheduler;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(localRoutingState, "localRoutingState");
        this.installedVersionState = localRoutingState.getInstalledVersionState();
        this.failureBackoffMs = requirePositiveMonotonicMillis(failureBackoff, "failureBackoff");
        if (maxInFlight <= 0) {
            throw new IllegalArgumentException("maxInFlight must be positive");
        }
        this.maxInFlight = maxInFlight;
        this.inflightSemaphore = new Semaphore(maxInFlight);
    }

    public static VersionRuntimeManager withOwnedRetryScheduler(ProcessArtifactResolver resolver,
            ProcessArtifactRuntimeLoader loader, Executor executor, LocalRoutingState localRoutingState,
            Duration failureBackoff, int maxInFlight) {
        return withOwnedRetryScheduler(resolver, loader, executor, localRoutingState, failureBackoff, maxInFlight,
                new DeploymentRuntimeMetrics());
    }

    public static VersionRuntimeManager withOwnedRetryScheduler(ProcessArtifactResolver resolver,
            ProcessArtifactRuntimeLoader loader, Executor executor, LocalRoutingState localRoutingState,
            Duration failureBackoff, int maxInFlight, DeploymentRuntimeMetrics metrics) {
        return new VersionRuntimeManager(resolver, loader, executor, newRetryScheduler(), true, localRoutingState,
                failureBackoff, maxInFlight, metrics);
    }

    private static RuntimeInstallReason installFailureReason(Exception failure) {
        if (failure instanceof RuntimeLoadFailure runtimeLoadFailure) {
            return runtimeLoadFailureReason(runtimeLoadFailure.failure);
        }
        if (failure instanceof DeploymentException deploymentFailure) {
            return switch (deploymentFailure.getErrorCode()) {
                case ARTIFACT_IDENTITY_MISMATCH -> RuntimeInstallReason.ARTIFACT_IDENTITY;
                case ARTIFACT_DIGEST_MISMATCH -> RuntimeInstallReason.ARTIFACT_INTEGRITY;
                default -> RuntimeInstallReason.ARTIFACT_RESOLUTION;
            };
        }
        return RuntimeInstallReason.ARTIFACT_RESOLUTION;
    }

    private static RuntimeInstallReason runtimeLoadFailureReason(RuntimeException failure) {
        if (failure instanceof DeploymentException deploymentFailure
                && deploymentFailure.getErrorCode() == DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH) {
            return RuntimeInstallReason.ARTIFACT_INTEGRITY;
        }
        return RuntimeInstallReason.RUNTIME_LOAD;
    }

    private static String boundedFailureReason(Throwable failure) {
        Throwable cause = Objects.requireNonNull(failure, "failure");
        String type = cause.getClass().getName();
        String reason =
                cause instanceof DeploymentException deploymentFailure ? type + "[" + deploymentFailure.getErrorCode()
                + "]" : type;
        return ProcessText.truncateCodePoints(reason, MAX_FAILURE_REASON_LENGTH);
    }

    static long requirePositiveMonotonicMillis(Duration duration, String name) {
        Duration value = Objects.requireNonNull(duration, name);
        long millis;
        long nanos;
        try {
            millis = value.toMillis();
            nanos = value.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name
                    + " must be a positive whole-millisecond duration representable as nanoseconds", exception);
        }
        if (millis <= 0 || nanos <= 0 || !value.equals(Duration.ofMillis(millis))) {
            throw new IllegalArgumentException(
                    name + " must be a positive whole-millisecond duration representable as nanoseconds");
        }
        return millis;
    }

    static long addSaturated(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    private static boolean isDemanded(OwnerState state, ProcessRef.Version key) {
        synchronized (state) {
            return state.demanded.contains(key);
        }
    }

    private static void forwardCompletion(CompletableFuture<Void> source, CompletableFuture<Void> target) {
        if (target.isDone()) {
            return;
        }
        source.whenComplete((ignored, failure) -> {
            if (failure == null) {
                target.complete(null);
            } else {
                target.completeExceptionally(failure);
            }
        });
    }

    private static ScheduledExecutorService newRetryScheduler() {
        ThreadFactory threadFactory =
                runnable -> {
            Thread thread = new Thread(runnable, "compileflow-deploy-runtime-retry");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    public CompletableFuture<Void> ensureInstalled(AliasVersionDemand decision) {
        return ensureInstalled(decision, NO_READY_ACTION);
    }

    public CompletableFuture<Void> ensureInstalled(AliasVersionDemand decision, Runnable readyAction) {
        Objects.requireNonNull(decision, "decision");
        String ownerId = RuntimeOwnerIds.alias(decision.getNamespace(), decision.getCode(), decision.getAlias());
        return ensureInstalled(ownerId, decision.getDemandedVersions(),
                Objects.requireNonNull(readyAction, "readyAction"), DemandKind.ALIAS);
    }

    /**
     * Materializes and retains one exact runtime for an execution attempt.
     *
     * <p>This path is independent of current Alias demand. It allows a durable worker to execute a
     * previously selected immutable version after the Alias has moved or on a node that did not
     * host the version when the request was accepted.
     *
     * @param root exact root version selected for execution
     * @return future lease completed when the exact runtime is locally ready
     */
    public CompletableFuture<VersionRuntimeLease> acquireInstallation(ProcessRef.Version root) {
        ProcessRef.Version requested = Objects.requireNonNull(root, "root");
        String ownerId = RuntimeOwnerIds.installationLease(installationLeaseSequence.incrementAndGet());
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> releaseInstallation(ownerId, released);
        CompletableFuture<VersionRuntimeLease> acquired = new CompletableFuture<>();
        acquired.whenComplete((lease, failure) -> {
            if (acquired.isCancelled()) {
                release.run();
            }
        });

        CompletableFuture<Void> ready;
        try {
            ready = ensureInstalled(ownerId, Set.of(requested), NO_READY_ACTION, DemandKind.EXECUTION);
        } catch (RuntimeException | Error failure) {
            release.run();
            acquired.completeExceptionally(failure);
            return acquired;
        }
        ready.whenComplete((ignored, failure) -> {
            if (failure != null) {
                release.run();
                acquired.completeExceptionally(failure);
                return;
            }
            VersionRuntimeLease lease = release::run;
            if (!acquired.complete(lease)) {
                lease.close();
            }
        });
        return acquired;
    }

    private CompletableFuture<Void> ensureInstalled(String ownerId, Set<ProcessRef.Version> demandedVersions,
            Runnable readyAction, DemandKind demandKind) {
        lifecycleGate.enter();
        try {
            ensureOpen();
            AtomicReference<OwnerState> updated = new AtomicReference<>();
            AtomicReference<CompletableFuture<Void>> convergence = new AtomicReference<>();
            ownerStates.compute(ownerId, (ignored, existing) -> {
                OwnerState state = existing == null ? new OwnerState(ownerId, demandKind) : existing;
                if (state.demandKind != demandKind) {
                    throw new IllegalStateException("Runtime owner changed demand kind: " + ownerId);
                }
                synchronized (state) {
                    CompletableFuture<Void> previousCompletion = state.completion;
                    CompletableFuture<Void> nextCompletion = new CompletableFuture<>();
                    state.generation++;
                    state.demanded = Set.copyOf(demandedVersions);
                    state.readyAction = readyAction;
                    state.completion = nextCompletion;
                    state.attemptFailed = false;
                    forwardCompletion(nextCompletion, previousCompletion);
                    convergence.set(nextCompletion);
                }
                updated.set(state);
                return state;
            });
            OwnerState state = updated.get();
            reconcile(state);
            return convergence.get();
        } finally {
            lifecycleGate.exit();
        }
    }

    private void releaseInstallation(String ownerId, AtomicBoolean released) {
        if (!released.compareAndSet(false, true) || closed.get()) {
            return;
        }
        try {
            ensureInstalled(ownerId, Set.of(), NO_READY_ACTION, DemandKind.EXECUTION);
        } catch (RuntimeException failure) {
            if (!closed.get()) {
                LOGGER.warn("Failed to release execution runtime demand: owner={}", ownerId, failure);
            }
        }
    }

    public VersionRuntimeManagerSnapshot snapshot() {
        long nowNanos = System.nanoTime();
        List<ProcessRef.Version> inflightVersions = inflight.keySet().stream().sorted(VERSION_ORDER).toList();
        List<VersionRuntimeManagerSnapshot.BackedOffVersion> backedOffVersions = failures
            .entrySet()
            .stream()
            .filter(entry -> isDemandedAnywhere(entry.getKey()))
            .map(entry -> new VersionRuntimeManagerSnapshot.BackedOffVersion(entry.getKey(), entry.getValue().reason,
                    entry.getValue().blockedUntilEpochMillis, entry.getValue().remainingMillis(nowNanos)))
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .toList();
        Set<ProcessRef.Version> demandedVersionSet = new TreeSet<>(VERSION_ORDER);
        for (OwnerState state : ownerStates.values()) {
            synchronized (state) {
                demandedVersionSet.addAll(state.demanded);
            }
        }
        List<ProcessRef.Version> demandedVersions = List.copyOf(demandedVersionSet);
        List<ProcessRef.Version> pendingReleases;
        int retainedRuntimeCount;
        synchronized (ownershipLock) {
            retainedRuntimeCount = managedVersions.size();
            pendingReleases = pendingReleaseVersions.stream().sorted(VERSION_ORDER).toList();
        }
        return new VersionRuntimeManagerSnapshot(inflight.size(), maxInFlight, inflightSemaphore.availablePermits(),
                failureBackoffMs, retainedRuntimeCount, inflightVersions, demandedVersions, pendingReleases,
                backedOffVersions,
                installedVersionState
                    .snapshot()
                    .stream()
                    .map(process -> new VersionRuntimeManagerSnapshot.InstalledProcessSnapshot(process.namespace(),
                            process.code(), List.copyOf(process.versions())))
                    .toList());
    }

    private void reconcile(OwnerState state) {
        reconcile(state, null);
    }

    private void reconcile(OwnerState state, ProcessRef.Version excludedKey) {
        if (closed.get()) {
            return;
        }
        List<ProcessRef.Version> demanded;
        synchronized (state) {
            pruneInactiveOwnedVersions(state);
            if (state.attemptFailed) {
                return;
            }
            demanded = state.demanded.stream().sorted(VERSION_ORDER).toList();
        }
        for (ProcessRef.Version key : demanded) {
            if (acquireInstalledOwnership(state, key)) {
                continue;
            }
            if (!key.equals(excludedKey)) {
                scheduleInstall(state, key);
            }
        }
        pruneIfReady(state);
    }

    private boolean acquireInstalledOwnership(OwnerState state, ProcessRef.Version key) {
        synchronized (state) {
            if (!state.demanded.contains(key)) {
                return false;
            }
            if (state.ownedVersions.contains(key)) {
                return true;
            }
            synchronized (ownershipLock) {
                // Ownership alone does not establish verified publication or graph readiness.
                if (!dependenciesByVersion.containsKey(key)) {
                    return false;
                }
                ownersByVersion
                    .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                    .add(state.ownerId);
                pendingReleaseVersions.remove(key);
                state.ownedVersions.add(key);
                return true;
            }
        }
    }

    private void scheduleInstall(OwnerState state, ProcessRef.Version key) {
        FailureBackoffEntry blocked = failures.get(key);
        long nowNanos = System.nanoTime();
        if (blocked != null && blocked.isBlocked(nowNanos)) {
            LOGGER.debug("Installation retry is delayed: key={} reason={} blockedUntil={}", key, blocked.reason,
                    blocked.blockedUntilEpochMillis);
            return;
        }
        if (blocked != null) {
            failures.remove(key, blocked);
        }

        AtomicReference<CompletableFuture<Void>> created = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> active = new AtomicReference<>();
        AtomicBoolean capacityDenied = new AtomicBoolean(false);
        inflight.compute(key, (ignored, existing) -> {
            if (existing != null) {
                active.set(existing);
                return existing;
            }
            if (!installationNeeded(state, key)) {
                return null;
            }
            if (!inflightSemaphore.tryAcquire()) {
                capacityDenied.set(true);
                return null;
            }
            CompletableFuture<Void> future = new CompletableFuture<>();
            created.set(future);
            active.set(future);
            return future;
        });
        CompletableFuture<Void> future = active.get();
        if (future == null) {
            if (capacityDenied.get()) {
                markPending(state);
            } else if (acquireInstalledOwnership(state, key)) {
                pruneIfReady(state);
            }
            return;
        }
        attachInstallationWaiter(state, key, future);
        if (created.get() == null) {
            return;
        }

        future.whenComplete((ignored, failure) -> {
            if (inflight.remove(key, future)) {
                inflightSemaphore.release();
            }
            if (failure == null) {
                LOGGER.info("Runtime installation completed: key={}", key);
            } else {
                LOGGER.error("Runtime installation failed: key={}", key, failure);
            }
            drainPending();
        });

        try {
            executor.execute(() -> runInstallation(key, future));
        } catch (RejectedExecutionException failure) {
            metrics.recordRuntimeInstall(AttemptOutcome.FAILURE, RuntimeInstallReason.EXECUTOR_REJECTED);
            recordFailure(key, failure);
            future.completeExceptionally(failure);
        }
    }

    private void attachInstallationWaiter(OwnerState state, ProcessRef.Version key, CompletableFuture<Void> future) {
        synchronized (state) {
            if (!state.demanded.contains(key) || state.ownedVersions.contains(key) || !state.waiting.add(key)) {
                return;
            }
        }
        future.whenComplete((ignored, failure) -> {
            synchronized (state) {
                state.waiting.remove(key);
            }
            if (closed.get()) {
                releaseUnownedRuntime(key);
                return;
            }
            if (failure == null) {
                boolean acquired = acquireInstalledOwnership(state, key);
                if (!acquired && !isDemandedAnywhere(key)) {
                    releaseUnownedRuntime(key);
                }
            } else {
                markAttemptFailed(state, key, failure);
            }
            reconcile(state, failure == null ? null : key);
        });
    }

    private boolean installationNeeded(OwnerState state, ProcessRef.Version key) {
        synchronized (state) {
            return state.demanded.contains(key) && !state.ownedVersions.contains(key);
        }
    }

    private void runInstallation(ProcessRef.Version key, CompletableFuture<Void> future) {
        if (!lifecycleGate.tryEnter()) {
            future.completeExceptionally(new IllegalStateException("VersionRuntimeManager is closed"));
            return;
        }
        try {
            boolean installed;
            if (closed.get() || !isDemandedAnywhere(key)) {
                installed = false;
            } else {
                installed = install(key);
            }
            if (installed) {
                metrics.recordRuntimeInstall(AttemptOutcome.SUCCESS, RuntimeInstallReason.NONE);
            }
            future.complete(null);
        } catch (Exception failure) {
            recordFailure(key, failure);
            future.completeExceptionally(failure);
        } catch (Error failure) {
            future.completeExceptionally(failure);
            throw failure;
        } finally {
            lifecycleGate.exit();
        }
    }

    private void releaseUnownedRuntime(ProcessRef.Version key) {
        releaseUnownedRuntime(key, false);
    }

    private void retryUnownedRuntime(ProcessRef.Version key) {
        releaseUnownedRuntime(key, true);
    }

    private void releaseUnownedRuntime(ProcessRef.Version key, boolean retry) {
        boolean releaseFailed = false;
        List<ProcessRef.Version> releasedDependencies = List.of();
        synchronized (ownershipLock) {
            Set<String> owners = ownersByVersion.get(key);
            if ((owners != null && !owners.isEmpty()) || dependencyReferenceCounts.getOrDefault(key, 0) > 0
                    || dependencyReservationCounts.getOrDefault(key, 0) > 0
                    || (!retry && pendingReleaseVersions.contains(key)) || !managedVersions.contains(key)) {
                return;
            }
            if (retry) {
                pendingReleaseVersions.remove(key);
            }
            ProcessArtifactRuntimeLoader.ReleaseResult result = releaseRuntime(key);
            if (result != ProcessArtifactRuntimeLoader.ReleaseResult.FAILED) {
                managedVersions.remove(key);
                pendingReleaseVersions.remove(key);
                if (result == ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED) {
                    installedVersionState.markUninstalled(key.namespace(), key.code(), key.version());
                }
                releasedDependencies = releaseDependencyEdges(key);
                LOGGER.info("Released superseded unowned runtime: key={} result={}", key, result);
            } else {
                pendingReleaseVersions.add(key);
                LOGGER.warn("Unowned runtime release failed: key={} result={}", key, result);
                releaseFailed = true;
            }
        }
        if (releaseFailed) {
            scheduleOrphanRelease(key);
            return;
        }
        releasedDependencies.forEach(this::releaseUnownedRuntime);
    }

    private boolean install(ProcessRef.Version key) throws Exception {
        synchronized (ownershipLock) {
            if (dependenciesByVersion.containsKey(key)) {
                return false;
            }
        }
        List<ProcessRef.Version> installedThisAttempt = new ArrayList<>();
        try {
            installGraph(key, new LinkedHashSet<>(), installedThisAttempt);
        } catch (Exception failure) {
            metrics.recordRuntimeInstall(AttemptOutcome.FAILURE, installFailureReason(failure));
            for (int index = installedThisAttempt.size() - 1; index >= 0; index--) {
                releaseUnownedRuntime(installedThisAttempt.get(index));
            }
            if (failure instanceof RuntimeLoadFailure runtimeLoadFailure) {
                throw runtimeLoadFailure.failure;
            }
            throw failure;
        }
        if (closed.get() || !isDemandedAnywhere(key)) {
            return false;
        }
        failures.remove(key);
        return true;
    }

    private void installGraph(ProcessRef.Version key, Set<ProcessRef.Version> visiting,
            List<ProcessRef.Version> installedThisAttempt) throws Exception {
        if (!visiting.add(key)) {
            throw DeploymentException.fromRef(DeploymentErrorCode.INVALID_ARGUMENT,
                    "Published Process dependency cycle contains " + key, key);
        }
        try {
            synchronized (ownershipLock) {
                if (dependenciesByVersion.containsKey(key)) {
                    return;
                }
            }
            ProcessArtifact artifact = resolveArtifact(key);
            Set<ProcessRef.Version> dependencies = artifact
                .getCallBindings()
                .values()
                .stream()
                .map(binding -> binding.target())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            reserveDependencies(dependencies);
            boolean committed = false;
            try {
                for (ProcessRef.Version dependency : dependencies) {
                    installGraph(dependency, visiting, installedThisAttempt);
                }
                boolean newlyInstalled;
                synchronized (ownershipLock) {
                    newlyInstalled = !managedVersions.contains(key);
                }
                if (newlyInstalled) {
                    try {
                        loader.load(artifact);
                    } catch (RuntimeException failure) {
                        throw new RuntimeLoadFailure(failure);
                    }
                    synchronized (ownershipLock) {
                        managedVersions.add(key);
                        pendingReleaseVersions.remove(key);
                    }
                    installedThisAttempt.add(key);
                }
                try {
                    loader.prepare(key);
                } catch (RuntimeException failure) {
                    throw new RuntimeLoadFailure(failure);
                }
                synchronized (ownershipLock) {
                    if (!dependenciesByVersion.containsKey(key)) {
                        dependenciesByVersion.put(key, Set.copyOf(dependencies));
                        dependencies.forEach(dependency -> dependencyReferenceCounts.merge(dependency, 1, Integer::sum));
                    }
                    releaseDependencyReservations(dependencies);
                    committed = true;
                    installedVersionState.markInstalled(key.namespace(), key.code(), key.version());
                }
            } finally {
                if (!committed) {
                    synchronized (ownershipLock) {
                        releaseDependencyReservations(dependencies);
                    }
                    dependencies.forEach(this::releaseUnownedRuntime);
                }
            }
        } finally {
            visiting.remove(key);
        }
    }

    private void reserveDependencies(Set<ProcessRef.Version> dependencies) {
        synchronized (ownershipLock) {
            dependencies.forEach(dependency -> dependencyReservationCounts.merge(dependency, 1, Integer::sum));
        }
    }

    private void releaseDependencyReservations(Set<ProcessRef.Version> dependencies) {
        for (ProcessRef.Version dependency : dependencies) {
            Integer reservations = dependencyReservationCounts.get(dependency);
            if (reservations == null || reservations <= 0) {
                throw new IllegalStateException("Dependency reservation underflow: " + dependency);
            }
            int remaining = reservations - 1;
            if (remaining <= 0) {
                dependencyReservationCounts.remove(dependency);
            } else {
                dependencyReservationCounts.put(dependency, remaining);
            }
        }
    }

    private ProcessArtifact resolveArtifact(ProcessRef.Version key) throws Exception {
        ProcessArtifact artifact =
                Objects.requireNonNull(resolver.resolve(key), "Resolver returned null artifact: " + key);
        if (!key.equals(artifact.getRef())) {
            throw DeploymentException
                .builder(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH,
                        "Resolved artifact identity does not match the requested version")
                .namespace(key.namespace())
                .code(key.code())
                .version(key.version())
                .build();
        }
        return artifact;
    }

    private List<ProcessRef.Version> releaseDependencyEdges(ProcessRef.Version key) {
        Set<ProcessRef.Version> dependencies = dependenciesByVersion.remove(key);
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        for (ProcessRef.Version dependency : dependencies) {
            int remaining = dependencyReferenceCounts.getOrDefault(dependency, 0) - 1;
            if (remaining <= 0) {
                dependencyReferenceCounts.remove(dependency);
            } else {
                dependencyReferenceCounts.put(dependency, remaining);
            }
        }
        return List.copyOf(dependencies);
    }

    private void recordFailure(ProcessRef.Version key, Exception failure) {
        if (!isDemandedAnywhere(key)) {
            failures.remove(key);
            return;
        }
        String reason = boundedFailureReason(failure);
        long blockedUntilEpochMillis = addSaturated(System.currentTimeMillis(), failureBackoffMs);
        long blockedUntilNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(failureBackoffMs);
        failures.put(key, new FailureBackoffEntry(reason, blockedUntilEpochMillis, blockedUntilNanos));
        if (!retryScheduled.add(key)) {
            return;
        }
        try {
            scheduleTracked(() -> {
                retryScheduled.remove(key);
                List<OwnerState> waitingOwners =
                        ownerStates
                    .values()
                    .stream()
                    .filter(current -> isDemanded(current, key))
                    .toList();
                if (waitingOwners.isEmpty()) {
                    failures.remove(key);
                    return;
                }
                for (OwnerState current : waitingOwners) {
                    synchronized (current) {
                        current.attemptFailed = false;
                    }
                    reconcile(current);
                }
            });
        } catch (RejectedExecutionException schedulingFailure) {
            retryScheduled.remove(key);
            LOGGER.warn("Failed to schedule runtime installation retry: key={}", key, schedulingFailure);
        }
    }

    private void markAttemptFailed(OwnerState state, ProcessRef.Version key, Throwable failure) {
        CompletableFuture<Void> completion;
        synchronized (state) {
            if (!state.demanded.contains(key) || state.attemptFailed) {
                return;
            }
            state.attemptFailed = true;
            completion = state.completion;
        }
        if (state.demandKind == DemandKind.ALIAS) {
            metrics.recordAliasConvergence(AttemptOutcome.FAILURE, AliasConvergenceReason.INSTALLATION);
        }
        completion.completeExceptionally(failure);
    }

    private void pruneIfReady(OwnerState state) {
        ReadyAttempt attempt;
        synchronized (state) {
            if (state.attemptFailed || state.publicationInProgress) {
                return;
            }
            boolean ready = state.demanded.stream().allMatch(state.ownedVersions::contains);
            if (!ready) {
                return;
            }
            state.publicationInProgress = true;
            attempt = new ReadyAttempt(state.generation, Set.copyOf(state.demanded), state.readyAction, state.completion);
        }

        if (!lifecycleGate.tryEnter()) {
            synchronized (state) {
                state.publicationInProgress = false;
            }
            return;
        }
        try {
            if (closed.get()) {
                synchronized (state) {
                    state.publicationInProgress = false;
                }
                return;
            }
            publishReadyAttempt(state, attempt);
        } finally {
            lifecycleGate.exit();
        }
    }

    private void publishReadyAttempt(OwnerState state, ReadyAttempt attempt) {
        try {
            attempt.readyAction().run();
        } catch (RuntimeException failure) {
            failPublication(state, attempt, failure);
            return;
        } catch (Error failure) {
            failPublication(state, attempt, failure);
            throw failure;
        }
        CompletableFuture<Void> completion = null;
        boolean potentiallyIdle = false;
        boolean convergencePublished = false;
        boolean reconcileSupersedingGeneration = false;
        synchronized (state) {
            state.publicationInProgress = false;
            if (state.generation == attempt.generation() && state.readyAction == attempt.readyAction()
                    && state.completion == attempt.completion() && !state.attemptFailed) {
                state.localReady = new LinkedHashSet<>(attempt.demanded());
                state.readyAction = NO_READY_ACTION;
                convergencePublished = attempt.readyAction() != NO_READY_ACTION;
                pruneOwnedVersions(state,
                        state.ownedVersions
                            .stream()
                            .filter(key -> !state.localReady.contains(key))
                            .sorted(VERSION_ORDER)
                            .toList());
                completion = state.completion;
                potentiallyIdle = state.demanded.isEmpty() && state.ownedVersions.isEmpty();
            } else {
                reconcileSupersedingGeneration = true;
            }
        }
        if (completion != null) {
            if (convergencePublished && state.demandKind == DemandKind.ALIAS) {
                metrics.recordAliasConvergence(AttemptOutcome.SUCCESS, AliasConvergenceReason.NONE);
            }
            completion.complete(null);
        }
        if (potentiallyIdle) {
            removeIfIdle(state);
        }
        if (reconcileSupersedingGeneration) {
            reconcile(state);
        }
    }

    private void failPublication(OwnerState state, ReadyAttempt attempt, Throwable failure) {
        CompletableFuture<Void> completion = null;
        boolean reconcileSupersedingGeneration = false;
        synchronized (state) {
            state.publicationInProgress = false;
            if (state.generation == attempt.generation() && state.readyAction == attempt.readyAction()
                    && state.completion == attempt.completion()) {
                state.attemptFailed = true;
                completion = state.completion;
                pruneInactiveOwnedVersions(state);
            } else {
                reconcileSupersedingGeneration = true;
            }
        }
        if (completion == null) {
            if (reconcileSupersedingGeneration) {
                reconcile(state);
            }
            return;
        }
        completion.completeExceptionally(failure);
        if (state.demandKind == DemandKind.ALIAS) {
            metrics.recordAliasConvergence(AttemptOutcome.FAILURE, AliasConvergenceReason.PUBLICATION);
        }
        LOGGER.error("Local-ready publication failed; retained the previous ownership: owner={} reason={}",
                state.ownerId, boundedFailureReason(failure), failure);
        scheduleReconciliation(state);
    }

    private void pruneInactiveOwnedVersions(OwnerState state) {
        List<ProcessRef.Version> inactive = state.ownedVersions
            .stream()
            .filter(key -> !state.localReady.contains(key))
            .filter(key -> state.attemptFailed || !state.demanded.contains(key))
            .sorted(VERSION_ORDER)
            .toList();
        pruneOwnedVersions(state, inactive);
    }

    private void pruneOwnedVersions(OwnerState state, List<ProcessRef.Version> versions) {
        for (ProcessRef.Version key : versions) {
            if (releaseOwnership(state, key)) {
                LOGGER.info("Runtime ownership released: key={} owner={}", key, state.ownerId);
            } else if (closed.get()) {
                LOGGER.warn("Runtime ownership release failed during close: key={} owner={}", key, state.ownerId);
            } else {
                LOGGER.warn("Runtime ownership release failed and will be retried: key={} owner={}", key, state.ownerId);
                scheduleReconciliation(state);
            }
        }
    }

    private boolean releaseOwnership(OwnerState state, ProcessRef.Version key) {
        List<ProcessRef.Version> releasedDependencies = List.of();
        synchronized (ownershipLock) {
            Set<String> owners = ownersByVersion.get(key);
            if (owners == null || !owners.contains(state.ownerId)) {
                state.ownedVersions.remove(key);
                return true;
            }
            if (owners.size() > 1 || dependencyReferenceCounts.getOrDefault(key, 0) > 0
                    || dependencyReservationCounts.getOrDefault(key, 0) > 0 || !managedVersions.contains(key)) {
                owners.remove(state.ownerId);
                if (owners.isEmpty()) {
                    ownersByVersion.remove(key);
                }
                pendingReleaseVersions.remove(key);
                state.ownedVersions.remove(key);
                return true;
            }

            ProcessArtifactRuntimeLoader.ReleaseResult result = releaseRuntime(key);
            if (result == ProcessArtifactRuntimeLoader.ReleaseResult.FAILED) {
                pendingReleaseVersions.add(key);
                return false;
            }
            ownersByVersion.remove(key);
            managedVersions.remove(key);
            pendingReleaseVersions.remove(key);
            state.ownedVersions.remove(key);
            if (result == ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED) {
                installedVersionState.markUninstalled(key.namespace(), key.code(), key.version());
            }
            releasedDependencies = releaseDependencyEdges(key);
        }
        releasedDependencies.forEach(this::releaseUnownedRuntime);
        return true;
    }

    private ProcessArtifactRuntimeLoader.ReleaseResult releaseRuntime(ProcessRef.Version key) {
        try {
            ProcessArtifactRuntimeLoader.ReleaseResult result =
                    loader.release(key.namespace(), key.code(), key.version());
            return result == null ? ProcessArtifactRuntimeLoader.ReleaseResult.FAILED : result;
        } catch (RuntimeException failure) {
            LOGGER.warn("Runtime ownership release raised an exception: key={}", key, failure);
            return ProcessArtifactRuntimeLoader.ReleaseResult.FAILED;
        }
    }

    private void removeIfIdle(OwnerState state) {
        ownerStates.computeIfPresent(state.ownerId, (ownerId, current) -> {
            if (current != state) {
                return current;
            }
            synchronized (current) {
                return current.demanded.isEmpty() && current.ownedVersions.isEmpty() && current.waiting.isEmpty()
                        ? null
                        : current;
            }
        });
    }

    private void scheduleReconciliation(OwnerState state) {
        String ownerId = state.ownerId;
        if (closed.get() || !reconciliationScheduled.add(ownerId)) {
            return;
        }
        try {
            scheduleTracked(() -> {
                reconciliationScheduled.remove(ownerId);
                OwnerState current = ownerStates.get(ownerId);
                if (current != null) {
                    synchronized (current) {
                        current.attemptFailed = false;
                    }
                    reconcile(current);
                }
            });
        } catch (RejectedExecutionException failure) {
            reconciliationScheduled.remove(ownerId);
            LOGGER.warn("Failed to schedule runtime reconciliation: owner={}", ownerId, failure);
        }
    }

    private void scheduleOrphanRelease(ProcessRef.Version key) {
        if (closed.get() || !orphanReleaseScheduled.add(key)) {
            return;
        }
        try {
            scheduleTracked(() -> {
                orphanReleaseScheduled.remove(key);
                retryUnownedRuntime(key);
            });
        } catch (RejectedExecutionException failure) {
            orphanReleaseScheduled.remove(key);
            LOGGER.warn("Failed to schedule unowned runtime release retry: key={}", key, failure);
        }
    }

    private void scheduleTracked(Runnable action) {
        TrackedScheduledTask task = new TrackedScheduledTask(action);
        ScheduledFuture<?> future = retryScheduler.schedule(task, failureBackoffMs, TimeUnit.MILLISECONDS);
        task.bind(future);
    }

    private void markPending(OwnerState state) {
        if (pendingOwnerIds.add(state.ownerId)) {
            pendingOwners.add(state.ownerId);
        }
    }

    private void drainPending() {
        while (true) {
            if (!drainingPending.compareAndSet(false, true)) {
                return;
            }
            try {
                while (!closed.get() && inflightSemaphore.availablePermits() > 0) {
                    String ownerId = pendingOwners.poll();
                    if (ownerId == null) {
                        break;
                    }
                    pendingOwnerIds.remove(ownerId);
                    OwnerState state = ownerStates.get(ownerId);
                    if (state != null) {
                        reconcile(state);
                    }
                }
            } finally {
                drainingPending.set(false);
            }
            if (closed.get() || inflightSemaphore.availablePermits() == 0 || pendingOwners.isEmpty()) {
                return;
            }
        }
    }

    private boolean isDemandedAnywhere(ProcessRef.Version key) {
        return ownerStates
            .values()
            .stream()
            .anyMatch(state -> isDemanded(state, key));
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("VersionRuntimeManager is closed");
        }
    }

    @Override
    public void close() {
        synchronized (closeLock) {
            if (lifecycleGate.isEnteredByCurrentThread()) {
                throw new IllegalStateException(
                        "VersionRuntimeManager cannot be closed from an active lifecycle operation");
            }
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            OperationGate.DrainResult drain = lifecycleGate.beginCloseAndAwaitDrained(SHUTDOWN_GRACE_PERIOD);
            try {
                closeState();
                if (drain == OperationGate.DrainResult.DRAINED) {
                    releaseOwnedVersionsOnClose();
                } else {
                    LOGGER.warn("VersionRuntimeManager did not drain before shutdown cleanup; "
                            + "result={}, active={}, retainedRuntimes={}", drain, lifecycleGate.activeOperations(),
                            managedRuntimeCount());
                }
            } finally {
                if (ownsRetryScheduler) {
                    retryScheduler.shutdownNow();
                }
                lifecycleGate.finishClose();
            }
        }
    }

    private void closeState() {
        retryScheduled.clear();
        orphanReleaseScheduled.clear();
        reconciliationScheduled.clear();
        scheduledTasks.forEach(future -> future.cancel(false));
        scheduledTasks.clear();
        pendingOwnerIds.clear();
        pendingOwners.clear();
        IllegalStateException failure = new IllegalStateException("VersionRuntimeManager is closed");
        for (OwnerState state : ownerStates.values()) {
            synchronized (state) {
                boolean completionFailed = state.completion.completeExceptionally(failure);
                if (state.demandKind == DemandKind.ALIAS && completionFailed) {
                    metrics.recordAliasConvergence(AttemptOutcome.FAILURE, AliasConvergenceReason.SHUTDOWN);
                }
            }
        }
    }

    private int managedRuntimeCount() {
        synchronized (ownershipLock) {
            return managedVersions.size();
        }
    }

    private void releaseOwnedVersionsOnClose() {
        List<OwnerState> states =
                ownerStates.values().stream().sorted(Comparator.comparing(state -> state.ownerId)).toList();
        for (OwnerState state : states) {
            synchronized (state) {
                pruneOwnedVersions(state, state.ownedVersions.stream().sorted(VERSION_ORDER).toList());
            }
        }
        List<ProcessRef.Version> unowned;
        synchronized (ownershipLock) {
            unowned = managedVersions
                .stream()
                .filter(key -> {
                    Set<String> owners = ownersByVersion.get(key);
                    return owners == null || owners.isEmpty();
                })
                .sorted(VERSION_ORDER)
                .toList();
        }
        unowned.forEach(this::releaseUnownedRuntime);
    }

    private enum DemandKind {
        ALIAS,
        EXECUTION
    }

    private static final class OwnerState {
        private final String ownerId;
        private final DemandKind demandKind;
        private final Set<ProcessRef.Version> ownedVersions = new LinkedHashSet<>();
        private final Set<ProcessRef.Version> waiting = new LinkedHashSet<>();
        private long generation;
        private Set<ProcessRef.Version> demanded = new LinkedHashSet<>();
        private Set<ProcessRef.Version> localReady = new LinkedHashSet<>();
        private Runnable readyAction = NO_READY_ACTION;
        private CompletableFuture<Void> completion = CompletableFuture.completedFuture(null);
        private boolean attemptFailed;
        private boolean publicationInProgress;

        private OwnerState(String ownerId, DemandKind demandKind) {
            this.ownerId = ownerId;
            this.demandKind = demandKind;
        }
    }

    private record ReadyAttempt(long generation, Set<ProcessRef.Version> demanded, Runnable readyAction,
            CompletableFuture<Void> completion) {}

    private static final class RuntimeLoadFailure extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;
        private final RuntimeException failure;

        private RuntimeLoadFailure(RuntimeException failure) {
            super(failure);
            this.failure = failure;
        }
    }

    private final class TrackedScheduledTask implements Runnable {
        private final Runnable action;
        private ScheduledFuture<?> future;
        private boolean completed;

        private TrackedScheduledTask(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        private synchronized void bind(ScheduledFuture<?> scheduledFuture) {
            future = Objects.requireNonNull(scheduledFuture, "scheduledFuture");
            if (completed) {
                return;
            }
            scheduledTasks.add(scheduledFuture);
            if (closed.get() && scheduledTasks.remove(scheduledFuture)) {
                scheduledFuture.cancel(false);
            }
        }

        @Override
        public void run() {
            if (!lifecycleGate.tryEnter()) {
                removeTrackedFuture();
                return;
            }
            try {
                if (!closed.get()) {
                    action.run();
                }
            } finally {
                lifecycleGate.exit();
                removeTrackedFuture();
            }
        }

        private synchronized void removeTrackedFuture() {
            completed = true;
            ScheduledFuture<?> scheduledFuture = future;
            if (scheduledFuture != null) {
                scheduledTasks.remove(scheduledFuture);
            }
        }
    }
}
