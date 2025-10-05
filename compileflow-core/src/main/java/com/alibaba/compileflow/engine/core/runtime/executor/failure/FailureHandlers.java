package com.alibaba.compileflow.engine.core.runtime.executor.failure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author yusu
 */
public class FailureHandlers {

    /**
     * A handler that immediately fails the entire execution upon any failure.
     * (Formerly the FAIL_FAST/STRONG strategy).
     */
    public static final FailureHandler INCIDENT = context -> Resolution.FAIL_PROCESS;
    private static final Logger LOGGER = LoggerFactory.getLogger(FailureHandlers.class);
    /**
     * A handler that logs the failure as a warning and allows the process to continue.
     * (Formerly the CONTINUE_ON_FAILURE/WEAK strategy).
     */
    public static final FailureHandler CONTINUE = context -> {
        LOGGER.warn("'{}' failed (attempt {}) and will be ignored.",
                context.getName(), context.getAttemptCount(), context.getCause());
        return Resolution.CONTINUE_PROCESS;
    };

    private FailureHandlers() {
    }

}
