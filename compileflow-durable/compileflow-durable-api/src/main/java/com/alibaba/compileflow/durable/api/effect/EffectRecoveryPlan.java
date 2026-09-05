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
package com.alibaba.compileflow.durable.api.effect;

import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import java.time.Duration;
import java.util.Objects;

/**
 * Recovery semantics frozen when one Effect occurrence is committed.
 *
 * <p>The value contains only Process-owned execution semantics. It does not identify an
 * application implementation, worker, compiler, or Runtime build.</p>
 *
 * @param mode closed recovery mode
 * @param maxAttempts maximum dispatch attempts, including the first dispatch
 * @param maxReconcileAttempts maximum reconciliation attempts
 * @param recoveryDelay delay between automatic recovery operations
 * @param maxRecoveryDuration optional maximum uncertainty duration for automatic recovery
 * @author yusu
 */
public record EffectRecoveryPlan(Mode mode, int maxAttempts, int maxReconcileAttempts, Duration recoveryDelay,
        Duration maxRecoveryDuration) {
    private static final int MAX_DISPATCH_ATTEMPTS = 100;
    private static final int MAX_RECONCILE_ATTEMPTS = 1_000;
    private static final Duration MAX_RECOVERY_DELAY = Duration.ofHours(24);
    private static final Duration MAX_MAX_RECOVERY_DURATION = Duration.ofDays(30);

    public EffectRecoveryPlan {
        mode = Objects.requireNonNull(mode, "mode");
        requireRange(maxAttempts, 1, MAX_DISPATCH_ATTEMPTS, "maxAttempts");
        requireRange(maxReconcileAttempts, 0, MAX_RECONCILE_ATTEMPTS, "maxReconcileAttempts");
        recoveryDelay = boundedPositive(recoveryDelay, MAX_RECOVERY_DELAY, "recoveryDelay");
        maxRecoveryDuration = boundedPositive(maxRecoveryDuration, MAX_MAX_RECOVERY_DURATION, "maxRecoveryDuration");
        switch (mode) {
            case MANUAL -> {
                if (maxAttempts != 1 || maxReconcileAttempts != 0 || recoveryDelay != null
                        || maxRecoveryDuration != null) {
                    throw new IllegalArgumentException("manual recovery must not declare automatic recovery settings");
                }
            }
            case RETRY -> {
                if (maxAttempts < 2 || maxReconcileAttempts != 0 || recoveryDelay == null) {
                    throw new IllegalArgumentException(
                            "retry recovery requires at least two attempts and recoveryDelay");
                }
            }
            case RECONCILE -> {
                if (maxReconcileAttempts == 0 || recoveryDelay == null) {
                    throw new IllegalArgumentException(
                            "reconcile recovery requires recoveryDelay and reconcile attempts");
                }
            }
        }
    }

    /**
     * Returns immediate manual review after an uncertain outcome.
     */
    public static EffectRecoveryPlan manual() {
        return new EffectRecoveryPlan(Mode.MANUAL, 1, 0, null, null);
    }

    /**
     * Returns bounded automatic redispatch recovery.
     */
    public static EffectRecoveryPlan retry(int maxAttempts, Duration recoveryDelay, Duration maxRecoveryDuration) {
        return new EffectRecoveryPlan(Mode.RETRY, maxAttempts, 0, recoveryDelay, maxRecoveryDuration);
    }

    /**
     * Returns bounded proof-before-redispatch recovery.
     */
    public static EffectRecoveryPlan reconcile(int maxAttempts, int maxReconcileAttempts, Duration recoveryDelay,
            Duration maxRecoveryDuration) {
        return new EffectRecoveryPlan(Mode.RECONCILE, maxAttempts, maxReconcileAttempts, recoveryDelay,
                maxRecoveryDuration);
    }

    private static Duration boundedPositive(Duration value, Duration maximum, String name) {
        if (value != null && (value.isZero() || value.isNegative() || value.compareTo(maximum) > 0)) {
            throw new IllegalArgumentException(name + " must be positive and at most " + maximum);
        }
        return value == null ? null : DurableNumbers.requireDurationMillis(value, maximum, name);
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be in [" + minimum + ", " + maximum + "]");
        }
    }

    /**
     * Closed recovery modes for an uncertain Effect occurrence.
     */
    public enum Mode {
        MANUAL,
        RETRY,
        RECONCILE
    }
}
