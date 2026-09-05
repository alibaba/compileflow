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
package com.alibaba.compileflow.workbench.server.api.problem;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ApiProblemsTest {
    @Test
    void serializesProblemWithoutOptionalMembers() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid request");
        problem.setDetail("The request is invalid.");

        assertThat(ApiProblems.body(problem))
            .containsExactly(Map.entry("type", "about:blank"), Map.entry("title", "Invalid request"),
                    Map.entry("status", 400), Map.entry("detail", "The request is invalid."));
    }

    @Test
    void serializesInstanceAndExtensionMembers() {
        ProblemDetail problem =
                ApiProblems.create(HttpStatus.CONFLICT, ApiProblems.CONFLICT, "Conflict", "The resource was modified.");
        problem.setInstance(URI.create("/api/processes/order"));
        problem.setProperty("revision", 7L);

        assertThat(ApiProblems.body(problem))
            .containsEntry("instance", "/api/processes/order")
            .containsEntry("code", ApiProblems.CONFLICT)
            .containsEntry("revision", 7L);
    }
}
