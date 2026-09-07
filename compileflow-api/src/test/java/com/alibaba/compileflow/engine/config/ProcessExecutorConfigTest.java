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
package com.alibaba.compileflow.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProcessExecutorConfigTest {
    @Test
    void exposesExplicitConcurrencyBudgets() {
        ProcessExecutorConfig config =
                ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(3).actionTimeoutMaxConcurrency(7).build();

        assertThat(config.getRuntimeLoadMaxConcurrency()).isEqualTo(3);
        assertThat(config.getActionTimeoutMaxConcurrency()).isEqualTo(7);
    }

    @Test
    void rejectsNegativePendingAndCancellationSettingsTogether() {
        assertThatThrownBy(() -> ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxPending(-1)
            .actionTimeoutMaxPending(-1)
            .parallelCancellationGracePeriod(Duration.ofMillis(-1))
            .actionTimeoutCancellationGracePeriod(Duration.ofMillis(-1))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("runtimeLoadMaxPending")
            .hasMessageContaining("actionTimeoutMaxPending")
            .hasMessageContaining("parallelCancellationGracePeriod")
            .hasMessageContaining("actionTimeoutCancellationGracePeriod");
    }

    @Test
    void allowsFailFastAdmissionAndImmediateCancellationChecks() {
        ProcessExecutorConfig config = ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxPending(0)
            .actionTimeoutMaxPending(0)
            .parallelCancellationGracePeriod(Duration.ZERO)
            .actionTimeoutCancellationGracePeriod(Duration.ZERO)
            .build();

        assertThat(config.getRuntimeLoadMaxPending()).isZero();
        assertThat(config.getActionTimeoutMaxPending()).isZero();
        assertThat(config.getParallelCancellationGracePeriod()).isZero();
        assertThat(config.getActionTimeoutCancellationGracePeriod()).isZero();
    }
}
