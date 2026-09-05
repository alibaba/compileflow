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
 * Immutable application command for creating a deployment rollout.
 *
 * @param idempotencyKey        retry identity for the rollout creation
 * @param processCode              published process code
 * @param version               published process version
 * @param alias                  target process alias
 * @param expectedRouteRevision expected alias revision
 * @param strategy              rollout strategy
 * @param notes                 optional operator notes
 * @param canaryWeightBps      optional canary traffic weight in basis points
 * @param targeting             optional named Alias targeting configuration
 * @author yusu
 */
public record CreateDeploymentCommand(String idempotencyKey, String processCode, String version, String alias,
        long expectedRouteRevision, String strategy, String notes, Integer canaryWeightBps, AliasTargeting targeting) {}
