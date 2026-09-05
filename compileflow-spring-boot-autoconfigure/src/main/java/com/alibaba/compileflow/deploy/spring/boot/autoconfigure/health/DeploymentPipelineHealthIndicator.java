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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.health;

import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxAdminService;
import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports routing-outbox pipeline health to Spring Boot Actuator.
 *
 * @author yusu
 */
public class DeploymentPipelineHealthIndicator implements HealthIndicator {
    private final RoutingOutboxAdminService routingOutboxAdminService;

    public DeploymentPipelineHealthIndicator(RoutingOutboxAdminService routingOutboxAdminService) {
        this.routingOutboxAdminService = Objects.requireNonNull(routingOutboxAdminService, "routingOutboxAdminService");
    }

    private static Health.Builder mapStatus(String pipelineStatus) {
        switch (pipelineStatus) {
            case "UP":
            case "DEGRADED":
                return Health.up();
            case "DOWN":
            default:
                return Health.down();
        }
    }

    @Override
    public Health health() {
        RoutingOutboxAdminService.PipelineHealth snapshot;
        try {
            snapshot = routingOutboxAdminService.getHealth();
        } catch (Exception failure) {
            return Health
                .down()
                .withDetail("error", failure.getClass().getName())
                .withDetail("message", "Deployment health query failed")
                .build();
        }

        Health.Builder builder = mapStatus(snapshot.getStatus());
        builder
            .withDetail("pendingCount", snapshot.getPendingCount())
            .withDetail("processingCount", snapshot.getProcessingCount())
            .withDetail("expiredClaimCount", snapshot.getExpiredClaimCount())
            .withDetail("failedCount", snapshot.getFailedCount())
            .withDetail("dispatcherRunning", snapshot.isDispatcherRunning())
            .withDetail("outboxStateAvailable", snapshot.isOutboxStateAvailable())
            .withDetail("pipelineStatus", snapshot.getStatus());
        return builder.build();
    }
}
