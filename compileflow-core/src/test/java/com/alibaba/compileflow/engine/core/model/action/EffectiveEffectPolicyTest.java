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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class EffectiveEffectPolicyTest {
    @Test
    void manualRecoveryRejectsADeadlineThatWouldHaveNoBehavior() {
        EffectPolicy policy = new EffectPolicy();
        policy.setRecovery(EffectRecovery.MANUAL);
        policy.setMaxRecoveryDuration("PT1M");

        assertThatThrownBy(() -> EffectiveEffectPolicy.from(policy))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("manual maxRecoveryDuration must be absent");
    }

    @Test
    void retryRecoveryRequiresAtLeastOneRedispatch() {
        EffectPolicy policy = new EffectPolicy();
        policy.setRecovery(EffectRecovery.RETRY);
        policy.setMaxAttempts(1);
        policy.setRecoveryDelay("PT1S");

        assertThatThrownBy(() -> EffectiveEffectPolicy.from(policy))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxAttempts must be between 2 and 100");
    }

    @Test
    void reconcileRecoveryReportsItsMissingActionAsInvalidConfiguration() {
        EffectPolicy policy = new EffectPolicy();
        policy.setRecovery(EffectRecovery.RECONCILE);
        policy.setMaxAttempts(1);
        policy.setMaxReconcileAttempts(1);
        policy.setRecoveryDelay("PT1S");

        assertThatThrownBy(() -> EffectiveEffectPolicy.from(policy))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("reconcileAction is required");
    }

    @Test
    void dynamicRecoveryRejectsBlankVariableIdentity() {
        EffectPolicy policy = new EffectPolicy();
        policy.setRecoveryPlanVariable(" ");

        assertThatThrownBy(() -> EffectiveEffectPolicy.from(policy))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("recoveryPlanVariable must not be blank");
    }

    @Test
    void rejectsNonCanonicalRecoveryDurations() {
        EffectPolicy policy = new EffectPolicy();
        policy.setRecovery(EffectRecovery.RETRY);
        policy.setMaxAttempts(2);
        policy.setRecoveryDelay("pt1s");

        assertThatThrownBy(() -> EffectiveEffectPolicy.from(policy))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("recoveryDelay must use canonical uppercase ISO-8601 notation");
    }
}
