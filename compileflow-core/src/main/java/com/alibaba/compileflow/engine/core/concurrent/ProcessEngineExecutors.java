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
package com.alibaba.compileflow.engine.core.concurrent;

import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.config.ProcessObservabilityConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Engine-owned executors for runtime loading, preflight, action timeout
 * enforcement, parallel branches, and event delivery.
 *
 * @author yusu
 */
public final class ProcessEngineExecutors implements AutoCloseable {
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessEngineExecutors.class);
    private static final String THREAD_NAME_PATTERN = "compileflow-%s-%s-%d";
    private static final int PREFLIGHT_MAX_CONCURRENCY =
            Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors() / 8));
    private static final int PREFLIGHT_MAX_PENDING = 4;
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    private final String engineId;
    private final Duration actionTimeoutCancellationGracePeriod;
    private final Duration parallelCancellationGracePeriod;
    private final Duration shutdownTimeout;
    private final ExecutorService runtimeLoadExecutor;
    private final ExecutorService preflightExecutor;
    private final ExecutorService actionExecutor;
    private final ExecutorService parallelExecutor;
    private final ExecutorService eventExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ThreadLocal<Boolean> parallelExecution = new ThreadLocal<>();

    private ProcessEngineExecutors(String engineId, ProcessExecutorConfig config,
            ProcessObservabilityConfig observability, Duration shutdownTimeout) {
        this.engineId = engineId;
        this.actionTimeoutCancellationGracePeriod = config.getActionTimeoutCancellationGracePeriod();
        this.parallelCancellationGracePeriod = config.getParallelCancellationGracePeriod();
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");

        List<ExecutorService> created = new ArrayList<>(5);
        ExecutorService createdRuntimeLoad;
        ExecutorService createdPreflight;
        ExecutorService createdAction;
        ExecutorService createdParallel;
        ExecutorService createdEvent;
        try {
            createdRuntimeLoad = createRuntimeLoadExecutor(config);
            created.add(createdRuntimeLoad);
            createdPreflight = createPreflightExecutor();
            created.add(createdPreflight);
            createdAction = createActionExecutor(config);
            created.add(createdAction);
            createdParallel = createParallelExecutor();
            created.add(createdParallel);
            createdEvent = createEventExecutor(observability);
            created.add(createdEvent);
        } catch (RuntimeException | Error startupFailure) {
            rollbackCreatedExecutors(created, startupFailure);
            throw startupFailure;
        }
        this.runtimeLoadExecutor = createdRuntimeLoad;
        this.preflightExecutor = createdPreflight;
        this.actionExecutor = createdAction;
        this.parallelExecutor = createdParallel;
        this.eventExecutor = createdEvent;

        LOGGER.info("Engine executors initialized: engineId={}, runtime-load[maxConcurrency={}, maxPending={}], "
                + "preflight[maxConcurrency={}, maxPending={}], "
                + "action-timeout[maxConcurrency={}, maxPending={}, cancellationGrace={}], "
                + "parallel[perTask={}, cancellationGrace={}], "
                + "event-delivery[maxConcurrency={}, maxPending={}], shutdown[timeout={}]", engineId,
                config.getRuntimeLoadMaxConcurrency(), config.getRuntimeLoadMaxPending(), PREFLIGHT_MAX_CONCURRENCY,
                PREFLIGHT_MAX_PENDING, config.getActionTimeoutMaxConcurrency(), config.getActionTimeoutMaxPending(),
                actionTimeoutCancellationGracePeriod, JdkTaskExecutors.usesVirtualThreads() ? "virtual" : "platform",
                parallelCancellationGracePeriod, observability.getEventDeliveryMaxConcurrency(),
                observability.getEventDeliveryMaxPending(), shutdownTimeout);
    }

    public static ProcessEngineExecutors create(ProcessExecutorConfig config) {
        return create(generateEngineId(), config, ProcessObservabilityConfig.defaults());
    }

    public static ProcessEngineExecutors create(ProcessExecutorConfig config, ProcessObservabilityConfig observability) {
        return create(generateEngineId(), config, observability);
    }

    public static ProcessEngineExecutors create(String engineId, ProcessExecutorConfig config) {
        return create(engineId, config, ProcessObservabilityConfig.defaults());
    }

    public static ProcessEngineExecutors create(String engineId, ProcessExecutorConfig config,
            ProcessObservabilityConfig observability) {
        return create(engineId, config, observability, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    public static ProcessEngineExecutors create(ProcessExecutorConfig config, ProcessObservabilityConfig observability,
            Duration shutdownTimeout) {
        return create(generateEngineId(), config, observability, shutdownTimeout);
    }

    public static ProcessEngineExecutors create(String engineId, ProcessExecutorConfig config,
            ProcessObservabilityConfig observability, Duration shutdownTimeout) {
        if (StringUtils.isBlank(engineId)) {
            throw new IllegalArgumentException("engineId must not be blank");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        Duration timeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("shutdownTimeout must be positive");
        }
        return new ProcessEngineExecutors(engineId, config,
                Objects.requireNonNull(observability, "observability must not be null"), timeout);
    }

    private static String generateEngineId() {
        return "engine-" + INSTANCE_COUNTER.incrementAndGet();
    }

    private static boolean awaitAll(List<NamedExecutor> executors, Duration budget) throws InterruptedException {
        long budgetNanos = toNanosSaturated(budget);
        long started = System.nanoTime();
        for (NamedExecutor named : executors) {
            if (named.executor().isTerminated()) {
                continue;
            }
            long remaining = budgetNanos - (System.nanoTime() - started);
            if (remaining <= 0L || !named.executor().awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                return false;
            }
        }
        return true;
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static Duration forceShutdownReserve(Duration totalBudget) {
        if (totalBudget.isZero()) {
            return Duration.ZERO;
        }
        long totalNanos = toNanosSaturated(totalBudget);
        long oneThird = Math.max(TimeUnit.MILLISECONDS.toNanos(1L), totalNanos / 3L);
        long reserve = Math.min(TimeUnit.SECONDS.toNanos(5L), Math.min(totalNanos, oneThird));
        return Duration.ofNanos(reserve);
    }

    private static void forceShutdown(List<NamedExecutor> executors) {
        for (NamedExecutor named : executors) {
            if (!named.executor().isTerminated()) {
                cancelNeverStarted(named.executor().shutdownNow());
            }
        }
    }

    private static void rollbackCreatedExecutors(List<ExecutorService> executors, Throwable startupFailure) {
        for (int index = executors.size() - 1; index >= 0; index--) {
            try {
                cancelNeverStarted(executors.get(index).shutdownNow());
            } catch (RuntimeException | Error closeFailure) {
                startupFailure.addSuppressed(closeFailure);
            }
        }
    }

    private static void cancelNeverStarted(List<Runnable> neverStarted) {
        for (Runnable task : neverStarted) {
            if (task instanceof Future<?> future) {
                future.cancel(false);
            }
        }
    }

    private static List<String> activeExecutorNames(List<NamedExecutor> executors) {
        return executors
            .stream()
            .filter(named -> !named.executor().isTerminated())
            .map(NamedExecutor::name)
            .toList();
    }

    public ExecutorService runtimeLoad() {
        return runtimeLoadExecutor;
    }

    public ExecutorService preflight() {
        return preflightExecutor;
    }

    public ExecutorService action() {
        return actionExecutor;
    }

    public Duration actionTimeoutCancellationGracePeriod() {
        return actionTimeoutCancellationGracePeriod;
    }

    public ExecutorService parallel() {
        return parallelExecutor;
    }

    public Duration parallelCancellationGracePeriod() {
        return parallelCancellationGracePeriod;
    }

    public Duration shutdownTimeout() {
        return shutdownTimeout;
    }

    public ExecutorService event() {
        return eventExecutor;
    }

    public String engineId() {
        return engineId;
    }

    /**
     * Returns whether the current execution belongs to this executor group.
     *
     * <p>This includes platform workers, virtual-thread branch tasks, and Java 17 branch work
     * running on a submitting thread under caller-runs backpressure.</p>
     *
     * @return {@code true} when called from engine-owned work
     */
    public boolean ownsCurrentThread() {
        Thread current = Thread.currentThread();
        return (current instanceof EngineThread engineThread && engineThread.owner == this)
                || Boolean.TRUE.equals(parallelExecution.get());
    }

    @Override
    public void close() {
        close(shutdownTimeout);
    }

    /**
     * Closes every executor within the total budget remaining to the lifecycle owner.
     *
     * @param shutdownBudget non-negative remaining shutdown budget
     */
    public void close(Duration shutdownBudget) {
        if (ownsCurrentThread()) {
            throw new IllegalStateException("Engine executors cannot be closed from one of their worker threads");
        }
        Duration totalBudget = Objects.requireNonNull(shutdownBudget, "shutdownBudget");
        if (totalBudget.isNegative()) {
            throw new IllegalArgumentException("shutdownBudget must not be negative");
        }
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        List<NamedExecutor> executors = allExecutors();
        LOGGER.info("Shutting down engine executors: engineId={}", engineId);
        executors.forEach(named -> named.executor().shutdown());
        Duration forceBudget = forceShutdownReserve(totalBudget);
        Duration gracefulBudget = totalBudget.minus(forceBudget);

        try {
            if (!awaitAll(executors, gracefulBudget)) {
                LOGGER.warn("Engine executors exceeded graceful shutdown budget: engineId={}, budget={}", engineId,
                        gracefulBudget);
                forceShutdown(executors);
                if (!awaitAll(executors, forceBudget)) {
                    List<String> active = activeExecutorNames(executors);
                    LOGGER.error("Engine executors exceeded forced shutdown budget: engineId={}, budget={}, active={}",
                            engineId, forceBudget, active);
                    throw new IllegalStateException(
                            "Engine executors did not terminate within the shutdown budget: " + active);
                }
            }
        } catch (InterruptedException interrupted) {
            forceShutdown(executors);
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while shutting down engine executors; forced shutdown requested: engineId={}",
                    engineId);
            throw new IllegalStateException("Interrupted while shutting down engine executors", interrupted);
        }
    }

    @Override
    public String toString() {
        return "ProcessEngineExecutors{engineId='" + engineId + "'}";
    }

    private ExecutorService createRuntimeLoadExecutor(ProcessExecutorConfig config) {
        int concurrency = config.getRuntimeLoadMaxConcurrency();
        return new BoundedThreadPoolExecutor(concurrency, config.getRuntimeLoadMaxPending(),
                platformThreadFactory("runtime-load"));
    }

    private ExecutorService createPreflightExecutor() {
        return new BoundedThreadPoolExecutor(PREFLIGHT_MAX_CONCURRENCY, PREFLIGHT_MAX_PENDING,
                platformThreadFactory("preflight"));
    }

    private ExecutorService createActionExecutor(ProcessExecutorConfig config) {
        int concurrency = config.getActionTimeoutMaxConcurrency();
        return new BoundedThreadPoolExecutor(concurrency, config.getActionTimeoutMaxPending(),
                platformThreadFactory("action-timeout"));
    }

    private ExecutorService createParallelExecutor() {
        return JdkTaskExecutors.newOrchestrationExecutor("compileflow-" + engineId + "-parallel-",
                (thread, failure) -> LOGGER.error("Uncaught exception in thread [{}]", thread.getName(), failure),
                this::markParallelExecution);
    }

    private ExecutorService createEventExecutor(ProcessObservabilityConfig observability) {
        int concurrency = observability.getEventDeliveryMaxConcurrency();
        return new BoundedThreadPoolExecutor(concurrency, observability.getEventDeliveryMaxPending(),
                platformThreadFactory("event-delivery"));
    }

    private ThreadFactory platformThreadFactory(String executorType) {
        return new EngineThreadFactory(this, engineId, executorType);
    }

    private Runnable markParallelExecution(Runnable task) {
        return () -> {
            Boolean previous = parallelExecution.get();
            parallelExecution.set(Boolean.TRUE);
            try {
                task.run();
            } finally {
                if (previous == null) {
                    parallelExecution.remove();
                } else {
                    parallelExecution.set(previous);
                }
            }
        };
    }

    private List<NamedExecutor> allExecutors() {
        return List.of(new NamedExecutor("runtime-load", runtimeLoadExecutor),
                new NamedExecutor("preflight", preflightExecutor), new NamedExecutor("action-timeout", actionExecutor),
                new NamedExecutor("parallel", parallelExecutor), new NamedExecutor("event-delivery", eventExecutor));
    }

    private record NamedExecutor(String name, ExecutorService executor) {}

    private static final class EngineThreadFactory implements ThreadFactory {
        private final ProcessEngineExecutors owner;
        private final String engineId;
        private final String executorType;
        private final AtomicInteger threadCounter = new AtomicInteger(1);

        EngineThreadFactory(ProcessEngineExecutors owner, String engineId, String executorType) {
            this.owner = owner;
            this.engineId = engineId;
            this.executorType = executorType;
        }

        @Override
        public Thread newThread(Runnable task) {
            String threadName =
                    String.format(Locale.ROOT, THREAD_NAME_PATTERN, engineId, executorType,
                            threadCounter.getAndIncrement());
            Thread thread = new EngineThread(owner, task, threadName);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((failedThread, failure) -> LOGGER.error("Uncaught exception in thread "
                    + "[{}]", failedThread.getName(), failure));
            return thread;
        }
    }

    private static final class EngineThread extends Thread {
        private final ProcessEngineExecutors owner;

        private EngineThread(ProcessEngineExecutors owner, Runnable task, String threadName) {
            super(task, threadName);
            this.owner = owner;
        }
    }

    private static final class BoundedThreadPoolExecutor extends ThreadPoolExecutor {
        BoundedThreadPoolExecutor(int maxConcurrency, int maxPending, ThreadFactory threadFactory) {
            super(maxConcurrency, maxConcurrency, 0L, TimeUnit.MILLISECONDS, pendingQueue(maxPending), threadFactory,
                    new AbortPolicy());
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
            return new QueueRemovingFutureTask<>(callable, this);
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
            return new QueueRemovingFutureTask<>(runnable, value, this);
        }
    }

    private static BlockingQueue<Runnable> pendingQueue(int maxPending) {
        return maxPending == 0 ? new SynchronousQueue<>() : new LinkedBlockingQueue<>(maxPending);
    }

    private static final class QueueRemovingFutureTask<T> extends FutureTask<T> {
        private final ThreadPoolExecutor owner;

        QueueRemovingFutureTask(Callable<T> callable, ThreadPoolExecutor owner) {
            super(callable);
            this.owner = owner;
        }

        QueueRemovingFutureTask(Runnable runnable, T value, ThreadPoolExecutor owner) {
            super(runnable, value);
            this.owner = owner;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                owner.remove(this);
            }
            return cancelled;
        }
    }
}
