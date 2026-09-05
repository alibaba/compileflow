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
package com.alibaba.compileflow.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class ProcessTriggerTest {
    @Test
    void preservesExactBoundedTriggerValues() {
        ProcessTrigger trigger = ProcessTrigger.on("entry", "message");

        assertThat(trigger.nodeId()).isEqualTo("entry");
        assertThat(trigger.event()).isEqualTo("message");
        assertThat(ProcessTrigger.at("entry").event()).isNull();
    }

    @Test
    void rejectsSurroundingWhitespaceInsteadOfSilentlyChangingIdentity() {
        assertThatThrownBy(() -> ProcessTrigger.at(" entry "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("nodeId must not contain surrounding whitespace");
        assertThatThrownBy(() -> ProcessTrigger.on("entry", " message "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("event must not contain surrounding whitespace");
        assertThatThrownBy(() -> new ProcessTrigger("entry", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("event must not be blank");
    }

    @Test
    void rejectsOversizedNodeAndEventSelectors() {
        assertThatThrownBy(() -> ProcessTrigger.at("n".repeat(513)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("nodeId must not exceed 512 characters");
        assertThatThrownBy(() -> ProcessTrigger.on("entry", "e".repeat(513)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("event must not exceed 512 characters");
    }
}
