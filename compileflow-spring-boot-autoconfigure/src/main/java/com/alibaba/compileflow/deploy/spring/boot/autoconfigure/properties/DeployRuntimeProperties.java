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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Strict binding adapter for runtime installation and convergence policy.
 *
 * @author yusu
 */
public final class DeployRuntimeProperties {
    private static final int MAX_CONCURRENCY = 256;
    private static final int MAX_QUEUE_CAPACITY = 10_000;
    /**
     * Suppression period before retrying a failed installation in either topology.
     */
    @NotNull
    private final Duration failureBackoff;
    /**
     * Maximum request wait for embedded on-demand local-ready convergence.
     */
    @NotNull
    private final Duration convergenceTimeout;
    /**
     * Maximum concurrent artifact installations in distributed topology.
     */
    @Min(value = 1, message = "concurrency must be between 1 and 256")
    @Max(value = MAX_CONCURRENCY, message = "concurrency must be between 1 and 256")
    private final int concurrency;
    /**
     * Distributed waiting queue; also contributes to the shared admission bound.
     */
    @Min(value = 1, message = "queue-capacity must be between 1 and 10000")
    @Max(value = MAX_QUEUE_CAPACITY, message = "queue-capacity must be between 1 and 10000")
    private final int queueCapacity;

    public DeployRuntimeProperties(@DefaultValue("5m") Duration failureBackoff,
            @DefaultValue("30s") Duration convergenceTimeout, @DefaultValue("1") int concurrency,
            @DefaultValue("256") int queueCapacity) {
        this.failureBackoff = failureBackoff;
        this.convergenceTimeout = convergenceTimeout;
        this.concurrency = concurrency;
        this.queueCapacity = queueCapacity;
    }

    public Duration getFailureBackoff() {
        return failureBackoff;
    }

    public Duration getConvergenceTimeout() {
        return convergenceTimeout;
    }

    @AssertTrue(message = "compileflow.deploy.runtime.failure-backoff must be a positive"
            + " whole-millisecond duration representable as nanoseconds")
    public boolean isFailureBackoffValid() {
        return DurationPropertyConstraints.isPositiveWholeMillisecondsRepresentableAsNanos(failureBackoff);
    }

    @AssertTrue(message = "compileflow.deploy.runtime.convergence-timeout must be a positive"
            + " whole-millisecond duration representable as a long")
    public boolean isConvergenceTimeoutValid() {
        return DurationPropertyConstraints.isPositiveWholeMilliseconds(convergenceTimeout);
    }

    public int getConcurrency() {
        return concurrency;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }
}
