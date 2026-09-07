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
package com.alibaba.compileflow.workbench.server.execution;

import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeLease;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Polls, leases, recovers, and executes persisted whole-process invocations.
 *
 * @author yusu
 */
@Service
class AsyncInvocationWorker
        implements SmartLifecycle, ApplicationContextAware, ApplicationListener<ContextClosedEvent> {
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_DEAD_LETTER = "dead_letter";
    static final int LIFECYCLE_PHASE = DEFAULT_PHASE - 1500;
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncInvocationWorker.class);
    private static final int MAX_ERROR_CODE_LENGTH = 128;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4096;
    private static final int RECOVERY_SLICE_SIZE = 100;
    private static final String LEASE_EXPIRED_ERROR = "Async invocation lease expired";
    private static final String LEASE_EXPIRED_ERROR_CODE = "LEASE_EXPIRED";
    private static final String RUNTIME_LOAD_ERROR = "Async invocation runtime could not be loaded";
    private static final String RUNTIME_LOAD_ERROR_CODE = "RUNTIME_LOAD_FAILED";
    private static final String UNEXPECTED_EXECUTION_ERROR = "Async invocation failed unexpectedly";
    private static final String UNEXPECTED_EXECUTION_ERROR_CODE = "ASYNC_INVOCATION_FAILED";
    private static final String INVALID_PAYLOAD_ERROR_CODE = "INVALID_ASYNC_INVOCATION_PAYLOAD";
    private static final Sort DISPATCH_ORDER =
            Sort.by(Sort.Order.asc("availableAt"), Sort.Order.asc("createdAt"), Sort.Order.asc("invocationId"));
    private static final Sort RECOVERY_ORDER =
            Sort.by(Sort.Order.asc("leaseUntil"), Sort.Order.asc("createdAt"), Sort.Order.asc("invocationId"));
    private final AsyncInvocationRepository repository;
    private final AsyncInvocationStore store;
    private final PublishedProcessExecutionService executionService;
    private final VersionRuntimeManager versionRuntimeManager;
    private final AsyncInvocationPayloadCodec payloadCodec;
    private volatile Executor executor;
    private final boolean ownsExecutor;
    private final int concurrency;
    private final Semaphore executionSlots;
    private final long leaseDurationMs;
    private final long shutdownGracePeriodMs;
    private final String workerId;
    private final ConcurrentMap<String, String> localRunningInvocations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, InvocationTask> dispatchedInvocations = new ConcurrentHashMap<>();
    private final AtomicReference<Executor> admissionExecutor = new AtomicReference<>();
    private final ThreadLocal<Boolean> workerInvocation = new ThreadLocal<>();
    private volatile ApplicationContext applicationContext;
    private volatile boolean acceptingWork;
    private volatile boolean running;
    private boolean starting;
    private boolean stopping;
    private boolean destroyed;

    @Autowired
    public AsyncInvocationWorker(AsyncInvocationRepository repository, AsyncInvocationStore store,
            PublishedProcessExecutionService executionService, VersionRuntimeManager versionRuntimeManager,
            CompileFlowWorkbenchServerProperties properties, LifecycleProperties lifecycleProperties) {
        this(repository, store, executionService, versionRuntimeManager, null, true,
                properties.getAsyncInvocation().getConcurrency(), properties.getAsyncInvocation().getLeaseDuration(),
                lifecycleProperties.getTimeoutPerShutdownPhase());
    }

    AsyncInvocationWorker(AsyncInvocationRepository repository, AsyncInvocationStore store,
            PublishedProcessExecutionService executionService, VersionRuntimeManager versionRuntimeManager,
            Executor executor, int concurrency, Duration leaseDuration) {
        this(repository, store, executionService, versionRuntimeManager, executor, false, concurrency, leaseDuration,
                Duration.ZERO);
    }

    private AsyncInvocationWorker(AsyncInvocationRepository repository, AsyncInvocationStore store,
            PublishedProcessExecutionService executionService, VersionRuntimeManager versionRuntimeManager,
            Executor executor, boolean ownsExecutor, int concurrency, Duration leaseDuration,
            Duration shutdownGracePeriod) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.store = Objects.requireNonNull(store, "store");
        this.executionService = Objects.requireNonNull(executionService, "executionService");
        this.versionRuntimeManager = Objects.requireNonNull(versionRuntimeManager, "versionRuntimeManager");
        this.payloadCodec = new AsyncInvocationPayloadCodec();
        this.ownsExecutor = ownsExecutor;
        this.concurrency = requirePositive(concurrency, "concurrency");
        this.executionSlots = new Semaphore(this.concurrency);
        this.executor = ownsExecutor ? createExecutor(this.concurrency) : Objects.requireNonNull(executor, "executor");
        admissionExecutor.set(this.executor);
        this.leaseDurationMs = requirePositiveMillis(leaseDuration, "leaseDuration");
        this.shutdownGracePeriodMs = requireNonNegativeMillis(shutdownGracePeriod, "shutdownGracePeriod");
        this.workerId = "worker-" + UUID.randomUUID();
    }

    private static PublishedProcessExecutionService.AliasPin toAliasPin(String processCode,
            PersistedInvocationRouting routing) {
        String effectiveVersion = routing.effectiveVersion();
        String alias = routing.alias();
        if (effectiveVersion == null || alias == null) {
            return null;
        }
        try {
            ProcessAliasTarget target = routing.target();
            if (target == null) {
                throw new IllegalArgumentException("target is required for a persisted Alias pin");
            }
            Long routeRevision = routing.routeRevision();
            if (routeRevision == null) {
                throw new IllegalArgumentException("routeRevision must be a positive integer");
            }
            return new PublishedProcessExecutionService.AliasPin(ProcessRef.alias(routing.namespace(), processCode,
                            alias), effectiveVersion, routeRevision, target);
        } catch (IllegalArgumentException exception) {
            throw new AsyncInvocationPayloadException("routingJson", exception);
        }
    }

    private static ProcessRef.Version toExactProcessRef(String processCode, PersistedInvocationRouting routing) {
        String version = routing.exactVersion();
        if (version == null) {
            throw new AsyncInvocationPayloadException("routingJson",
                    new IllegalArgumentException("exact version is required"));
        }
        try {
            return ProcessRef.version(routing.namespace(), processCode, version);
        } catch (IllegalArgumentException failure) {
            throw new AsyncInvocationPayloadException("routingJson", failure);
        }
    }

    private static void logDiscardedTransition(String invocationId, int transitioned) {
        if (transitioned == 0) {
            LOGGER.warn("Discarded async invocation result after its ownership lease was lost: invocationId={}",
                    invocationId);
        }
    }

    private static String normalizeErrorCode(String errorCode) {
        return StringUtils.abbreviate(StringUtils.defaultIfBlank(errorCode, UNEXPECTED_EXECUTION_ERROR_CODE),
                MAX_ERROR_CODE_LENGTH);
    }

    private static String normalizeError(String errorMessage, String defaultMessage) {
        String normalized = StringUtils.defaultIfBlank(errorMessage, defaultMessage);
        return StringUtils.abbreviate(normalized, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
        return value;
    }

    private static long requirePositiveMillis(Duration value, String name) {
        long millis = requireWholeMilliseconds(value, name);
        if (millis <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return millis;
    }

    private static long requireNonNegativeMillis(Duration value, String name) {
        long millis = requireWholeMilliseconds(value, name);
        if (millis < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return millis;
    }

    private static long requireWholeMilliseconds(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " must be a whole-millisecond duration representable as a long",
                    exception);
        }
        if (!duration.equals(Duration.ofMillis(millis))) {
            throw new IllegalArgumentException(name + " must be a whole-millisecond duration representable as a long");
        }
        return millis;
    }

    private static ExecutorService createExecutor(int concurrency) {
        int threads = requirePositive(concurrency, "concurrency");
        // A task releases its slot before its executor thread returns; allow that bounded handoff.
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(threads),
                new AsyncInvocationThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void start() {
        Executor startupExecutor;
        synchronized (this) {
            awaitLifecycleTransition();
            if (running) {
                return;
            }
            if (destroyed) {
                throw new IllegalStateException("Async invocation worker is destroyed");
            }
            ensureExecutor();
            startupExecutor = executor;
            admissionExecutor.set(startupExecutor);
            starting = true;
            running = true;
            acceptingWork = true;
        }
        try {
            recoverExpiredRunningInvocations();
            dispatchQueuedInvocations();
            LOGGER.info("Async invocation worker started: workerId={}, leaseDurationMs={}, concurrency={}", workerId,
                    leaseDurationMs, concurrency);
        } catch (RuntimeException | Error failure) {
            admissionExecutor.compareAndSet(startupExecutor, null);
            synchronized (this) {
                acceptingWork = false;
                running = false;
            }
            if (ownsExecutor) {
                shutdownExecutor(startupExecutor);
            }
            throw failure;
        } finally {
            synchronized (this) {
                starting = false;
                notifyAll();
            }
        }
    }

    public boolean dispatchQueuedInvocations() {
        int capacity = executionSlots.availablePermits();
        if (admissionExecutor.get() == null || capacity == 0) {
            return false;
        }
        long now = store.currentTimeMillis();
        Page<AsyncInvocationEntity> invocations = repository.findByStatusAndAvailableAtLessThanEqual(STATUS_QUEUED, now,
                PageRequest.of(0, capacity, DISPATCH_ORDER));
        int accepted = 0;
        for (AsyncInvocationEntity invocation : invocations.getContent()) {
            if (dispatch(invocation.getInvocationId())) {
                accepted++;
            }
        }
        return accepted > 0 && invocations.getNumberOfElements() >= capacity;
    }

    public void renewLocalRunningLeases() {
        Map<String, String> snapshot = Map.copyOf(localRunningInvocations);
        Set<String> renewed = store.renewLeases(snapshot, STATUS_RUNNING, leaseDurationMs);
        if (!snapshot.keySet().containsAll(renewed)) {
            throw new IllegalStateException("Lease renewal returned an invocation outside the requested snapshot");
        }
        for (Map.Entry<String, String> invocation : snapshot.entrySet()) {
            if (!renewed.contains(invocation.getKey())) {
                localRunningInvocations.remove(invocation.getKey(), invocation.getValue());
            }
        }
    }

    public boolean recoverExpiredRunningInvocations() {
        long now = store.currentTimeMillis();
        Page<AsyncInvocationEntity> expiredInvocations = repository.findByStatusAndLeaseUntilLessThan(STATUS_RUNNING,
                now, PageRequest.of(0, RECOVERY_SLICE_SIZE, RECOVERY_ORDER));
        int recovered = 0;
        for (AsyncInvocationEntity invocation : expiredInvocations.getContent()) {
            recovered += store.recoverExpired(invocation, LEASE_EXPIRED_ERROR_CODE, LEASE_EXPIRED_ERROR);
        }
        return recovered > 0 && expiredInvocations.getNumberOfElements() >= RECOVERY_SLICE_SIZE;
    }

    @Override
    public void stop() {
        stop(false);
    }

    private void stop(boolean terminate) {
        Executor executorToStop;
        boolean wasRunning;
        synchronized (this) {
            if (starting && Boolean.TRUE.equals(workerInvocation.get())) {
                throw new IllegalStateException(
                        "Async invocation worker cannot be stopped from a startup-dispatched invocation");
            }
            awaitLifecycleTransition();
            if (destroyed) {
                return;
            }
            if (terminate) {
                destroyed = true;
            }
            wasRunning = running;
            acceptingWork = false;
            admissionExecutor.set(null);
            executorToStop = ownsExecutor ? executor : null;
            stopping = executorToStop != null;
        }
        if (executorToStop == null) {
            synchronized (this) {
                running = false;
            }
            if (wasRunning) {
                LOGGER.info("Async invocation worker stopped: workerId={}", workerId);
            }
            return;
        }
        try {
            shutdownExecutor(executorToStop);
        } finally {
            synchronized (this) {
                running = false;
                if (executor == executorToStop) {
                    executor = null;
                }
                stopping = false;
                notifyAll();
            }
        }
        if (wasRunning) {
            LOGGER.info("Async invocation worker stopped: workerId={}", workerId);
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    boolean isAcceptingWork() {
        return acceptingWork;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (event.getApplicationContext() == applicationContext) {
            acceptingWork = false;
            admissionExecutor.set(null);
        }
    }

    @Override
    public int getPhase() {
        return LIFECYCLE_PHASE;
    }

    @PreDestroy
    void destroy() {
        stop(true);
    }

    private void ensureExecutor() {
        if (!ownsExecutor) {
            return;
        }
        if (executor instanceof ExecutorService executorService && !executorService.isShutdown()) {
            return;
        }
        executor = createExecutor(concurrency);
    }

    private void shutdownExecutor(Executor executorToStop) {
        if (!(executorToStop instanceof ExecutorService executorService)) {
            return;
        }
        executorService.shutdown();
        if (Boolean.TRUE.equals(workerInvocation.get())) {
            return;
        }
        long totalNanos = TimeUnit.MILLISECONDS.toNanos(shutdownGracePeriodMs);
        long startedAt = System.nanoTime();
        long forceReserveNanos = Math.min(totalNanos / 5L, TimeUnit.SECONDS.toNanos(1L));
        long gracefulNanos = totalNanos - forceReserveNanos;
        try {
            if (!executorService.awaitTermination(gracefulNanos, TimeUnit.NANOSECONDS)) {
                List<Runnable> cancelledTasks = executorService.shutdownNow();
                releaseCancelledDispatches(cancelledTasks);
                LOGGER.warn("Forced async worker shutdown; gracePeriodMs={}, cancelledQueuedTasks={}",
                        TimeUnit.NANOSECONDS.toMillis(gracefulNanos), cancelledTasks.size());
                long remaining = remainingNanos(startedAt, totalNanos);
                if (!executorService.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    LOGGER.warn("Async worker tasks remain active after the {}ms shutdown budget", shutdownGracePeriodMs);
                }
            }
        } catch (InterruptedException interrupted) {
            releaseCancelledDispatches(executorService.shutdownNow());
            try {
                long remaining = remainingNanos(startedAt, totalNanos);
                if (remaining > 0L && !executorService.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    LOGGER.warn("Async worker tasks remain active after interrupted shutdown");
                }
            } catch (InterruptedException forceInterrupted) {
                LOGGER.warn("Interrupted again while waiting for forced async worker shutdown");
            }
            Thread.currentThread().interrupt();
        }
    }

    private void releaseCancelledDispatches(List<Runnable> cancelledTasks) {
        for (Runnable task : cancelledTasks) {
            if (task instanceof InvocationTask invocationTask) {
                invocationTask.release();
            }
        }
    }

    boolean dispatch(String invocationId) {
        if (!executionSlots.tryAcquire()) {
            return false;
        }
        InvocationTask task = new InvocationTask(invocationId);
        if (dispatchedInvocations.putIfAbsent(invocationId, task) != null) {
            executionSlots.release();
            return false;
        }
        try {
            Executor dispatchExecutor = admissionExecutor.get();
            if (dispatchExecutor == null) {
                task.release();
                return false;
            }
            dispatchExecutor.execute(task);
            return true;
        } catch (RejectedExecutionException failure) {
            task.release();
            return false;
        } catch (RuntimeException | Error failure) {
            task.release();
            throw failure;
        }
    }

    int localRunningCount() {
        return localRunningInvocations.size();
    }

    int dispatchedCount() {
        return dispatchedInvocations.size();
    }

    String workerId() {
        return workerId;
    }

    long leaseDurationMs() {
        return leaseDurationMs;
    }

    int concurrency() {
        return concurrency;
    }

    private void awaitLifecycleTransition() {
        boolean interrupted = false;
        while (starting || stopping) {
            try {
                wait();
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static long remainingNanos(long startedAt, long totalNanos) {
        long elapsed = System.nanoTime() - startedAt;
        return elapsed >= totalNanos ? 0L : totalNanos - elapsed;
    }

    private final class InvocationTask implements Runnable {
        private final String invocationId;

        private InvocationTask(String invocationId) {
            this.invocationId = invocationId;
        }

        private void release() {
            if (dispatchedInvocations.remove(invocationId, this)) {
                executionSlots.release();
            }
        }

        @Override
        public void run() {
            workerInvocation.set(Boolean.TRUE);
            try {
                executeOnce(invocationId);
            } catch (RuntimeException failure) {
                LOGGER.error("Async worker infrastructure failure: invocationId={}, failureType={}", invocationId,
                        failure.getClass().getName());
            } finally {
                workerInvocation.remove();
                release();
            }
        }
    }

    private void executeOnce(String invocationId) {
        Optional<AsyncInvocationStore.Claim> claimed = store.claim(invocationId, workerId, leaseDurationMs);
        if (claimed.isEmpty()) {
            return;
        }
        AsyncInvocationStore.Claim claim = claimed.get();
        String leaseToken = claim.leaseToken();
        localRunningInvocations.put(invocationId, leaseToken);
        try {
            AsyncInvocationEntity invocation = claim.invocation();
            try {
                Map<String, Object> params = payloadCodec.readMap("paramsJson", invocation.getParamsJson());
                PersistedInvocationRouting routing =
                        payloadCodec.readRouting("routingJson", invocation.getRoutingJson());
                routing = persistAliasSelection(invocation, leaseToken, routing);
                if (routing == null) {
                    return;
                }
                ProcessRef.Version exactRef = toExactProcessRef(invocation.getProcessCode(), routing);
                VersionRuntimeLease installation = acquireRuntimeInstallation(exactRef);
                try {
                    if (!renewBeforeExecution(invocation, leaseToken)) {
                        return;
                    }
                    PublishedProcessExecutionService.AliasPin aliasPin =
                            toAliasPin(invocation.getProcessCode(), routing);
                    ProcessExecutionOptions options =
                            ProcessExecutionOptions.builder().invocationId(invocation.getInvocationId()).build();
                    ProcessExecutionResponse response = aliasPin == null
                            ? executionService.executeInstalled(exactRef, installation, params, options)
                            : executionService.execute(aliasPin, params, options);
                    transitionResponse(claim, routing, response);
                } finally {
                    installation.close();
                }
            } catch (AsyncInvocationPayloadException failure) {
                transitionFailure(claim, INVALID_PAYLOAD_ERROR_CODE, failure.getMessage(), true);
            } catch (AsyncRuntimeLoadException failure) {
                LOGGER.warn("Async runtime loading failed: invocationId={}, processCode={}, failureType={}",
                        invocation.getInvocationId(), invocation.getProcessCode(),
                        failure.getCause().getClass().getName());
                transitionFailure(claim, RUNTIME_LOAD_ERROR_CODE, RUNTIME_LOAD_ERROR, false);
            } catch (Exception failure) {
                LOGGER.error("Unexpected async invocation failure: invocationId={}, processCode={}, failureType={}",
                        invocation.getInvocationId(), invocation.getProcessCode(), failure.getClass().getName(), failure);
                transitionFailure(claim, UNEXPECTED_EXECUTION_ERROR_CODE, UNEXPECTED_EXECUTION_ERROR, false);
            }
        } finally {
            localRunningInvocations.remove(invocationId, leaseToken);
        }
    }

    private PersistedInvocationRouting persistAliasSelection(AsyncInvocationEntity invocation, String leaseToken,
            PersistedInvocationRouting routing) {
        if (routing.effectiveVersion() != null || routing.alias() == null) {
            return routing;
        }
        PublishedProcessExecutionService.AliasPin pin = executionService.resolveAliasPin(invocation.getProcessCode(),
                routing.namespace(), routing.alias(), new AliasRoutingOptions(invocation.getInvocationId()));
        PersistedInvocationRouting pinnedRouting = routing.pin(pin);
        String routingJson = payloadCodec.write(pinnedRouting.asMap());
        int pinned = store.pinRouting(invocation.getInvocationId(), STATUS_RUNNING, leaseToken, routingJson);
        if (pinned == 0) {
            logDiscardedTransition(invocation.getInvocationId(), pinned);
            return null;
        }
        invocation.setRoutingJson(routingJson);
        return pinnedRouting;
    }

    private VersionRuntimeLease acquireRuntimeInstallation(ProcessRef.Version ref) {
        try {
            CompletableFuture<VersionRuntimeLease> installation = versionRuntimeManager.acquireInstallation(ref);
            try {
                return installation.get();
            } catch (InterruptedException interrupted) {
                if (!installation.cancel(false)) {
                    // Completion may win the cancellation race; release its unconsumed lease.
                    installation.thenAccept(VersionRuntimeLease::close);
                }
                throw interrupted;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AsyncRuntimeLoadException(interrupted);
        } catch (ExecutionException | CompletionException failure) {
            throw new AsyncRuntimeLoadException(failure.getCause() == null ? failure : failure.getCause());
        } catch (RuntimeException failure) {
            throw new AsyncRuntimeLoadException(failure);
        }
    }

    private boolean renewBeforeExecution(AsyncInvocationEntity invocation, String leaseToken) {
        Set<String> renewed =
                store.renewLeases(Map.of(invocation.getInvocationId(), leaseToken), STATUS_RUNNING, leaseDurationMs);
        if (!renewed.contains(invocation.getInvocationId())) {
            LOGGER.warn("Skipped async invocation after its ownership lease was lost during runtime loading: "
                    + "invocationId={}", invocation.getInvocationId());
            localRunningInvocations.remove(invocation.getInvocationId(), leaseToken);
            return false;
        }
        return true;
    }

    private void transitionResponse(AsyncInvocationStore.Claim claim, PersistedInvocationRouting requestedRouting,
            ProcessExecutionResponse response) {
        AsyncInvocationEntity invocation = claim.invocation();
        ProcessExecutionResponse publicResponse = payloadCodec.withRoutingSnapshot(requestedRouting, response);
        String routingJson =
                payloadCodec.routingJsonForExecutedVersion(invocation.getRoutingJson(), requestedRouting, publicResponse);
        String resultJson = payloadCodec.write(publicResponse);
        String traceId = publicResponse.traceId();
        long durationMs = publicResponse.durationMs();
        int transitioned;
        if (publicResponse.success()) {
            transitioned = store.complete(claim, routingJson, resultJson, traceId, durationMs);
        } else {
            transitioned = store.fail(claim, routingJson, resultJson, normalizeErrorCode(publicResponse.errorCode()),
                    normalizeError(publicResponse.error(), "Execution failed"), traceId, durationMs, false);
        }
        logDiscardedTransition(invocation.getInvocationId(), transitioned);
    }

    private void transitionFailure(AsyncInvocationStore.Claim claim, String errorCode, String errorMessage,
            boolean permanent) {
        AsyncInvocationEntity invocation = claim.invocation();
        int transitioned = store.fail(claim, invocation.getRoutingJson(), null, errorCode,
                normalizeError(errorMessage, permanent ? "Invalid async invocation payload" : "Execution failed"), null,
                null, permanent);
        logDiscardedTransition(invocation.getInvocationId(), transitioned);
    }

    private static final class AsyncRuntimeLoadException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private AsyncRuntimeLoadException(Throwable cause) {
            super(RUNTIME_LOAD_ERROR, Objects.requireNonNull(cause, "cause"));
        }
    }

    private static final class AsyncInvocationThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "compileflow-async-exec-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
