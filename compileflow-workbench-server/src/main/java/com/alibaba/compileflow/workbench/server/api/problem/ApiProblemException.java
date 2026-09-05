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

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponseException;

/**
 * Typed control-flow exception for an expected HTTP API problem.
 *
 * @author yusu
 */
public final class ApiProblemException extends ErrorResponseException {
    private static final long serialVersionUID = 1L;

    private ApiProblemException(HttpStatusCode status, String code, String title, String detail) {
        super(status, ApiProblems.create(status, code, title, detail), null);
    }

    /**
     * Creates an invalid-request problem.
     *
     * @param detail safe explanation of the invalid input
     * @return exception carrying an HTTP 400 problem
     */
    public static ApiProblemException invalidRequest(String detail) {
        return of(HttpStatus.BAD_REQUEST, ApiProblems.INVALID_REQUEST, "Invalid request", detail);
    }

    /**
     * Creates a missing-resource problem.
     *
     * @param detail safe description of the missing resource
     * @return exception carrying an HTTP 404 problem
     */
    public static ApiProblemException notFound(String detail) {
        return of(HttpStatus.NOT_FOUND, ApiProblems.RESOURCE_NOT_FOUND, "Resource not found", detail);
    }

    /**
     * Creates a state-conflict problem.
     *
     * @param detail safe explanation of the conflict
     * @return exception carrying an HTTP 409 problem
     */
    public static ApiProblemException conflict(String detail) {
        return of(HttpStatus.CONFLICT, ApiProblems.CONFLICT, "Conflict", detail);
    }

    /**
     * Creates a redacted internal-error problem.
     *
     * @param detail fixed public explanation with no implementation detail
     * @return exception carrying an HTTP 500 problem
     */
    public static ApiProblemException internalError(String detail) {
        return of(HttpStatus.INTERNAL_SERVER_ERROR, ApiProblems.INTERNAL_ERROR, "Internal server error", detail);
    }

    /**
     * Creates a typed API problem with explicit HTTP and application semantics.
     *
     * @param status HTTP status
     * @param code   stable machine-readable application code
     * @param title  short, stable problem title
     * @param detail safe occurrence-specific explanation
     * @return exception carrying the configured problem
     */
    public static ApiProblemException of(HttpStatusCode status, String code, String title, String detail) {
        return new ApiProblemException(status, code, title, detail);
    }
}
