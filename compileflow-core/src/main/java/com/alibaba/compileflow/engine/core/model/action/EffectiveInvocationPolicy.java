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
package com.alibaba.compileflow.engine.core.model.action;

import com.alibaba.compileflow.engine.core.semantic.ProtocolDuration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validated, runtime-ready form of {@link InvocationPolicy}.
 * Parses authoring-layer string values into numeric fields for execution.
 *
 * @author yusu
 */
public final class EffectiveInvocationPolicy {
    private static final int DEFAULT_MAX_BACKOFF_MULTIPLIER = 100;
    private static final int MAX_ATTEMPTS = 100;
    private static final int MAX_POLICY_IDENTIFIER_LENGTH = 256;
    private static final Pattern POLICY_IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
    private static final EffectiveInvocationPolicy DEFAULT =
            new EffectiveInvocationPolicy(0L, 0L, 1, 1000L, 1.0d, 100000L, RetryJitter.FULL, "always", "propagate");
    private final long timeoutMs;
    private final long attemptTimeoutMs;
    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double backoffMultiplier;
    private final long maxBackoffMs;
    private final RetryJitter jitter;
    // never/transient/always or policy name
    private final String retryOn;
    // propagate/continue or handler name
    private final String onFailure;

    private EffectiveInvocationPolicy(long timeoutMs, long attemptTimeoutMs, int maxAttempts, long initialBackoffMs,
            double backoffMultiplier, long maxBackoffMs, RetryJitter jitter, String retryOn, String onFailure) {
        this.timeoutMs = requireNonNegative(timeoutMs, "timeoutMs");
        this.attemptTimeoutMs = requireNonNegative(attemptTimeoutMs, "attemptTimeoutMs");
        if (timeoutMs > 0L && attemptTimeoutMs > timeoutMs) {
            throw new IllegalArgumentException("attemptTimeoutMs must be less than or equal to timeoutMs");
        }
        this.maxAttempts = requireMaxAttempts(maxAttempts);
        this.initialBackoffMs = requireNonNegative(initialBackoffMs, "initialBackoffMs");
        this.backoffMultiplier = requireBackoffMultiplier(backoffMultiplier);
        this.maxBackoffMs = requireMaxBackoff(maxBackoffMs, initialBackoffMs);
        this.jitter = Objects.requireNonNull(jitter, "jitter");
        this.retryOn = requirePolicyIdentifier(retryOn, "retryOn");
        this.onFailure = requirePolicyIdentifier(onFailure, "onFailure");
    }

    /**
     * Factory from effective numeric values (used by code generator).
     */
    public static EffectiveInvocationPolicy of(long timeoutMs, long attemptTimeoutMs, int maxAttempts,
            long initialBackoffMs, double backoffMultiplier, long maxBackoffMs, RetryJitter jitter, String retryOn,
            String onFailure) {
        return new EffectiveInvocationPolicy(timeoutMs, attemptTimeoutMs, maxAttempts, initialBackoffMs,
                backoffMultiplier, maxBackoffMs, jitter, retryOn, onFailure);
    }

    /**
     * Returns the immutable default policy used by actions without an explicit invocation policy.
     *
     * @return default invocation policy
     */
    public static EffectiveInvocationPolicy defaults() {
        return DEFAULT;
    }

    /**
     * Factory from authoring-layer split fields.
     * Null fields receive semantic defaults:
     * <ul>
     *   <li>timeout = null → timeoutMs = 0 (no overall timeout)</li>
     *   <li>attemptTimeout = null → attemptTimeoutMs = 0 (no per-attempt timeout)</li>
     *   <li>maxAttempts = null → maxAttempts = 1 (one invocation, no retry)</li>
     *   <li>initialBackoff = null → initialBackoffMs = 1000 (PT1S)</li>
     *   <li>backoffMultiplier = null → 1.0 (fixed backoff)</li>
     *   <li>maxBackoff = null → 100 × initialBackoffMs</li>
     *   <li>jitter = null → full jitter</li>
     *   <li>retryOn = null → "always"</li>
     *   <li>onFailure = null → "propagate"</li>
     * </ul>
     */
    public static EffectiveInvocationPolicy of(String timeout, String attemptTimeout, Integer maxAttempts,
            String initialBackoff, Double backoffMultiplier, String maxBackoff, RetryJitter jitter, String retryOn,
            String onFailure) {
        long timeoutMs = (timeout == null) ? 0L : ProtocolDuration.parsePositiveMillis(timeout, "timeout");
        long tMs =
                (attemptTimeout == null) ? 0L : ProtocolDuration.parsePositiveMillis(attemptTimeout, "attemptTimeout");
        int attempts = (maxAttempts == null) ? 1 : maxAttempts;
        long riMs =
                (initialBackoff == null)
                ? 1000L
                : ProtocolDuration.parseNonNegativeMillis(initialBackoff, "initialBackoff");
        double mult = (backoffMultiplier == null) ? 1.0 : backoffMultiplier;
        long maxMs = (maxBackoff == null)
                ? defaultMaxBackoff(riMs)
                : ProtocolDuration.parseNonNegativeMillis(maxBackoff, "maxBackoff");
        RetryJitter effectiveJitter = jitter == null ? RetryJitter.FULL : jitter;
        String rOn = (retryOn == null) ? "always" : retryOn;
        String oF = (onFailure == null) ? "propagate" : onFailure;
        return new EffectiveInvocationPolicy(timeoutMs, tMs, attempts, riMs, mult, maxMs, effectiveJitter, rOn, oF);
    }

    /**
     * Resolves and validates an authoring-layer policy without mutating the source model.
     *
     * @param policy non-null authoring-layer policy
     * @return validated runtime-ready policy
     */
    public static EffectiveInvocationPolicy from(InvocationPolicy policy) {
        InvocationPolicy source = Objects.requireNonNull(policy, "policy");
        return of(source.getTimeout(), source.getAttemptTimeout(), source.getMaxAttempts(), source.getInitialBackoff(),
                source.getBackoffMultiplier(), source.getMaxBackoff(), source.getJitter(), source.getRetryOn(),
                source.getOnFailure());
    }

    private static long defaultMaxBackoff(long initialBackoffMs) {
        try {
            return Math.multiplyExact(initialBackoffMs, DEFAULT_MAX_BACKOFF_MULTIPLIER);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Default maxBackoff exceeds the supported millisecond range", overflow);
        }
    }

    private static long requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative, got: " + value);
        }
        return value;
    }

    private static int requireMaxAttempts(int value) {
        if (value < 1 || value > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and " + MAX_ATTEMPTS + ", got: " + value);
        }
        return value;
    }

    private static double requireBackoffMultiplier(double value) {
        if (!Double.isFinite(value) || value < 1.0d) {
            throw new IllegalArgumentException("backoffMultiplier must be finite and at least 1.0, got: " + value);
        }
        return value;
    }

    private static long requireMaxBackoff(long value, long initialBackoffMs) {
        requireNonNegative(value, "maxBackoffMs");
        if (value < initialBackoffMs) {
            throw new IllegalArgumentException("maxBackoffMs must be greater than or equal to initialBackoffMs");
        }
        return value;
    }

    private static String requirePolicyIdentifier(String value, String field) {
        String identifier = Objects.requireNonNull(value, field + " must not be null");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (identifier.length() > MAX_POLICY_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + MAX_POLICY_IDENTIFIER_LENGTH + " characters");
        }
        if (!POLICY_IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException(field + " must use lowercase kebab-case");
        }
        return identifier;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public long getAttemptTimeoutMs() {
        return attemptTimeoutMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public RetryJitter getJitter() {
        return jitter;
    }

    public String getRetryOn() {
        return retryOn;
    }

    public String getOnFailure() {
        return onFailure;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EffectiveInvocationPolicy that)) {
            return false;
        }
        return timeoutMs == that.timeoutMs && attemptTimeoutMs == that.attemptTimeoutMs
                && maxAttempts == that.maxAttempts && initialBackoffMs == that.initialBackoffMs
                && Double.compare(backoffMultiplier, that.backoffMultiplier) == 0 && maxBackoffMs == that.maxBackoffMs
                && jitter == that.jitter && retryOn.equals(that.retryOn) && onFailure.equals(that.onFailure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeoutMs, attemptTimeoutMs, maxAttempts, initialBackoffMs, backoffMultiplier, maxBackoffMs,
                jitter, retryOn, onFailure);
    }
}
