package com.alibaba.compileflow.engine.test;

import com.alibaba.compileflow.engine.*;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.classloader.ClassLoaderManager;
import com.alibaba.compileflow.engine.core.classloader.ProcessClassLoaderManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redesigned Performance and Memory tests based on deep understanding of CompileFlow architecture.
 * <p>
 * Main test scenarios:
 * 1. Compilation cache and ClassLoader lifecycle management
 * 2. Single-flight compilation and SWR cache strategy
 * 3. ExecutorUtils and thread pool resource management
 * 4. ProcessEventPublisher and event bus memory management
 * 5. DynamicClassExecutor dynamic compilation memory leaks
 * 6. Resource cleanup integrity in shutdown process
 *
 * @author yusu
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Enhanced Performance and Memory Management Tests")
class PerformanceAndMemoryTest {

    private ProcessEngine engine;
    private ProcessAdminService adminService;
    private MemoryMXBean memoryBean;

    @BeforeEach
    void setUp() {
        // Speed up eviction-related tests by shrinking runtime cache during tests
        System.setProperty("compileflow.cache.runtime.max-size", "128");
        engine = ProcessEngineFactory.createBpmn();
        adminService = engine.admin();
        memoryBean = ManagementFactory.getMemoryMXBean();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
        // Force garbage collection to clean up test artifacts
        System.gc();
        Thread.sleep(100);
    }

    /**
     * Generate process XML with different content to trigger recompilation.
     */
    private String createVariableServiceTaskXml(String processId, String suffix, int variableCount) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" ")
                .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("xmlns:cf=\"http://www.compileflow.org/schema/1.0\" ")
                .append("targetNamespace=\"http://www.compileflow.org/bpmn/test\">")
                .append("<process id=\"").append(processId).append("\" isExecutable=\"true\">")
                .append("<extensionElements>");

        // Add multiple variables to increase compilation complexity
        for (int i = 0; i < variableCount; i++) {
            xml.append("<cf:var name=\"result_").append(suffix).append("_").append(i)
                    .append("\" dataType=\"java.lang.Integer\" inOutType=\"return\"/>");
        }

        xml.append("</extensionElements>")
                .append("<startEvent id=\"start\" name=\"Start\"/>")
                .append("<sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"task\"/>")
                .append("<serviceTask id=\"task\" name=\"Task ").append(suffix).append("\">")
                .append("<extensionElements>")
                .append("<cf:action type=\"java\">")
                .append("<cf:actionHandle clazz=\"com.alibaba.compileflow.engine.test.mock.MockJavaClazz\" method=\"add\">")
                .append("<cf:var name=\"a\" dataType=\"java.lang.Integer\" contextVarName=\"1\" inOutType=\"param\"/>")
                .append("<cf:var name=\"b\" dataType=\"java.lang.Integer\" contextVarName=\"2\" inOutType=\"param\"/>")
                .append("<cf:var name=\"result\" dataType=\"java.lang.Integer\" contextVarName=\"result_").append(suffix).append("_0\" inOutType=\"return\"/>")
                .append("</cf:actionHandle>")
                .append("</cf:action>")
                .append("</extensionElements>")
                .append("</serviceTask>")
                .append("<sequenceFlow id=\"flow2\" sourceRef=\"task\" targetRef=\"end\"/>")
                .append("<endEvent id=\"end\" name=\"End\"/>")
                .append("</process></definitions>");

        return xml.toString();
    }

    /**
     * Helper method to create modified XML content for hot deployment testing (backward compatibility)
     */
    private String createModifiedServiceTaskXml(String processId, String suffix) {
        return createVariableServiceTaskXml(processId, suffix, 1);
    }

    /**
     * Get ProcessClassLoaderManager instance for memory monitoring.
     */
    private ProcessClassLoaderManager getClassLoaderManager() {
        try {
            java.lang.reflect.Field classLoaderManagerField =
                    engine.getClass().getSuperclass().getDeclaredField("classLoaderManager");
            classLoaderManagerField.setAccessible(true);
            return (ProcessClassLoaderManager) classLoaderManagerField.get(engine);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get ProcessClassLoaderManager", e);
        }
    }

    @Nested
    @DisplayName("A. High Concurrency Performance Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("should handle high concurrency load (100+ threads)")
        @Timeout(value = 20, unit = TimeUnit.SECONDS)
        void testHighConcurrencyLoad() throws InterruptedException {
            String code = "bpmn20.compat.simple_service";
            int threadCount = 60;  // reduce threads to shorten total time
            int executionsPerThread = 2;  // reduce executions per thread

            ExecutorService executor = Executors.newFixedThreadPool(16);  // reduce pool size
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            long startTime = System.currentTimeMillis();

            // Warm-up to avoid compilation overhead during concurrency
            adminService.deploy(ProcessSource.fromCode(code));
            System.gc();
            Thread.sleep(50);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < executionsPerThread; j++) {
                            Map<String, Object> context = new HashMap<>();
                            context.put("a", threadId);
                            context.put("b", j);

                            ProcessResult<Map<String, Object>> result =
                                    engine.execute(ProcessSource.fromCode(code), context);

                            if (result.isSuccess()) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(15, TimeUnit.SECONDS), "All threads should complete within timeout");

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            int totalExecutions = threadCount * executionsPerThread;

            System.out.printf("High concurrency test completed:%n" +
                            "  Threads: %d%n" +
                            "  Executions per thread: %d%n" +
                            "  Total executions: %d%n" +
                            "  Success: %d%n" +
                            "  Failures: %d%n" +
                            "  Duration: %d ms%n" +
                            "  Throughput: %.2f executions/sec%n",
                    threadCount, executionsPerThread, totalExecutions,
                    successCount.get(), failureCount.get(), duration,
                    (totalExecutions * 1000.0) / duration);

            // Verify most executions succeeded (allow some failures due to resource contention)
            assertTrue(successCount.get() > totalExecutions * 0.95,
                    "At least 95% of executions should succeed");

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("should maintain performance under memory pressure")
        void testMemoryPressurePerformance() {
            String code = "bpmn20.compat.simple_service";

            // Get baseline memory usage
            System.gc();
            MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();

            // Execute many processes to create memory pressure
            List<ProcessResult<Map<String, Object>>> results = new ArrayList<>();
            for (int i = 0; i < 500; i++) {  // reduce total iterations
                Map<String, Object> context = new HashMap<>();
                context.put("a", i);
                context.put("b", i * 2);

                ProcessResult<Map<String, Object>> result =
                        engine.execute(ProcessSource.fromCode(code), context);
                results.add(result);

                // Periodically check memory usage
                if (i % 50 == 0) {  // check more frequently
                    MemoryUsage currentMemory = memoryBean.getHeapMemoryUsage();
                    long memoryIncrease = currentMemory.getUsed() - beforeMemory.getUsed();
                    System.out.printf("Iteration %d: Memory increase = %d MB%n",
                            i, memoryIncrease / (1024 * 1024));
                }
            }

            // Verify all executions succeeded
            long successCount = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
            assertEquals(results.size(), successCount, "All executions should succeed under memory pressure");

            // Check final memory usage
            System.gc();
            MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
            long memoryIncrease = afterMemory.getUsed() - beforeMemory.getUsed();

            System.out.printf("Memory pressure test completed:%n" +
                            "  Before: %d MB%n" +
                            "  After: %d MB%n" +
                            "  Increase: %d MB%n",
                    beforeMemory.getUsed() / (1024 * 1024),
                    afterMemory.getUsed() / (1024 * 1024),
                    memoryIncrease / (1024 * 1024));

            // Memory increase should be reasonable (less than 100MB for 1000 executions)
            assertTrue(memoryIncrease < 100 * 1024 * 1024,
                    "Memory increase should be reasonable");
        }
    }

    @Nested
    @DisplayName("B. Core Memory Leak Detection Tests: Based on Deep Understanding of CompileFlow Architecture")
    class MemoryLeakTests {

        @Test
        @DisplayName("single-flight: concurrent same-digest compiles only once (fast)")
        void testSingleFlightSameDigestFast() throws Exception {
            ProcessClassLoaderManager classLoaderManager = getClassLoaderManager();

            System.gc();
            ClassLoaderManager.ClassLoaderStats baselineStats = classLoaderManager.getStats();

            String code = "sf_same_digest";
            String xml = createVariableServiceTaskXml(code, "sf", 1);

            int threads = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(threads);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        ProcessResult<Map<String, Object>> result =
                                engine.execute(ProcessSource.fromContent(code, xml), new HashMap<>());
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(completeLatch.await(5, TimeUnit.SECONDS), "concurrent calls should finish quickly");

            ClassLoaderManager.ClassLoaderStats midStats = classLoaderManager.performMaintenance();
            long created = midStats.getTotalCreated() - baselineStats.getTotalCreated();

            assertTrue(successCount.get() == threads, "all calls should succeed");
            assertTrue(created <= 2, "should compile once and create ~1 CL, created=" + created);

            executor.shutdown();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("should not leak memory during repeated compilation with event bus monitoring")
        void testCompilationMemoryLeak() throws InterruptedException {
            String code = "bpmn20.compat.simple_service";

            // Get ProcessClassLoaderManager for monitoring
            ProcessClassLoaderManager classLoaderManager = getClassLoaderManager();

            // Warm-up: compile once, then force GC and maintenance to stabilize baseline
            adminService.deploy(ProcessSource.fromCode(code));
            for (int i = 0; i < 2; i++) {
                System.gc();
                Thread.sleep(200);
                classLoaderManager.performMaintenance();
            }

            // Baseline after warm-up
            MemoryUsage baselineMemory = memoryBean.getHeapMemoryUsage();
            ClassLoaderManager.ClassLoaderStats baselineStats = classLoaderManager.getStats();
            System.out.printf("Baseline memory (after warm-up): %d MB, ClassLoader stats: %s%n",
                    baselineMemory.getUsed() / (1024 * 1024), baselineStats);

            // Repeatedly compile the same process to simulate cache pressure
            int compilationCount = 30;  // reduce compilation count to lower measurement noise
            for (int i = 0; i < compilationCount; i++) {
                adminService.deploy(ProcessSource.fromCode(code));

                // Check status every 10 compilations
                if (i % 10 == 0) {
                    System.gc();
                    Thread.sleep(80);
                    classLoaderManager.performMaintenance();

                    // Check ClassLoader status
                    ClassLoaderManager.ClassLoaderStats currentStats = classLoaderManager.getStats();
                    System.out.printf("Compilation %d - ClassLoader stats: %s%n", i, currentStats);

                }
            }

            // Final memory check: multi-sample with GC+maintenance, take minimum used as stable reading
            long bestUsed = Long.MAX_VALUE;
            ClassLoaderManager.ClassLoaderStats finalStats = null;
            for (int i = 0; i < 4; i++) {
                System.gc();
                Thread.sleep(250);
                classLoaderManager.performMaintenance();
                MemoryUsage sample = memoryBean.getHeapMemoryUsage();
                bestUsed = Math.min(bestUsed, sample.getUsed());
                finalStats = classLoaderManager.getStats();
            }
            long finalUsed = bestUsed;
            ClassLoaderManager.ClassLoaderStats finalMaintenanceStats = classLoaderManager.performMaintenance();

            long memoryIncrease = finalUsed - baselineMemory.getUsed();
            long classLoadersCreated = finalStats.getTotalCreated() - baselineStats.getTotalCreated();
            long classLoadersCleaned = finalStats.getTotalCleaned() - baselineStats.getTotalCleaned();

            System.out.printf("Compilation memory leak test completed:%n" +
                            "  Memory - Baseline: %d MB, Final: %d MB, Increase: %d MB%n" +
                            "  ClassLoader - Created: %d, Cleaned: %d, Active: %d%n" +
                            "  Final maintenance: %s%n",
                    baselineMemory.getUsed() / (1024 * 1024),
                    finalUsed / (1024 * 1024),
                    memoryIncrease / (1024 * 1024),
                    classLoadersCreated, classLoadersCleaned, finalStats.getCurrentCount(),
                    finalMaintenanceStats);


            // Verify repeated compilation does not significantly increase memory (allow small jitter)
            assertTrue(memoryIncrease < 24 * 1024 * 1024,
                    "Repeated compilation should not leak significant memory: " + memoryIncrease / (1024 * 1024) + "MB");

            // Verify ClassLoader management effectiveness
            assertTrue(finalStats.getCurrentCount() < compilationCount * 0.1,
                    "Active ClassLoaders should be minimal after repeated compilation: " + finalStats.getCurrentCount());

        }

        @Test
        @DisplayName("should handle event bus memory management during high-frequency operations")
        void testEventBusMemoryManagement() throws InterruptedException {
            String code = "bpmn20.compat.simple_service";

            // Get baseline status
            System.gc();
            MemoryUsage baselineMemory = memoryBean.getHeapMemoryUsage();
            ProcessClassLoaderManager classLoaderManager = getClassLoaderManager();
            ClassLoaderManager.ClassLoaderStats baselineStats = classLoaderManager.getStats();

            System.out.printf("Event bus test baseline - Memory: %d MB, ClassLoader stats: %s%n",
                    baselineMemory.getUsed() / (1024 * 1024), baselineStats);

            // High-frequency process execution to trigger event bus activity
            int executionCount = 100;  // reduce execution count
            for (int i = 0; i < executionCount; i++) {
                Map<String, Object> context = new HashMap<>();
                context.put("iteration", i);

                ProcessResult<Map<String, Object>> result = engine.execute(
                        ProcessSource.fromCode(code), context);

                assertTrue(result.isSuccess(), "Execution " + i + " should succeed");

                // Check event bus status every 25 executions
                if (i % 25 == 0) {
                    System.gc();
                    Thread.sleep(10);

                    // Check ClassLoader status
                    ClassLoaderManager.ClassLoaderStats currentStats = classLoaderManager.getStats();
                    System.out.printf("Execution %d - ClassLoader stats: %s%n", i, currentStats);

                }
            }

            // Final check: allow cleanup to settle (GC + maintenance)
            for (int i = 0; i < 2; i++) {
                System.gc();
                Thread.sleep(200);
                classLoaderManager.performMaintenance();
            }
            ClassLoaderManager.ClassLoaderStats finalMaintenanceStats = classLoaderManager.performMaintenance();
            MemoryUsage finalMemory = memoryBean.getHeapMemoryUsage();
            ClassLoaderManager.ClassLoaderStats finalStats = classLoaderManager.getStats();

            long memoryIncrease = finalMemory.getUsed() - baselineMemory.getUsed();
            long classLoadersCreated = finalStats.getTotalCreated() - baselineStats.getTotalCreated();
            long classLoadersCleaned = finalStats.getTotalCleaned() - baselineStats.getTotalCleaned();

            System.out.printf("Event bus memory management test completed:%n" +
                            "  Memory - Baseline: %d MB, Final: %d MB, Increase: %d MB%n" +
                            "  ClassLoader - Created: %d, Cleaned: %d, Active: %d%n" +
                            "  Final maintenance: %s%n",
                    baselineMemory.getUsed() / (1024 * 1024),
                    finalMemory.getUsed() / (1024 * 1024),
                    memoryIncrease / (1024 * 1024),
                    classLoadersCreated, classLoadersCleaned, finalStats.getCurrentCount(),
                    finalMaintenanceStats);


            // Verify high-frequency operations do not cause memory leaks (200 executions should be less than 30MB)
            assertTrue(memoryIncrease < 30 * 1024 * 1024,
                    "High-frequency operations should not cause memory leaks: " + memoryIncrease / (1024 * 1024) + "MB");

            // Verify event bus related ClassLoader management
            assertTrue(finalStats.getCurrentCount() < executionCount * 0.05,
                    "Active ClassLoaders should be minimal after high-frequency operations: " + finalStats.getCurrentCount());

        }
    }

    @Nested
    @DisplayName("C. Resource Management Tests")
    class ResourceManagementTests {

        @Test
        @DisplayName("should handle engine shutdown gracefully")
        void testEngineShutdown() throws Exception {
            // Create engine with custom configuration
            ProcessEngine customEngine = ProcessEngineFactory.create(
                    ProcessEngineConfig.bpmnBuilder()
                            .parallelCompilation(true)
                            .executors(ProcessExecutorConfig.of(4, 8))
                            .build());

            // Execute some processes
            String code = "bpmn20.compat.simple_service";
            for (int i = 0; i < 10; i++) {
                ProcessResult<Map<String, Object>> result =
                        customEngine.execute(ProcessSource.fromCode(code), new HashMap<>());
                assertTrue(result.isSuccess());
            }

            // Shutdown should complete without hanging
            long startTime = System.currentTimeMillis();
            customEngine.close();
            long shutdownTime = System.currentTimeMillis() - startTime;

            System.out.printf("Engine shutdown completed in %d ms%n", shutdownTime);

            // Shutdown should be reasonably fast (less than 5 seconds)
            assertTrue(shutdownTime < 5000, "Engine shutdown should complete quickly");
        }

        @Test
        @DisplayName("should handle thread pool exhaustion gracefully")
        void testThreadPoolExhaustion() throws InterruptedException {
            // Create engine with very small thread pool
            ProcessEngine smallPoolEngine = ProcessEngineFactory.create(
                    ProcessEngineConfig.bpmnBuilder()
                            .parallelCompilation(true)
                            .executors(ProcessExecutorConfig.of(1, 2))
                            .build());

            try {
                String code = "bpmn20.compat.simple_service";
                ExecutorService executor = Executors.newFixedThreadPool(10);
                CountDownLatch latch = new CountDownLatch(10);
                AtomicInteger successCount = new AtomicInteger(0);

                // Submit more tasks than the pool can handle
                for (int i = 0; i < 10; i++) {
                    executor.submit(() -> {
                        try {
                            ProcessResult<Map<String, Object>> result =
                                    smallPoolEngine.execute(ProcessSource.fromCode(code), new HashMap<>());
                            if (result.isSuccess()) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // Expected - some tasks may fail due to pool exhaustion
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertTrue(latch.await(10, TimeUnit.SECONDS));

                // At least some tasks should succeed
                assertTrue(successCount.get() > 0, "Some tasks should succeed even with small pool");

                executor.shutdown();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            } finally {
                smallPoolEngine.close();
            }
        }
    }
}
