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
package com.alibaba.compileflow.deploy.api.rollout;

import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import java.time.Instant;
import java.util.Objects;

/**
 * Append-only audit event for a rollout state transition.
 *
 * @author yusu
 */
public final class RolloutEvent {
    private final long id;
    private final String rolloutId;
    private final long sequence;
    private final String type;
    private final RolloutPhase fromPhase;
    private final RolloutPhase toPhase;
    private final String actor;
    private final String reason;
    private final Instant createdAt;

    /**
     * Creates one immutable rollout audit event.
     *
     * @param id        persistent event identifier
     * @param rolloutId owning rollout
     * @param sequence  one-based sequence within the rollout
     * @param type      stable uppercase event type
     * @param fromPhase previous phase, absent only for the first event
     * @param toPhase   resulting phase
     * @param actor     authenticated mutation actor
     * @param reason    optional audit reason
     * @param createdAt authority timestamp
     */
    public RolloutEvent(long id, String rolloutId, long sequence, String type, RolloutPhase fromPhase,
            RolloutPhase toPhase, String actor, String reason, Instant createdAt) {
        if (id <= 0L) {
            throw new IllegalArgumentException("id must be positive");
        }
        this.id = id;
        this.rolloutId = RolloutConstraints.requireId(rolloutId, "rolloutId");
        if (sequence <= 0L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        this.sequence = sequence;
        this.type = RolloutConstraints.requireEventType(type);
        this.fromPhase = fromPhase;
        this.toPhase = Objects.requireNonNull(toPhase, "toPhase");
        if (sequence == 1L) {
            if (fromPhase != null || toPhase == RolloutPhase.ABORTED) {
                throw new IllegalArgumentException(
                        "first rollout event must enter IN_PROGRESS or COMPLETED without fromPhase");
            }
        } else if (fromPhase != RolloutPhase.IN_PROGRESS) {
            throw new IllegalArgumentException("subsequent rollout events must start from IN_PROGRESS");
        }
        this.actor = DeploymentAudit.requireActor(actor);
        this.reason = DeploymentAudit.optionalNotes(reason, "reason");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!createdAt.isAfter(Instant.EPOCH)) {
            throw new IllegalArgumentException("createdAt must be after the epoch");
        }
    }

    /**
     * Returns the persistent event identifier.
     *
     * @return positive event identifier
     */
    public long getId() {
        return id;
    }

    /**
     * Returns the owning rollout identifier.
     *
     * @return rollout identifier
     */
    public String getRolloutId() {
        return rolloutId;
    }

    /**
     * Returns the event sequence within the rollout.
     *
     * @return positive one-based sequence
     */
    public long getSequence() {
        return sequence;
    }

    /**
     * Returns the stable event type.
     *
     * @return uppercase event type
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the phase before this transition.
     *
     * @return previous phase, or {@code null} for the first event
     */
    public RolloutPhase getFromPhase() {
        return fromPhase;
    }

    /**
     * Returns the phase after this transition.
     *
     * @return resulting phase
     */
    public RolloutPhase getToPhase() {
        return toPhase;
    }

    /**
     * Returns the authenticated mutation actor.
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

    /**
     * Returns the authority timestamp.
     *
     * @return authority timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
