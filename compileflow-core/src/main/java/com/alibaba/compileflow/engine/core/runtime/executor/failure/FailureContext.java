package com.alibaba.compileflow.engine.core.runtime.executor.failure;

/**
 * Provides context about a failed execution to a FailureHandler.
 *
 * @author yusu
 */
public class FailureContext {
    private final String name;
    private final Throwable cause;
    private final int attemptCount;

    public FailureContext(String name, Throwable cause, int attemptCount) {
        this.name = name;
        this.cause = cause;
        this.attemptCount = attemptCount;
    }

    public String getName() {
        return name;
    }

    public Throwable getCause() {
        return cause;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

}
