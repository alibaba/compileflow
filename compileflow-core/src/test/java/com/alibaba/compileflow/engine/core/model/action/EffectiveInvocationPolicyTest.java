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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class EffectiveInvocationPolicyTest {
    @Test
    void appliesValidatedDefaults() {
        EffectiveInvocationPolicy policy =
                EffectiveInvocationPolicy.of(null, null, null, null, null, null, null, null, null);

        assertThat(policy.getTimeoutMs()).isZero();
        assertThat(policy.getAttemptTimeoutMs()).isZero();
        assertThat(policy.getMaxAttempts()).isOne();
        assertThat(policy.getInitialBackoffMs()).isEqualTo(1000L);
        assertThat(policy.getBackoffMultiplier()).isEqualTo(1.0d);
        assertThat(policy.getMaxBackoffMs()).isEqualTo(100_000L);
        assertThat(policy.getJitter()).isEqualTo(RetryJitter.FULL);
        assertThat(policy.getRetryOn()).isEqualTo("always");
        assertThat(policy.getOnFailure()).isEqualTo("propagate");
    }

    @Test
    void preservesExactPolicyIdentifiers() {
        EffectiveInvocationPolicy policy =
                EffectiveInvocationPolicy.of(0L, 0, 1, 0, 1.0d, 0, RetryJitter.NONE, "custom-retry", "custom-failure");

        assertThat(policy.getRetryOn()).isEqualTo("custom-retry");
        assertThat(policy.getOnFailure()).isEqualTo("custom-failure");
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of(0L, 0, 1, 0, 1.0d, 0, RetryJitter.NONE, " custom-retry ",
                "custom-failure"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lowercase kebab-case");
    }

    @Test
    void rejectsUnsafeOrUnboundedValues() {
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of(0L, -1, 1, 0, 1.0, 0, RetryJitter.NONE, "never",
                "propagate"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("attemptTimeoutMs");
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of(0L, 0, 101, 0, 1.0, 0, RetryJitter.NONE, "never",
                "propagate"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxAttempts");
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of(0L, 0, 1, 10, 0.5, 10, RetryJitter.NONE, "never",
                "propagate"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("backoffMultiplier");
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of(0L, 0, 1, 10, Double.NaN, 10, RetryJitter.NONE, "never",
                "propagate"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("backoffMultiplier");
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of(0L, 0, 1, 10, 1.0, 9, RetryJitter.NONE, "never",
                "propagate"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxBackoffMs");
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of(0L, 0, 1, 0, 1.0, 0, RetryJitter.NONE, "bad\nname",
                "propagate"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lowercase kebab-case");
    }

    @Test
    void requiresNonNegativeWholeMillisecondDurations() {
        EffectiveInvocationPolicy policy = EffectiveInvocationPolicy.of(null, "PT0.001S", 1, "PT0.002S", 1.0d,
                "PT0.003S", RetryJitter.NONE, "always", "propagate");

        assertThat(policy.getAttemptTimeoutMs()).isEqualTo(1L);
        assertThat(policy.getInitialBackoffMs()).isEqualTo(2L);
        assertThat(policy.getMaxBackoffMs()).isEqualTo(3L);
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of(null, "PT0.0001S", 1, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whole-millisecond precision");
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of(null, "PT0.0015S", 1, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whole-millisecond precision");
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of(null, "-PT0.0001S", 1, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be negative");
    }

    @Test
    void validatesNestedInvocationBudgets() {
        EffectiveInvocationPolicy policy = EffectiveInvocationPolicy.of("PT2M", "PT20S", 3, "PT1S", 2.0d, "PT10S",
                RetryJitter.NONE, "transient", "propagate");

        assertThat(policy.getTimeoutMs()).isEqualTo(120_000L);
        assertThat(policy.getAttemptTimeoutMs()).isEqualTo(20_000L);
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of("PT10S", "PT11S", 1, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("less than or equal to timeoutMs");
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of("PT0S", null, 1, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be positive");
        assertThatThrownBy(() -> EffectiveInvocationPolicy.of("pt1s", null, 1, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("canonical uppercase");
    }
}
