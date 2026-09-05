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
package com.alibaba.compileflow.deploy.runtime.demand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.runtime.state.DesiredRoutingState;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class VersionDemandPlannerTest {
    private static DesiredRoutingState route(String alias, String stableVersion, String candidateVersion,
            int candidateWeightBps, boolean deleted, long revision) {
        return DesiredRoutingState
            .builder()
            .namespace("default")
            .code("order.flow")
            .alias(alias)
            .stableVersion(stableVersion)
            .candidateVersion(candidateVersion)
            .candidateWeightBps(candidateWeightBps)
            .deleted(deleted)
            .actor("test")
            .updatedAt(revision)
            .revision(revision)
            .build();
    }

    private static Set<String> versions(AliasVersionDemand decision) {
        return decision.getDemandedVersions().stream().map(ProcessRef.Version::version).collect(Collectors.toSet());
    }

    @Test
    void plansOnlyTheNamedAliasTargets() {
        VersionDemandPlanner planner = new VersionDemandPlanner();

        AliasVersionDemand decision = planner.plan(route("preview", "v2", null, 0, false, 1));

        assertThat(decision.getAlias()).isEqualTo("preview");
        assertThat(versions(decision)).containsExactly("v2");
    }

    @Test
    void plansAnEmptyDemandForATombstone() {
        VersionDemandPlanner planner = new VersionDemandPlanner();

        AliasVersionDemand decision = planner.plan(route("preview", null, null, 0, true, 2));

        assertThat(decision.getDemandedVersions()).isEmpty();
    }

    @Test
    void stateChangeRequiresValidEnvelopeAndCandidate() {
        assertThatNullPointerException()
            .isThrownBy(() -> DesiredRoutingState
                .builder()
                .code("order.flow")
                .stableVersion("v1")
                .actor("test")
                .updatedAt(1)
                .revision(1)
                .build());
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DesiredRoutingState
                .builder()
                .namespace("default")
                .code("order.flow")
                .alias("production")
                .stableVersion("v1")
                .actor("test")
                .updatedAt(1)
                .revision(0)
                .build());
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DesiredRoutingState
                .builder()
                .namespace("default")
                .code("order.flow")
                .alias("production")
                .stableVersion("v1")
                .candidateVersion("v2")
                .candidateWeightBps(10_000)
                .actor("test")
                .updatedAt(1)
                .revision(1)
                .build());
    }
}
