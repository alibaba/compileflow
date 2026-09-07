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
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DesiredRoutingStateTest {
    @Test
    void preservesAuthoritativeCanaryWeightBps() {
        ProcessAliasState alias = new ProcessAliasState(ProcessRef.alias("default", "order.process", "production"),
                ProcessRef.version("default", "order.process", "v1"),
                ProcessRef.version("default", "order.process", "v2"), 2_500, 3L, "release-bot",
                Instant.ofEpochMilli(10L));

        DesiredRoutingState desired = DesiredRoutingState.from(alias);

        assertThat(desired.getNamespace()).isEqualTo("default");
        assertThat(desired.getCode()).isEqualTo("order.process");
        assertThat(desired.getAlias()).isEqualTo("production");
        assertThat(desired.getStableVersion()).isEqualTo("v1");
        assertThat(desired.getCandidateVersion()).isEqualTo("v2");
        assertThat(desired.getCandidateWeightBps()).isEqualTo(2_500);
        assertThat(desired.getRevision()).isEqualTo(3L);
        assertThat(desired.getActor()).isEqualTo("release-bot");
        assertThat(desired.getUpdatedAt()).isEqualTo(10L);
    }

    @Test
    void rejectsNonCanonicalActorText() {
        assertThatThrownBy(() -> DesiredRoutingState
            .builder()
            .namespace("default")
            .code("order.process")
            .alias("production")
            .stableVersion("v1")
            .revision(1L)
            .actor(" release-bot ")
            .updatedAt(10L)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actor");
    }
}
