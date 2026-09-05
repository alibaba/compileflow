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
package com.alibaba.compileflow.deploy.control.repository;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.rollout.RolloutOperationKind;
import com.alibaba.compileflow.deploy.api.rollout.RolloutStrategy;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.util.Objects;

/**
 * Internal persistence request that adds an authoritative operation kind to validated rollout
 * creation values.
 *
 * @author yusu
 */
public final class RolloutCreateRequest {
    private final CreateRolloutCommand command;
    private final RolloutOperationKind operationKind;

    private RolloutCreateRequest(CreateRolloutCommand command, RolloutOperationKind operationKind) {
        this.command = Objects.requireNonNull(command, "command");
        this.operationKind = Objects.requireNonNull(operationKind, "operationKind");
    }

    public static RolloutCreateRequest deploy(CreateRolloutCommand command) {
        return new RolloutCreateRequest(command, RolloutOperationKind.DEPLOY);
    }

    public static RolloutCreateRequest rollback(String idempotencyKey, ProcessRef.Alias alias,
            ProcessRef.Version targetVersion, long expectedRouteRevision, String actor, String notes) {
        return new RolloutCreateRequest(CreateRolloutCommand.allAtOnce(idempotencyKey, alias, targetVersion,
                        expectedRouteRevision, actor, notes), RolloutOperationKind.ROLLBACK);
    }

    public String getIdempotencyKey() {
        return command.getIdempotencyKey();
    }

    public RolloutOperationKind getOperationKind() {
        return operationKind;
    }

    public String getNamespace() {
        return command.getAlias().namespace();
    }

    public String getCode() {
        return command.getAlias().code();
    }

    public String getAlias() {
        return command.getAlias().alias();
    }

    public String getTargetVersion() {
        return command.getTargetVersion().version();
    }

    public long getExpectedAliasRevision() {
        return command.getExpectedAliasRevision();
    }

    public RolloutStrategy getStrategy() {
        return command.getStrategy();
    }

    public Integer getCanaryWeightBps() {
        return command.getCanaryWeightBps();
    }

    public AliasTargeting getTargeting() {
        return command.getTargeting();
    }

    public String getActor() {
        return command.getActor();
    }

    public String getNotes() {
        return command.getNotes();
    }
}
