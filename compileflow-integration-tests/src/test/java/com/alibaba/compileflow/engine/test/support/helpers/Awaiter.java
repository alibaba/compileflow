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
package com.alibaba.compileflow.engine.test.support.helpers;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class Awaiter {
    private Awaiter() {
    }

    public static void await(String description, Duration timeout, Duration interval, Callable<Boolean> condition,
            Supplier<String> debugDump) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(condition, "condition");

        long timeoutNanos = requirePositiveNanos(timeout, "timeout");
        long intervalNanos = requirePositiveNanos(interval, "interval");
        long startedAt = System.nanoTime();
        Exception lastError = null;

        while (System.nanoTime() - startedAt < timeoutNanos) {
            try {
                if (Boolean.TRUE.equals(condition.call())) {
                    return;
                }
            } catch (Exception failure) {
                lastError = failure;
            }

            try {
                TimeUnit.NANOSECONDS.sleep(intervalNanos);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting: " + description, ie);
            }
        }

        StringBuilder msg = new StringBuilder();
        msg
            .append("Timeout awaiting: ")
            .append(description)
            .append(" (timeout=")
            .append(timeout.toMillis())
            .append("ms, interval=")
            .append(interval.toMillis())
            .append("ms)");

        if (lastError != null) {
            msg
                .append(". Last observed error: ")
                .append(lastError.getClass().getName())
                .append(": ")
                .append(lastError.getMessage());
        }

        if (debugDump != null) {
            try {
                String dump = debugDump.get();
                if (dump != null && !dump.isEmpty()) {
                    msg.append("\n--- debug dump ---\n").append(dump);
                }
            } catch (RuntimeException ignore) {
                // ignore debug dump errors
            }
        }

        throw new AssertionError(msg.toString());
    }

    public static void await(String description, Duration timeout, Callable<Boolean> condition,
            Supplier<String> debugDump) {
        await(description, timeout, Duration.ofMillis(50), condition, debugDump);
    }

    public static void await(String description, Duration timeout, Callable<Boolean> condition) {
        await(description, timeout, Duration.ofMillis(50), condition, null);
    }

    private static long requirePositiveNanos(Duration duration, String name) {
        long nanos;
        try {
            nanos = duration.toNanos();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(name + " is too large", failure);
        }
        if (nanos <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return nanos;
    }
}
