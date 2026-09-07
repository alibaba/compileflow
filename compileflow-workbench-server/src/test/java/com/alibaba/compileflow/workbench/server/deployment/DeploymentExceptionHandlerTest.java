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
package com.alibaba.compileflow.workbench.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class DeploymentExceptionHandlerTest {
    private final DeploymentExceptionHandler handler = new DeploymentExceptionHandler();

    @Test
    void mapsConcurrencyFailureToHttpPrecondition() {
        ResponseEntity<ProblemDetail> response = handler.handle(DeploymentException
            .builder(DeploymentErrorCode.CONCURRENT_MODIFICATION, "Route revision mismatch")
            .namespace("default")
            .code("order.flow")
            .alias("production")
            .build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(response.getBody().getDetail()).isEqualTo("Deployment state changed; retry with the latest revision");
        assertThat(response.getBody().getProperties())
            .containsEntry("code", "CONCURRENT_MODIFICATION")
            .containsEntry("processCode", "order.flow")
            .containsEntry("route", "production");
    }

    @Test
    void doesNotExposeInternalDeploymentDiagnostics() {
        ResponseEntity<ProblemDetail> response = handler.handle(DeploymentException.of(DeploymentErrorCode.STORAGE_ERROR,
                "password=secret jdbc:postgresql://internal-host/compileflow",
                new IllegalStateException("private SQL detail")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getDetail())
            .isEqualTo("Deployment storage is temporarily unavailable")
            .doesNotContain("password=secret jdbc:postgresql://internal-host/compileflow");
        assertThat(response.getBody().getProperties()).containsEntry("code", "STORAGE_ERROR");
    }

    @Test
    void distinguishesNotFoundConflictAndInfrastructureFailures() {
        assertThat(status(DeploymentErrorCode.VERSION_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(DeploymentErrorCode.DEPENDENCY_NOT_FOUND)).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(status(DeploymentErrorCode.IDEMPOTENCY_CONFLICT)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(status(DeploymentErrorCode.STORAGE_ERROR)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(status(DeploymentErrorCode.INTERNAL_ERROR)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private HttpStatus status(DeploymentErrorCode code) {
        return (HttpStatus) handler.handle(DeploymentException.of(code, "failure")).getStatusCode();
    }
}
