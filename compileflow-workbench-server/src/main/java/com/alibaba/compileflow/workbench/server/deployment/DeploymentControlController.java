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

import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxAdminService;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for deployment control-plane health and dead-letter operations.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/deployment-control")
public class DeploymentControlController {
    private final RoutingOutboxAdminService routingOutboxAdminService;
    private final Clock clock;

    @Autowired
    public DeploymentControlController(RoutingOutboxAdminService routingOutboxAdminService) {
        this(routingOutboxAdminService, Clock.systemUTC());
    }

    DeploymentControlController(RoutingOutboxAdminService routingOutboxAdminService, Clock clock) {
        this.routingOutboxAdminService = routingOutboxAdminService;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static DeploymentControlHealthResponse toResponse(RoutingOutboxAdminService.PipelineHealth health,
            Instant checkedAt) {
        return new DeploymentControlHealthResponse(health.getStatus(), health.getPendingCount(),
                health.getProcessingCount(), health.getExpiredClaimCount(), health.getFailedCount(),
                health.isDispatcherRunning(), health.isOutboxStateAvailable(), checkedAt.toString());
    }

    @GetMapping("/health")
    public ResponseEntity<DeploymentControlHealthResponse> getDeploymentControlHealth() {
        return ResponseEntity.ok(toResponse(routingOutboxAdminService.getHealth(), clock.instant()));
    }

    @PostMapping("/dead-letters/requeue")
    public ResponseEntity<RequeueDeploymentDeadLettersResponse> requeueDeploymentDeadLetters() {
        int requeued = routingOutboxAdminService.requeueDeadLetters();
        Instant requeuedAt = clock.instant();
        return ResponseEntity.ok(
                new RequeueDeploymentDeadLettersResponse(requeued, requeuedAt.toString(),
                        toResponse(routingOutboxAdminService.getHealth(), clock.instant())));
    }
}
