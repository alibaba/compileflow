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
package com.alibaba.compileflow.deploy.api.command;

import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.deploy.api.rollout.RolloutConstraints;

/**
 * Requests a new all-at-once rollout to an earlier rollout's captured baseline.
 *
 * @author yusu
 */
public final class RollbackRolloutCommand {
    private final String sourceRolloutId;
    private final String idempotencyKey;
    private final long expectedAliasRevision;
    private final String actor;

    /**
     * Creates a command that records a new rollout toward a captured baseline.
     *
     * @param sourceRolloutId       rollout whose baseline is restored
     * @param idempotencyKey        caller-owned idempotency key for the new rollout
     * @param expectedAliasRevision current alias revision expected by the caller
     * @param actor                 authenticated actor requesting rollback
     */
    public RollbackRolloutCommand(String sourceRolloutId, String idempotencyKey, long expectedAliasRevision,
            String actor) {
        this.sourceRolloutId = RolloutConstraints.requireId(sourceRolloutId, "sourceRolloutId");
        this.idempotencyKey = RolloutConstraints.requireIdempotencyKey(idempotencyKey);
        this.expectedAliasRevision = RolloutConstraints.requirePositiveRevision(expectedAliasRevision,
                "expectedAliasRevision");
        this.actor = DeploymentAudit.requireActor(actor);
    }

    /**
     * Returns the rollout that supplies the captured baseline.
     *
     * @return source rollout identifier
     */
    public String getSourceRolloutId() {
        return sourceRolloutId;
    }

    /**
     * Returns the caller-owned idempotency key.
     *
     * @return idempotency key
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Returns the expected alias revision.
     *
     * @return positive expected alias revision
     */
    public long getExpectedAliasRevision() {
        return expectedAliasRevision;
    }

    /**
     * Returns the authenticated actor.
     *
     * @return actor identity
     */
    public String getActor() {
        return actor;
    }
}
