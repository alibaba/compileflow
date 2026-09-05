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

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Creates the RFC 9457 error representation used by every server HTTP endpoint.
 *
 * @author yusu
 */
public final class ApiProblems {
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String PRECONDITION_FAILED = "PRECONDITION_FAILED";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String MALFORMED_REQUEST_BODY = "MALFORMED_REQUEST_BODY";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    private static final URI ABOUT_BLANK = URI.create("about:blank");
    private static final String TYPE_PREFIX = "urn:compileflow:problem:";

    private ApiProblems() {
    }

    /**
     * Creates an RFC 9457 response with a stable application error code.
     *
     * @param status HTTP status
     * @param code   stable machine-readable application code
     * @param title  short, stable problem title
     * @param detail safe occurrence-specific explanation
     * @return response containing the configured problem detail
     */
    public static ResponseEntity<ProblemDetail> response(HttpStatusCode status, String code, String title,
            String detail) {
        return ResponseEntity.status(status).body(create(status, code, title, detail));
    }

    /**
     * Creates an RFC 9457 problem detail with a stable application error code.
     *
     * @param status HTTP status
     * @param code   stable machine-readable application code
     * @param title  short, stable problem title
     * @param detail safe occurrence-specific explanation
     * @return configured problem detail
     */
    public static ProblemDetail create(HttpStatusCode status, String code, String title, String detail) {
        String normalizedCode = requireNonBlank("code", code);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, requireNonBlank("detail", detail));
        problem.setType(problemType(normalizedCode));
        problem.setTitle(requireNonBlank("title", title));
        problem.setProperty("code", normalizedCode);
        return problem;
    }

    /**
     * Adds CompileFlow problem identity to a framework-generated problem.
     *
     * @param problem framework-generated problem detail
     * @param status  response status
     * @return the same problem after missing identity fields are populated
     */
    public static ProblemDetail normalize(ProblemDetail problem, HttpStatusCode status) {
        if (problem.getStatus() == 0) {
            problem.setStatus(status.value());
        }
        String code = defaultCode(status);
        Map<String, Object> properties = problem.getProperties();
        if (properties != null && properties.get("code") instanceof String configuredCode) {
            code = configuredCode;
        } else {
            problem.setProperty("code", code);
        }
        if (problem.getType() == null || ABOUT_BLANK.equals(problem.getType())) {
            problem.setType(problemType(code));
        }
        return problem;
    }

    /**
     * Converts a problem to its JSON object shape for responses emitted before MVC.
     *
     * @param problem problem detail
     * @return ordered map containing standard and extension members
     */
    public static Map<String, Object> body(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        URI type = problem.getType();
        body.put("type", type == null ? ABOUT_BLANK.toString() : type.toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        URI instance = problem.getInstance();
        if (instance != null) {
            body.put("instance", instance.toString());
        }
        Map<String, Object> properties = problem.getProperties();
        if (properties != null) {
            body.putAll(properties);
        }
        return body;
    }

    private static URI problemType(String code) {
        return URI.create(TYPE_PREFIX + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private static String defaultCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> INVALID_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> "FORBIDDEN";
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> CONFLICT;
            case 412 -> PRECONDITION_FAILED;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "UNPROCESSABLE_CONTENT";
            case 500 -> INTERNAL_ERROR;
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> "HTTP_" + status.value();
        };
    }

    private static String requireNonBlank(String name, String value) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
