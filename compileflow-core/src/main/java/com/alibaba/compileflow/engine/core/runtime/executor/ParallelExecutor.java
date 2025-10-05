package com.alibaba.compileflow.engine.core.runtime.executor;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ExecutorUtils;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Helper for executing parallel branches with timeout and error handling.
 *
 * @author yusu
 */
public final class ParallelExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelExecutor.class);
    private static final long DEFAULT_TIMEOUT_MS = 3000L;

    private ParallelExecutor() {
    }

    public static void execute(ParallelBranch... branches) throws Exception {
        execute(Arrays.asList(branches));
    }

    public static void execute(List<ParallelBranch> branches) throws Exception {
        if (CollectionUtils.isEmpty(branches)) {
            return;
        }
        ExecutorService executor = EngineExecutionContextHolder.execution();

        if (LOGGER.isDebugEnabled()) {
            List<String> branchNames = new ArrayList<>();
            for (ParallelBranch b : branches) {
                branchNames.add(b.getName());
            }
            LOGGER.debug("Parallel execution starting: branchCount={}, branches={}", branches.size(), branchNames);
        }

        ExecutionContextPropagator.ContextSnapshot snapshot = ExecutionContextPropagator.capture();
        final long startTs = System.currentTimeMillis();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ParallelBranch branch : branches) {
            Runnable finalTask = ExecutionContextPropagator.wrap(branch.getTask(), snapshot);
            try {
                CompletableFuture<Void> future = CompletableFuture.runAsync(finalTask, executor);
                futures.add(future);
            } catch (RejectedExecutionException ree) {
                ExecutorUtils.cancelUnfinished(futures);
                throw new CompileFlowException.SystemException(
                        ErrorCode.CF_EXEC_002,
                        String.format("Parallel submission rejected: branch='%s'", branch.getName()),
                        ree
                ).withContext("branchName", branch.getName());
            }
        }

        try {
            ExecutorUtils.waitAllWithTimeout(futures, DEFAULT_TIMEOUT_MS);
        } catch (InterruptedException ie) {
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_EXEC_002,
                    "Parallel execution interrupted",
                    ie
            ).withContext("timeout", DEFAULT_TIMEOUT_MS)
                    .withContext("interrupted", true);
        } catch (TimeoutException te) {
            long duration = System.currentTimeMillis() - startTs;
            LOGGER.error("Parallel execution timed out: branchCount={}, duration={}ms, timeout={}ms",
                    branches.size(), duration, DEFAULT_TIMEOUT_MS);
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_EXEC_002,
                    "Parallel execution timed out",
                    te
            ).withContext("timeout", DEFAULT_TIMEOUT_MS)
                    .withContext("duration", duration);
        } catch (CancellationException ce) {
            long duration = System.currentTimeMillis() - startTs;
            LOGGER.warn("Parallel execution cancelled: branchCount={}, duration={}ms",
                    branches.size(), duration);
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_EXEC_002,
                    "Parallel execution was cancelled",
                    ce
            ).withContext("timeout", DEFAULT_TIMEOUT_MS)
                    .withContext("duration", duration);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            long duration = System.currentTimeMillis() - startTs;
            LOGGER.error("Parallel execution failed: branchCount={}, duration={}ms, reason={}",
                    branches.size(), duration, cause.getMessage());
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_EXEC_002,
                    "Parallel execution failed",
                    cause
            ).withContext("timeout", DEFAULT_TIMEOUT_MS)
                    .withContext("duration", duration);
        }

        if (LOGGER.isDebugEnabled()) {
            long duration = System.currentTimeMillis() - startTs;
            LOGGER.debug("Parallel execution completed: branchCount={}, duration={}ms", branches.size(), duration);
        }
    }

}
