package com.alibaba.compileflow.engine.core.runtime.executor.failure;

/**
 * A strategy interface for handling failures in an execution.
 * Allows for custom, programmable failure logic.
 *
 * @author yusu
 */
@FunctionalInterface
public interface FailureHandler {

    /**
     * Handles the failure of an execution.
     *
     * @param context An object containing detailed information about the failure.
     * @return A {@link Resolution} indicating whether to fail the entire process
     * or to continue despite this failure.
     */
    Resolution handle(FailureContext context);

}
