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
package com.alibaba.compileflow.deploy.api.protocol.json;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import tools.jackson.core.exc.StreamConstraintsException;

class DeploymentProtocolJsonTest {
    @Test
    void rejectsPayloadsThatExceedStructuralResourceLimits() {
        String excessiveNesting = "[".repeat(65) + "0" + "]".repeat(65);
        String excessiveName = "{\"" + "x".repeat(513) + "\":true}";
        String excessiveNumber = "{\"value\":" + "1".repeat(1_001) + "}";

        assertThatThrownBy(() -> DeploymentProtocolJson.readTreeForTest(excessiveNesting))
            .isInstanceOf(StreamConstraintsException.class);
        assertThatThrownBy(() -> DeploymentProtocolJson.readTreeForTest(excessiveName))
            .isInstanceOf(StreamConstraintsException.class);
        assertThatThrownBy(() -> DeploymentProtocolJson.readTreeForTest(excessiveNumber))
            .isInstanceOf(StreamConstraintsException.class);
    }
}
