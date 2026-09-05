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
package com.alibaba.compileflow.workbench.server.process;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class PublicationDigestsTest {
    @Test
    void freezesPublicationVersionIdentity() {
        assertThat(PublicationDigests.version("default", "order", "idem-42")).isEqualTo(
                "r-8a1f805e1c5a713e763d731653411ac6");
    }

    @Test
    void freezesPublicationRequestFingerprint() {
        assertThat(PublicationDigests.request("order", 7, "release notes"))
            .isEqualTo("e7c7bd12d3677ff0400e8e7c332c8c4901f9b8b770fd42eb09a35198f30dca0e");
    }
}
