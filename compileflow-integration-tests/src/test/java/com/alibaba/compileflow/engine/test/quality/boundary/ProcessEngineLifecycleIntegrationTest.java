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
package com.alibaba.compileflow.engine.test.quality.boundary;

import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProcessEngineLifecycleIntegrationTest {
    private static final String FLOW =
            """
        <?xml version="1.0" encoding="UTF-8" ?>
        <bpm code="test.lifecycle" name="Lifecycle">
            <var name="result" dataType="java.lang.String" inOutType="return"/>
            <start id="start" name="Start" g="50,50,32,32">
                <transition g=":-15,20" to="script"/>
            </start>
            <scriptTask id="script" name="Blocking Script" g="140,42,120,48">
                <transition g=":-15,20" to="end"/>
                <action type="script" language="blocking">
                        <output dataType="java.lang.String"
                             target="result"/>
                        <code>block()</code>

                </action>
            </scriptTask>
            <end id="end" name="End" g="320,50,32,32"/>
        </bpm>
        """;
    private static final ProcessDefinition.Inline SOURCE = ProcessDefinition.inline("test.lifecycle", FLOW);
    private static final ProcessDefinition.Inline SIMPLE_SOURCE = ProcessDefinition.inline("test.lifecycle.simple",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <bpm code="test.lifecycle.simple" name="Simple Lifecycle">
                <start id="start" name="Start" g="50,50,32,32">
                    <transition to="end"/>
                </start>
                <end id="end" name="End" g="160,50,32,32"/>
            </bpm>
            """);
    private static final ProcessDefinition.Inline CLOSE_FROM_ACTION_SOURCE = ProcessDefinition.inline("test."
            + "lifecycle.close-from-action",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <bpm code="test.lifecycle.close-from-action"
                 name="Close From Action">
                <start id="start" name="Start" g="50,50,32,32">
                    <transition to="action"/>
                </start>
                <scriptTask id="action" name="Closing Action" g="140,42,120,48">
                    <action type="script" language="closing">
                            <code>close()</code>

                        <invocationPolicy attemptTimeout="PT2S" maxAttempts="1" initialBackoff="PT0S"
                                          retryOn="never" onFailure="propagate"/>
                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" name="End" g="320,50,32,32"/>
            </bpm>
            """);

    private static ExecutorService newTaskExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static ScriptExecutor blockingExecutor(CountDownLatch executionStarted,
            CountDownLatch allowExecutionToFinish) {
        return TestScriptExecutors.of("blocking", (source, context) -> {
            executionStarted.countDown();
            try {
                if (!allowExecutionToFinish.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to finish test execution");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test execution was interrupted", interrupted);
            }
            return "completed";
        });
    }

    private static ScriptExecutor closingExecutor(AtomicReference<ProcessEngine> engineReference,
            AtomicReference<RuntimeException> closeFailure) {
        return TestScriptExecutors.of("closing", (source, context) -> {
            try {
                engineReference.get().close();
            } catch (RuntimeException failure) {
                closeFailure.set(failure);
                throw failure;
            }
            return null;
        });
    }

    private static void assertClosed(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(IllegalStateException.class).hasMessage(
                "ProcessEngine is closed");
    }

    private static void awaitShutdownDrain(Thread shutdown) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Thread.State state = shutdown.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Shutdown did not reach executor drain; state=" + shutdown.getState());
    }

    @Test
    void closeWaitsForActiveOperationsAndRejectsEveryLaterEntryPoint() throws Exception {
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch allowExecutionToFinish = new CountDownLatch(1);
        ProcessEngineConfig config = ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .scriptExecutor(blockingExecutor(executionStarted, allowExecutionToFinish))
            .build();

        ExecutorService tasks = newTaskExecutor();
        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            Future<ProcessResult<Map<String, Object>>> execution = tasks.submit(() -> engine.execute(SOURCE, Map.of()));
            assertThat(executionStarted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> shutdown = tasks.submit(engine::close);
            assertThatThrownBy(() -> shutdown.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            allowExecutionToFinish.countDown();
            assertThat(execution.get(10, TimeUnit.SECONDS).isSuccess()).isTrue();
            shutdown.get(10, TimeUnit.SECONDS);

            assertClosed(() -> engine.execute(SOURCE, Map.of()));
            assertClosed(() -> engine.execute(SOURCE, "input", String.class, ProcessExecutionOptions.defaults()));
            assertClosed(() -> engine.trigger(ProcessDefinition.classpath(SOURCE.code(), "test/lifecycle.bpm"),
                    ProcessTrigger.on("node", "event"), Map.of()));
            assertClosed(() -> engine.trigger(ProcessDefinition.classpath(SOURCE.code(), "test/lifecycle.bpm"),
                    ProcessTrigger.on("node", "event"), "input", String.class, ProcessExecutionOptions.defaults()));
            ProcessRef.Version version = ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, SOURCE.code(), "v1");
            assertClosed(() -> engine.runtime().warmUp(SOURCE));
            assertClosed(() -> engine.runtime().load(version, SOURCE));
            assertClosed(() -> engine.runtime().unload(version));
            assertClosed(() -> engine.tooling().preflight(SOURCE, ProcessPreflightOptions.fast()));
            assertClosed(() -> engine.tooling().generateJavaCode(SOURCE));
        } finally {
            allowExecutionToFinish.countDown();
            tasks.shutdownNow();
        }
    }

    @Test
    void timedActionCannotDeadlockByClosingItsOwnEngine() throws Exception {
        AtomicReference<ProcessEngine> engineReference = new AtomicReference<>();
        AtomicReference<RuntimeException> closeFailure = new AtomicReference<>();
        ProcessEngineConfig config = ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .scriptExecutor(closingExecutor(engineReference, closeFailure))
            .build();

        ExecutorService tasks = newTaskExecutor();
        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            engineReference.set(engine);

            ProcessResult<Map<String, Object>> result =
                    tasks
                .submit(() -> engine.execute(CLOSE_FROM_ACTION_SOURCE, Map.of()))
                .get(5, TimeUnit.SECONDS);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError().getCode()).isEqualTo("CF_EXEC_003");
            assertThat(closeFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ProcessEngine cannot be closed from an active operation" + " or engine callback");
            assertThat(engine.tooling().generateJavaCode(CLOSE_FROM_ACTION_SOURCE)).isNotBlank();
        } finally {
            tasks.shutdownNow();
        }
    }

    @Test
    void closeDrainsAReentrantAsyncListenerWithoutHoldingTheLifecycleWriteLock() throws Exception {
        CountDownLatch listenerStarted = new CountDownLatch(1);
        CountDownLatch allowReentry = new CountDownLatch(1);
        AtomicBoolean firstEvent = new AtomicBoolean(true);
        AtomicReference<ProcessEngine> engineReference = new AtomicReference<>();
        AtomicReference<Throwable> listenerFailure = new AtomicReference<>();

        ProcessEngineConfig config =
                ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .eventListener(event -> {
                if (!firstEvent.compareAndSet(true, false)) {
                    return;
                }
                listenerStarted.countDown();
                try {
                    allowReentry.await();
                    engineReference.get().tooling().generateJavaCode(SIMPLE_SOURCE);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    listenerFailure.set(interrupted);
                } catch (Throwable failure) {
                    listenerFailure.set(failure);
                }
            })
            .build();

        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        ProcessEngine engine = ProcessEngineFactory.create(config);
        engineReference.set(engine);
        Thread shutdown = new Thread(() -> {
            try {
                engine.close();
            } catch (Throwable failure) {
                closeFailure.set(failure);
            }
        }, "process-engine-close-test");
        shutdown.setDaemon(true);

        try {
            assertThat(engine.execute(SIMPLE_SOURCE, Map.of()).isSuccess()).isTrue();
            assertThat(listenerStarted.await(10, TimeUnit.SECONDS)).isTrue();

            shutdown.start();
            awaitShutdownDrain(shutdown);
            allowReentry.countDown();

            shutdown.join(TimeUnit.SECONDS.toMillis(2));
            assertThat(shutdown.isAlive()).isFalse();
            assertThat(closeFailure.get()).isNull();
            assertThat(listenerFailure.get()).isInstanceOf(IllegalStateException.class).hasMessage(
                    "ProcessEngine is closed");
        } finally {
            allowReentry.countDown();
            if (shutdown.isAlive()) {
                shutdown.interrupt();
                shutdown.join(TimeUnit.SECONDS.toMillis(10));
            }
            engine.close();
        }
    }
}
