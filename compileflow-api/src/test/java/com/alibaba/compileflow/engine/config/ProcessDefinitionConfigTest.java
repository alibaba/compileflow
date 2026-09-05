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

class ProcessDefinitionConfigTest {
    @Test
    void defaultsBoundDefinitionSize() {
        ProcessDefinitionConfig config = ProcessDefinitionConfig.defaults();

        assertThat(config.getMaxBytes()).isEqualTo(4 * 1024 * 1024);
    }

    @Test
    void toBuilderPreservesTheSizeLimit() {
        ProcessDefinitionConfig config = ProcessDefinitionConfig.builder().maxBytes(1024).build();

        assertThat(config.toBuilder().build().getMaxBytes()).isEqualTo(1024);
        assertThat(config.toString()).isEqualTo("ProcessDefinitionConfig{maxBytes=1024}");
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> ProcessDefinitionConfig.builder().maxBytes(0).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("definition.maxBytes");
        assertThatThrownBy(() -> ProcessDefinitionConfig
            .builder()
            .maxBytes(ProcessDefinitionConfig.MAX_BYTES + 1)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("definition.maxBytes");
    }
}
