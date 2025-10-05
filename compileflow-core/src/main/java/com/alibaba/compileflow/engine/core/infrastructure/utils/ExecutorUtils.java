package com.alibaba.compileflow.engine.core.infrastructure.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Utility class for Future operations with standardized timeout and cancellation semantics.
 *
 * @author yusu
 */
public class ExecutorUtils {


    /**
     * Waits for Future completion with timeout and no automatic cancellation.
     */
    public static <T> T getWithTimeoutNoCancel(Future<T> future, long timeoutMs)
            throws TimeoutException, ExecutionException, InterruptedException, CancellationException {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive, got: " + timeoutMs);
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        }
    }

    /**
     * Waits for Future completion with timeout and automatic cancellation.
     *
     * @param future    the Future to wait for
     * @param timeoutMs timeout in milliseconds
     * @return the Future result
     * @throws TimeoutException      if timeout occurs
     * @throws ExecutionException    if execution fails
     * @throws InterruptedException  if interrupted
     * @throws CancellationException if Future is cancelled
     */
    public static <T> T getWithTimeout(Future<T> future, long timeoutMs)
            throws TimeoutException, ExecutionException, InterruptedException, CancellationException {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive, got: " + timeoutMs);
        }

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        } catch (CancellationException e) {
            throw e;
        }
    }

    /**
     * Submits a task and waits for completion with timeout and automatic cancellation.
     *
     * @param executor  the executor service
     * @param task      the task to execute
     * @param timeoutMs timeout in milliseconds
     * @return the task result
     * @throws TimeoutException           if timeout occurs
     * @throws ExecutionException         if execution fails
     * @throws InterruptedException       if interrupted
     * @throws CancellationException      if task is cancelled
     * @throws RejectedExecutionException if task submission is rejected
     */
    public static <T> T callWithTimeout(ExecutorService executor, Callable<T> task, long timeoutMs)
            throws TimeoutException, ExecutionException, InterruptedException, CancellationException, RejectedExecutionException {
        Future<T> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException ree) {
            throw ree;
        }
        return getWithTimeout(future, timeoutMs);
    }


    /**
     * Waits for all CompletableFutures to complete with timeout and automatic cancellation.
     *
     * @param futures   the collection of Futures to wait for
     * @param timeoutMs timeout in milliseconds
     * @throws TimeoutException      if timeout occurs
     * @throws InterruptedException  if interrupted
     * @throws ExecutionException    if any Future execution fails
     * @throws CancellationException if any Future is cancelled
     */
    public static void waitAllWithTimeout(Collection<? extends CompletableFuture<?>> futures, long timeoutMs)
            throws TimeoutException, InterruptedException, ExecutionException, CancellationException {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive, got: " + timeoutMs);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        try {
            getWithTimeout(allFutures, timeoutMs);
        } catch (TimeoutException | InterruptedException | CancellationException e) {
            cancelUnfinished(futures);
            throw e;
        }
    }

    /**
     * Collects results from CompletableFutures, separating successes and failures.
     *
     * @param futures the collection of Futures to collect results from
     * @return CollectionResult containing successes and failures
     */
    public static <T> CollectionResult<T> collectResults(Collection<CompletableFuture<T>> futures) {
        List<T> successes = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();

        for (CompletableFuture<T> future : futures) {
            try {
                if (future.isDone() && !future.isCancelled()) {
                    T result = future.get();
                    successes.add(result);
                } else if (future.isCancelled()) {
                    failures.add(new CancellationException("Future was cancelled"));
                }
            } catch (ExecutionException ee) {
                failures.add(ee.getCause() != null ? ee.getCause() : ee);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                failures.add(ie);
            } catch (CancellationException ce) {
                failures.add(ce);
            }
        }

        return new CollectionResult<>(successes, failures);
    }

    /**
     * Cancels all unfinished Futures in the collection.
     *
     * @param futures the collection of Futures to cancel
     */
    public static void cancelUnfinished(Collection<? extends Future<?>> futures) {
        for (Future<?> f : futures) {
            if (!f.isDone()) {
                f.cancel(true);
            }
        }
    }

    /**
     * Cancels all unfinished Futures in the collection, excluding the specified Future.
     *
     * @param futures the collection of Futures to cancel
     * @param exclude the Future to exclude from cancellation
     */
    public static void cancelUnfinished(Collection<? extends Future<?>> futures, Future<?> exclude) {
        for (Future<?> f : futures) {
            if (f != exclude && !f.isDone()) {
                f.cancel(true);
            }
        }
    }

    /**
     * Result container for batch operations with success and failure separation.
     */
    public static class CollectionResult<T> {
        private final List<T> successes;
        private final List<Throwable> failures;

        public CollectionResult(List<T> successes, List<Throwable> failures) {
            this.successes = successes;
            this.failures = failures;
        }

        public List<T> getSuccesses() {
            return successes;
        }

        public List<Throwable> getFailures() {
            return failures;
        }

        public boolean hasFailures() {
            return !failures.isEmpty();
        }

        public boolean hasSuccesses() {
            return !successes.isEmpty();
        }

        public int getSuccessCount() {
            return successes.size();
        }

        public int getFailureCount() {
            return failures.size();
        }

        @Override
        public String toString() {
            return String.format("CollectionResult{successes=%d, failures=%d}",
                    successes.size(), failures.size());
        }
    }


}

