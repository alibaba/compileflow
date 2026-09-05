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

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import com.alibaba.compileflow.workbench.server.api.problem.ApiProblemException;
import com.alibaba.compileflow.workbench.server.api.problem.RedactedFailure;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for the current alias route and its compare-and-set revision.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/deployment-routes")
public class DeploymentRouteController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentRouteController.class);
    private final DeploymentService deploymentService;

    public DeploymentRouteController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    private static DeploymentRouteResponse toResponse(ProcessAliasState route) {
        return new DeploymentRouteResponse(route.getRef().code(), route.getRef().alias(),
                route.getStableVersion().version(),
                route.getCandidateVersion() == null ? null : route.getCandidateVersion().version(),
                route.getCandidateWeightBps(), route.getTargeting() == null ? null : route.getTargeting().policy(),
                route.getTargeting() == null ? null : route.getTargeting().parameters(), route.getAliasRevision(),
                route.getActor(), route.getUpdatedAt().toEpochMilli());
    }

    @GetMapping
    public ResponseEntity<DeploymentRouteResponse> getDeploymentRoute(@RequestParam String processCode,
            @RequestParam String alias) {
        String normalizedProcessCode;
        String normalizedAlias;
        try {
            normalizedAlias = DeploymentRequestValidation.requireAlias(alias);
            normalizedProcessCode = ProcessRef
                .alias(ProcessRef.DEFAULT_NAMESPACE, processCode, normalizedAlias)
                .code();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        Optional<ProcessAliasState> route;
        try {
            route = deploymentService.getRoute(normalizedProcessCode, normalizedAlias);
        } catch (InvalidDeploymentRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        } catch (DeploymentException failure) {
            throw failure;
        } catch (Exception failure) {
            LOGGER.error("Unexpected deployment route lookup failure: processCode={}, alias={}", normalizedProcessCode,
                    normalizedAlias, RedactedFailure.forLogging(failure));
            throw ApiProblemException.internalError("Internal deployment route error");
        }
        return ResponseEntity.ok(
                toResponse(route.orElseThrow(() -> ApiProblemException.notFound("Deployment route was not found"))));
    }
}
