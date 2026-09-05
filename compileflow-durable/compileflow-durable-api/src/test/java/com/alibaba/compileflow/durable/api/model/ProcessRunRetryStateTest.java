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
package com.alibaba.compileflow.durable.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProcessRunRetryStateTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-04T00:00:00Z");

    @Test
    void retryEvidenceRequiresAtLeastOneTurnFault() {
        assertThatThrownBy(() -> new ProcessRunRetryState(RunRetryCode.TURN_EXECUTION_FAULT, 0, OBSERVED_AT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("consecutiveTurnFaults must be between 1 and 2147483647");
    }

    @Test
    void acceptsPositiveTurnFaultStreak() {
        ProcessRunRetryState retry = new ProcessRunRetryState(RunRetryCode.TURN_EXECUTION_FAULT, 1, OBSERVED_AT);

        assertThat(retry.consecutiveTurnFaults()).isOne();
    }
}
