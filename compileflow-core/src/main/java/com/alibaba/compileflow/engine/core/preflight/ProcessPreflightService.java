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
package com.alibaba.compileflow.engine.core.preflight;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.core.runtime.ProcessFailureClassifier;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.runtime.loading.ProcessRuntimeLoader;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Validates and compiles process definitions ahead of execution.
 *
 * @author yusu
 */
public final class ProcessPreflightService {
    private final ProcessRuntimeLoader runtimeLoader;
    private final Consumer<ProcessDefinitionSnapshot> linter;
    private final ExecutorService coordinator;

    public ProcessPreflightService(ProcessRuntimeLoader runtimeLoader, Consumer<ProcessDefinitionSnapshot> linter,
            ExecutorService coordinator) {
        this.runtimeLoader = Objects.requireNonNull(runtimeLoader, "runtimeLoader");
        this.linter = Objects.requireNonNull(linter, "linter");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    private static ProcessPreflightReport failureReport(ProcessDefinition definition,
            ProcessPreflightReport.ItemType itemType, ProcessPreflightReport.ItemStatus status, long durationMs,
            String message) {
        return baseReport(definition)
            .addItem(itemType, status, durationMs, message)
            .totalDurationMs(durationMs)
            .build();
    }

    private static ProcessPreflightReport.Builder baseReport(ProcessDefinition definition) {
        return ProcessPreflightReport.builder().code(definition.code());
    }

    private static String failureMessage(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof CompileFlowException compileFlowFailure) {
            String message = compileFlowFailure.getMessage();
            return message == null || message.isBlank() ? compileFlowFailure.getErrorCode().getMessage() : message;
        }
        if (cause instanceof CancellationException) {
            return "preflight was cancelled";
        }
        return "preflight failed unexpectedly";
    }

    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private static void throwIfInterrupted(Throwable failure) {
        if (!Thread.currentThread().isInterrupted() && !causedByInterruption(failure)) {
            return;
        }
        Thread.currentThread().interrupt();
        CancellationException cancellation = new CancellationException("Preflight was interrupted");
        cancellation.initCause(failure);
        throw cancellation;
    }

    private static boolean causedByInterruption(Throwable failure) {
        Throwable cause = failure;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (cause != null && visited.add(cause)) {
            if (cause instanceof InterruptedException || cause instanceof CancellationException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof ExecutionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static ProcessPreflightReport.ItemType initialStage(ProcessPreflightOptions options) {
        return options.isLintEnabled() ? ProcessPreflightReport.ItemType.LINT : ProcessPreflightReport.ItemType.COMPILE;
    }

    public ProcessPreflightReport preflight(ClassLoader classLoader, ProcessDefinition definition,
            ProcessPreflightOptions options) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(options, "options");
        TaskProgress progress = new TaskProgress(initialStage(options));
        long timeoutMillis = options.getTimeout().toMillis();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Future<ProcessPreflightReport> future;
        try {
            future = coordinator.submit(() -> preflightOne(definition, classLoader, options, progress));
        } catch (RejectedExecutionException rejected) {
            throw ProcessFailureClassifier.classify(rejected);
        }
        try {
            return future.get(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            return failureReport(definition, progress.currentStage(), ProcessPreflightReport.ItemStatus.TIMEOUT,
                    timeoutMillis, "preflight timed out");
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw ProcessFailureClassifier.classify(interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = unwrap(failure);
            if (cause instanceof Error error) {
                throw error;
            }
            return failureReport(definition, progress.currentStage(), ProcessPreflightReport.ItemStatus.FAIL, 0L,
                    failureMessage(cause));
        } catch (CancellationException failure) {
            return failureReport(definition, progress.currentStage(), ProcessPreflightReport.ItemStatus.FAIL, 0L,
                    failureMessage(failure));
        }
    }

    private ProcessPreflightReport preflightOne(ProcessDefinition definition, ClassLoader classLoader,
            ProcessPreflightOptions options, TaskProgress progress) {
        long totalStartedAtNanos = System.nanoTime();
        ProcessPreflightReport.Builder report = baseReport(definition);
        ProcessDefinitionSnapshot snapshot = runtimeLoader.resolve(ProcessRuntimeRequest.from(definition), classLoader);
        if (options.isLintEnabled()) {
            progress.currentStage(ProcessPreflightReport.ItemType.LINT);
            long startedAtNanos = System.nanoTime();
            try {
                linter.accept(snapshot);
                report.addItem(ProcessPreflightReport.ItemType.LINT, ProcessPreflightReport.ItemStatus.PASS,
                        elapsedMillis(startedAtNanos), "lint ok");
            } catch (Exception failure) {
                throwIfInterrupted(failure);
                report.addItem(ProcessPreflightReport.ItemType.LINT, ProcessPreflightReport.ItemStatus.FAIL,
                        elapsedMillis(startedAtNanos), failureMessage(failure));
            }
        }

        if (options.isCompileEnabled()) {
            progress.currentStage(ProcessPreflightReport.ItemType.COMPILE);
            long startedAtNanos = System.nanoTime();
            try {
                runtimeLoader.runtimeCheckSync(snapshot, classLoader);
                report.addItem(ProcessPreflightReport.ItemType.COMPILE, ProcessPreflightReport.ItemStatus.PASS,
                        elapsedMillis(startedAtNanos), "compile ok");
            } catch (Exception failure) {
                throwIfInterrupted(failure);
                report.addItem(ProcessPreflightReport.ItemType.COMPILE, ProcessPreflightReport.ItemStatus.FAIL,
                        elapsedMillis(startedAtNanos), failureMessage(failure));
            }
        }

        return report.totalDurationMs(elapsedMillis(totalStartedAtNanos)).build();
    }

    private static final class TaskProgress {
        private volatile ProcessPreflightReport.ItemType currentStage;

        private TaskProgress(ProcessPreflightReport.ItemType initialStage) {
            this.currentStage = initialStage;
        }

        private ProcessPreflightReport.ItemType currentStage() {
            return currentStage;
        }

        private void currentStage(ProcessPreflightReport.ItemType stage) {
            currentStage = stage;
        }
    }
}
