package com.alibaba.compileflow.engine.test.mock;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A mock service that can be configured to simulate failures, delays, and retries.
 *
 * @author yusu
 */
@Service
public class FlakyService {

    // Use a thread-safe counter to track invocation counts for retry tests.
    private final AtomicInteger invocationCounter = new AtomicInteger(0);

    public String successfulTask(Map<String, Object> context) {
        System.out.println(Thread.currentThread().getName() + " -> Executing successfulTask...");
        return "success";
    }

    public String taskWithDelay(Map<String, Object> context) throws InterruptedException {
        long delay = (long) context.getOrDefault("delayMillis", 100L);
        System.out.println(Thread.currentThread().getName() + " -> Executing taskWithDelay, sleeping for " + delay + "ms...");
        Thread.sleep(delay);
        System.out.println(Thread.currentThread().getName() + " -> taskWithDelay finished.");
        return "delayedSuccess";
    }

    public String taskThatThrowsException(Map<String, Object> context) {
        System.out.println(Thread.currentThread().getName() + " -> Executing taskThatThrowsException...");
        throw new IllegalStateException("This task is designed to fail.");
    }

    public String taskThatFailsThenSucceeds(Map<String, Object> context) throws IOException {
        int currentAttempt = invocationCounter.incrementAndGet();
        System.out.println(Thread.currentThread().getName() + " -> Executing taskThatFailsThenSucceeds, attempt " + currentAttempt);

        int failUntilAttempt = (int) context.getOrDefault("failUntilAttempt", 2);

        if (currentAttempt < failUntilAttempt) {
            throw new IOException("Simulated transient failure on attempt " + currentAttempt);
        }

        return "successAfterRetry";
    }

    public String taskWithDeterministicException(Map<String, Object> context) {
        System.out.println(Thread.currentThread().getName() + " -> Executing taskWithDeterministicException...");
        throw new IllegalArgumentException("This is a deterministic, non-retriable error.");
    }

}
