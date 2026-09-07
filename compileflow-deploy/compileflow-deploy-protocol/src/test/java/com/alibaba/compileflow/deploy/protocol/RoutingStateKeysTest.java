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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class RoutingStateKeysTest {
    @Test
    void derivesBoundedKeysWithoutTupleCollisions() {
        String first = RoutingStateKeys.aliasState("compileflow.test.", "a", "b.c", "production");
        String second = RoutingStateKeys.aliasState("compileflow.test.", "a.b", "c", "production");

        assertThat(first)
            .isNotEqualTo(second)
            .isEqualTo("compileflow.test.alias." + "3c1462a0e23eca935bc2908254c8b6980d99497fa76c01a6f31a88164f1219c7");
        assertThat(second)
            .isEqualTo("compileflow.test.alias." + "998131f55a8cb221f5913322c1dbd7848a80bd7ce4ca6495282f14e46f28beaf");
        assertThat(first).hasSizeLessThanOrEqualTo(256);
    }

    @Test
    void matchesPayloadIdentityIndependentlyOfTheConfiguredPrefix() {
        String key = RoutingStateKeys.aliasState("tenant-specific.", "default", "order.flow", "production");

        assertThat(RoutingStateKeys.matchesAliasState(key, "default", "order.flow", "production")).isTrue();
        assertThat(RoutingStateKeys.matchesAliasState(key, "default", "other.flow", "production")).isFalse();
    }

    @Test
    void rejectsTransportKeysWithSurroundingWhitespace() {
        String key = RoutingStateKeys.aliasState("tenant-specific.", "default", "order.flow", "production");

        assertThat(RoutingStateKeys.matchesAliasState(" " + key, "default", "order.flow", "production")).isFalse();
        assertThat(RoutingStateKeys.matchesAliasState(key + " ", "default", "order.flow", "production")).isFalse();
        assertThat(RoutingStateKeys.matchesAliasState("\u00a0" + key, "default", "order.flow", "production")).isFalse();
        assertThat(RoutingStateKeys.matchesAliasState(key + "\u2003", "default", "order.flow", "production")).isFalse();
    }

    @Test
    void requiresAnExplicitCanonicalAlias() {
        assertThatThrownBy(() -> RoutingStateKeys.aliasState(null, "default", "order.flow", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("alias");
        assertThatThrownBy(() -> RoutingStateKeys.aliasState(null, "default", "order.flow", " "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoutingStateKeys.aliasState(null, "default", "order.flow", " production "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesCompleteAliasStateKeyStructure() {
        String key = RoutingStateKeys.aliasState("tenant-specific.", "default", "order.flow", "production");

        assertThat(RoutingStateKeys.isAliasStateKey(key)).isTrue();
        assertThat(RoutingStateKeys.isAliasStateKey("tenant-specific.alias.not-a-digest")).isFalse();
        assertThat(RoutingStateKeys.isAliasStateKey(
                "tenant/specific.alias." + "3c1462a0e23eca935bc2908254c8b6980d99497fa76c01a6f31a88164f1219c7"))
            .isFalse();
    }
}
