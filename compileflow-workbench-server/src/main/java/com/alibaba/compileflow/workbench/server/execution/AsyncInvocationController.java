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

import com.alibaba.compileflow.workbench.server.api.problem.ApiProblemException;
import com.alibaba.compileflow.workbench.server.api.problem.RedactedFailure;
import com.alibaba.compileflow.workbench.server.api.validation.RequestValueParser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for persisted whole-process invocations.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api")
public class AsyncInvocationController {
    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final String AFTER_ZERO = "0";
    private static final String MAX_PAGE_SIZE = "" + RequestValueParser.MAX_LIST_PAGE_SIZE;
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncInvocationController.class);
    private final AsyncInvocationService asyncInvocationService;

    public AsyncInvocationController(AsyncInvocationService asyncInvocationService) {
        this.asyncInvocationService = asyncInvocationService;
    }

    private static String normalizeStatus(String status) {
        String normalized = StringUtils.trimToNull(status);
        if (normalized == null) {
            return null;
        }
        if (AsyncInvocationService.STATUS_QUEUED.equals(normalized)
                || AsyncInvocationService.STATUS_RUNNING.equals(normalized)
                || AsyncInvocationService.STATUS_SUCCEEDED.equals(normalized)
                || AsyncInvocationService.STATUS_DEAD_LETTER.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("status must be one of: queued, running, succeeded, dead_letter");
    }

    @PostMapping("/processes/{code}/async-invocations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<AsyncInvocationResponse> submitAsyncInvocation(@PathVariable String code,
            @RequestBody(required = false) AsyncInvocationSubmitRequest body) {
        try {
            return ResponseEntity.accepted().body(asyncInvocationService.submit(code, body));
        } catch (InvalidAsyncInvocationRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        } catch (AsyncInvocationService.AsyncInvocationConflictException failure) {
            throw ApiProblemException.conflict(failure.getMessage());
        } catch (Exception failure) {
            LOGGER.error("Unexpected persisted async submission failure: code={}", code,
                    RedactedFailure.forLogging(failure));
            throw ApiProblemException.internalError("Internal async invocation error");
        }
    }

    @GetMapping("/async-invocations")
    @Parameters({@Parameter(name = "page", in = ParameterIn.QUERY, schema = @Schema(implementation = Integer.class, defaultValue = DEFAULT_PAGE, minimum =
            "1")),
            @Parameter(name = "pageSize", in = ParameterIn.QUERY, schema = @Schema(implementation = Integer.class, defaultValue = DEFAULT_PAGE_SIZE, minimum =
            "1", maximum = MAX_PAGE_SIZE))})
    public ResponseEntity<AsyncInvocationListResponse> listAsyncInvocations(
            @Parameter(schema = @Schema(allowableValues = {"queued", "running", "succeeded", "dead_letter"})) @RequestParam(required = false) String status,
            @RequestParam(required = false) String processCode, @RequestParam(defaultValue = DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int pageSize) {
        String normalizedStatus;
        try {
            normalizedStatus = normalizeStatus(status);
            RequestValueParser.requireOneBasedPage(page, pageSize, RequestValueParser.MAX_LIST_PAGE_SIZE);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        return ResponseEntity.ok(asyncInvocationService.list(normalizedStatus, StringUtils.trimToNull(processCode), page,
                pageSize));
    }

    @GetMapping("/async-invocations/health")
    public ResponseEntity<AsyncInvocationHealthResponse> getAsyncInvocationHealth() {
        return ResponseEntity.ok(asyncInvocationService.health());
    }

    @GetMapping("/async-invocations/{invocationId}")
    public ResponseEntity<AsyncInvocationResponse> getAsyncInvocation(@PathVariable String invocationId) {
        String normalizedInvocationId;
        try {
            normalizedInvocationId = RequestValueParser.requiredInvocationId(invocationId);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        AsyncInvocationResponse invocation = asyncInvocationService
            .get(normalizedInvocationId)
            .orElseThrow(() -> ApiProblemException.notFound(
                    "Async invocation '" + normalizedInvocationId + "' was not found"));
        return ResponseEntity.ok(invocation);
    }

    @GetMapping("/async-invocations/{invocationId}/attempts")
    @Parameters({@Parameter(name = "afterSequence", in = ParameterIn.QUERY, schema = @Schema(implementation = Long.class, defaultValue = AFTER_ZERO, minimum =
            "0")),
            @Parameter(name = "limit", in = ParameterIn.QUERY, schema = @Schema(implementation = Integer.class, defaultValue = DEFAULT_PAGE_SIZE, minimum =
            "1", maximum = MAX_PAGE_SIZE))})
    public ResponseEntity<AsyncInvocationAttemptListResponse> listAsyncInvocationAttempts(
            @PathVariable String invocationId, @RequestParam(defaultValue = AFTER_ZERO) long afterSequence,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int limit) {
        String normalizedInvocationId;
        try {
            normalizedInvocationId = RequestValueParser.requiredInvocationId(invocationId);
            if (afterSequence < 0L) {
                throw new IllegalArgumentException("afterSequence must be greater than or equal to 0");
            }
            RequestValueParser.requireLimit(limit, "limit", RequestValueParser.MAX_LIST_PAGE_SIZE);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        return ResponseEntity.ok(asyncInvocationService
            .listAttempts(normalizedInvocationId, afterSequence, limit)
            .orElseThrow(() -> ApiProblemException.notFound(
                    "Async invocation '" + normalizedInvocationId + "' was not found")));
    }

    @PostMapping("/async-invocations/{invocationId}/requeue")
    public ResponseEntity<AsyncInvocationResponse> requeueAsyncInvocation(@PathVariable String invocationId) {
        String normalizedInvocationId;
        try {
            normalizedInvocationId = RequestValueParser.requiredInvocationId(invocationId);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        Optional<AsyncInvocationResponse> invocation;
        try {
            invocation = asyncInvocationService.requeue(normalizedInvocationId);
        } catch (AsyncInvocationService.AsyncInvocationConflictException failure) {
            throw ApiProblemException.conflict(failure.getMessage());
        } catch (Exception failure) {
            LOGGER.error("Unexpected persisted async requeue failure: invocationId={}", normalizedInvocationId,
                    RedactedFailure.forLogging(failure));
            throw ApiProblemException.internalError("Internal async invocation error");
        }
        return ResponseEntity.ok(invocation.orElseThrow(() -> ApiProblemException.notFound(
                "Async invocation '" + normalizedInvocationId + "' was not found")));
    }

    @PostMapping("/async-invocations/dead-letters/requeue")
    public ResponseEntity<AsyncInvocationDeadLetterRequeueResponse> requeueAsyncInvocationDeadLetters(
            @RequestBody(required = false) DeadLetterRequeueRequest body) {
        DeadLetterRequeueRequest request = body == null ? DeadLetterRequeueRequest.empty() : body;
        return ResponseEntity.ok(asyncInvocationService.requeueDeadLetters(request.processCode(),
                request.effectiveLimit()));
    }
}
