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
package com.alibaba.compileflow.engine.spi.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessRef;
import org.junit.jupiter.api.Test;

class ProcessAliasRouteTest {
    private static final ProcessRef.Alias ALIAS = ProcessRef.alias("shop", "order", "production");

    @Test
    void stableRouteHasNoCandidate() {
        ProcessAliasRoute route = ProcessAliasRoute.stable(ALIAS, "v1", 7L);

        assertThat(route.stableVersion().version()).isEqualTo("v1");
        assertThat(route.candidateVersion()).isNull();
        assertThat(route.candidateWeightBps()).isZero();
        assertThat(route.revision()).isEqualTo(7L);
        assertThat(route.hasCandidate()).isFalse();
    }

    @Test
    void canaryRouteMapsOnlyItsAuthorizedTargets() {
        ProcessAliasRoute route = ProcessAliasRoute.canary(ALIAS, "v1", "v2", 1_250, 8L);

        assertThat(route.versionFor(ProcessAliasTarget.STABLE).version()).isEqualTo("v1");
        assertThat(route.versionFor(ProcessAliasTarget.CANDIDATE).version()).isEqualTo("v2");
        assertThat(route.hasCandidate()).isTrue();
    }

    @Test
    void rejectsInvalidRoute() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessAliasRoute.canary(ALIAS, "v1", "v1", 100, 1L))
            .withMessageContaining("must differ");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessAliasRoute.canary(ALIAS, "v1", "v2", 0, 1L))
            .withMessageContaining("between 1 and 9999");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessAliasRoute.stable(ALIAS, "v1", 0L))
            .withMessageContaining("revision");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessAliasRoute(ALIAS, ProcessRef.version("other", "order", "v1"), null, 0, null, 1L))
            .withMessageContaining("same namespace");
    }

    @Test
    void stableRouteRejectsCandidateSelection() {
        ProcessAliasRoute route = ProcessAliasRoute.stable(ALIAS, "v1", 1L);

        assertThatThrownBy(() -> route.versionFor(ProcessAliasTarget.CANDIDATE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("without an active candidate");
    }
}
