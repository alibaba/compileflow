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
import java.time.Duration;
import java.util.Objects;

/**
 * Validated, typed view of Process-owned Effect recovery semantics.
 *
 * @author yusu
 */
public final class EffectiveEffectPolicy {
    private static final Duration MAX_RECOVERY_DELAY = Duration.ofHours(24);
    private static final Duration MAX_MAX_RECOVERY_DURATION = Duration.ofDays(30);
    private static final int MAX_ATTEMPTS = 100;
    private static final int MAX_RECONCILE_ATTEMPTS = 1000;
    private final String recoveryPlanVariable;
    private final EffectRecovery recovery;
    private final int maxAttempts;
    private final int maxReconcileAttempts;
    private final Duration recoveryDelay;
    private final Duration maxRecoveryDuration;
    private final ReconcileAction reconcileAction;

    private EffectiveEffectPolicy(String recoveryPlanVariable, EffectRecovery recovery, int maxAttempts,
            int maxReconcileAttempts, Duration recoveryDelay, Duration maxRecoveryDuration,
            ReconcileAction reconcileAction) {
        this.recoveryPlanVariable = recoveryPlanVariable;
        this.recovery = recovery;
        this.maxAttempts = maxAttempts;
        this.maxReconcileAttempts = maxReconcileAttempts;
        this.recoveryDelay = recoveryDelay;
        this.maxRecoveryDuration = maxRecoveryDuration;
        this.reconcileAction = reconcileAction;
    }

    public static EffectiveEffectPolicy manualDefault() {
        return new EffectiveEffectPolicy(null, EffectRecovery.MANUAL, 1, 0, null, null, null);
    }

    public static EffectiveEffectPolicy from(EffectPolicy source) {
        Objects.requireNonNull(source, "source");
        String recoveryPlanVariable = optionalVariable(source.getRecoveryPlanVariable());
        if (recoveryPlanVariable != null) {
            return dynamic(source, recoveryPlanVariable);
        }
        EffectRecovery recovery = source.getRecovery() == null ? EffectRecovery.MANUAL : source.getRecovery();
        Duration deadline =
                positiveDuration(source.getMaxRecoveryDuration(), "maxRecoveryDuration", MAX_MAX_RECOVERY_DURATION);
        return switch (recovery) {
            case MANUAL -> manual(source, deadline);
            case RETRY -> retry(source, deadline);
            case RECONCILE -> reconcile(source, deadline);
        };
    }

    private static EffectiveEffectPolicy dynamic(EffectPolicy source, String recoveryPlanVariable) {
        requireAbsent(source.getRecovery(), "dynamic recovery");
        requireAbsent(source.getMaxAttempts(), "dynamic maxAttempts");
        requireAbsent(source.getMaxReconcileAttempts(), "dynamic maxReconcileAttempts");
        requireAbsent(source.getRecoveryDelay(), "dynamic recoveryDelay");
        requireAbsent(source.getMaxRecoveryDuration(), "dynamic maxRecoveryDuration");
        ReconcileAction reconcileAction = source.getReconcileAction();
        return new EffectiveEffectPolicy(recoveryPlanVariable, null, 0, 0, null, null, reconcileAction);
    }

    private static EffectiveEffectPolicy manual(EffectPolicy source, Duration deadline) {
        if (source.getMaxAttempts() != null && source.getMaxAttempts() != 1) {
            throw new IllegalArgumentException("manual maxAttempts must be 1 when present");
        }
        requireAbsent(source.getMaxReconcileAttempts(), "manual maxReconcileAttempts");
        requireAbsent(source.getRecoveryDelay(), "manual recoveryDelay");
        if (deadline != null) {
            throw new IllegalArgumentException("manual maxRecoveryDuration must be absent");
        }
        requireAbsent(source.getReconcileAction(), "manual reconcileAction");
        return manualDefault();
    }

    private static EffectiveEffectPolicy retry(EffectPolicy source, Duration deadline) {
        int attempts = bounded(source.getMaxAttempts(), "maxAttempts", 2, MAX_ATTEMPTS);
        Duration recoveryDelay =
                requiredPositiveDuration(source.getRecoveryDelay(), "recoveryDelay", MAX_RECOVERY_DELAY);
        requireAbsent(source.getMaxReconcileAttempts(), "retry maxReconcileAttempts");
        requireAbsent(source.getReconcileAction(), "retry reconcileAction");
        return new EffectiveEffectPolicy(null, EffectRecovery.RETRY, attempts, 0, recoveryDelay, deadline, null);
    }

    private static EffectiveEffectPolicy reconcile(EffectPolicy source, Duration deadline) {
        int attempts = bounded(source.getMaxAttempts(), "maxAttempts", 1, MAX_ATTEMPTS);
        int reconcileAttempts =
                bounded(source.getMaxReconcileAttempts(), "maxReconcileAttempts", 1, MAX_RECONCILE_ATTEMPTS);
        Duration recoveryDelay =
                requiredPositiveDuration(source.getRecoveryDelay(), "recoveryDelay", MAX_RECOVERY_DELAY);
        ReconcileAction reconcileAction = source.getReconcileAction();
        if (reconcileAction == null) {
            throw new IllegalArgumentException("reconcileAction is required");
        }
        return new EffectiveEffectPolicy(null, EffectRecovery.RECONCILE, attempts, reconcileAttempts, recoveryDelay,
                deadline, reconcileAction);
    }

    private static int bounded(Integer value, String name, int minimum, int maximum) {
        if (value == null || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static Duration requiredPositiveDuration(String value, String name, Duration maximum) {
        Duration duration = positiveDuration(value, name, maximum);
        if (duration == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return duration;
    }

    private static Duration positiveDuration(String value, String name, Duration maximum) {
        if (value == null) {
            return null;
        }
        Duration duration = Duration.ofMillis(ProtocolDuration.parsePositiveMillis(value, name));
        if (duration.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " must be positive and at most " + maximum);
        }
        return duration;
    }

    private static void requireAbsent(Object value, String name) {
        if (value != null) {
            throw new IllegalArgumentException(name + " must be absent");
        }
    }

    private static String optionalVariable(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("recoveryPlanVariable must not be blank");
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException("recoveryPlanVariable must not contain surrounding whitespace");
        }
        if (!javax.lang.model.SourceVersion.isIdentifier(value) || javax.lang.model.SourceVersion.isKeyword(value)) {
            throw new IllegalArgumentException("recoveryPlanVariable must be a variable name");
        }
        return value;
    }

    public String recoveryPlanVariable() {
        return recoveryPlanVariable;
    }

    public EffectRecovery recovery() {
        return recovery;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public int maxReconcileAttempts() {
        return maxReconcileAttempts;
    }

    public Duration recoveryDelay() {
        return recoveryDelay;
    }

    public Duration maxRecoveryDuration() {
        return maxRecoveryDuration;
    }

    public ReconcileAction reconcileAction() {
        return reconcileAction;
    }
}
