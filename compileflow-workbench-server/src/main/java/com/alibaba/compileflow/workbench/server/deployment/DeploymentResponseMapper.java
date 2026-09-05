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

/**
 * Maps deployment domain views to API responses.
 *
 * @author yusu
 */
final class DeploymentResponseMapper {
    private DeploymentResponseMapper() {
    }

    static DeploymentResponse toResponse(DeploymentView deployment) {
        return new DeploymentResponse(deployment.id, deployment.processCode, deployment.version,
                deployment.baselineVersion, deployment.alias, deployment.operation, deployment.status,
                deployment.strategy, deployment.canaryWeightBps,
                deployment.targeting == null ? null : deployment.targeting.policy(),
                deployment.targeting == null ? null : deployment.targeting.parameters(), deployment.revision,
                deployment.baseRouteRevision, deployment.routeRevision, deployment.createdAt, deployment.deployedAt,
                deployment.createdBy, deployment.notes, deployment.duration);
    }
}
