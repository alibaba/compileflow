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
package com.alibaba.compileflow.engine.test.quality.concurrent;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessContextBuilder;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@Tag("integration")
@Tag("concurrent")
@Tag("slow")
@DisplayName("Concurrent Execution Tests")
class ConcurrentExecutionTest {
    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.create();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("should handle concurrent executions without deadlock")
    void shouldHandleConcurrentExecutionsWithoutDeadlock() throws InterruptedException {
        // Concurrency parameters.
        int threadCount = 20;
        int executionsPerThread = 10;
        int totalExecutions = threadCount * executionsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(totalExecutions);
        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        // Concurrent execution.
        for (int i = 0; i < totalExecutions; i++) {
            final int executionId = i;
            executor.submit(() -> {
                try {
                    Map<String, Object> context =
                            ProcessContextBuilder
                        .newContext()
                        .withCalculation(executionId % 100, (executionId + 1) % 100)
                        .build();

                    ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                                    "bpmn20." + "compat.simple_service",
                                    "bpmn20.compat.simple_service".replace(".", "/") + ".bpmn"), context);

                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failures.add(new AssertionError("Execution failed: " + result.getError()));
                    }
                } catch (Throwable failure) {
                    failures.add(failure);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed;
        try {
            completed = latch.await(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(completed).as("All executions should complete without deadlock").isTrue();

        assertThat(failures).as("Concurrent executions should not throw exceptions").isEmpty();

        assertThat(successCount.get()).as("Every concurrent execution should succeed").isEqualTo(totalExecutions);
    }

    @Test
    @DisplayName("should handle high-frequency executions without resource exhaustion")
    void shouldHandleHighFrequencyExecutions() {
        // Execution count.
        int executionCount = 1000;
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < executionCount; i++) {
            try {
                Map<String, Object> context =
                        ProcessContextBuilder.newContext().withCalculation(i % 100, (i + 1) % 100).build();

                ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                                "bpmn20." + "compat.simple_service",
                                "bpmn20.compat.simple_service".replace(".", "/") + ".bpmn"), context);

                if (result.isSuccess()) {
                    successCount.incrementAndGet();
                }
            } catch (OutOfMemoryError failure) {
                throw new AssertionError("Resource exhaustion detected", failure);
            }
        }

        assertThat(successCount.get()).as("Every repeated execution should succeed").isEqualTo(executionCount);
    }

    @Test
    @DisplayName("should handle concurrent compilations safely")
    void shouldHandleConcurrentCompilations() throws InterruptedException {
        // Select multiple different structured processes as compilation/execution entry points.
        String[] flowCodes = {"bpmn20.compat.simple_service", "bpmn20.gateway.exclusive_gateway",
                "bpmn20.gateway.parallel_gateway", "bpmn20.compat.standard_loop"};

        int threadsPerFlow = 5;
        int totalThreads = flowCodes.length * threadsPerFlow;

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch latch = new CountDownLatch(totalThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        // Concurrent compilation triggering (indirectly via execute).
        for (String flowCode : flowCodes) {
            for (int i = 0; i < threadsPerFlow; i++) {
                executor.submit(() -> {
                    try {
                        ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                                        flowCode, flowCode.replace(".", "/") + ".bpmn"), compilationInput(flowCode));

                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failures.add(
                                    new AssertionError(
                                            "Compilation execution failed for " + flowCode + ": " + result.getError()));
                        }
                    } catch (Throwable failure) {
                        failures.add(failure);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        // Wait for completion.
        boolean completed;
        try {
            completed = latch.await(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(completed).as("All compilations should complete").isTrue();

        assertThat(successCount.get()).as("Every concurrent compilation should succeed").isEqualTo(totalThreads);

        assertThat(failures).as("Concurrent compilation should not fail").isEmpty();
    }

    private static Map<String, Object> compilationInput(String flowCode) {
        return switch (flowCode) {
            case "bpmn20.compat.simple_service", "bpmn20.gateway.parallel_gateway" -> Map.of("a", 5, "b", 3);
            case "bpmn20.gateway.exclusive_gateway" -> Map.of("flag", true);
            case "bpmn20.compat.standard_loop" -> Map.of();
            default -> throw new IllegalArgumentException("Unsupported test process: " + flowCode);
        };
    }

    @Test
    @DisplayName("should handle thread pool saturation gracefully")
    void shouldHandleThreadPoolSaturation() throws InterruptedException {
        // Small thread pool + large number of tasks.
        int poolSize = 4;
        int taskCount = 100;

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completedCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        // Submit tasks.
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    // Add some execution pressure.
                    ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                                    "bpmn20." + "compat.simple_service",
                                    "bpmn20.compat.simple_service".replace(".", "/") + ".bpmn"),
                            ProcessContextBuilder.newContext().withCalculation(taskId, taskId + 1).build());

                    if (result.isSuccess()) {
                        completedCount.incrementAndGet();
                    } else {
                        failures.add(new AssertionError("Saturated execution failed: " + result.getError()));
                    }
                } catch (Throwable failure) {
                    failures.add(failure);
                } finally {
                    latch.countDown();
                }
            });
        }
        // Wait for completion.
        boolean completed;
        try {
            completed = latch.await(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(completed).as("All tasks should complete despite saturation").isTrue();

        assertThat(completedCount.get()).as("Every submitted task should execute successfully").isEqualTo(taskCount);

        assertThat(failures).as("Saturated execution should not lose tasks").isEmpty();
    }
}
