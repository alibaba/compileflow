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
package com.alibaba.compileflow.deploy.runtime.observability;

import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Monotonic, instance-scoped deployment counters with bounded dimensions.
 *
 * @author yusu
 */
public final class DeploymentRuntimeMetrics {
    private final LongAdder[][] runtimeInstallAttempts =
            counters(AttemptOutcome.values().length, RuntimeInstallReason.values().length);
    private final LongAdder[][] aliasConvergenceAttempts =
            counters(AttemptOutcome.values().length, AliasConvergenceReason.values().length);
    private final ConcurrentHashMap<DeploymentErrorCode, LongAdder> errorByCode = new ConcurrentHashMap<>();

    /**
     * Creates an independent metrics registry with all counters initialized to zero.
     */
    public DeploymentRuntimeMetrics() {
    }

    /**
     * Tests whether a runtime installation outcome and reason form a valid pair.
     *
     * @param outcome terminal attempt outcome
     * @param reason  terminal installation reason
     * @return {@code true} when success has no failure reason or failure has one
     */
    public static boolean isValid(AttemptOutcome outcome, RuntimeInstallReason reason) {
        return isValid(Objects.requireNonNull(outcome, "outcome"),
                Objects.requireNonNull(reason, "reason") == RuntimeInstallReason.NONE);
    }

    /**
     * Tests whether an alias convergence outcome and reason form a valid pair.
     *
     * @param outcome terminal attempt outcome
     * @param reason  terminal convergence reason
     * @return {@code true} when success has no failure reason or failure has one
     */
    public static boolean isValid(AttemptOutcome outcome, AliasConvergenceReason reason) {
        return isValid(Objects.requireNonNull(outcome, "outcome"),
                Objects.requireNonNull(reason, "reason") == AliasConvergenceReason.NONE);
    }

    private static boolean isValid(AttemptOutcome outcome, boolean noFailureReason) {
        return outcome == AttemptOutcome.SUCCESS ? noFailureReason : !noFailureReason;
    }

    private static void requireValidCombination(AttemptOutcome outcome, boolean noFailureReason) {
        if (!isValid(outcome, noFailureReason)) {
            throw new IllegalArgumentException(
                    "Successful attempts require reason=NONE and failed attempts require a bounded failure reason");
        }
    }

    private static LongAdder[][] counters(int outcomeCount, int reasonCount) {
        LongAdder[][] result = new LongAdder[outcomeCount][reasonCount];
        for (int outcome = 0; outcome < outcomeCount; outcome++) {
            for (int reason = 0; reason < reasonCount; reason++) {
                result[outcome][reason] = new LongAdder();
            }
        }
        return result;
    }

    /**
     * Records one terminal runtime installation attempt.
     *
     * @param outcome terminal attempt outcome
     * @param reason  {@link RuntimeInstallReason#NONE} for success, otherwise a failure reason
     * @throws IllegalArgumentException if the outcome and reason combination is inconsistent
     */
    public void recordRuntimeInstall(AttemptOutcome outcome, RuntimeInstallReason reason) {
        AttemptOutcome terminalOutcome = Objects.requireNonNull(outcome, "outcome");
        RuntimeInstallReason terminalReason = Objects.requireNonNull(reason, "reason");
        requireValidCombination(terminalOutcome, terminalReason == RuntimeInstallReason.NONE);
        runtimeInstallAttempts[terminalOutcome.ordinal()][terminalReason.ordinal()].increment();
    }

    /**
     * Returns the current runtime installation count for one bounded dimension pair.
     *
     * @param outcome terminal attempt outcome
     * @param reason  terminal installation reason
     * @return non-negative count observed by this metrics instance
     */
    public long runtimeInstallCount(AttemptOutcome outcome, RuntimeInstallReason reason) {
        return runtimeInstallAttempts[Objects.requireNonNull(outcome, "outcome").ordinal()][Objects
            .requireNonNull(reason, "reason")
            .ordinal()]
            .sum();
    }

    /**
     * Records one terminal node-local alias convergence attempt.
     *
     * @param outcome terminal attempt outcome
     * @param reason  {@link AliasConvergenceReason#NONE} for success, otherwise a failure reason
     * @throws IllegalArgumentException if the outcome and reason combination is inconsistent
     */
    public void recordAliasConvergence(AttemptOutcome outcome, AliasConvergenceReason reason) {
        AttemptOutcome terminalOutcome = Objects.requireNonNull(outcome, "outcome");
        AliasConvergenceReason terminalReason = Objects.requireNonNull(reason, "reason");
        requireValidCombination(terminalOutcome, terminalReason == AliasConvergenceReason.NONE);
        aliasConvergenceAttempts[terminalOutcome.ordinal()][terminalReason.ordinal()].increment();
    }

    /**
     * Returns the current alias convergence count for one bounded dimension pair.
     *
     * @param outcome terminal attempt outcome
     * @param reason  terminal convergence reason
     * @return non-negative count observed by this metrics instance
     */
    public long aliasConvergenceCount(AttemptOutcome outcome, AliasConvergenceReason reason) {
        return aliasConvergenceAttempts[Objects.requireNonNull(outcome, "outcome").ordinal()][Objects
            .requireNonNull(reason, "reason")
            .ordinal()]
            .sum();
    }

    /**
     * Records one deployment failure under its bounded error code.
     *
     * <p>A {@code null} value is ignored so optional observation code does not hide the original
     * failure with a secondary metrics exception.
     *
     * @param errorCode failure category, or {@code null}
     */
    public void recordError(DeploymentErrorCode errorCode) {
        if (errorCode != null) {
            errorByCode
                .computeIfAbsent(errorCode, ignored -> new LongAdder())
                .increment();
        }
    }

    /**
     * Returns the current count for one deployment error code.
     *
     * @param errorCode error category to inspect
     * @return non-negative count, or zero when the code has not been recorded
     */
    public long getErrorCount(DeploymentErrorCode errorCode) {
        LongAdder counter = errorByCode.get(errorCode);
        return counter == null ? 0L : counter.sum();
    }

    /**
     * Terminal outcome of one runtime installation or alias convergence attempt.
     */
    public enum AttemptOutcome {
        /**
         * The attempt completed and established its intended local state.
         */
        SUCCESS("success"),
        /**
         * The attempt terminated before establishing its intended local state.
         */
        FAILURE("failure");
        private final String tagValue;

        AttemptOutcome(String tagValue) {
            this.tagValue = tagValue;
        }

        /**
         * Returns the stable, bounded metric tag value.
         *
         * @return lowercase outcome tag
         */
        public String getTagValue() {
            return tagValue;
        }
    }

    /**
     * Bounded terminal reason for one runtime installation attempt.
     */
    public enum RuntimeInstallReason {
        /**
         * No failure occurred; valid only with {@link AttemptOutcome#SUCCESS}.
         */
        NONE("none"),
        /**
         * The immutable artifact could not be resolved.
         */
        ARTIFACT_RESOLUTION("artifact_resolution"),
        /**
         * The resolved artifact did not match the requested process identity.
         */
        ARTIFACT_IDENTITY("artifact_identity"),
        /**
         * The resolved artifact failed its content-integrity check.
         */
        ARTIFACT_INTEGRITY("artifact_integrity"),
        /**
         * The engine could not load the verified artifact into the runtime.
         */
        RUNTIME_LOAD("runtime_load"),
        /**
         * The bounded installation executor rejected the work.
         */
        EXECUTOR_REJECTED("executor_rejected"),
        /**
         * The attempt failed for a bounded reason not represented above.
         */
        INTERNAL("internal");
        private final String tagValue;

        RuntimeInstallReason(String tagValue) {
            this.tagValue = tagValue;
        }

        /**
         * Returns the stable, bounded metric tag value.
         *
         * @return lowercase installation-reason tag
         */
        public String getTagValue() {
            return tagValue;
        }
    }

    /**
     * Bounded terminal reason for one node-local alias convergence attempt.
     */
    public enum AliasConvergenceReason {
        /**
         * No failure occurred; valid only with {@link AttemptOutcome#SUCCESS}.
         */
        NONE("none"),
        /**
         * A required process version was not installed locally.
         */
        INSTALLATION("installation"),
        /**
         * The routing publication could not be consumed or applied.
         */
        PUBLICATION("publication"),
        /**
         * Convergence stopped because the owning runtime was shutting down.
         */
        SHUTDOWN("shutdown");
        private final String tagValue;

        AliasConvergenceReason(String tagValue) {
            this.tagValue = tagValue;
        }

        /**
         * Returns the stable, bounded metric tag value.
         *
         * @return lowercase convergence-reason tag
         */
        public String getTagValue() {
            return tagValue;
        }
    }
}
