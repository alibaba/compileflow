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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.deploy.protocol.RoutingStateKeys;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeploymentProtocolPropertyConstraintsTest {
    private static boolean canonicalPrefixAdmission(String candidate) {
        try {
            RoutingStateKeys.requirePrefix(candidate);
            return candidate != null && !candidate.isBlank();
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    @Test
    void prefixAdmissionMatchesTheCanonicalDeployProtocol() {
        for (String candidate :
                List.of("compileflow.deployment.", "a_b:c-d.", "a".repeat(128), "a".repeat(129), "", " ", " leading",
                        "trailing ", "contains/slash", "非ascii")) {
            assertThat(DeploymentProtocolPropertyConstraints.isPortableKeyPrefix(candidate))
                .as("prefix %s", candidate)
                .isEqualTo(canonicalPrefixAdmission(candidate));
        }
        assertThat(DeploymentProtocolPropertyConstraints.isPortableKeyPrefix(null)).isFalse();
    }

    @Test
    void aliasKeyAdmissionMatchesTheCanonicalDeployProtocol() {
        String valid = RoutingStateKeys.aliasState("compileflow.deployment.", "default", "order.create", "production");
        for (String candidate :
                List.of(valid, "custom.alias." + "0".repeat(64), "custom.alias." + "A".repeat(64),
                        "custom/alias." + "0".repeat(64), "custom.alias." + "0".repeat(63), "alias." + "0".repeat(64))) {
            assertThat(DeploymentProtocolPropertyConstraints.isAliasStateKey(candidate))
                .as("routing key %s", candidate)
                .isEqualTo(RoutingStateKeys.isAliasStateKey(candidate));
        }
        assertThat(DeploymentProtocolPropertyConstraints.isAliasStateKey(null)).isFalse();
    }
}
