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
package com.alibaba.compileflow.deploy.api.protocol.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import org.junit.jupiter.api.Test;

class RoutingStatePayloadsTest {
    @Test
    void stableStateRoundTripsWithOneAuthoritativeRevision() {
        String json = RoutingStatePayloads.aliasStateJson("default", "c2", "prod", "v9", null, null, 11L, "op", 2L);

        RoutingStateUpdate update = RoutingStateParser.parse(json);

        assertThat(json)
            .contains("\"schemaVersion\":1")
            .contains("\"kind\":\"aliasState\"")
            .doesNotContain("\"seq\"")
            .doesNotContain("\"weights\"");
        assertThat(update).isNotNull();
        assertThat(update.getStableVersion()).isEqualTo("v9");
        assertThat(update.getCandidateVersion()).isNull();
        assertThat(update.getCandidateWeightBps()).isZero();
        assertThat(update.getAliasRevision()).isEqualTo(11L);
    }

    @Test
    void tombstoneUsesTheSameAliasStateSchema() {
        String json = RoutingStatePayloads.aliasTombstoneJson("default", "c2", "prod", 12L, "op", 3L);

        RoutingStateUpdate update = RoutingStateParser.parse(json);

        assertThat(json).contains("\"kind\":\"aliasState\"").contains("\"deleted\":true").doesNotContain(
                "stableVersion");
        assertThat(update).isNotNull();
        assertThat(update.isDeleted()).isTrue();
    }

    @Test
    void rejectsCandidateWithoutAValidBasisPointWeight() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> RoutingStatePayloads.aliasStateJson("default", "c2", "prod", "v1", "v2", null, 11L, "op",
                    2L))
            .withMessage("candidateWeightBps must be between 1 and 9999");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> RoutingStatePayloads.aliasStateJson("default", "c2", "prod", "v1", "v2", 10_000, 11L, "op",
                    2L))
            .withMessage("candidateWeightBps must be between 1 and 9999");
    }

    @Test
    void rejectsTheSameStableAndCandidateVersion() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> RoutingStatePayloads.aliasStateJson("default", "c2", "prod", "v1", "v1", 500, 11L, "op",
                    2L))
            .withMessage("stableVersion and candidateVersion must differ");
    }

    @Test
    void rejectsBlankActor() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> RoutingStatePayloads.aliasStateJson("default", "c2", "prod", "v1", null, null, 11L, " ",
                    2L))
            .withMessage("actor must not be blank");
    }
}
