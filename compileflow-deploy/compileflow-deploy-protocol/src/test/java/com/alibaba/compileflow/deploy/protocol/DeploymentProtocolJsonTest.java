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
package com.alibaba.compileflow.deploy.protocol;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import tools.jackson.core.exc.StreamConstraintsException;

class DeploymentProtocolJsonTest {
    @Test
    void keepsConstructionClosed() {
        assertThat(DeploymentProtocolJson.class.getDeclaredConstructors())
            .allSatisfy(constructor -> assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue());
    }

    @Test
    void rejectsPayloadsThatExceedStructuralResourceLimits() {
        String excessiveNesting = "{\"value\":" + "[".repeat(65) + "0" + "]".repeat(65) + "}";
        String excessiveName = "{\"" + "x".repeat(513) + "\":true}";
        String excessiveNumber = "{\"value\":" + "1".repeat(1_001) + "}";

        assertThatThrownBy(() -> DeploymentProtocolJson.readObject(excessiveNesting))
            .isInstanceOf(StreamConstraintsException.class);
        assertThatThrownBy(() -> DeploymentProtocolJson.readObject(excessiveName))
            .isInstanceOf(StreamConstraintsException.class);
        assertThatThrownBy(() -> DeploymentProtocolJson.readObject(excessiveNumber))
            .isInstanceOf(StreamConstraintsException.class);
    }
}
