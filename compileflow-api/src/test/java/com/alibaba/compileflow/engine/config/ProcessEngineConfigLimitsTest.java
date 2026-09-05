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

class ProcessEngineConfigLimitsTest {
    @Test
    void providesBoundedDefaultsAndDirectScalarOverrides() {
        ProcessEngineConfig defaults = ProcessEngineConfig.tbbpm();
        ProcessEngineConfig configured = ProcessEngineConfig
            .tbbpmBuilder()
            .maxCallDepth(64)
            .maxResidentRuntimes(512)
            .shutdownTimeout(Duration.ofSeconds(7))
            .build();

        assertThat(defaults.getMaxCallDepth()).isEqualTo(32);
        assertThat(defaults.getMaxResidentRuntimes()).isEqualTo(2048);
        assertThat(defaults.getShutdownTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(configured.getMaxCallDepth()).isEqualTo(64);
        assertThat(configured.getMaxResidentRuntimes()).isEqualTo(512);
        assertThat(configured.getShutdownTimeout()).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void rejectsInvalidEngineWideLimitsTogether() {
        assertThatThrownBy(() -> ProcessEngineConfig
            .tbbpmBuilder()
            .maxCallDepth(257)
            .maxResidentRuntimes(0)
            .shutdownTimeout(Duration.ZERO)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxCallDepth")
            .hasMessageContaining("maxResidentRuntimes")
            .hasMessageContaining("shutdownTimeout");
    }

    @Test
    void rejectsShutdownTimeoutsThatExecutorApisCannotRepresent() {
        assertThatThrownBy(() -> ProcessEngineConfig
            .tbbpmBuilder()
            .shutdownTimeout(Duration.ofSeconds(Long.MAX_VALUE))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("shutdownTimeout")
            .hasMessageContaining("representable as a long");
    }
}
