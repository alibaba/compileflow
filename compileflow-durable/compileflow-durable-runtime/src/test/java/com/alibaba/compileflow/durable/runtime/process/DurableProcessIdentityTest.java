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
package com.alibaba.compileflow.durable.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DurableProcessIdentityTest {
    @Test
    void freezesDefinitionDigestToUuidV8Mapping() {
        UUID identity = DurableProcessIdentity.fromDefinitionDigest(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertThat(identity).hasToString("01234567-89ab-8def-8123-456789abcdef");
        assertThat(identity.version()).isEqualTo(8);
        assertThat(identity.variant()).isEqualTo(2);
    }

    @Test
    void rejectsValuesOutsideTheDefinitionDigestContract() {
        assertThatThrownBy(() -> DurableProcessIdentity.fromDefinitionDigest("0123456789ABCDEF"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("definitionDigest must be a lowercase SHA-256 digest");
    }
}
