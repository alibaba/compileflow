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
package com.alibaba.compileflow.deploy.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AliasVersionDemandTest {
    @Test
    void acceptsOnlyVersionsOwnedByTheAliasProcess() {
        ProcessRef.Version stable = ProcessRef.version("default", "order.process", "v1");
        ProcessRef.Version candidate = ProcessRef.version("default", "order.process", "v2");

        AliasVersionDemand decision =
                AliasVersionDemand.forAlias("default", "order.process", "production", Set.of(stable, candidate));

        assertThat(decision.getDemandedVersions()).containsExactlyInAnyOrder(stable, candidate);
    }

    @Test
    void rejectsARequestedVersionFromAnotherProcess() {
        ProcessRef.Version foreign = ProcessRef.version("default", "payment.process", "v1");

        assertThatThrownBy(() -> AliasVersionDemand.forAlias("default", "order.process", "production", Set.of(foreign)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("another process");
    }

    @Test
    void rejectsMoreThanStableAndCandidate() {
        Set<ProcessRef.Version> versions = Set.of(ProcessRef.version("default", "order.process", "v1"),
                ProcessRef.version("default", "order.process", "v2"),
                ProcessRef.version("default", "order.process", "v3"));

        assertThatThrownBy(() -> AliasVersionDemand.forAlias("default", "order.process", "production", versions))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most one stable and one candidate");
    }

    @Test
    void rejectsNullDemandInsteadOfTreatingItAsDeletion() {
        assertThatThrownBy(() -> AliasVersionDemand.forAlias("default", "order.process", "production", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("demandedVersions");
    }
}
