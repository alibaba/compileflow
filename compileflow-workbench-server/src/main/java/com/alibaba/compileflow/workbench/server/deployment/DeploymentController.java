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

import com.alibaba.compileflow.deploy.api.rollout.RolloutConstraints;
import com.alibaba.compileflow.workbench.server.api.problem.ApiProblemException;
import com.alibaba.compileflow.workbench.server.api.validation.RequestValueParser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for deployment rollouts and canary operations.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {
    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final String MAX_PAGE_SIZE = "100";
    private final DeploymentService deploymentService;
    private final CanaryHealthService canaryHealthService;

    @Autowired
    public DeploymentController(DeploymentService deploymentService, CanaryHealthService canaryHealthService) {
        this.deploymentService = deploymentService;
        this.canaryHealthService = canaryHealthService;
    }

    private static String normalizeOptionalAlias(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            return null;
        }
        return DeploymentRequestValidation.requireAlias(alias);
    }

    private static <T> T requireBody(T body) {
        if (body == null) {
            throw ApiProblemException.invalidRequest("request body is required");
        }
        return body;
    }

    /**
     * Lists deployments with optional filters and keyset continuation.
     *
     * @param processCode optional process code filter
     * @param keyword     optional process-code or deployment-id search term
     * @param alias optional deployment alias filter
     * @param status      optional deployment status filter
     * @param cursor      optional opaque continuation cursor
     * @param limit       maximum number of deployments returned
     * @return bounded deployment page and continuation metadata
     */
    @GetMapping
    @Parameters(@Parameter(name = "limit", in = ParameterIn.QUERY, schema = @Schema(implementation = Integer.class, defaultValue = DEFAULT_PAGE_SIZE, minimum =
            "1", maximum = MAX_PAGE_SIZE)))
    public ResponseEntity<DeploymentListResponse> listDeployments(
            @Parameter(schema = @Schema(maxLength = 128, pattern = "[A-Za-z0-9][A-Za-z0-9._-]*")) @RequestParam(required = false) String processCode,
            @Parameter(schema = @Schema(maxLength = 128)) @RequestParam(required = false) String keyword,
            @Parameter(schema = @Schema(maxLength = 64, pattern = "[A-Za-z0-9][A-Za-z0-9._-]*")) @RequestParam(required = false) String alias,
            @Parameter(schema = @Schema(allowableValues = {"in_progress", "completed", "aborted"})) @RequestParam(required = false) String status,
            @Parameter(schema = @Schema(maxLength = 1024)) @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int limit) {
        String normalizedAlias;
        try {
            normalizedAlias = normalizeOptionalAlias(alias);
            RequestValueParser.requireLimit(limit, "limit", RequestValueParser.MAX_LIST_PAGE_SIZE);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }

        DeploymentPage result;
        try {
            result = deploymentService.listDeployments(processCode, keyword, normalizedAlias, status, cursor, limit);
        } catch (InvalidDeploymentRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }

        return ResponseEntity.ok(
                new DeploymentListResponse(result.deployments
                            .stream()
                            .map(DeploymentResponseMapper::toResponse)
                            .toList(), result.nextCursor, result.nextCursor != null));
    }

    /**
     * Returns a deployment by id.
     *
     * @param id deployment id
     * @return deployment details, or {@code 404} when the deployment does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<DeploymentResponse> getDeployment(@PathVariable String id) {
        DeploymentView deployment;
        try {
            deployment = deploymentService
                .getDeployment(id)
                .orElseThrow(() -> ApiProblemException.notFound("Deployment '" + id + "' was not found"));
        } catch (InvalidDeploymentRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        return ResponseEntity.ok(DeploymentResponseMapper.toResponse(deployment));
    }

    /**
     * Creates a deployment for a published process version.
     *
     * @param body request body containing process code, version, alias, and optional rollout fields
     * @return created deployment details, or an error response when the request is invalid
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<DeploymentResponse> createDeployment(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey, @RequestBody CreateDeploymentRequest body) {
        if (body == null) {
            throw ApiProblemException.invalidRequest("request body is required");
        }
        CreateDeploymentCommand command;
        try {
            command = body.toCommand(idempotencyKey);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        try {
            DeploymentView deployment = deploymentService.createDeployment(command);
            return ResponseEntity.status(201).body(DeploymentResponseMapper.toResponse(deployment));
        } catch (InvalidDeploymentRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
    }

    /**
     * Rolls back a deployment.
     *
     * @param id   deployment id
     * @param body current route revision used for compare-and-set
     * @return rollback deployment details
     */
    @PostMapping("/{id}/rollback")
    public ResponseEntity<DeploymentResponse> rollbackDeployment(@PathVariable String id,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody RollbackDeploymentRequest body) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiProblemException.invalidRequest("Idempotency-Key is required");
        }
        long expectedRouteRevision;
        try {
            expectedRouteRevision = requireBody(body).requireExpectedRouteRevision();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        DeploymentView deployment;
        try {
            deployment = deploymentService.rollbackDeployment(id,
                    RolloutConstraints.requireIdempotencyKey(idempotencyKey), expectedRouteRevision);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        return ResponseEntity.ok(DeploymentResponseMapper.toResponse(deployment));
    }

    /**
     * Updates the canary traffic weight for a canary deployment.
     *
     * @param id   deployment id
     * @param body request body containing the new canary weight in basis points
     * @return updated deployment details, or an error response when the update is invalid
     */
    @PutMapping("/{id}/canary")
    public ResponseEntity<DeploymentResponse> updateCanary(@PathVariable String id,
            @RequestBody UpdateCanaryRequest body) {
        int weightBps;
        long expectedRevision;
        try {
            UpdateCanaryRequest request = requireBody(body);
            weightBps = request.requireWeightBps();
            expectedRevision = request.requireExpectedRevision();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        DeploymentView deployment;
        try {
            deployment = deploymentService.updateCanary(id, weightBps, expectedRevision);
        } catch (InvalidDeploymentRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        return ResponseEntity.ok(DeploymentResponseMapper.toResponse(deployment));
    }

    /**
     * Promotes a canary deployment to full traffic.
     *
     * @param id deployment id
     * @return promoted deployment details, or an error response when promotion cannot be performed
     */
    @PostMapping("/{id}/promote")
    public ResponseEntity<DeploymentResponse> promoteCanary(@PathVariable String id,
            @RequestBody PromoteCanaryRequest body) {
        long expectedRevision;
        try {
            expectedRevision = requireBody(body).requireExpectedRevision();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        DeploymentView deployment;
        try {
            deployment = deploymentService.promoteCanary(id, expectedRevision);
        } catch (InvalidDeploymentRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        return ResponseEntity.ok(DeploymentResponseMapper.toResponse(deployment));
    }

    /**
     * Aborts an active canary and restores its captured baseline route.
     *
     * @param id   deployment id
     * @param body expected revision and optional reason
     * @return aborted deployment details
     */
    @PostMapping("/{id}/abort")
    public ResponseEntity<DeploymentResponse> abortCanary(@PathVariable String id, @RequestBody AbortCanaryRequest body) {
        long expectedRevision;
        AbortCanaryRequest request;
        try {
            request = requireBody(body);
            expectedRevision = request.requireExpectedRevision();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        try {
            return ResponseEntity.ok(DeploymentResponseMapper.toResponse(deploymentService.abortCanary(id,
                    expectedRevision, request.reason())));
        } catch (InvalidDeploymentRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
    }

    /**
     * Evaluates canary health for a deployment.
     *
     * @param id   deployment id
     * @param body optional health evaluation thresholds
     * @return canary health result, or an error response when evaluation cannot be performed
     */
    @PostMapping("/{id}/canary/evaluate")
    public ResponseEntity<CanaryHealthEvaluationResponse> evaluateCanary(@PathVariable String id,
            @RequestBody(required = false) EvaluateCanaryRequest body) {
        CanaryHealthService.CanaryHealthRequest request;
        try {
            request = (body == null ? EvaluateCanaryRequest.empty() : body).toServiceRequest();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        try {
            return ResponseEntity.ok(canaryHealthService
                .evaluate(id, request)
                .orElseThrow(() -> ApiProblemException.notFound("Deployment '" + id + "' was not found")));
        } catch (InvalidDeploymentRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
    }

    /**
     * Returns the append-only audit events associated with a deployment.
     *
     * @param id deployment id
     * @return ordered audit events, or {@code 404} when the deployment does not exist
     */
    @GetMapping("/{id}/events")
    public ResponseEntity<List<DeploymentEventView>> getDeploymentEvents(@PathVariable String id) {
        List<DeploymentEventView> events;
        try {
            events = deploymentService
                .getDeploymentEvents(id)
                .orElseThrow(() -> ApiProblemException.notFound("Deployment '" + id + "' was not found"));
        } catch (InvalidDeploymentRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        return ResponseEntity.ok(events);
    }
}
