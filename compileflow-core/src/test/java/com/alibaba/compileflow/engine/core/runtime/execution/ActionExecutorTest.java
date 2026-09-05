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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.rejectingProcessCallInvoker;
import static org.assertj.core.api.Assertions.catchThrowable;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.RetryJitter;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.execution.ActionExecutionContext;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.FailureResolution;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ActionExecutorTest {
    private static final String SOURCE_DIGEST = "a".repeat(64);
    private static final String OTHER_SOURCE_DIGEST = "b".repeat(64);
    private ProcessEngineExecutors executors;

    private static EffectiveInvocationPolicy attemptTimeoutPolicy(long attemptTimeoutMillis) {
        return EffectiveInvocationPolicy.of(0L, attemptTimeoutMillis, 1, 0, 1.0, 0, RetryJitter.NONE, "never",
                "propagate");
    }

    private static void await(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        EngineExecutionContextHolder.clear();
        Thread.interrupted();
        if (executors != null) {
            executors.close();
        }
    }

    @Test
    void saturationRejectsWithoutExecutingTheActionOnTheCaller() throws Exception {
        installContext();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        executors
            .action()
            .execute(() -> await(workerStarted, releaseWorker));
        assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        executors.action().execute(() -> {});
        AtomicBoolean actionRan = new AtomicBoolean();

        Throwable failure = catchThrowable(() -> ActionExecutor.call("saturated",
                () -> actionRan.compareAndSet(false, true), attemptTimeoutPolicy(100)));

        assertThat(failure)
            .isInstanceOf(CompileFlowException.class)
            .extracting(value -> ((CompileFlowException) value).getErrorCode())
            .isEqualTo(ErrorCode.CF_EXEC_005);
        assertThat(failure).hasRootCauseInstanceOf(RejectedExecutionException.class);

        assertThat(actionRan).isFalse();
        releaseWorker.countDown();
    }

    @Test
    void timeoutIncludesTimeWaitingForActionCapacity() throws Exception {
        installContext();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        executors
            .action()
            .execute(() -> await(workerStarted, releaseWorker));
        assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        AtomicBoolean actionRan = new AtomicBoolean();

        Throwable failure = catchThrowable(() -> ActionExecutor.call("queued",
                () -> actionRan.compareAndSet(false, true), attemptTimeoutPolicy(50)));

        assertThat(failure)
            .isInstanceOf(CompileFlowException.class)
            .extracting(value -> ((CompileFlowException) value).getErrorCode())
            .isEqualTo(ErrorCode.CF_EXEC_004);
        assertThat(failure).hasRootCauseInstanceOf(TimeoutException.class);

        releaseWorker.countDown();
        assertThat(actionRan).isFalse();
    }

    @Test
    void callerInterruptionCancelsTheActionAndPropagatesImmediately() throws Exception {
        installContext();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        executors
            .action()
            .execute(() -> await(workerStarted, releaseWorker));
        assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> ActionExecutor.call("interrupted", () -> true, attemptTimeoutPolicy(1000)))
            .isInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        releaseWorker.countDown();
    }

    @Test
    void interruptionDuringRetryBackoffIsPreservedAndPropagated() {
        installContext();
        Thread.currentThread().interrupt();
        EffectiveInvocationPolicy retrying =
                EffectiveInvocationPolicy.of(0L, 0, 2, 1000, 1.0, 100000, RetryJitter.NONE, "always", "propagate");

        assertThatThrownBy(() -> ActionExecutor.call("retrying", () -> {
            throw new IllegalStateException("retry");
        }, retrying)).isInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void fatalErrorWithoutTimeoutIsNotRetriedOrHandled() {
        installContext();
        AssertionError fatal = new AssertionError("fatal action");
        AtomicInteger attempts = new AtomicInteger();
        EffectiveInvocationPolicy policy =
                EffectiveInvocationPolicy.of(0L, 0, 4, 0, 1.0, 0, RetryJitter.NONE, "always", "continue");

        assertThatThrownBy(() -> ActionExecutor.call("fatal", () -> {
            attempts.incrementAndGet();
            throw fatal;
        }, policy)).isSameAs(fatal);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void fatalErrorFromTimedWorkerIsPropagated() {
        installContext();
        AssertionError fatal = new AssertionError("fatal timed action");

        assertThatThrownBy(() -> ActionExecutor.call("fatal-timed", () -> {
            throw fatal;
        }, attemptTimeoutPolicy(1000))).isSameAs(fatal);
    }

    @Test
    void rejectsMissingPoliciesBeforeTheActionCanRun() {
        installContext();
        AtomicBoolean actionRan = new AtomicBoolean();
        EffectiveInvocationPolicy customPolicies =
                EffectiveInvocationPolicy.of(0L, 0, 2, 0, 1.0, 0, RetryJitter.NONE, "custom-retry", "custom-failure");

        assertThatThrownBy(() -> ActionExecutor.call("invalid", () -> {
            actionRan.set(true);
            return "done";
        }, customPolicies))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .extracting(failure -> ((CompileFlowException) failure).getErrorCode())
            .isEqualTo(ErrorCode.CF_CONFIG_003);
        assertThat(actionRan).isFalse();
    }

    @Test
    void rejectsMissingFailureHandlerBeforeTheActionCanRun() {
        installContext();
        AtomicBoolean actionRan = new AtomicBoolean();
        EffectiveInvocationPolicy customPolicies =
                EffectiveInvocationPolicy.of(0L, 0, 1, 0, 1.0, 0, RetryJitter.NONE, "never", "custom-failure");

        assertThatThrownBy(() -> ActionExecutor.call("invalid", () -> {
            actionRan.set(true);
            return "done";
        }, customPolicies))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .extracting(failure -> ((CompileFlowException) failure).getErrorCode())
            .isEqualTo(ErrorCode.CF_CONFIG_003);
        assertThat(actionRan).isFalse();
    }

    @Test
    void resolvesNamedPoliciesFromTheExecutionConfiguration() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        installContext(Map.of("custom-retry", failure -> true),
                Map.of("custom-failure", context -> FailureResolution.CONTINUE_PROCESS));
        EffectiveInvocationPolicy customPolicies =
                EffectiveInvocationPolicy.of(0L, 0, 2, 0, 1.0, 0, RetryJitter.NONE, "custom-retry", "custom-failure");

        Object result = ActionExecutor.call("custom", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("retry me");
        }, customPolicies);

        assertThat(result).isNull();
        assertThat(attempts).hasValue(2);
    }

    @Test
    void rejectsANullCustomFailureResolution() {
        installContext(Map.of(), Map.of("invalid-failure", context -> null));
        EffectiveInvocationPolicy customPolicies =
                EffectiveInvocationPolicy.of(0L, 0, 1, 0, 1.0, 0, RetryJitter.NONE, "never", "invalid-failure");

        assertThatThrownBy(() -> ActionExecutor.call("custom", () -> {
            throw new IllegalStateException("failed");
        }, customPolicies))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .extracting(failure -> ((CompileFlowException) failure).getErrorCode())
            .isEqualTo(ErrorCode.CF_CONFIG_003);
    }

    @Test
    void preservesAnExplicitCompileFlowFailureAfterRetriesAreExhausted() {
        installContext();
        CompileFlowException expected = new CompileFlowException(ErrorCode.CF_EXEC_008, "declined");

        assertThatThrownBy(() -> ActionExecutor.call("typed", () -> {
            throw expected;
        }, attemptTimeoutPolicy(0))).isSameAs(expected);
    }

    @Test
    void propagatesDirectActionInterruptionWithoutRetryOrFailureHandling() {
        installContext();
        AtomicInteger attempts = new AtomicInteger();
        EffectiveInvocationPolicy retrying =
                EffectiveInvocationPolicy.of(0L, 0, 4, 0, 1.0, 0, RetryJitter.NONE, "always", "continue");

        assertThatThrownBy(() -> ActionExecutor.call("interrupted", () -> {
            attempts.incrementAndGet();
            throw new InterruptedException("stop");
        }, retrying))
            .isInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(attempts).hasValue(1);
    }

    @Test
    void timedOutAttemptStopsBeforeRetryStarts() throws Exception {
        installContext(Duration.ofSeconds(1));
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger activeAttempts = new AtomicInteger();
        AtomicInteger maxActiveAttempts = new AtomicInteger();
        EffectiveInvocationPolicy retrying =
                EffectiveInvocationPolicy.of(0L, 30, 2, 0, 1.0, 0, RetryJitter.NONE, "always", "propagate");

        String result = ActionExecutor.call("timeout-retry", () -> {
            int attempt = attempts.incrementAndGet();
            int active = activeAttempts.incrementAndGet();
            maxActiveAttempts.accumulateAndGet(active, Math::max);
            try {
                if (attempt == 1) {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                }
                return "done";
            } finally {
                activeAttempts.decrementAndGet();
            }
        }, retrying);

        assertThat(result).isEqualTo("done");
        assertThat(attempts).hasValue(2);
        assertThat(maxActiveAttempts).hasValue(1);
        assertThat(activeAttempts).hasValue(0);
    }

    @Test
    void continuedTimeoutDoesNotCommitTheLateAttemptResult() throws Exception {
        installContext(Duration.ofSeconds(1));
        AtomicReference<String> committed = new AtomicReference<>("initial");
        EffectiveInvocationPolicy continuing =
                EffectiveInvocationPolicy.of(0L, 20, 1, 0, 1.0, 0, RetryJitter.NONE, "never", "continue");

        ActionExecutor.callAndCommit("timeout-output", () -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException ignored) {
                return "late";
            }
            return "unexpected";
        }, continuing, committed::set);

        assertThat(committed).hasValue("initial");
    }

    @Test
    void actionIgnoringCancellationFailsClosedWithoutRetry() {
        installContext(Duration.ofMillis(40));
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        EffectiveInvocationPolicy retrying =
                EffectiveInvocationPolicy.of(0L, 20, 4, 0, 1.0, 0, RetryJitter.NONE, "always", "propagate");

        try {
            assertThatThrownBy(() -> ActionExecutor.call("ignores-cancellation", () -> {
                attempts.incrementAndGet();
                while (release.getCount() > 0L) {
                    try {
                        release.await();
                    } catch (InterruptedException ignored) {
                        // Deliberately model a non-cooperative
                        // action.
                    }
                }
                return "too late";
            }, retrying))
                .isInstanceOf(CompileFlowException.class)
                .extracting(value -> ((CompileFlowException) value).getErrorCode())
                .isEqualTo(ErrorCode.CF_EXEC_007);
        } finally {
            release.countDown();
        }
        assertThat(attempts).hasValue(1);
    }

    @Test
    void invocationTimeoutExpiresDuringBackoffWithoutStartingAnotherAttempt() {
        installContext();
        AtomicInteger attempts = new AtomicInteger();
        EffectiveInvocationPolicy bounded =
                EffectiveInvocationPolicy.of(50, 0, 4, 200, 1.0, 200, RetryJitter.NONE, "always", "propagate");

        Throwable failure = catchThrowable(() -> ActionExecutor.call("overall-backoff", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("retry");
        }, bounded));

        assertThat(failure)
            .isInstanceOf(CompileFlowException.class)
            .extracting(value -> ((CompileFlowException) value).getErrorCode())
            .isEqualTo(ErrorCode.CF_EXEC_004);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void remainingInvocationBudgetCapsTheNextAttempt() {
        installContext(Duration.ofSeconds(1));
        AtomicInteger attempts = new AtomicInteger();
        EffectiveInvocationPolicy bounded =
                EffectiveInvocationPolicy.of(120, 100, 3, 0, 1.0, 0, RetryJitter.NONE, "always", "propagate");

        Throwable failure = catchThrowable(() -> ActionExecutor.call("overall-cap", () -> {
            if (attempts.incrementAndGet() == 1) {
                Thread.sleep(60);
                throw new IllegalStateException("retry");
            }
            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            return "late";
        }, bounded));

        assertThat(failure)
            .isInstanceOf(CompileFlowException.class)
            .extracting(value -> ((CompileFlowException) value).getErrorCode())
            .isEqualTo(ErrorCode.CF_EXEC_004);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void invocationTimeoutUsesFailureHandlerWithoutRetrying() throws Exception {
        installContext(Duration.ofSeconds(1));
        AtomicInteger attempts = new AtomicInteger();
        EffectiveInvocationPolicy bounded =
                EffectiveInvocationPolicy.of(30, 30, 4, 0, 1.0, 0, RetryJitter.NONE, "always", "continue");

        Object result = ActionExecutor.call("overall-continue", () -> {
            attempts.incrementAndGet();
            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            return "late";
        }, bounded);

        assertThat(result).isNull();
        assertThat(attempts).hasValue(1);
    }

    @Test
    void keepsOneInvocationKeyAcrossRetriesAndExposesAttemptNumbers() throws Exception {
        installContext();
        AtomicInteger calls = new AtomicInteger();
        List<ActionExecutionContext> observed = new ArrayList<>();
        EffectiveInvocationPolicy retrying =
                EffectiveInvocationPolicy.of(0L, 0, 2, 0, 1.0, 0, RetryJitter.FULL, "always", "propagate");

        String result = ActionExecutor.call("retry-node", () -> {
            observed.add(ActionExecutionContext.current());
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("retry");
            }
            return "done";
        }, retrying);

        assertThat(result).isEqualTo("done");
        assertThat(observed).hasSize(2);
        assertThat(observed).extracting(ActionExecutionContext::getAttemptNumber).containsExactly(1, 2);
        assertThat(observed)
            .extracting(ActionExecutionContext::getInvocationKey)
            .containsOnly(observed.get(0).getInvocationKey());
        assertThat(observed.get(0).getProcessInvocationId()).isEqualTo("action-test-invocation");
        assertThat(observed.get(0).getNamespace()).isEqualTo("default");
        assertThat(observed.get(0).getProcessCode()).isEqualTo("action.test");
        assertThat(observed.get(0).getModelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(observed.get(0).getSourceDigest()).isEqualTo(SOURCE_DIGEST);
        assertThat(ActionExecutionContext.currentOptional()).isEmpty();
    }

    @Test
    void allocatesDistinctKeysForRepeatedInvocationsOfTheSameNode() throws Exception {
        installContext();
        AtomicReference<ActionExecutionContext> first = new AtomicReference<>();
        AtomicReference<ActionExecutionContext> second = new AtomicReference<>();

        ActionExecutor.run("loop-node", () -> first.set(ActionExecutionContext.current()),
                EffectiveInvocationPolicy.defaults());
        ActionExecutor.run("loop-node", () -> second.set(ActionExecutionContext.current()),
                EffectiveInvocationPolicy.defaults());

        assertThat(first.get().getInvocationOrdinal()).isEqualTo(1L);
        assertThat(second.get().getInvocationOrdinal()).isEqualTo(2L);
        assertThat(first.get().getInvocationKey()).isNotEqualTo(second.get().getInvocationKey());
    }

    @Test
    void scopesInvocationKeysToTheExactProcessArtifact() {
        String first = new ActionExecutionContext("logical-process-invocation", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, "charge", 1L, 1)
            .getInvocationKey();
        String replay = new ActionExecutionContext("logical-process-invocation", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, "charge", 1L, 2)
            .getInvocationKey();
        String changedSource = new ActionExecutionContext("logical-process-invocation", "default", "order.process",
                ProcessModelType.TBBPM, OTHER_SOURCE_DIGEST, "charge", 1L, 1)
            .getInvocationKey();

        assertThat(replay).isEqualTo(first);
        assertThat(changedSource).isNotEqualTo(first);
        assertThat(first).startsWith("cfai_");
    }

    @Test
    void rejectsIncompleteInvocationKeyIdentity() {
        assertThatThrownBy(() -> new ActionExecutionContext("logical-process-invocation", "default", "order.process",
                ProcessModelType.TBBPM, null, "charge", 1L, 1))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("sourceDigest");
        assertThatThrownBy(() -> new ActionExecutionContext("logical-process-invocation", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, "charge", 0L, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invocationOrdinal");
    }

    @Test
    void directScopeRejectsExecutionWithoutAnEngineContext() {
        assertThatThrownBy(() -> ActionExecutor.open("orphan-action"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("engine execution context");
    }

    @Test
    void policyPathRejectsExecutionBeforeRuntimeIdentityIsRecorded() {
        installContext(false);

        assertThatThrownBy(() -> ActionExecutor.run("unresolved-action", () -> {}, attemptTimeoutPolicy(0)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("runtime source digest");
    }

    @Test
    void exposesActionContextInsideTheTimedWorker() throws Exception {
        installContext();
        AtomicReference<ActionExecutionContext> observed = new AtomicReference<>();

        String result = ActionExecutor.call("timed-context", () -> {
            observed.set(ActionExecutionContext.current());
            return "done";
        }, attemptTimeoutPolicy(1000));

        assertThat(result).isEqualTo("done");
        assertThat(observed.get().getNodeId()).isEqualTo("timed-context");
        assertThat(observed.get().getAttemptNumber()).isEqualTo(1);
        assertThat(ActionExecutionContext.currentOptional()).isEmpty();
    }

    @Test
    void directScopeExposesExactIdentityAndRestoresThePreviousContext() {
        installContext();
        ActionExecutionContext observed;
        ActionExecutor.ActionScope scope = ActionExecutor.open("direct-node");

        try (scope) {
            observed = ActionExecutionContext.current();
        }

        assertThat(observed.getProcessInvocationId()).isEqualTo("action-test-invocation");
        assertThat(observed.getProcessCode()).isEqualTo("action.test");
        assertThat(observed.getModelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(observed.getSourceDigest()).isEqualTo(SOURCE_DIGEST);
        assertThat(observed.getNodeId()).isEqualTo("direct-node");
        assertThat(observed.getInvocationOrdinal()).isEqualTo(1L);
        assertThat(observed.getAttemptNumber()).isEqualTo(1);
        assertThat(observed.getInvocationKey()).startsWith("cfai_");
        assertThat(ActionExecutionContext.currentOptional()).isEmpty();
    }

    @Test
    void directScopeNormalizesFailuresAndAlwaysCleansUpContext() {
        installContext();
        ActionExecutor.ActionScope scope = ActionExecutor.open("direct-failure");

        Throwable failure = catchThrowable(() -> {
            try (scope) {
                throw new IllegalStateException("planned direct failure");
            } catch (Exception actionFailure) {
                throw scope.failure(actionFailure);
            }
        });

        assertThat(failure)
            .isInstanceOf(CompileFlowException.class)
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("planned direct failure");
        assertThat(((CompileFlowException) failure).getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_001);
        assertThat(((CompileFlowException) failure).getContext())
            .containsEntry("nodeId", "direct-failure")
            .containsEntry("attempts", 1);
        assertThat(ActionExecutionContext.currentOptional()).isEmpty();
    }

    @Test
    void directScopeHandlesFailuresOnlyAfterRestoringContext() {
        installContext();
        ActionExecutor.ActionScope scope = ActionExecutor.open("direct-order");
        IllegalStateException actionFailure = new IllegalStateException("planned");

        try {
            assertThatThrownBy(() -> scope.failure(actionFailure))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must close")
                .hasCause(actionFailure);
        } finally {
            scope.close();
        }
        assertThat(ActionExecutionContext.currentOptional()).isEmpty();
    }

    @Test
    void directScopePreservesInterruption() {
        installContext();
        ActionExecutor.ActionScope scope = ActionExecutor.open("direct-interruption");

        Throwable failure = catchThrowable(() -> {
            try (scope) {
                throw new InterruptedException("stop");
            } catch (Exception actionFailure) {
                throw scope.failure(actionFailure);
            }
        });

        assertThat(failure).isInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(ActionExecutionContext.currentOptional()).isEmpty();
    }

    @Test
    void computesCappedBackoffAndDeterministicFullJitter() {
        assertThat(ActionExecutor.computeBackoffDelay(100L, 2.0d, 4, 500L)).isEqualTo(500L);
        assertThat(ActionExecutor.computeRetryDelay(100L, 2.0d, 3, 1000L, RetryJitter.NONE, () -> 0.25d)).isEqualTo(
                400L);
        assertThat(ActionExecutor.computeRetryDelay(100L, 2.0d, 3, 1000L, RetryJitter.FULL, () -> 0.0d)).isZero();
        assertThat(ActionExecutor.computeRetryDelay(100L, 2.0d, 3, 1000L, RetryJitter.FULL, () -> Math.nextDown(1.0d)))
            .isEqualTo(400L);
    }

    private void installContext() {
        installContext(Map.of(), Map.of());
    }

    private void installContext(boolean recordRuntime) {
        installContext(Map.of(), Map.of(), Duration.ofSeconds(2), recordRuntime);
    }

    private void installContext(Duration cancellationGracePeriod) {
        installContext(Map.of(), Map.of(), cancellationGracePeriod);
    }

    private void installContext(Map<String, RetryPolicy> retryPolicies, Map<String, FailureHandler> failureHandlers) {
        installContext(retryPolicies, failureHandlers, Duration.ofSeconds(2));
    }

    private void installContext(Map<String, RetryPolicy> retryPolicies, Map<String, FailureHandler> failureHandlers,
            Duration cancellationGracePeriod) {
        installContext(retryPolicies, failureHandlers, cancellationGracePeriod, true);
    }

    private void installContext(Map<String, RetryPolicy> retryPolicies, Map<String, FailureHandler> failureHandlers,
            Duration cancellationGracePeriod, boolean recordRuntime) {
        ProcessExecutorConfig config = ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxConcurrency(1)
            .actionTimeoutMaxConcurrency(1)
            .actionTimeoutMaxPending(1)
            .actionTimeoutCancellationGracePeriod(cancellationGracePeriod)
            .build();
        executors = ProcessEngineExecutors.create("action-test", config);
        EngineExecutionContext context = EngineExecutionContext
            .builder()
            .invocationId("action-test-invocation")
            .namespace("default")
            .processCode("action.test")
            .modelType(ProcessModelType.TBBPM)
            .sourceDigest(recordRuntime ? SOURCE_DIGEST : null)
            .processCallInvoker(rejectingProcessCallInvoker())
            .executors(executors)
            .componentResolver(ProcessComponentResolver.disabled())
            .scriptExecutors(ScriptExecutorRegistry.builtIns(ProcessEngineConfig.tbbpm()))
            .retryPolicies(retryPolicies)
            .failureHandlers(failureHandlers)
            .build();
        EngineExecutionContextHolder.set(context);
    }
}
