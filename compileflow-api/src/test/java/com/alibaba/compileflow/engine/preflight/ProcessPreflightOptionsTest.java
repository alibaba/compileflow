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
package com.alibaba.compileflow.engine.preflight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProcessPreflightOptionsTest {
    @Test
    void rejectsConfigurationWithoutAnyStage() {
        assertThatThrownBy(() -> ProcessPreflightOptions.builder().lintEnabled(false).compileEnabled(false).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("At least one preflight stage must be enabled");
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> ProcessPreflightOptions.builder().timeout(Duration.ZERO).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("timeout must be a positive whole-millisecond duration");
    }

    @Test
    void rejectsSubMillisecondTimeout() {
        assertThatThrownBy(() -> ProcessPreflightOptions.builder().timeout(Duration.ofNanos(1)).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("timeout must be a positive whole-millisecond duration");
    }

    @Test
    void rejectsTimeoutThatCannotBeRepresentedInMilliseconds() {
        assertThatThrownBy(() -> ProcessPreflightOptions
            .builder()
            .timeout(Duration.ofSeconds(Long.MAX_VALUE))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("timeout must be a positive whole-millisecond duration");
    }

    @Test
    void strictAndFastDefaultsUseTheSameBoundedTimeout() {
        assertThat(ProcessPreflightOptions.strict().getTimeout()).isEqualTo(Duration.ofMinutes(1));
        assertThat(ProcessPreflightOptions.fast().getTimeout()).isEqualTo(Duration.ofMinutes(1));
    }
}
