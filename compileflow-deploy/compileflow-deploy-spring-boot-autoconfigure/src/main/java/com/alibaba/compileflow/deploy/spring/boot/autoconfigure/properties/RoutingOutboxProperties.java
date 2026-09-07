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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties;

import com.alibaba.compileflow.spring.boot.autoconfigure.properties.DurationPropertyConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Strict binding adapter for routing outbox dispatch and retention.
 *
 * @author yusu
 */
public final class RoutingOutboxProperties {
    private static final int MAX_BATCH_SIZE = 1_000;
    /**
     * Delay between outbox dispatch cycles.
     */
    @NotNull
    private final Duration dispatchInterval;
    /**
     * Maximum outbox records processed sequentially per dispatch cycle.
     */
    @Min(value = 1, message = "dispatch-batch-size must be between 1 and 1000")
    @Max(value = MAX_BATCH_SIZE, message = "dispatch-batch-size must be between 1 and 1000")
    private final int dispatchBatchSize;
    /**
     * Retention for delivered records; zero disables cleanup.
     */
    @NotNull
    private final Duration retention;
    /**
     * Lease held by one dispatcher while delivering a claimed record.
     */
    @NotNull
    private final Duration leaseDuration;
    /**
     * Failed-delivery retry policy.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final Retry retry;

    public RoutingOutboxProperties(@DefaultValue("1s") Duration dispatchInterval,
            @DefaultValue("50") int dispatchBatchSize, @DefaultValue("24h") Duration retention,
            @DefaultValue("1m") Duration leaseDuration, @DefaultValue Retry retry) {
        this.dispatchInterval = dispatchInterval;
        this.dispatchBatchSize = dispatchBatchSize;
        this.retention = retention;
        this.leaseDuration = leaseDuration;
        this.retry = retry;
    }

    public Duration getDispatchInterval() {
        return dispatchInterval;
    }

    public int getDispatchBatchSize() {
        return dispatchBatchSize;
    }

    public Duration getRetention() {
        return retention;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public Retry getRetry() {
        return retry;
    }

    @AssertTrue(message = "compileflow.deploy.outbox.dispatch-interval must be a positive"
            + " whole-millisecond duration representable as a long")
    public boolean isDispatchIntervalValid() {
        return DurationPropertyConstraints.isPositiveWholeMilliseconds(dispatchInterval);
    }

    @AssertTrue(message = "compileflow.deploy.outbox.retention must be a non-negative whole-millisecond"
            + " duration representable as a long")
    public boolean isRetentionValid() {
        return DurationPropertyConstraints.isNonNegativeWholeMilliseconds(retention);
    }

    @AssertTrue(message = "compileflow.deploy.outbox.lease-duration must be a positive whole-millisecond"
            + " duration representable as a long")
    public boolean isLeaseDurationValid() {
        return DurationPropertyConstraints.isPositiveWholeMilliseconds(leaseDuration);
    }

    /**
     * Capped exponential retry bounds; jitter is owned by the dispatcher.
     */
    public static final class Retry {
        /**
         * Delay applied after the first delivery failure.
         */
        @NotNull
        private final Duration initialDelay;
        /**
         * Upper bound for exponential retry delay.
         */
        @NotNull
        private final Duration maxDelay;
        /**
         * Failed delivery attempts before a record enters the dead-letter state.
         */
        @Min(1)
        private final int maxAttempts;

        public Retry(@DefaultValue("5s") Duration initialDelay, @DefaultValue("5m") Duration maxDelay,
                @DefaultValue("10") int maxAttempts) {
            this.initialDelay = initialDelay;
            this.maxDelay = maxDelay;
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        @AssertTrue(message = "compileflow.deploy.outbox.retry.initial-delay must be a positive"
                + " whole-millisecond duration representable as a long")
        public boolean isInitialDelayValid() {
            return DurationPropertyConstraints.isPositiveWholeMilliseconds(initialDelay);
        }

        @AssertTrue(message = "compileflow.deploy.outbox.retry.max-delay must be a positive whole-millisecond"
                + " duration representable as a long and at least compileflow.deploy.outbox.retry.initial-delay")
        public boolean isMaxDelayValid() {
            return DurationPropertyConstraints.isPositiveWholeMilliseconds(maxDelay) && initialDelay != null
                    && maxDelay.compareTo(initialDelay) >= 0;
        }
    }
}
