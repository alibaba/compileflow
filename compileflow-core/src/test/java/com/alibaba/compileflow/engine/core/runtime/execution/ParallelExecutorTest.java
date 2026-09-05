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
import static org.assertj.core.groups.Tuple.tuple;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ParallelExecutorTest {
    private ProcessEngineExecutors executors;

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    private static void executeNested(AtomicInteger completed, String prefix) {
        try {
            ParallelExecutor.execute(List.of(ParallelTask.run(prefix + "-1", completed::incrementAndGet),
                    ParallelTask.run(prefix + "-2", completed::incrementAndGet)));
        } catch (Exception failure) {
            throw new IllegalStateException("Nested parallel execution failed", failure);
        }
    }

    @AfterEach
    void tearDown() {
        EngineExecutionContextHolder.clear();
        if (executors != null) {
            executors.close();
        }
    }

    @Test
    void waitsForEveryBranchWithoutAnArbitraryGatewayTimeout() throws Exception {
        installContext();
        CountDownLatch delayedBranchStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        Thread releaser = new Thread(() -> {
            try {
                delayedBranchStarted.await();
                release.countDown();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        });
        releaser.start();

        ParallelExecutor.execute(List.of(ParallelTask.run("delayed", () -> {
                    delayedBranchStarted.countDown();
                    await(release);
                }), ParallelTask.run("immediate", () -> completed.set(true))));

        releaser.join();
        assertThat(completed).isTrue();
    }

    @Test
    void returnsResultsInModelOrderRatherThanCompletionOrder() throws Exception {
        installContext();
        CountDownLatch secondCompleted = new CountDownLatch(1);

        List<String> results =
                ParallelExecutor.execute(List.of(ParallelTask.of("first", () -> {
                            secondCompleted.await();
                            return "first";
                        }), ParallelTask.of("second", () -> {
                            secondCompleted.countDown();
                            return "second";
                        })));

        assertThat(results).containsExactly("first", "second");
    }

    @Test
    void cancelsUnfinishedBranchesAfterTheFirstFailure() {
        installContext();
        CountDownLatch siblingStarted = new CountDownLatch(1);
        AtomicBoolean siblingInterrupted = new AtomicBoolean();

        assertThatThrownBy(() -> ParallelExecutor.execute(List.of(ParallelTask.run("blocked", () -> {
                    siblingStarted.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException failure) {
                        siblingInterrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                }), ParallelTask.run("failing", () -> {
                    await(siblingStarted);
                    throw new IllegalStateException("branch failed");
                }))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("branch failed");

        assertThat(siblingInterrupted).isTrue();
    }

    @Test
    void reportsBranchesThatIgnoreCancellation() throws Exception {
        installContext(Duration.ofMillis(40));
        CountDownLatch siblingStarted = new CountDownLatch(1);
        CountDownLatch siblingStopped = new CountDownLatch(1);
        AtomicBoolean releaseSibling = new AtomicBoolean();

        try {
            assertThatThrownBy(() -> ParallelExecutor.execute(List.of(ParallelTask.run("non-cooperative", () -> {
                        siblingStarted.countDown();
                        try {
                            while (!releaseSibling.get()) {
                                Thread.yield();
                            }
                        } finally {
                            siblingStopped.countDown();
                        }
                    }), ParallelTask.run("failing", () -> {
                        await(siblingStarted);
                        throw new IllegalStateException("branch failed");
                    }))))
                .isInstanceOf(CompileFlowException.class)
                .satisfies(failure -> {
                    CompileFlowException classified = (CompileFlowException) failure;
                    assertThat(classified.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_007);
                    assertThat(classified.getContext())
                        .containsEntry("branches", List.of("non-cooperative"))
                        .containsEntry("cancellationGraceMillis", 40L);
                })
                .hasMessage("Parallel branches did not stop within the cancellation grace period");
        } finally {
            releaseSibling.set(true);
            assertThat(siblingStopped.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void interruptedCallerDrainsCooperativeBranchesBeforeReturning() throws Exception {
        installContext();
        ExecutionContextPropagator.ContextSnapshot snapshot = ExecutionContextPropagator.capture();
        CountDownLatch branchStarted = new CountDownLatch(1);
        CountDownLatch branchStopped = new CountDownLatch(1);
        CountDownLatch callerReturned = new CountDownLatch(1);
        AtomicBoolean branchInterrupted = new AtomicBoolean();
        AtomicBoolean callerInterruptRestored = new AtomicBoolean();
        AtomicBoolean returnedBeforeBranchStopped = new AtomicBoolean();
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        ExecutorService caller = Executors.newSingleThreadExecutor();

        try {
            caller.submit(ExecutionContextPropagator.wrap(() -> {
                callerThread.set(Thread.currentThread());
                try {
                    ParallelExecutor.execute(List.of(ParallelTask.run("cooperative", () -> {
                        branchStarted.countDown();
                        try {
                            new CountDownLatch(1).await();
                        } catch (InterruptedException expected) {
                            branchInterrupted.set(true);
                            Thread.currentThread().interrupt();
                        } finally {
                            branchStopped.countDown();
                        }
                    })));
                } catch (Throwable failure) {
                    callerFailure.set(failure);
                    callerInterruptRestored.set(Thread.currentThread().isInterrupted());
                } finally {
                    returnedBeforeBranchStopped.set(branchStopped.getCount() != 0);
                    callerReturned.countDown();
                }
            }, snapshot));

            assertThat(branchStarted.await(1, TimeUnit.SECONDS)).isTrue();
            callerThread.get().interrupt();
            assertThat(callerReturned.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            caller.shutdownNow();
        }

        assertThat(callerFailure.get()).isInstanceOf(InterruptedException.class);
        assertThat(callerInterruptRestored).isTrue();
        assertThat(branchInterrupted).isTrue();
        assertThat(returnedBeforeBranchStopped).isFalse();
    }

    @Test
    void interruptionDuringFailureDrainPreservesTheOriginalFailure() throws Exception {
        installContext(Duration.ofSeconds(1));
        ExecutionContextPropagator.ContextSnapshot snapshot = ExecutionContextPropagator.capture();
        CountDownLatch branchStarted = new CountDownLatch(1);
        CountDownLatch branchInterrupted = new CountDownLatch(1);
        CountDownLatch allowBranchToStop = new CountDownLatch(1);
        CountDownLatch branchStopped = new CountDownLatch(1);
        CountDownLatch callerReturned = new CountDownLatch(1);
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        AtomicBoolean callerInterruptRestored = new AtomicBoolean();
        AtomicBoolean returnedBeforeBranchStopped = new AtomicBoolean();
        ExecutorService caller = Executors.newSingleThreadExecutor();

        try {
            caller.submit(ExecutionContextPropagator.wrap(() -> {
                callerThread.set(Thread.currentThread());
                try {
                    ParallelExecutor.execute(List.of(ParallelTask.run("slow-stop", () -> {
                                branchStarted.countDown();
                                try {
                                    new CountDownLatch(1).await();
                                } catch (InterruptedException expected) {
                                    branchInterrupted.countDown();
                                    allowBranchToStop.await();
                                } finally {
                                    branchStopped.countDown();
                                }
                            }), ParallelTask.run("failing", () -> {
                                branchStarted.await();
                                throw new IllegalStateException("branch failed");
                            })));
                } catch (Throwable failure) {
                    callerFailure.set(failure);
                    callerInterruptRestored.set(Thread.currentThread().isInterrupted());
                } finally {
                    returnedBeforeBranchStopped.set(branchStopped.getCount() != 0);
                    callerReturned.countDown();
                }
            }, snapshot));

            assertThat(branchInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
            callerThread.get().interrupt();
            allowBranchToStop.countDown();
            assertThat(callerReturned.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            allowBranchToStop.countDown();
            caller.shutdownNow();
        }

        assertThat(callerFailure.get()).isInstanceOf(IllegalStateException.class).hasMessage("branch failed");
        assertThat(callerInterruptRestored).isTrue();
        assertThat(returnedBeforeBranchStopped).isFalse();
    }

    @Test
    void preservesAnAlreadyClassifiedBranchFailure() {
        installContext();
        CompileFlowException expected =
                new CompileFlowException(ErrorCode.CF_EXEC_004, "timed out", new TimeoutException());

        assertThatThrownBy(() -> ParallelExecutor.execute(List.of(ParallelTask.run("timed", () -> {
            throw expected;
        })))).isSameAs(expected);
    }

    @Test
    void nestedParallelExecutionCannotStarveItsOwnChildren() throws Exception {
        installContext();
        AtomicInteger completed = new AtomicInteger();
        ExecutionContextPropagator.ContextSnapshot snapshot = ExecutionContextPropagator.capture();

        ExecutorService caller = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        try {
            Future<Void> result = caller.submit(ExecutionContextPropagator.wrap(() -> {
                ParallelExecutor.execute(List.of(ParallelTask.run("outer-a", () -> executeNested(completed, "a")),
                        ParallelTask.run("outer-b", () -> executeNested(completed, "b"))));
                return null;
            }, snapshot));

            assertThat(result.get(2, TimeUnit.SECONDS)).isNull();
        } finally {
            caller.shutdownNow();
        }
        assertThat(completed).hasValue(4);
    }

    @Test
    void fatalBranchErrorIsPropagatedWithoutBusinessWrapping() {
        installContext();
        AssertionError fatal = new AssertionError("fatal branch");

        assertThatThrownBy(() -> ParallelExecutor.execute(List.of(ParallelTask.run("fatal", () -> {
            throw fatal;
        })))).isSameAs(fatal);
    }

    @Test
    void gatewayBranchesPreserveTheOriginalCheckedFailure() {
        installContext();
        for (int iteration = 0; iteration < 100; iteration++) {
            IOException expected = new IOException("branch failed " + iteration);

            assertThatThrownBy(() -> GatewayExecutor
                .<Void>parallel("parallel")
                .branch(0, "failing", () -> {
                    throw expected;
                })
                .branch(1, "sibling", () -> null)
                .run())
                .isSameAs(expected);
        }
    }

    @Test
    void gatewayBranchesRejectAnExecutionWithNoMatchingBranch() {
        assertThatThrownBy(() -> GatewayExecutor.<Void>inclusive("decision").run())
            .isInstanceOf(CompileFlowException.class)
            .satisfies(failure -> {
                CompileFlowException compileFlowFailure = (CompileFlowException) failure;
                assertThat(compileFlowFailure.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_008);
                assertThat(compileFlowFailure.getContext()).containsEntry("gatewayId", "decision");
            })
            .hasMessage("No outgoing branch matched gateway 'decision'");
    }

    @Test
    void inclusiveGatewayReturnsOnlyActiveBranchesInModelOrder() throws Exception {
        installContext();

        List<GatewayExecutor.BranchResult<String>> results = GatewayExecutor
            .<String>inclusive("inclusive")
            .when(true, 0, "first-target", () -> "first")
            .when(false, 1, "inactive-target", () -> "inactive")
            .when(Boolean.TRUE, 2, "third-target", () -> "third")
            .when((Boolean) null, 3, "null-target", () -> "null")
            .otherwise(4, "default-target", () -> "default")
            .run();

        assertThat(results)
            .extracting(result -> result.branchId().ordinal(), result -> result.branchId().targetId(),
                    GatewayExecutor.BranchResult::value)
            .containsExactly(tuple(0, "first-target", "first"), tuple(2, "third-target", "third"));
    }

    @Test
    void gatewayRejectsDuplicateBranchOrdinalsBeforeExecution() {
        AtomicBoolean ran = new AtomicBoolean();
        GatewayExecutor.ParallelBranches<String> branches =
                GatewayExecutor
            .<String>parallel("parallel")
            .branch(0, "first", () -> {
                ran.set(true);
                return "first";
            });

        assertThatThrownBy(() -> branches.branch(0, "duplicate", () -> "duplicate"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Duplicate branch ordinal 0 for gateway 'parallel'");
        assertThat(ran).isFalse();
    }

    @Test
    void rejectsInvalidBranchesBeforeSubmittingAnyWork() {
        installContext();
        AtomicBoolean submittedBranchRan = new AtomicBoolean();

        assertThatThrownBy(() -> ParallelTask.run(" ", () -> {}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Parallel task name must not be blank");
        assertThatThrownBy(() -> ParallelTask.run(" padded ", () -> {}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThatThrownBy(() -> ParallelTask.run("missing-task", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Parallel task body must not be null");
        assertThatThrownBy(() -> ParallelExecutor.execute(Arrays.asList(ParallelTask.run("valid", () -> submittedBranchRan.set(
                        true)), null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("task");
        assertThat(submittedBranchRan).isFalse();
    }

    private void installContext() {
        installContext(Duration.ofSeconds(2));
    }

    private void installContext(Duration cancellationGracePeriod) {
        ProcessExecutorConfig config = ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxConcurrency(1)
            .actionTimeoutMaxConcurrency(2)
            .parallelCancellationGracePeriod(cancellationGracePeriod)
            .build();
        executors = ProcessEngineExecutors.create("parallel-test", config);
        EngineExecutionContextHolder.set(EngineExecutionContext
            .builder()
            .namespace("default")
            .processCode("parallel.test")
            .modelType(ProcessModelType.TBBPM)
            .processCallInvoker(rejectingProcessCallInvoker())
            .executors(executors)
            .componentResolver(ProcessComponentResolver.disabled())
            .scriptExecutors(ScriptExecutorRegistry.builtIns(ProcessEngineConfig.tbbpm()))
            .build());
    }
}
