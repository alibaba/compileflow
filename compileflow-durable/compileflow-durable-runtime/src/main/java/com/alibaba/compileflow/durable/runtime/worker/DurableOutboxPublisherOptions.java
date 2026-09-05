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
package com.alibaba.compileflow.durable.runtime.worker;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Operational at-least-once delivery policy with capped exponential full jitter.
 *
 * @author yusu
 */
public record DurableOutboxPublisherOptions(String workerId, Duration initialRetryDelay, Duration maxRetryDelay,
        int maxAttempts) {
    public DurableOutboxPublisherOptions {
        workerId = DurableIdentifiers.requireIdentity(workerId, "workerId", 128);
        initialRetryDelay = WorkerDurationConstraints.requirePositive(initialRetryDelay, "initialRetryDelay",
                Duration.ofHours(1));
        maxRetryDelay = WorkerDurationConstraints.requirePositive(maxRetryDelay, "maxRetryDelay", Duration.ofHours(1));
        if (maxRetryDelay.compareTo(initialRetryDelay) < 0) {
            throw new IllegalArgumentException("maxRetryDelay must be greater than or equal to initialRetryDelay");
        }
        maxAttempts = DurableNumbers.requireRange(maxAttempts, 1, 10_000, "maxAttempts");
    }

    public static DurableOutboxPublisherOptions defaults(String workerId) {
        return new DurableOutboxPublisherOptions(workerId, Duration.ofSeconds(1), Duration.ofMinutes(1), 100);
    }

    /**
     * Returns a positive full-jitter delay bounded by the capped exponential window.
     */
    public Duration retryDelay(int attempt) {
        long ceiling = retryCeilingMillis(attempt);
        long jittered = ceiling == 1L ? 1L : ThreadLocalRandom.current().nextLong(1L, ceiling + 1L);
        return Duration.ofMillis(jittered);
    }

    long retryCeilingMillis(int attempt) {
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        long initial = initialRetryDelay.toMillis();
        int exponent = Math.min(attempt - 1, 62);
        long factor = 1L << exponent;
        long scaled = initial > Long.MAX_VALUE / factor ? Long.MAX_VALUE : initial * factor;
        return Math.min(scaled, maxRetryDelay.toMillis());
    }
}
