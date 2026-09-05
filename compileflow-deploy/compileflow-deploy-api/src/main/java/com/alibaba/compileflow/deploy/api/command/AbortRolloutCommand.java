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
 * Command that restores the baseline captured by a canary rollout.
 *
 * @author yusu
 */
public final class AbortRolloutCommand {
    private final String rolloutId;
    private final long expectedRevision;
    private final String actor;
    private final String reason;

    /**
     * Creates a compare-and-set abort command.
     *
     * @param rolloutId        rollout to abort
     * @param expectedRevision current rollout revision expected by the caller
     * @param actor            authenticated actor requesting the abort
     * @param reason           optional audit reason
     */
    public AbortRolloutCommand(String rolloutId, long expectedRevision, String actor, String reason) {
        this.rolloutId = RolloutConstraints.requireId(rolloutId, "rolloutId");
        this.expectedRevision = RolloutConstraints.requirePositiveRevision(expectedRevision, "expectedRevision");
        this.actor = DeploymentAudit.requireActor(actor);
        this.reason = DeploymentAudit.optionalNotes(reason, "reason");
    }

    /**
     * Returns the rollout identifier.
     *
     * @return rollout identifier
     */
    public String getRolloutId() {
        return rolloutId;
    }

    /**
     * Returns the expected rollout revision.
     *
     * @return positive expected revision
     */
    public long getExpectedRevision() {
        return expectedRevision;
    }

    /**
     * Returns the authenticated actor.
     *
     * @return actor identity
     */
    public String getActor() {
        return actor;
    }

    /**
     * Returns the audit reason.
     *
     * @return normalized reason, or {@code null}
     */
    public String getReason() {
        return reason;
    }
}
