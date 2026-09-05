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
package com.alibaba.compileflow.engine.test.support.mocks;

import com.alibaba.compileflow.engine.spi.execution.ActionExecutionContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class InvocationPolicyTestService {
    private static final AtomicInteger ATTEMPT_COUNTER = new AtomicInteger(0);
    private static final AtomicInteger GLOBAL_COUNTER = new AtomicInteger(0);
    private static final CopyOnWriteArrayList<ActionExecutionContext> ACTION_CONTEXTS = new CopyOnWriteArrayList<>();

    public static void resetCounters() {
        ATTEMPT_COUNTER.set(0);
        GLOBAL_COUNTER.set(0);
        ACTION_CONTEXTS.clear();
    }

    public static List<ActionExecutionContext> actionContexts() {
        return List.copyOf(ACTION_CONTEXTS);
    }

    public String delayedExecution(Map<String, Object> context) throws InterruptedException {
        Integer delayMs = (Integer) context.get("delayMs");
        if (delayMs != null && delayMs > 0) {
            Thread.sleep(delayMs);
        }
        return "success_after_delay";
    }

    public String retryableTask(Integer failUntilAttempt) {
        ACTION_CONTEXTS.add(ActionExecutionContext.current());
        int currentAttempt = ATTEMPT_COUNTER.incrementAndGet();
        int failUntil = failUntilAttempt != null ? failUntilAttempt : 1;

        if (currentAttempt < failUntil) {
            throw new RuntimeException("Transient failure on attempt " + currentAttempt);
        }
        // Reset counter for next test
        ATTEMPT_COUNTER.set(0);
        return "success_after_retry";
    }

    public String deterministicFailure() {
        throw new IllegalArgumentException("Deterministic failure - invalid input");
    }

    public String transientFailure() {
        throw new RuntimeException("Transient failure - temporary issue");
    }

    public String simpleSuccess() {
        return "simple_success";
    }

    public String defaultedValue(Integer value) {
        return "default_" + value;
    }

    public String returnLateValueAfterInterruption() {
        try {
            Thread.sleep(5000);
            return "unexpected";
        } catch (InterruptedException ignored) {
            return "late";
        }
    }

    public void voidMethodWithInvocationPolicy(Map<String, Object> context) {
        Integer delayMs = (Integer) context.get("delayMs");
        if (delayMs != null && delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during execution", failure);
            }
        }
    }

    public Integer calculateWithInvocationPolicy(Map<String, Object> context) {
        Integer a = (Integer) context.getOrDefault("a", 0);
        Integer b = (Integer) context.getOrDefault("b", 0);

        Integer delayMs = (Integer) context.get("delayMs");
        if (delayMs != null && delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during calculation", failure);
            }
        }

        return a + b;
    }

    public String parallelBranch1(Integer a) throws InterruptedException {
        int valueA = a != null ? a : 0;
        // Simulate some processing time
        Thread.sleep(50);

        return "branch1_result_" + (valueA * 2);
    }

    public String parallelBranch2(Integer b) throws InterruptedException {
        int valueB = b != null ? b : 0;
        // Simulate some processing time
        Thread.sleep(100);

        return "branch2_result_" + (valueB * 3);
    }

    public String networkCall(Map<String, Object> context) throws InterruptedException {
        Integer timeoutMs = (Integer) context.getOrDefault("networkTimeout", 2000);
        // Simulate network delay
        Thread.sleep(timeoutMs);

        return "network_success";
    }

    public String customFailureScenario(String scenario) {
        String scenarioValue = scenario != null ? scenario : "success";

        switch (scenarioValue) {
            case "timeout":
                try {
                    // Long delay to trigger timeout
                    Thread.sleep(5000);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", failure);
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

    public String complexInvocationPolicyExecution(Map<String, Object> context) throws InterruptedException {
        Integer delayMs = (Integer) context.getOrDefault("delayMs", 0);
        Boolean shouldFail = (Boolean) context.getOrDefault("shouldFail", false);
        Integer attemptThreshold = (Integer) context.getOrDefault("attemptThreshold", 1);

        int currentAttempt = GLOBAL_COUNTER.incrementAndGet();

        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }

        if (shouldFail && currentAttempt < attemptThreshold) {
            throw new RuntimeException("Complex failure on attempt " + currentAttempt);
        }
        // Reset for next test
        GLOBAL_COUNTER.set(0);
        return "complex_success";
    }
}
