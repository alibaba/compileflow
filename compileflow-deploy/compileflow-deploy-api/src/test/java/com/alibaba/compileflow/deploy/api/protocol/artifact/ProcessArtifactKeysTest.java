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
package com.alibaba.compileflow.deploy.api.protocol.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ProcessArtifactKeysTest {
    @Test
    void boundedCanonicalKeysPreventTupleCollisions() {
        String first = ProcessArtifactKeys.versioned(null, "a", "b.c", "v1");
        String second = ProcessArtifactKeys.versioned(null, "a.b", "c", "v1");

        assertThat(first).isNotEqualTo(second);
        assertThat(first)
            .isEqualTo(
                    "compileflow.process.version." + "6bb862bd30b166f9192e5df70d5faf3186b0315b388d8ba0f8a2eeb6064dc12c");
        assertThat(second)
            .isEqualTo(
                    "compileflow.process.version." + "d417320b2b0178aa183b1a9a067e69d22ab7f0676b806d3a563cb44f30702656");
        assertThat(first).hasSizeLessThanOrEqualTo(256);
    }
}
