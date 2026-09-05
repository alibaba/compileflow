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
package com.alibaba.compileflow.workbench.server.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AsyncInvocationWireEnumTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void serializesAndDeserializesStableLowercaseWireValues() throws Exception {
        assertThat(objectMapper.writeValueAsString(AsyncInvocationStatus.DEAD_LETTER)).isEqualTo("\"dead_letter\"");
        assertThat(objectMapper.readValue("\"lease_expired\"", AsyncInvocationAttemptOutcome.class))
            .isEqualTo(AsyncInvocationAttemptOutcome.LEASE_EXPIRED);
        assertThat(objectMapper.readValue("\"retry_scheduled\"", AsyncInvocationAttemptDisposition.class))
            .isEqualTo(AsyncInvocationAttemptDisposition.RETRY_SCHEDULED);
    }

    @Test
    void rejectsUnknownWireValuesAtTheApiBoundary() {
        assertThatThrownBy(() -> AsyncInvocationStatus.fromValue("completed"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown async invocation status: completed");
    }
}
