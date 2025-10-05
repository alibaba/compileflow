package com.alibaba.compileflow.engine.core.runtime.executor.retry;

/**
 * @author yusu
 */
public interface RetryPolicy {

    /**
     * Determines if a retry should be attempted for the given throwable.
     *
     * @param throwable The exception that occurred.
     * @return {@code true} to attempt a retry, {@code false} to fail immediately.
     */
    boolean shouldRetryOn(Throwable throwable);

}
