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
package com.alibaba.compileflow.engine.spi.execution;

/**
 * Decides whether a failed action is eligible for another configured retry attempt.
 * <p>
 * Implementations must be thread-safe, deterministic, bounded, and non-blocking. A thrown
 * exception fails the Process invocation rather than selecting a fallback policy. The application
 * or dependency-injection container owns the policy lifecycle. The retry
 * count and delay remain part of the process definition; this policy only classifies failures.
 * A timed-out action is interrupted cooperatively. The engine waits for it to stop before
 * another attempt may start and fails closed when it ignores cancellation. A timeout can still
 * occur after an external side effect committed, so retryable actions must remain idempotent or
 * enforce their own idempotency key.
 *
 * @author yusu
 */
@FunctionalInterface
public interface RetryPolicy {
    /**
     * Returns whether the supplied failure is retryable.
     *
     * @param failure non-null action failure
     * @return {@code true} when another configured attempt may run
     */
    boolean shouldRetryOn(Throwable failure);
}
