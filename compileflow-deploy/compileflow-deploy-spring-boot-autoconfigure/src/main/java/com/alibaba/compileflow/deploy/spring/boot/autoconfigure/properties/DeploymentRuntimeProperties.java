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
public final class DeploymentRuntimeProperties {
    private static final int MAX_INSTALLATION_CONCURRENCY = 256;
    /**
     * Suppression period before retrying a failed installation in either topology.
     */
    @NotNull
    private final Duration failureBackoff;
    /**
     * Maximum embedded caller wait, including alias lookup, artifact resolution and runtime loading.
     */
    @NotNull
    private final Duration convergenceTimeout;
    /**
     * Maximum concurrent artifact installations in distributed topology, or admitted convergence
     * operations in embedded topology. Embedded admission remains occupied after a caller times out
     * until its background convergence completes.
     */
    @Min(value = 1, message = "installation-concurrency must be between 1 and 256")
    @Max(value = MAX_INSTALLATION_CONCURRENCY, message = "installation-concurrency must be between 1 and 256")
    private final int installationConcurrency;

    public DeploymentRuntimeProperties(@DefaultValue("5m") Duration failureBackoff,
            @DefaultValue("30s") Duration convergenceTimeout, @DefaultValue("1") int installationConcurrency) {
        this.failureBackoff = failureBackoff;
        this.convergenceTimeout = convergenceTimeout;
        this.installationConcurrency = installationConcurrency;
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

    public int getInstallationConcurrency() {
        return installationConcurrency;
    }
}
