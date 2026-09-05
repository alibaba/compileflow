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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProcessRefTest {
    @Test
    void variantsHaveOneExplicitMeaning() {
        assertThat(ProcessRef.version("order.flow", "v2").namespace()).isEqualTo("default");
        assertThat(ProcessRef.alias("order.flow", "production").namespace()).isEqualTo("default");
        assertThat(ProcessRef.version("tenant-a", "order.flow", "v2").version()).isEqualTo("v2");
        assertThat(ProcessRef.alias("tenant-a", "order.flow", "production").alias()).isEqualTo("production");
    }

    @Test
    void identifiersRejectNullBlankAndAmbiguousWhitespace() {
        assertThatThrownBy(() -> ProcessIdentifiers.requireCode(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("code");
        assertThatThrownBy(() -> ProcessIdentifiers.requireCode(" order.flow"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whitespace");
        assertThatThrownBy(() -> ProcessRef.version("default", "order.flow", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version");
        assertThatThrownBy(() -> ProcessRef.alias("default", "order.flow", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("alias");
    }

    @ParameterizedTest
    @ValueSource(strings = {".hidden", "-option", "_internal", "order/flow", "order#flow", "order|flow",
            "order@flow", "\u8ba2\u5355\u6d41\u7a0b"})
    void identifiersRejectCharactersThatAreUnsafeAcrossLogsKeysAndProtocols(String identifier) {
        assertThatThrownBy(() -> ProcessIdentifiers.requireCode(identifier))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ASCII");
        assertThatThrownBy(() -> ProcessRef.version("default", "order.flow", identifier))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ASCII");
        assertThatThrownBy(() -> ProcessRef.alias("default", "order.flow", identifier))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ASCII");
    }

    @Test
    void identifiersRejectControlCharactersExplicitly() {
        assertThatThrownBy(() -> ProcessIdentifiers.requireCode("order\nflow"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("control characters");
        assertThatThrownBy(() -> ProcessRef.version("default", "order.flow", "v\n1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("control characters");
        assertThatThrownBy(() -> ProcessRef.alias("default", "order.flow", "prod\ncanary"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("control characters");
    }

    @Test
    void exactTextIdentifiersUseUnicodeBoundariesAndCodePointLengths() {
        assertThatThrownBy(() -> ProcessIdentifiers.requireNodeId("\u00a0node"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThat(ProcessIdentifiers.requireNodeId("\uD83D\uDE00".repeat(512))).hasSize(1_024);
        assertThatThrownBy(() -> ProcessIdentifiers.requireNodeId("\uD83D\uDE00".repeat(513)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("512");
        assertThatThrownBy(() -> ProcessIdentifiers.requireExactIdentity("value", "field", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("maxLength must be positive");
    }

    @Test
    void identifiersEnforceDurableStorageLengths() {
        assertThatThrownBy(() -> ProcessIdentifiers.requireCode("c".repeat(129)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("128");
        assertThatThrownBy(() -> ProcessRef.version("n".repeat(129), "order.flow", "v1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("128");
        assertThatThrownBy(() -> ProcessRef.version("default", "order.flow", "v".repeat(65)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("64");
        assertThatThrownBy(() -> ProcessRef.alias("default", "order.flow", "a".repeat(65)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("64");

        assertThat(ProcessRef.version("n".repeat(128), "c".repeat(128), "v".repeat(64)).version()).hasSize(64);
        assertThat(ProcessRef.alias("n".repeat(128), "c".repeat(128), "a".repeat(64)).alias()).hasSize(64);
    }

    @Test
    void explicitNamespaceRejectsNullOrBlankValues() {
        assertThatThrownBy(() -> ProcessIdentifiers.requireNamespace(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("namespace");
        assertThatThrownBy(() -> ProcessRef.version(null, "order.flow", "v1"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("namespace");
        assertThatThrownBy(() -> ProcessRef.alias(" ", "order.flow", "prod"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("namespace");
        assertThatThrownBy(() -> ProcessRef.version("", "order.flow", "v1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("namespace");
        assertThatThrownBy(() -> ProcessRef.alias("\t", "order.flow", "prod"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("namespace");
    }
}
