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
package com.alibaba.compileflow.deploy.spi.store;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DeploymentPersistentDigestsTest {
    @Test
    void freezesRolloutRequestFingerprint() {
        assertThat(DeploymentPersistentDigests.rolloutRequest("CREATE", "commerce", "order", "stable", "v7", "42",
                "CANARY", "2500", "release-bot", "progressive"))
            .isEqualTo("453558e5763e74709d354a6309757e1f60e5892602721ee9cbf2d1cc3e4f3eea");
    }

    @Test
    void freezesRoutingOutboxDeliveryKey() {
        assertThat(DeploymentPersistentDigests.routingOutbox("ALIAS_STATE_CHANGED", "commerce", "order", "stable",
                "commerce/order/stable", "{\"revision\":42}"))
            .isEqualTo("7df1d67670e2ba4ba406b3d7d3e57f2031bfd5556b80cc74dea8bf3140c18ef8");
    }

    @Test
    void domainSeparatesEqualFieldSequences() {
        assertThat(DeploymentPersistentDigests.rolloutRequest("same"))
            .isNotEqualTo(DeploymentPersistentDigests.routingOutbox("same"));
    }
}
