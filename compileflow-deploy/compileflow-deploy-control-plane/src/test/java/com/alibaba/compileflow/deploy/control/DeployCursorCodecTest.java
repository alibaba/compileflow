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
import com.alibaba.compileflow.deploy.control.repository.DeployCursorCodec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DeployCursorCodecTest {
    @Test
    void roundTripsTypedKeysetCursors() {
        var version = DeployCursorCodec.publishedVersion(123L, "v2");
        var rollout = DeployCursorCodec.rollout(456L, "rollout-2");

        assertThat(DeployCursorCodec.publishedVersion(version))
            .isEqualTo(new DeployCursorCodec.PublishedVersionKey(123L, "v2"));
        assertThat(DeployCursorCodec.rollout(rollout)).isEqualTo(new DeployCursorCodec.RolloutKey(456L, "rollout-2"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DeployCursorCodec.rollout(
                    new com.alibaba.compileflow.deploy.api.rollout.RolloutCursor(version.value())));
    }

    @Test
    void rejectsMalformedUtf8() {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {(byte) 0xc3, 0x28});

        assertThatIllegalArgumentException()
            .isThrownBy(() -> DeployCursorCodec.publishedVersion(new PublishedVersionCursor(token)))
            .withMessage("Invalid Deploy cursor");
    }

    @Test
    void rejectsAnUnencodableRolloutTieBreaker() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DeployCursorCodec.rollout(1L, "left|right"))
            .withMessage("rolloutId must not contain '|'");
    }
}
