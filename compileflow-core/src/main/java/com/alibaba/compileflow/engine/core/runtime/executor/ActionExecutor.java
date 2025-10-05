package com.alibaba.compileflow.engine.core.runtime.executor;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ExecutorUtils;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.executor.failure.FailureContext;
import com.alibaba.compileflow.engine.core.runtime.executor.failure.FailureHandler;
import com.alibaba.compileflow.engine.core.runtime.executor.failure.FailureHandlerResolver;
import com.alibaba.compileflow.engine.core.runtime.executor.failure.Resolution;
import com.alibaba.compileflow.engine.core.runtime.executor.policy.ExecutionPolicy;
import com.alibaba.compileflow.engine.core.runtime.executor.retry.RetryPolicy;
import com.alibaba.compileflow.engine.core.runtime.executor.retry.RetryPolicyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.*;

/**
 * Executes actions with timeout, retry and failure handling.
 * Context is propagated; retries precede failure-handling strategies.
 *
 * @author yusu
 */
public final class ActionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionExecutor.class);

    private ActionExecutor() {
    }

    /**
     * Executes a supplier function with retry logic and failure handling.
     *
     * @param nodeId   the identifier of the node being executed
     * @param supplier the function to execute
     * @param policy   the execution policy defining timeouts, retries, and failure handling
     * @param <T>      the return type of the supplier
     * @return the result of the supplier execution
     * @throws Exception if execution fails after all retries are exhausted
     */
    public static <T> T call(String nodeId, ThrowingSupplier<T> supplier, ExecutionPolicy policy) throws Exception {
        return executeInternal(nodeId, supplier, policy);
    }

    /**
     * Executes a runnable with retry logic and failure handling.
     *
     * @param nodeId   the identifier of the node being executed
     * @param runnable the task to execute
     * @param policy   the execution policy defining timeouts, retries, and failure handling
     * @throws Exception if execution fails after all retries are exhausted
     */
    public static void run(String nodeId, ThrowingRunnable runnable, ExecutionPolicy policy) throws Exception {
        executeInternal(nodeId, () -> {
            runnable.run();
            return null;
        }, policy);
    }

    /**
     * Retry loop semantics:
     * try -> (errorMapping? throw BPMN Error) -> (retryable by `retryOn` and attempts <= retryCount ? sleep+retry : handle by `onFailure`)
     * Special case: retryCount == 0 => first failure jumps directly to `onFailure`.
     */
    private static <T> T executeInternal(String nodeId, ThrowingSupplier<T> supplier, ExecutionPolicy policy) throws Exception {
        long timeoutMs = policy.getTimeoutMs();
        int retryCount = policy.getRetryCount();
        long retryInterval = policy.getRetryIntervalMs();

        RetryPolicy retryPolicy = RetryPolicyResolver.resolve(policy.getRetryOn());
        FailureHandler failureHandler = FailureHandlerResolver.resolve(policy.getOnFailure());

        int attempt = 0;
        Throwable last;
        boolean lastWasTimeout;

        while (true) {
            attempt++;
            lastWasTimeout = false;

            if (timeoutMs <= 0) {
                try {
                    return supplier.get();
                } catch (Throwable ex) {
                    last = ex;
                }
            } else {
                ExecutorService executor = EngineExecutionContextHolder.execution();
                ExecutionContextPropagator.ContextSnapshot snap = ExecutionContextPropagator.capture();
                Callable<T> task = ExecutionContextPropagator.wrap(supplier::get, snap);
                try {
                    return ExecutorUtils.callWithTimeout(executor, task, timeoutMs);
                } catch (RejectedExecutionException rex) {
                    last = rex;
                    LOGGER.warn("action submit rejected: node={}, attempt={}", nodeId, attempt, rex);
                } catch (TimeoutException te) {
                    last = te;
                    lastWasTimeout = true;
                    LOGGER.warn("action timeout: node={}, timeoutMs={}, attempt={}", nodeId, timeoutMs, attempt);
                } catch (InterruptedException ie) {
                    last = ie;
                    LOGGER.warn("action interrupted: node={}, attempt={}", nodeId, attempt);
                } catch (CancellationException ce) {
                    last = ce;
                    LOGGER.warn("action cancelled: node={}, attempt={}", nodeId, attempt);
                } catch (ExecutionException ee) {
                    last = ee.getCause() == null ? ee : ee.getCause();
                }
            }

            if (attempt <= retryCount && retryPolicy.shouldRetryOn(last)) {
                if (retryInterval > 0) {
                    sleepQuietly(retryInterval);
                }
                continue;
            }

            FailureContext ctx = new FailureContext(nodeId, last, attempt);
            Resolution r = failureHandler.handle(ctx);
            if (r == Resolution.CONTINUE_PROCESS) {
                LOGGER.warn("action continued after failure: {}", ctx);
                return null;
            }

            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_EXEC_002,
                    String.format(Locale.ROOT, "Action failed: node=%s, attempts=%d", nodeId, attempt),
                    last
            ).withContext("nodeId", nodeId)
                    .withContext("attempts", attempt)
                    .withContext("timeoutMs", timeoutMs)
                    .withContext("retryCount", retryCount)
                    .withContext("retryIntervalMs", retryInterval)
                    .withContext("timeout", lastWasTimeout);
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

}
