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
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ProcessExecutionResponseTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static ExecutionRoutingResponse routing() {
        return new ExecutionRoutingResponse("default", null, "production", "v2", "production", 7L,
                ProcessAliasTarget.CANDIDATE);
    }

    @Test
    void successRoundTripPreservesExecutionFactsAndOmitsFailureFields() throws Exception {
        ProcessExecutionResponse response = new ProcessExecutionResponse(true, "Process executed successfully",
                "trace-1", "inv-1", "payment.approve", 12L, routing(), Map.of("approved", true), null, null);

        String json = objectMapper.writeValueAsString(response);
        JsonNode body = objectMapper.readTree(json);
        ProcessExecutionResponse restored = objectMapper.readValue(json, ProcessExecutionResponse.class);

        assertThat(body.path("traceId").stringValue()).isEqualTo("trace-1");
        assertThat(body.has("namespace")).isFalse();
        assertThat(body.path("routing").path("namespace").stringValue()).isEqualTo("default");
        assertThat(body.path("processCode").stringValue()).isEqualTo("payment.approve");
        assertThat(body.path("routing").path("target").stringValue()).isEqualTo("CANDIDATE");
        assertThat(body.has("errorCode")).isFalse();
        assertThat(body.has("error")).isFalse();
        assertThat(restored).isEqualTo(response);
    }

    @Test
    void failureRoundTripOmitsResultAndRetainsStableError() throws Exception {
        ProcessExecutionResponse response = new ProcessExecutionResponse(false, "Process execution failed", "trace-2",
                "inv-2", "payment.approve", 7L, routing(), null, "CF_EXEC_004", "Process execution failed");

        String json = objectMapper.writeValueAsString(response);
        JsonNode body = objectMapper.readTree(json);
        ProcessExecutionResponse restored = objectMapper.readValue(json, ProcessExecutionResponse.class);

        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("errorCode").stringValue()).isEqualTo("CF_EXEC_004");
        assertThat(body.has("result")).isFalse();
        assertThat(restored).isEqualTo(response);
    }
}
