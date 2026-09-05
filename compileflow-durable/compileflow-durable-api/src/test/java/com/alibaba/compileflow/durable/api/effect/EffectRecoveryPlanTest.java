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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EffectRecoveryPlanTest {
    @Test
    void exposesClosedValidFactories() {
        assertThat(EffectRecoveryPlan.manual().mode()).isEqualTo(EffectRecoveryPlan.Mode.MANUAL);
        assertThat(EffectRecoveryPlan.retry(3, Duration.ofSeconds(1), null).maxAttempts()).isEqualTo(3);
        assertThat(EffectRecoveryPlan
            .reconcile(2, 5, Duration.ofSeconds(2), Duration.ofMinutes(30))
            .maxReconcileAttempts())
            .isEqualTo(5);
    }

    @Test
    void rejectsContradictoryRecoverySemantics() {
        assertThatThrownBy(() -> new EffectRecoveryPlan(EffectRecoveryPlan.Mode.MANUAL, 2, 0, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EffectRecoveryPlan(EffectRecoveryPlan.Mode.MANUAL, 1, 0, null,
                Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EffectRecoveryPlan.retry(1, Duration.ofSeconds(1), null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EffectRecoveryPlan.retry(2, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EffectRecoveryPlan.reconcile(2, 0, Duration.ofSeconds(1), null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
