package com.alibaba.compileflow.engine.core.runtime.executor.failure;

/**
 * @author yusu
 */
public enum Resolution {
    /**
     * Instructs the executor to propagate the failure, failing the entire parallel execution.
     */
    FAIL_PROCESS,

    /**
     * Instructs the executor to ignore the failure of this branch and continue processing others.
     */
    CONTINUE_PROCESS;
}
