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
package com.alibaba.compileflow.workbench.server.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ExecutionLogExceptionHandlerTest {
    @Test
    void returnsAStableRfc9457ProblemDetailForIncompleteQueries() {
        ResponseEntity<ProblemDetail> response =
                new ExecutionLogExceptionHandler()
            .handleQueryLimit(new ExecutionLogQueryLimitExceededException(10_000));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType().toString())
            .isEqualTo("urn:compileflow:problem:execution-log-query-limit-exceeded");
        assertThat(response.getBody().getProperties())
            .containsEntry("code", "EXECUTION_LOG_QUERY_LIMIT_EXCEEDED")
            .containsEntry("maxRows", 10_000);
    }
}
