package com.alibaba.compileflow.engine.test.execution;

import com.alibaba.compileflow.engine.core.runtime.executor.failure.FailureContext;
import com.alibaba.compileflow.engine.core.runtime.executor.failure.FailureHandler;
import com.alibaba.compileflow.engine.core.runtime.executor.failure.Resolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;

/**
 * A custom, production-ready failure handler for parallel branches.
 * It implements sophisticated logic to decide the process outcome based on the error type.
 *
 * @author yusu
 */
@Component
public class MyCustomErrorHandler implements FailureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyCustomErrorHandler.class);

    @Autowired
    private MonitoringService monitoringService;

    @Override
    public Resolution handle(FailureContext context) {
        String branchName = context.getName();
        Throwable error = context.getCause();
        int attempts = context.getAttemptCount();

        LOGGER.error("Parallel branch '{}' has failed definitively after {} attempts.",
                branchName, attempts, error);

        if (error instanceof TimeoutException) {
            monitoringService.alert("FlowBranchTimeout",
                    "Branch " + branchName + " timed out.", error);
            return Resolution.CONTINUE_PROCESS;
        } else if (error instanceof IllegalArgumentException) {
            // For deterministic errors, fail the process.
            monitoringService.alert("FlowBranchCriticalError",
                    "Branch " + branchName + " failed with a deterministic error.", error);
            return Resolution.FAIL_PROCESS;
        } else {
            // For unexpected errors, we log and alert but do not fail the process.
            monitoringService.alert("FlowBranchUnknownError",
                    "Branch " + branchName + " failed with an unexpected error.", error);
            return Resolution.FAIL_PROCESS;
        }
    }
}
