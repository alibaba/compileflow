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

import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;

/**
 * Internal view of one deployment rollout.
 *
 * @author yusu
 */
final class DeploymentView {
    final String id;
    final String processCode;
    final String version;
    final String baselineVersion;
    final String alias;
    final String operation;
    final String status;
    final String strategy;
    final Integer canaryWeightBps;
    final AliasTargeting targeting;
    final long revision;
    final long baseRouteRevision;
    final long routeRevision;
    final String createdAt;
    final String deployedAt;
    final String createdBy;
    final String notes;
    final Long duration;

    DeploymentView(String id, String processCode, String version, String baselineVersion, String alias, String operation,
            String status, String strategy, Integer canaryWeightBps, AliasTargeting targeting, long revision,
            long baseRouteRevision, long routeRevision, String createdAt, String deployedAt, String createdBy,
            String notes, Long duration) {
        this.id = id;
        this.processCode = processCode;
        this.version = version;
        this.baselineVersion = baselineVersion;
        this.alias = alias;
        this.operation = operation;
        this.status = status;
        this.strategy = strategy;
        this.canaryWeightBps = canaryWeightBps;
        this.targeting = targeting;
        this.revision = revision;
        this.baseRouteRevision = baseRouteRevision;
        this.routeRevision = routeRevision;
        this.createdAt = createdAt;
        this.deployedAt = deployedAt;
        this.createdBy = createdBy;
        this.notes = notes;
        this.duration = duration;
    }
}
