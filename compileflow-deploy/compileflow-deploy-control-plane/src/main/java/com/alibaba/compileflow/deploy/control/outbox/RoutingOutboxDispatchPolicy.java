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
package com.alibaba.compileflow.deploy.control.outbox;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable batching, ownership, and retry policy for routing outbox delivery.
 *
 * @author yusu
 */
public record RoutingOutboxDispatchPolicy(int batchSize, Duration claimLease, int maxAttempts,
        Duration initialRetryDelay, Duration maxRetryDelay) {
    public RoutingOutboxDispatchPolicy {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        claimLease = requirePositiveWholeMilliseconds(claimLease, "claimLease");
        initialRetryDelay = requirePositiveWholeMilliseconds(initialRetryDelay, "initialRetryDelay");
        maxRetryDelay = requirePositiveWholeMilliseconds(maxRetryDelay, "maxRetryDelay");
        if (maxRetryDelay.compareTo(initialRetryDelay) < 0) {
            throw new IllegalArgumentException("maxRetryDelay must be greater than or equal to initialRetryDelay");
        }
    }

    private static Duration requirePositiveWholeMilliseconds(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.compareTo(Duration.ofMillis(1)) < 0 || duration.compareTo(Duration.ofMillis(Long.MAX_VALUE)) > 0
                || duration.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    name + " must be a positive whole-millisecond duration representable as a long");
        }
        return duration;
    }

    long claimLeaseMs() {
        return claimLease.toMillis();
    }

    long retryDelayMs(int previousAttemptCount) {
        long ceiling = retryCeilingMs(previousAttemptCount);
        if (ceiling == 1L) {
            return 1L;
        }
        if (ceiling == Long.MAX_VALUE) {
            return ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
        }
        return ThreadLocalRandom.current().nextLong(1L, ceiling + 1L);
    }

    long retryCeilingMs(int previousAttemptCount) {
        if (previousAttemptCount < 0) {
            throw new IllegalArgumentException("previousAttemptCount must not be negative");
        }
        long initialMs = initialRetryDelay.toMillis();
        long factor = 1L << Math.min(previousAttemptCount, 62);
        long scaled = initialMs > Long.MAX_VALUE / factor ? Long.MAX_VALUE : initialMs * factor;
        return Math.min(scaled, maxRetryDelay.toMillis());
    }
}
