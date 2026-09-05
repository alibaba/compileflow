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
package com.alibaba.compileflow.engine.core.semantic.plan;

import com.alibaba.compileflow.engine.core.java.naming.JavaNames;
import com.alibaba.compileflow.engine.core.model.action.EffectRecovery;
import com.alibaba.compileflow.engine.core.semantic.ProtocolDuration;
import com.alibaba.compileflow.engine.core.semantic.SemanticText;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable Effect recovery semantics without an authoring-layer Action reference.
 *
 * @author yusu
 */
public record EffectPolicyPlan(String recoveryPlanVariable, EffectRecovery recovery, int maxAttempts,
        int maxReconcileAttempts, Duration recoveryDelay, Duration maxRecoveryDuration, ReconcilePlan reconcileAction) {
    private static final int MAX_ATTEMPTS = 100;
    private static final int MAX_RECONCILE_ATTEMPTS = 1000;
    private static final Duration MAX_RECOVERY_DELAY = Duration.ofDays(1);
    private static final Duration MAX_RECOVERY_DURATION = Duration.ofDays(30);

    public EffectPolicyPlan {
        recoveryPlanVariable = optional(recoveryPlanVariable);
        if (recoveryPlanVariable != null) {
            if (recovery != null || maxAttempts != 0 || maxReconcileAttempts != 0 || recoveryDelay != null
                    || maxRecoveryDuration != null) {
                throw new IllegalArgumentException(
                        "A dynamic Effect policy must not also declare static recovery settings");
            }
        } else {
            recovery = Objects.requireNonNull(recovery, "recovery");
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("maxAttempts must be positive");
            }
            if (maxAttempts > MAX_ATTEMPTS) {
                throw new IllegalArgumentException("maxAttempts must not exceed " + MAX_ATTEMPTS);
            }
            if (maxReconcileAttempts < 0) {
                throw new IllegalArgumentException("maxReconcileAttempts must not be negative");
            }
            if (maxReconcileAttempts > MAX_RECONCILE_ATTEMPTS) {
                throw new IllegalArgumentException("maxReconcileAttempts must not exceed " + MAX_RECONCILE_ATTEMPTS);
            }
            recoveryDelay = optionalPositiveDuration(recoveryDelay, "recoveryDelay", MAX_RECOVERY_DELAY);
            maxRecoveryDuration = optionalPositiveDuration(maxRecoveryDuration, "maxRecoveryDuration",
                    MAX_RECOVERY_DURATION);
            switch (recovery) {
                case MANUAL -> {
                    if (maxAttempts != 1 || maxReconcileAttempts != 0 || recoveryDelay != null
                            || maxRecoveryDuration != null || reconcileAction != null) {
                        throw new IllegalArgumentException(
                                "manual Effect recovery must not declare automatic recovery settings");
                    }
                }
                case RETRY -> {
                    if (maxAttempts < 2 || maxReconcileAttempts != 0 || recoveryDelay == null || reconcileAction != null) {
                        throw new IllegalArgumentException(
                                "retry Effect recovery requires at least two attempts and recoveryDelay");
                    }
                }
                case RECONCILE -> {
                    if (maxReconcileAttempts == 0 || recoveryDelay == null || reconcileAction == null) {
                        throw new IllegalArgumentException("reconcile Effect recovery requires reconcile settings");
                    }
                }
            }
        }
    }

    public boolean dynamic() {
        return recoveryPlanVariable != null;
    }

    private static String optional(String value) {
        String variable = SemanticText.optionalIdentity(value, "recoveryPlanVariable");
        if (variable != null && !JavaNames.isIdentifier(variable)) {
            throw new IllegalArgumentException("recoveryPlanVariable must be a variable name");
        }
        return variable;
    }

    private static Duration optionalPositiveDuration(Duration value, String name, Duration maximum) {
        if (value == null) {
            return null;
        }
        long millis = ProtocolDuration.requireNonNegativeMillis(value, name);
        if (millis == 0L || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " must be positive and at most " + maximum);
        }
        return value;
    }
}
