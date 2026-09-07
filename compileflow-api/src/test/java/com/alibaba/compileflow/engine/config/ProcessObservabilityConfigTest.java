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
import org.junit.jupiter.api.Test;

class ProcessObservabilityConfigTest {
    @Test
    void retainsScalarObservabilitySettings() {
        ProcessObservabilityConfig config = ProcessObservabilityConfig
            .builder()
            .eventsAsync(false)
            .eventDeliveryMaxConcurrency(3)
            .eventDeliveryMaxPending(20)
            .mdcPropagationEnabled(true)
            .build();

        assertThat(config.isEventsAsync()).isFalse();
        assertThat(config.getEventDeliveryMaxConcurrency()).isEqualTo(3);
        assertThat(config.getEventDeliveryMaxPending()).isEqualTo(20);
        assertThat(config.isMdcPropagationEnabled()).isTrue();
    }

    @Test
    void usesDocumentedDefaults() {
        ProcessObservabilityConfig config = ProcessObservabilityConfig.defaults();

        assertThat(config.isEventsAsync()).isTrue();
        assertThat(config.getEventDeliveryMaxConcurrency()).isEqualTo(2);
        assertThat(config.getEventDeliveryMaxPending()).isEqualTo(16);
        assertThat(config.isMdcPropagationEnabled()).isFalse();
    }

    @Test
    void supportsFailFastEventDeliveryButRejectsNegativePendingLimits() {
        assertThat(ProcessObservabilityConfig
            .builder()
            .eventDeliveryMaxPending(0)
            .build()
            .getEventDeliveryMaxPending())
            .isZero();

        assertThatThrownBy(() -> ProcessObservabilityConfig.builder().eventDeliveryMaxPending(-1).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventDeliveryMaxPending");
    }
}
