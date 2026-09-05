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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ProcessDraftExceptionHandlerTest {
    private final ProcessDraftExceptionHandler handler = new ProcessDraftExceptionHandler();

    @Test
    void mapsRevisionMismatchToPreconditionFailed() {
        ResponseEntity<ProblemDetail> response =
                handler.handleRevisionConflict(new ProcessRevisionConflictException("order.flow", 2L, 3L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(response.getBody().getDetail())
            .isEqualTo("Process revision mismatch: code=order.flow, expected=2, current=3");
        assertThat(response.getBody().getProperties()).containsEntry("code", "PROCESS_REVISION_CONFLICT");
    }

    @Test
    void redactsDatabaseDetailsFromIdentityConflicts() {
        ResponseEntity<ProblemDetail> response = handler.handleIdentityConflict(
                new ProcessAlreadyExistsException("order.flow",
                        new IllegalStateException("duplicate key value violates cf_process_draft_pkey")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getDetail()).isEqualTo("Process code already exists");
        assertThat(response.getBody().getProperties()).containsEntry("code", "PROCESS_ALREADY_EXISTS");
    }
}
