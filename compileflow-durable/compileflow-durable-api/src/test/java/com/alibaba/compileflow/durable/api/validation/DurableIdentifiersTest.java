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
package com.alibaba.compileflow.durable.api.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class DurableIdentifiersTest {
    @Test
    void rejectsRatherThanNormalizesOptionalIdentities() {
        assertThat(DurableIdentifiers.optionalIdentity(null, "identity", 32)).isNull();
        assertThatThrownBy(() -> DurableIdentifiers.optionalIdentity(" identity ", "identity", 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThatThrownBy(() -> DurableIdentifiers.optionalIdentity(" ", "identity", 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("identity must not be blank");
        assertThatThrownBy(() -> DurableIdentifiers.requireIdentity("line\nbreak", "identity", 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("control characters");
        assertThatThrownBy(() -> DurableIdentifiers.requireIdentity("\u00a0identity", "identity", 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThat(DurableIdentifiers.requireIdentity("\uD83D\uDE00".repeat(32), "identity", 32)).hasSize(64);
        assertThatThrownBy(() -> DurableIdentifiers.requireIdentity("\uD83D\uDE00".repeat(33), "identity", 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32");
    }

    @Test
    void preservesHumanReadableTextExactly() {
        assertThat(DurableIdentifiers.requireHumanText("  reviewed manually  ", "reason", 32))
            .isEqualTo("  reviewed manually  ");
        assertThatThrownBy(() -> DurableIdentifiers.optionalHumanText("  ", "reason", 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
        assertThatThrownBy(() -> DurableIdentifiers.requireHumanText("\u00a0", "reason", 32))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void acceptsOnlyLowercaseCanonicalUuidText() {
        String uuid = "abcdefab-cdef-abcd-efab-cdefabcdef01";

        assertThat(DurableIdentifiers.requireCanonicalUuid(uuid, "eventId")).isEqualTo(uuid);
        assertThatThrownBy(() -> DurableIdentifiers.requireCanonicalUuid(uuid.toUpperCase(), "eventId"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("canonical UUID");
        assertThatThrownBy(() -> DurableIdentifiers.requireCanonicalUuid("1-1-1-1-1", "eventId"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("canonical UUID");
    }
}
