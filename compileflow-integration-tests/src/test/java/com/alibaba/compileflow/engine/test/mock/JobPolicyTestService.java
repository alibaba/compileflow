package com.alibaba.compileflow.engine.test.mock;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test service specifically designed for JobPolicy integration testing.
 * Provides various methods to simulate different execution scenarios:
 * - Timeout scenarios
 * - Retry scenarios
 * - Failure scenarios
 * - Success scenarios
 *
 * @author yusu
 */
@Service
public class JobPolicyTestService {

    private static final AtomicInteger attemptCounter = new AtomicInteger(0);
    private static final AtomicInteger globalCounter = new AtomicInteger(0);

    /**
     * Method that delays execution for testing timeout scenarios
     */
    public String delayedExecution(Map<String, Object> context) throws InterruptedException {
        Integer delayMs = (Integer) context.get("delayMs");
        if (delayMs != null && delayMs > 0) {
            System.out.println("Delaying execution for " + delayMs + "ms");
            Thread.sleep(delayMs);
        }
        return "success_after_delay";
    }

    /**
     * Method that fails initially but succeeds after specified attempts
     */
    public String retryableTask(Integer failUntilAttempt) {
        int currentAttempt = attemptCounter.incrementAndGet();
        int failUntil = failUntilAttempt != null ? failUntilAttempt : 1;

        System.out.println("Retryable task attempt: " + currentAttempt);

        if (currentAttempt < failUntil) {
            throw new RuntimeException("Transient failure on attempt " + currentAttempt);
        }

        // Reset counter for next test
        attemptCounter.set(0);
        return "success_after_retry";
    }

    /**
     * Method that always throws a deterministic exception (should not be retried)
     */
    public String deterministicFailure() {
        System.out.println("Deterministic failure - should not retry");
        throw new IllegalArgumentException("Deterministic failure - invalid input");
    }

    /**
     * Method that always throws a transient exception (should be retried)
     */
    public String transientFailure() {
        System.out.println("Transient failure - should retry");
        throw new RuntimeException("Transient failure - temporary issue");
    }

    /**
     * Simple successful method for basic JobPolicy testing
     */
    public String simpleSuccess() {
        System.out.println("Simple success execution");
        return "simple_success";
    }

    /**
     * Void method for testing ActionExecutor.run() generation
     */
    public void voidMethodWithJobPolicy(Map<String, Object> context) {
        System.out.println("Void method executed with JobPolicy");
        Integer delayMs = (Integer) context.get("delayMs");
        if (delayMs != null && delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during execution", e);
            }
        }
    }

    /**
     * Method with return value for testing ActionExecutor.call() generation
     */
    public Integer calculateWithJobPolicy(Map<String, Object> context) {
        System.out.println("Calculate method executed with JobPolicy");
        Integer a = (Integer) context.getOrDefault("a", 0);
        Integer b = (Integer) context.getOrDefault("b", 0);

        Integer delayMs = (Integer) context.get("delayMs");
        if (delayMs != null && delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during calculation", e);
            }
        }

        return a + b;
    }

    /**
     * Method for parallel branch testing - Branch 1
     */
    public String parallelBranch1(Integer a) throws InterruptedException {
        System.out.println("Parallel branch 1 execution");
        int valueA = a != null ? a : 0;

        // Simulate some processing time
        Thread.sleep(50);

        return "branch1_result_" + (valueA * 2);
    }

    /**
     * Method for parallel branch testing - Branch 2
     */
    public String parallelBranch2(Integer b) throws InterruptedException {
        System.out.println("Parallel branch 2 execution");
        int valueB = b != null ? b : 0;

        // Simulate some processing time
        Thread.sleep(100);

        return "branch2_result_" + (valueB * 3);
    }

    /**
     * Method that simulates network timeout
     */
    public String networkCall(Map<String, Object> context) throws InterruptedException {
        System.out.println("Simulating network call");
        Integer timeoutMs = (Integer) context.getOrDefault("networkTimeout", 2000);

        // Simulate network delay
        Thread.sleep(timeoutMs);

        return "network_success";
    }

    /**
     * Method for testing custom failure handler
     */
    public String customFailureScenario(String scenario) {
        String scenarioValue = scenario != null ? scenario : "success";

        switch (scenarioValue) {
            case "timeout":
                try {
                    Thread.sleep(5000); // Long delay to trigger timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
                break;
            case "transient_error":
                throw new RuntimeException("Transient error for custom handler");
            case "deterministic_error":
                throw new IllegalArgumentException("Deterministic error for custom handler");
            case "success":
            default:
                return "custom_success";
        }

        return "should_not_reach_here";
    }

    /**
     * Method for testing complex JobPolicy configurations
     */
    public String complexJobPolicyExecution(Map<String, Object> context) throws InterruptedException {
        System.out.println("Complex JobPolicy execution");

        Integer delayMs = (Integer) context.getOrDefault("delayMs", 0);
        Boolean shouldFail = (Boolean) context.getOrDefault("shouldFail", false);
        Integer attemptThreshold = (Integer) context.getOrDefault("attemptThreshold", 1);

        int currentAttempt = globalCounter.incrementAndGet();

        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }

        if (shouldFail && currentAttempt < attemptThreshold) {
            throw new RuntimeException("Complex failure on attempt " + currentAttempt);
        }

        globalCounter.set(0); // Reset for next test
        return "complex_success";
    }

    /**
     * Reset all counters - useful for test isolation
     */
    public void resetCounters() {
        attemptCounter.set(0);
        globalCounter.set(0);
    }

    /**
     * Get current attempt count - useful for testing retry behavior
     */
    public int getCurrentAttemptCount() {
        return attemptCounter.get();
    }

    /**
     * Get global counter value
     */
    public int getGlobalCounterValue() {
        return globalCounter.get();
    }
}
