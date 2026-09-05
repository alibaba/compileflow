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
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes parallel gateway branches with failure propagation and bounded
 * cancellation draining.
 *
 * @author yusu
 */
final class ParallelExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelExecutor.class);

    private ParallelExecutor() {
    }

    static <T> List<T> execute(List<ParallelTask<T>> tasks) throws Exception {
        Objects.requireNonNull(tasks, "tasks");
        if (tasks.isEmpty()) {
            return List.of();
        }
        tasks.forEach(task -> Objects.requireNonNull(task, "task"));
        ExecutorService executor = EngineExecutionContextHolder.parallel();

        if (LOGGER.isDebugEnabled()) {
            List<String> taskNames = new ArrayList<>();
            for (ParallelTask<T> task : tasks) {
                taskNames.add(task.name());
            }
            LOGGER.debug("Parallel execution starting: taskCount={}, tasks={}", tasks.size(), taskNames);
        }

        ExecutionContextPropagator.ContextSnapshot snapshot = ExecutionContextPropagator.capture();
        final long startedAtNanos = System.nanoTime();
        BlockingQueue<Future<IndexedResult<T>>> completions = new LinkedBlockingQueue<>();
        List<Future<IndexedResult<T>>> futures = new ArrayList<>();
        List<BranchExecution> executions = new ArrayList<>();
        List<T> orderedResults = new ArrayList<>(Collections.nCopies(tasks.size(), null));

        for (int index = 0; index < tasks.size(); index++) {
            ParallelTask<T> task = tasks.get(index);
            int resultIndex = index;
            BranchExecution execution = new BranchExecution(task.name());
            Callable<IndexedResult<T>> contextualTask =
                    ExecutionContextPropagator.wrap(() -> new IndexedResult<>(resultIndex, task.task().get()), snapshot);
            FutureTask<IndexedResult<T>> future =
                    new CompletionQueueFutureTask<>(task.name(), contextualTask, completions, execution);
            try {
                executor.execute(future);
                futures.add(future);
                executions.add(execution);
            } catch (RejectedExecutionException ree) {
                cancelUnfinished(futures);
                List<String> lingering = awaitCancelledBranches(executions);
                if (!lingering.isEmpty()) {
                    throw cancellationDrainFailure(lingering, ree, cancellationGracePeriod());
                }
                throw new CompileFlowException(ErrorCode.CF_EXEC_005,
                        String.format(Locale.ROOT, "Parallel submission rejected: task='%s'", task.name()), ree)
                    .withContext("taskName", task.name());
            }
        }

        Future<IndexedResult<T>> completedFuture = null;
        try {
            for (int completed = 0; completed < futures.size(); completed++) {
                completedFuture = completions.take();
                IndexedResult<T> result = completedFuture.get();
                orderedResults.set(result.index(), result.value());
            }
        } catch (InterruptedException ie) {
            cancelUnfinished(futures);
            Duration cancellationGrace = cancellationGracePeriod();
            List<String> lingering = awaitCancelledBranches(executions, cancellationGrace);
            Thread.currentThread().interrupt();
            if (!lingering.isEmpty()) {
                throw cancellationDrainFailure(lingering, ie, cancellationGrace);
            }
            throw ie;
        } catch (CancellationException ce) {
            cancelUnfinished(futures);
            Duration cancellationGrace = cancellationGracePeriod();
            List<String> lingering = awaitCancelledBranches(executions, cancellationGrace);
            if (!lingering.isEmpty()) {
                throw cancellationDrainFailure(lingering, ce, cancellationGrace);
            }
            long duration = elapsedMillis(startedAtNanos);
            LOGGER.warn("Parallel execution cancelled: taskCount={}, duration={}ms", tasks.size(), duration);
            throw new CompileFlowException(ErrorCode.CF_EXEC_007, "Parallel execution was cancelled", ce)
                .withContext("duration", duration);
        } catch (ExecutionException ee) {
            cancelUnfinished(futures);
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            Duration cancellationGrace = cancellationGracePeriod();
            List<String> lingering = awaitCancelledBranches(executions, cancellationGrace);
            if (!lingering.isEmpty()) {
                CompileFlowException cancellationFailure = cancellationDrainFailure(lingering, cause, cancellationGrace);
                if (cause instanceof Error error) {
                    error.addSuppressed(cancellationFailure);
                    throw error;
                }
                throw cancellationFailure;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            long duration = elapsedMillis(startedAtNanos);
            String failedTask = taskName(completedFuture);
            LOGGER.error("Parallel execution failed: taskCount={}, failedTask={}, duration={}ms, failureType={}",
                    tasks.size(), failedTask, duration, cause.getClass().getName());
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new CompileFlowException(ErrorCode.CF_EXEC_001, "Parallel branch execution failed", cause)
                .withContext("duration", duration);
        }

        if (LOGGER.isDebugEnabled()) {
            long duration = elapsedMillis(startedAtNanos);
            LOGGER.debug("Parallel execution completed: taskCount={}, duration={}ms", tasks.size(), duration);
        }
        return Collections.unmodifiableList(orderedResults);
    }

    private static void cancelUnfinished(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private static List<String> awaitCancelledBranches(List<BranchExecution> executions) {
        return awaitCancelledBranches(executions, cancellationGracePeriod());
    }

    private static List<String> awaitCancelledBranches(List<BranchExecution> executions, Duration gracePeriod) {
        long budgetNanos = toNanosSaturated(gracePeriod);
        long startedAt = System.nanoTime();
        boolean interrupted = false;
        // After cancellation, a runner that has not entered yet can no longer
        // pass FutureTask's NEW-state gate and therefore cannot call user code.
        List<BranchExecution> runningAtCancellation =
                executions
            .stream()
            .filter(execution -> execution.runnerStarted.get())
            .toList();
        try {
            for (BranchExecution execution : runningAtCancellation) {
                if (execution.runnerFinished.getCount() == 0) {
                    continue;
                }
                while (execution.runnerFinished.getCount() != 0) {
                    long elapsed = System.nanoTime() - startedAt;
                    long remaining = budgetNanos - Math.max(0L, elapsed);
                    if (remaining <= 0L) {
                        return lingeringBranches(runningAtCancellation);
                    }
                    try {
                        if (!execution.runnerFinished.await(remaining, TimeUnit.NANOSECONDS)) {
                            return lingeringBranches(runningAtCancellation);
                        }
                    } catch (InterruptedException repeatedInterruption) {
                        interrupted = true;
                    }
                }
            }
            return lingeringBranches(runningAtCancellation);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static List<String> lingeringBranches(List<BranchExecution> executions) {
        List<String> lingering = new ArrayList<>();
        for (BranchExecution execution : executions) {
            if (execution.runnerFinished.getCount() != 0) {
                lingering.add(execution.name);
            }
        }
        return lingering;
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static Duration cancellationGracePeriod() {
        return EngineExecutionContextHolder.parallelCancellationGracePeriod();
    }

    private static CompileFlowException cancellationDrainFailure(List<String> lingering, Throwable cause,
            Duration gracePeriod) {
        LOGGER.error("Parallel branches ignored cancellation: branches={}, grace={}", lingering, gracePeriod);
        CompileFlowException failure = new CompileFlowException(ErrorCode.CF_EXEC_007,
                "Parallel branches did not stop within the cancellation grace period", cause);
        failure.withContext("branches", List.copyOf(lingering));
        failure.withContext("cancellationGraceMillis", gracePeriod.toMillis());
        return failure;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private static String taskName(Future<?> future) {
        if (future instanceof CompletionQueueFutureTask<?> completed) {
            return completed.taskName;
        }
        return "unknown";
    }

    private static final class CompletionQueueFutureTask<T> extends FutureTask<T> {
        private final String taskName;
        private final BlockingQueue<Future<T>> completions;
        private final BranchExecution execution;

        CompletionQueueFutureTask(String taskName, Callable<T> task, BlockingQueue<Future<T>> completions,
                BranchExecution execution) {
            super(task);
            this.taskName = taskName;
            this.completions = completions;
            this.execution = execution;
        }

        @Override
        public void run() {
            execution.runnerStarted.set(true);
            try {
                super.run();
            } finally {
                execution.runnerFinished.countDown();
            }
        }

        @Override
        protected void done() {
            completions.add(this);
        }
    }

    private record IndexedResult<T>(int index, T value) {}

    private static final class BranchExecution {
        private final String name;
        private final AtomicBoolean runnerStarted = new AtomicBoolean();
        private final CountDownLatch runnerFinished = new CountDownLatch(1);

        private BranchExecution(String name) {
            this.name = name;
        }
    }
}
