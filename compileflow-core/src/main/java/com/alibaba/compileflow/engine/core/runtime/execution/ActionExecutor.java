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
package com.alibaba.compileflow.engine.core.runtime.execution;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessExecutionException;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.RetryJitter;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.execution.failure.FailureHandlerResolver;
import com.alibaba.compileflow.engine.core.runtime.execution.retry.RetryPolicyResolver;
import com.alibaba.compileflow.engine.spi.execution.ActionExecutionContext;
import com.alibaba.compileflow.engine.spi.execution.FailureContext;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.FailureResolution;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes process actions with retry and failure handling.
 *
 * @author yusu
 */
public final class ActionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionExecutor.class);

    private ActionExecutor() {
    }

    public static <T> T call(String nodeId, ThrowingSupplier<T> supplier, EffectiveInvocationPolicy policy)
            throws Exception {
        return executeInternal(nodeId, supplier, policy).value();
    }

    /**
     * Executes a value-producing action and publishes its result only after a
     * successful attempt.
     *
     * <p>Timed-out, failed, retried, cancelled, and continue-on-failure
     * attempts never invoke the commit callback. This keeps generated process
     * fields isolated from late worker completion.
     *
     * @param nodeId   model node identifier
     * @param supplier isolated action invocation
     * @param policy   resolved timeout, retry, and failure policy
     * @param commit   successful result publisher
     * @param <T>      action result type
     * @throws Exception when action execution fails
     */
    public static <T> void callAndCommit(String nodeId, ThrowingSupplier<T> supplier, EffectiveInvocationPolicy policy,
            Consumer<? super T> commit) throws Exception {
        Objects.requireNonNull(commit, "commit");
        ActionOutcome<T> outcome = executeInternal(nodeId, supplier, policy);
        if (outcome.shouldCommit()) {
            commit.accept(outcome.value());
        }
    }

    public static void run(String nodeId, ThrowingRunnable runnable, EffectiveInvocationPolicy policy) throws Exception {
        executeInternal(nodeId, () -> {
            runnable.run();
            return null;
        }, policy);
    }

    /**
     * Opens the single synchronous attempt used by an action without an explicit invocation policy.
     *
     * <p>Generated code uses this scope instead of allocating one lambda per
     * action. The scope exposes the same action identity as the policy-driven
     * path while avoiding class-file bootstrap and constant-pool growth for
     * large process models.
     *
     * @param nodeId model node identifier
     * @return thread-confined action scope
     */
    public static ActionScope open(String nodeId) {
        return new ActionScope(createInvocationContext(nodeId));
    }

    private static <T> ActionOutcome<T> executeInternal(String nodeId, ThrowingSupplier<T> supplier,
            EffectiveInvocationPolicy policy) throws Exception {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(policy, "policy");
        long invocationStartedAt = System.nanoTime();
        ActionExecutionContext invocation = createInvocationContext(nodeId);
        String exactNodeId = invocation.getNodeId();
        long timeoutMs = policy.getTimeoutMs();
        long attemptTimeoutMs = policy.getAttemptTimeoutMs();
        long invocationBudgetNanos = millisecondsToNanosSaturated(timeoutMs);
        int maxAttempts = policy.getMaxAttempts();
        long initialBackoffMs = policy.getInitialBackoffMs();
        double backoffMultiplier = policy.getBackoffMultiplier();
        long maxBackoffMs = policy.getMaxBackoffMs();
        RetryJitter jitter = policy.getJitter();

        RetryPolicy retryPolicy = maxAttempts > 1 ? RetryPolicyResolver.resolve(policy.getRetryOn()) : null;
        FailureHandler failureHandler = FailureHandlerResolver.resolve(policy.getOnFailure());
        int attempt = 0;
        while (true) {
            attempt++;
            ActionExecutionContext actionContext = attempt == 1 ? invocation : actionAttempt(invocation, attempt);
            Throwable failure = null;

            long remainingNanos = remainingNanos(invocationBudgetNanos, invocationStartedAt);
            long attemptBudgetNanos = effectiveAttemptBudgetNanos(attemptTimeoutMs, remainingNanos);
            boolean overallDeadline = invocationBudgetNanos > 0L
                    && (attemptTimeoutMs <= 0L || remainingNanos <= millisecondsToNanosSaturated(attemptTimeoutMs));
            if (invocationBudgetNanos > 0L && remainingNanos <= 0L) {
                if (attempt > 1) {
                    attempt--;
                    actionContext = attempt == 1 ? invocation : actionAttempt(invocation, attempt);
                }
                failure = invocationTimeout(timeoutMs, null);
            }

            if (failure == null && attemptBudgetNanos <= 0L) {
                try {
                    return ActionOutcome.commit(invokeAttempt(actionContext, supplier));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                } catch (Exception actionFailure) {
                    failure = actionFailure;
                }
            } else if (failure == null) {
                ExecutorService executor = EngineExecutionContextHolder.action();
                ExecutionContextPropagator.ContextSnapshot capturedContext = ExecutionContextPropagator.capture();
                ActionExecutionContext attemptContext = actionContext;
                Callable<T> task =
                        ExecutionContextPropagator.wrap(() -> invokeAttempt(attemptContext, supplier), capturedContext);
                ActionExecution execution = new ActionExecution();
                FutureTask<T> future = new TrackedFutureTask<>(task, execution);
                boolean submitted = false;
                try {
                    executor.execute(future);
                    submitted = true;
                } catch (RejectedExecutionException rejected) {
                    failure = rejected;
                    LOGGER.warn("action submit rejected: node={}, invocationKey={}, attempt={}", exactNodeId,
                            actionContext.getInvocationKey(), attempt);
                }
                if (submitted) {
                    try {
                        return ActionOutcome.commit(future.get(attemptBudgetNanos, TimeUnit.NANOSECONDS));
                    } catch (TimeoutException timeout) {
                        cancel(future, executor);
                        awaitCancelledAction(actionContext, execution, timeout);
                        failure = overallDeadline ? invocationTimeout(timeoutMs, timeout) : timeout;
                        LOGGER.warn("action timeout: node={}, invocationKey={}, timeoutMs={}, attemptTimeoutMs={}, "
                                + "attempt={}", exactNodeId, actionContext.getInvocationKey(), timeoutMs,
                                attemptTimeoutMs, attempt);
                    } catch (InterruptedException interrupted) {
                        cancel(future, executor);
                        try {
                            awaitCancelledAction(actionContext, execution, interrupted);
                        } finally {
                            Thread.currentThread().interrupt();
                        }
                        LOGGER.warn("action interrupted: node={}, invocationKey={}, attempt={}", exactNodeId,
                                actionContext.getInvocationKey(), attempt);
                        throw interrupted;
                    } catch (CancellationException cancelled) {
                        cancel(future, executor);
                        awaitCancelledAction(actionContext, execution, cancelled);
                        failure = cancelled;
                        LOGGER.warn("action cancelled: node={}, invocationKey={}, attempt={}", exactNodeId,
                                actionContext.getInvocationKey(), attempt);
                    } catch (ExecutionException executionFailure) {
                        failure = executionFailure.getCause() == null ? executionFailure : executionFailure.getCause();
                        if (failure instanceof Error error) {
                            throw error;
                        }
                    }
                }
            }

            if (failure == null) {
                throw new IllegalStateException("Action attempt completed without a result or failure");
            }
            if (failure instanceof InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            if (failure instanceof CancellationException cancellation) {
                throw actionFailure(actionContext, timeoutMs, attemptTimeoutMs, maxAttempts, cancellation);
            }
            boolean retry = !(failure instanceof InvocationTimeoutException) && attempt < maxAttempts
                    && shouldRetry(retryPolicy, policy.getRetryOn(), failure);
            if (retry) {
                long delay = 0L;
                if (initialBackoffMs > 0) {
                    DoubleSupplier randomSupplier = () -> ThreadLocalRandom.current().nextDouble();
                    delay = computeRetryDelay(initialBackoffMs, backoffMultiplier, attempt, maxBackoffMs, jitter,
                            randomSupplier);
                }
                if (invocationBudgetNanos > 0L) {
                    remainingNanos = remainingNanos(invocationBudgetNanos, invocationStartedAt);
                    long delayNanos = millisecondsToNanosSaturated(delay);
                    if (remainingNanos <= 0L || delayNanos >= remainingNanos) {
                        if (remainingNanos > 0L) {
                            pauseBeforeRetryNanos(remainingNanos);
                        }
                        failure = invocationTimeout(timeoutMs, failure);
                        retry = false;
                    }
                }
                if (retry) {
                    if (delay > 0L) {
                        pauseBeforeRetry(delay);
                    }
                    continue;
                }
            }

            FailureContext failureContext = new FailureContext(actionContext, failure);
            FailureResolution resolution = handleFailure(failureHandler, policy.getOnFailure(), failureContext);
            if (resolution == FailureResolution.CONTINUE_PROCESS) {
                LOGGER.warn("action continued after failure: {}", failureContext, failure);
                return ActionOutcome.skip();
            }

            throw actionFailure(actionContext, timeoutMs, attemptTimeoutMs, maxAttempts, failure)
                .withContext("initialBackoffMs", initialBackoffMs);
        }
    }

    private static void cancel(FutureTask<?> future, ExecutorService executor) {
        future.cancel(true);
        if (executor instanceof ThreadPoolExecutor threadPool) {
            threadPool.remove(future);
        }
    }

    private static void awaitCancelledAction(ActionExecutionContext actionContext, ActionExecution execution,
            Throwable cause) {
        if (!execution.runnerStarted.get()) {
            return;
        }
        Duration gracePeriod = EngineExecutionContextHolder.actionTimeoutCancellationGracePeriod();
        boolean interrupted = false;
        boolean stopped = false;
        try {
            long remainingNanos = toNanosSaturated(gracePeriod);
            long startedAt = System.nanoTime();
            while (!stopped && remainingNanos > 0L) {
                try {
                    stopped = execution.runnerFinished.await(remainingNanos, TimeUnit.NANOSECONDS);
                } catch (InterruptedException repeatedInterruption) {
                    interrupted = true;
                }
                remainingNanos = toNanosSaturated(gracePeriod) - Math.max(0L, System.nanoTime() - startedAt);
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (stopped || execution.runnerFinished.getCount() == 0L) {
            return;
        }

        LOGGER.error("Action ignored cancellation: node={}, attempt={}, grace={}", actionContext.getNodeId(),
                actionContext.getAttemptNumber(), gracePeriod);
        CompileFlowException failure = new CompileFlowException(ErrorCode.CF_EXEC_007,
                "Action did not stop within the cancellation grace period", cause);
        failure.withContext("nodeId", actionContext.getNodeId());
        failure.withContext("invocationKey", actionContext.getInvocationKey());
        failure.withContext("attempt", actionContext.getAttemptNumber());
        failure.withContext("cancellationGraceMillis", gracePeriod.toMillis());
        throw failure;
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static boolean shouldRetry(RetryPolicy retryPolicy, String policyName, Throwable failure) {
        try {
            return retryPolicy.shouldRetryOn(failure);
        } catch (RuntimeException callbackFailure) {
            throw callbackFailure("RetryPolicy", policyName, callbackFailure);
        }
    }

    private static FailureResolution handleFailure(FailureHandler failureHandler, String handlerName,
            FailureContext context) {
        try {
            return Objects.requireNonNull(failureHandler.handle(context), "FailureHandler returned null");
        } catch (RuntimeException callbackFailure) {
            throw callbackFailure("FailureHandler", handlerName, callbackFailure);
        }
    }

    private static CompileFlowException.ConfigurationException callbackFailure(String collaborator, String name,
            RuntimeException failure) {
        CompileFlowException.ConfigurationException exception = new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                String.format(Locale.ROOT, "%s '%s' failed while evaluating an action failure", collaborator, name),
                failure);
        exception.withContext("collaborator", collaborator).withContext("name", name);
        return exception;
    }

    private static CompileFlowException actionFailure(ActionExecutionContext actionContext, long timeoutMs,
            long attemptTimeoutMs, int maxAttempts, Throwable failure) {
        if (failure instanceof CompileFlowException compileFlowFailure) {
            return compileFlowFailure
                .withContext("nodeId", actionContext.getNodeId())
                .withContext("invocationKey", actionContext.getInvocationKey())
                .withContext("attempts", actionContext.getAttemptNumber())
                .withContext("timeoutMs", timeoutMs)
                .withContext("attemptTimeoutMs", attemptTimeoutMs)
                .withContext("maxAttempts", maxAttempts);
        }
        if (failure instanceof ProcessExecutionException nestedFailure) {
            throw nestedFailure;
        }
        CompileFlowException exception = new CompileFlowException(errorCode(failure),
                String.format(Locale.ROOT, "Action failed: node=%s, attempts=%d", actionContext.getNodeId(),
                        actionContext.getAttemptNumber()), failure);
        exception
            .withContext("nodeId", actionContext.getNodeId())
            .withContext("invocationKey", actionContext.getInvocationKey())
            .withContext("attempts", actionContext.getAttemptNumber())
            .withContext("timeoutMs", timeoutMs)
            .withContext("attemptTimeoutMs", attemptTimeoutMs)
            .withContext("maxAttempts", maxAttempts);
        return exception;
    }

    private static ErrorCode errorCode(Throwable failure) {
        if (failure instanceof TimeoutException) {
            return ErrorCode.CF_EXEC_004;
        }
        if (failure instanceof RejectedExecutionException) {
            return ErrorCode.CF_EXEC_005;
        }
        return failure instanceof CancellationException ? ErrorCode.CF_EXEC_007 : ErrorCode.CF_EXEC_001;
    }

    private static long effectiveAttemptBudgetNanos(long attemptTimeoutMs, long remainingInvocationNanos) {
        long attemptNanos = millisecondsToNanosSaturated(attemptTimeoutMs);
        if (remainingInvocationNanos <= 0L) {
            return attemptNanos;
        }
        return attemptNanos <= 0L ? remainingInvocationNanos : Math.min(attemptNanos, remainingInvocationNanos);
    }

    private static long remainingNanos(long invocationBudgetNanos, long startedAt) {
        if (invocationBudgetNanos <= 0L) {
            return 0L;
        }
        long elapsed = Math.max(0L, System.nanoTime() - startedAt);
        return elapsed >= invocationBudgetNanos ? 0L : invocationBudgetNanos - elapsed;
    }

    private static long millisecondsToNanosSaturated(long milliseconds) {
        if (milliseconds <= 0L) {
            return 0L;
        }
        return milliseconds > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : milliseconds * 1_000_000L;
    }

    private static InvocationTimeoutException invocationTimeout(long timeoutMs, Throwable cause) {
        InvocationTimeoutException timeout =
                new InvocationTimeoutException("Action invocation exceeded timeout of " + timeoutMs + " ms");
        if (cause != null) {
            timeout.initCause(cause);
        }
        return timeout;
    }

    /**
     * Computes capped exponential backoff and applies the selected jitter.
     */
    static long computeRetryDelay(long baseBackoffMs, double multiplier, int attempt, long maxBackoffMs,
            RetryJitter jitter, DoubleSupplier random) {
        Objects.requireNonNull(jitter, "jitter");
        long cap = computeBackoffDelay(baseBackoffMs, multiplier, attempt, maxBackoffMs);
        if (jitter == RetryJitter.NONE || cap == 0L) {
            return cap;
        }
        double sample = Objects.requireNonNull(random, "random").getAsDouble();
        if (!Double.isFinite(sample) || sample < 0.0d || sample >= 1.0d) {
            throw new IllegalArgumentException("random sample must be in [0.0, 1.0)");
        }
        return (long) Math.floor(sample * ((double) cap + 1.0d));
    }

    /**
     * Computes {@code min(base * multiplier^(attempt - 1), max)}.
     */
    static long computeBackoffDelay(long baseBackoffMs, double multiplier, int attempt, long maxBackoffMs) {
        if (baseBackoffMs < 0L || maxBackoffMs < 0L) {
            throw new IllegalArgumentException("Retry backoffs must be non-negative");
        }
        if (maxBackoffMs < baseBackoffMs) {
            throw new IllegalArgumentException("Maximum backoff must not be shorter than the base backoff");
        }
        if (!Double.isFinite(multiplier) || multiplier < 1.0d) {
            throw new IllegalArgumentException("Retry multiplier must be finite and at least 1.0");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("Retry attempt must be positive");
        }
        long boundedBase = Math.min(baseBackoffMs, maxBackoffMs);
        if (multiplier == 1.0d || boundedBase == maxBackoffMs) {
            return boundedBase;
        }
        double computed = (double) baseBackoffMs * Math.pow(multiplier, attempt - 1);
        if (!Double.isFinite(computed) || computed >= maxBackoffMs) {
            return maxBackoffMs;
        }
        return Math.min((long) computed, maxBackoffMs);
    }

    private static void pauseBeforeRetry(long initialBackoff) throws InterruptedException {
        try {
            Thread.sleep(initialBackoff);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    private static void pauseBeforeRetryNanos(long delayNanos) throws InterruptedException {
        try {
            TimeUnit.NANOSECONDS.sleep(delayNanos);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    private static <T> T invokeAttempt(ActionExecutionContext actionContext, ThrowingSupplier<T> supplier)
            throws Exception {
        ActionExecutionContext.Scope scope = ActionExecutionContext.open(actionContext);
        try {
            return supplier.get();
        } finally {
            scope.close();
        }
    }

    private static EngineExecutionContext requireEngineContext() {
        EngineExecutionContext context = EngineExecutionContextHolder.current();
        if (context == null) {
            throw new IllegalStateException("Action execution requires an engine execution context");
        }
        return context;
    }

    private static ActionExecutionContext createInvocationContext(String nodeId) {
        String exactNodeId = ProcessIdentifiers.requireNodeId(nodeId);
        EngineExecutionContext engineContext = requireEngineContext();
        String processInvocationId = requireExecutionIdentity(engineContext.invocationId(), "process invocation ID");
        String sourceDigest = requireExecutionIdentity(engineContext.sourceDigest(), "runtime source digest");
        long invocationOrdinal = engineContext.nextActionInvocationOrdinal(exactNodeId);
        return new ActionExecutionContext(processInvocationId, engineContext.namespace(), engineContext.processCode(),
                engineContext.modelType(), sourceDigest, exactNodeId, invocationOrdinal, 1);
    }

    private static ActionExecutionContext actionAttempt(ActionExecutionContext invocation, int attemptNumber) {
        return new ActionExecutionContext(invocation.getProcessInvocationId(), invocation.getNamespace(),
                invocation.getProcessCode(), invocation.getModelType(), invocation.getSourceDigest(),
                invocation.getNodeId(), invocation.getInvocationOrdinal(), attemptNumber);
    }

    private static String requireExecutionIdentity(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Action execution requires a non-blank " + name);
        }
        return value;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class InvocationTimeoutException extends TimeoutException {
        private static final long serialVersionUID = 1L;

        private InvocationTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * Thread-confined context for a synchronous action without an explicit invocation policy.
     *
     * <p>Use the returned instance as a try-with-resources variable. A caught
     * {@link Exception} must be passed to {@link #failure(Exception)} after the
     * scope closes so the default action error contract is preserved.
     */
    public static final class ActionScope implements AutoCloseable {
        private final Thread owner;
        private final ActionExecutionContext actionContext;
        private final ActionExecutionContext.Scope contextScope;
        private boolean closed;
        private boolean failureHandled;

        private ActionScope(ActionExecutionContext actionContext) {
            this.owner = Thread.currentThread();
            this.actionContext = actionContext;
            this.contextScope = ActionExecutionContext.open(actionContext);
        }

        /**
         * Normalizes one failed direct attempt using the standard action error model.
         *
         * @param failure action failure caught by generated code
         * @return checked or runtime exception to throw
         */
        public Exception failure(Exception failure) {
            requireOwner();
            Objects.requireNonNull(failure, "failure");
            if (!closed) {
                throw new IllegalStateException("Action scope must close before its failure is handled", failure);
            }
            if (failureHandled) {
                throw new IllegalStateException("Action failure has already been handled");
            }
            failureHandled = true;
            if (failure instanceof InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return interrupted;
            }
            return actionFailure(actionContext, 0L, 0L, 1, failure);
        }

        @Override
        public void close() {
            requireOwner();
            if (closed) {
                return;
            }
            contextScope.close();
            closed = true;
        }

        private void requireOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Action scope must be used by its owner thread");
            }
        }
    }

    private record ActionOutcome<T>(boolean shouldCommit, T value) {
        private static <T> ActionOutcome<T> commit(T value) {
            return new ActionOutcome<>(true, value);
        }

        private static <T> ActionOutcome<T> skip() {
            return new ActionOutcome<>(false, null);
        }
    }

    private static final class ActionExecution {
        private final AtomicBoolean runnerStarted = new AtomicBoolean();
        private final CountDownLatch runnerFinished = new CountDownLatch(1);
    }

    private static final class TrackedFutureTask<T> extends FutureTask<T> {
        private final ActionExecution execution;

        private TrackedFutureTask(Callable<T> task, ActionExecution execution) {
            super(task);
            this.execution = execution;
        }

        @Override
        public void run() {
            // Mark before FutureTask checks its state so cancellation can
            // distinguish a queued task from a runner that may enter user code.
            execution.runnerStarted.set(true);
            try {
                super.run();
            } finally {
                execution.runnerFinished.countDown();
            }
        }
    }
}
