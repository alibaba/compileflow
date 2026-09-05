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

import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.workbench.server.api.problem.ApiProblems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps deployment domain failures to stable HTTP error semantics.
 *
 * @author yusu
 */
@RestControllerAdvice
public final class DeploymentExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentExceptionHandler.class);

    private static String causeType(DeploymentException failure) {
        Throwable cause = failure.getCause();
        return cause == null ? failure.getClass().getName() : cause.getClass().getName();
    }

    private static String publicMessage(DeploymentErrorCode code) {
        return switch (code) {
            case INVALID_ARGUMENT -> "Invalid deployment request";
            case VERSION_CONFLICT -> "The process version already exists with different content";
            case ARTIFACT_IDENTITY_MISMATCH -> "The resolved artifact does not match the requested process version";
            case ARTIFACT_DIGEST_MISMATCH -> "The artifact content failed integrity verification";
            case DEPENDENCY_NOT_FOUND -> "An exact child-process version referenced by the model was not found";
            case VERSION_NOT_FOUND -> "The process version was not found";
            case ROLLOUT_NOT_FOUND -> "The rollout was not found";
            case ROLLOUT_CONFLICT -> "The rollout cannot be changed in its current state";
            case IDEMPOTENCY_CONFLICT -> "The idempotency key was reused for a different request";
            case REPOSITORY_ERROR -> "Deployment storage is temporarily unavailable";
            case CONCURRENT_MODIFICATION -> "Deployment state changed; retry with the latest revision";
            case CONVERGENCE_FAILED -> "The deployment could not converge to a ready state";
            case ARTIFACT_PROJECTION_FAILED -> "The deployment artifact could not be published";
            case INTERNAL_ERROR -> "An internal deployment error occurred";
        };
    }

    private static HttpStatus status(DeploymentErrorCode code) {
        return switch (code) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case VERSION_NOT_FOUND, ROLLOUT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DEPENDENCY_NOT_FOUND -> HttpStatus.UNPROCESSABLE_CONTENT;
            case CONCURRENT_MODIFICATION -> HttpStatus.PRECONDITION_FAILED;
            case VERSION_CONFLICT, ROLLOUT_CONFLICT, IDEMPOTENCY_CONFLICT, ARTIFACT_DIGEST_MISMATCH,
                    ARTIFACT_IDENTITY_MISMATCH -> HttpStatus.CONFLICT;
            case CONVERGENCE_FAILED, REPOSITORY_ERROR, ARTIFACT_PROJECTION_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static void setIfPresent(ProblemDetail target, String key, String value) {
        if (value != null) {
            target.setProperty(key, value);
        }
    }

    @ExceptionHandler(DeploymentException.class)
    public ResponseEntity<ProblemDetail> handle(DeploymentException failure) {
        HttpStatus status = status(failure.getErrorCode());
        if (status.is5xxServerError()) {
            LOGGER.error("Deployment request failed. errorCode={}, namespace={}, "
                    + "code={}, version={}, route={}, causeType={}", failure.getErrorCode(), failure.getNamespace(),
                    failure.getCode(), failure.getVersion(), failure.getAlias(), causeType(failure));
        }
        ProblemDetail problem = ApiProblems.create(status, failure.getErrorCode().name(), "Deployment request failed",
                publicMessage(failure.getErrorCode()));
        setIfPresent(problem, "namespace", failure.getNamespace());
        setIfPresent(problem, "processCode", failure.getCode());
        setIfPresent(problem, "version", failure.getVersion());
        setIfPresent(problem, "route", failure.getAlias());
        return ResponseEntity.status(status).body(problem);
    }
}
