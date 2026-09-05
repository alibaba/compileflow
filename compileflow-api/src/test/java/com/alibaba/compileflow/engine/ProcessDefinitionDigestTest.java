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
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProcessDefinitionDigestTest {
    @Test
    void identifiesTheModelCodeAndExactDefinitionBytes() {
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order", "<bpm code=\"order\"/>");

        String digest = ProcessDefinitionDigest.compute(ProcessModelType.TBBPM, definition);

        assertThat(digest)
            .isEqualTo(ProcessDefinitionDigest.compute(ProcessModelType.TBBPM, "order",
                    definition.content().getBytes(StandardCharsets.UTF_8)))
            .isNotEqualTo(ProcessDefinitionDigest.compute(ProcessModelType.BPMN, definition))
            .isNotEqualTo(ProcessDefinitionDigest.compute(ProcessModelType.TBBPM,
                    ProcessDefinition.inline("payment", definition.content())));
    }
}
