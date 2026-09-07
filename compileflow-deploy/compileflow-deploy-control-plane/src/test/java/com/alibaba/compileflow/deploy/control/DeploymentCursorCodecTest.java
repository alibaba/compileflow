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
package com.alibaba.compileflow.deploy.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionCursor;
import com.alibaba.compileflow.deploy.control.repository.DeploymentCursorCodec;
import com.alibaba.compileflow.deploy.spi.store.PublishedVersionPageKey;
import com.alibaba.compileflow.deploy.spi.store.RolloutPageKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DeploymentCursorCodecTest {
    @Test
    void roundTripsTypedKeysetCursors() {
        var version = DeploymentCursorCodec.publishedVersion(123L, "v2");
        var rollout = DeploymentCursorCodec.rollout(456L, "rollout-2");

        assertThat(DeploymentCursorCodec.publishedVersion(version)).isEqualTo(new PublishedVersionPageKey(123L, "v2"));
        assertThat(DeploymentCursorCodec.rollout(rollout)).isEqualTo(new RolloutPageKey(456L, "rollout-2"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DeploymentCursorCodec.rollout(
                    new com.alibaba.compileflow.deploy.api.rollout.RolloutCursor(version.value())));
    }

    @Test
    void rejectsMalformedUtf8() {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {(byte) 0xc3, 0x28});

        assertThatIllegalArgumentException()
            .isThrownBy(() -> DeploymentCursorCodec.publishedVersion(new PublishedVersionCursor(token)))
            .withMessage("Invalid Deploy cursor");
    }

    @Test
    void rejectsAnUnencodableRolloutTieBreaker() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DeploymentCursorCodec.rollout(1L, "left|right"))
            .withMessage("rolloutId must not contain '|'");
    }
}
