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

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, evolvable state of one rollout operation.
 *
 * @author yusu
 */
public final class ProcessRollout {
    private final String id;
    private final ProcessRef.Alias alias;
    private final ProcessRef.Version baselineVersion;
    private final ProcessRef.Version targetVersion;
    private final long baseAliasRevision;
    private final long aliasRevision;
    private final RolloutStrategy strategy;
    private final RolloutPhase phase;
    private final int targetWeightBps;
    private final AliasTargeting targeting;
    private final long rolloutRevision;
    private final String idempotencyKey;
    private final RolloutOperationKind operationKind;
    private final String requestFingerprint;
    private final String createdBy;
    private final String notes;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant completedAt;

    private ProcessRollout(Builder builder) {
        this.id = RolloutConstraints.requireId(builder.id, "id");
        this.alias = Objects.requireNonNull(builder.alias, "alias");
        this.baselineVersion = builder.baselineVersion == null
                ? null
                : requireSameProcess(alias, builder.baselineVersion, "baselineVersion");
        this.targetVersion = requireSameProcess(alias, builder.targetVersion, "targetVersion");
        this.baseAliasRevision = RolloutConstraints.requireNonNegativeRevision(builder.baseAliasRevision,
                "baseAliasRevision");
        if (builder.aliasRevision <= baseAliasRevision) {
            throw new IllegalArgumentException("aliasRevision must be greater than baseAliasRevision");
        }
        this.aliasRevision = builder.aliasRevision;
        this.strategy = Objects.requireNonNull(builder.strategy, "strategy");
        this.phase = Objects.requireNonNull(builder.phase, "phase");
        this.targetWeightBps = builder.targetWeightBps;
        this.targeting = builder.targeting;
        this.rolloutRevision = RolloutConstraints.requirePositiveRevision(builder.rolloutRevision, "rolloutRevision");
        this.idempotencyKey = RolloutConstraints.requireIdempotencyKey(builder.idempotencyKey);
        this.operationKind = Objects.requireNonNull(builder.operationKind, "operationKind");
        this.requestFingerprint = RolloutConstraints.requireSha256(builder.requestFingerprint, "requestFingerprint");
        this.createdBy = DeploymentAudit.requireActor(builder.createdBy);
        this.notes = DeploymentAudit.optionalNotes(builder.notes, "notes");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt");
        this.completedAt = builder.completedAt;
        validate();
    }

    /**
     * Creates an empty rollout-view builder.
     *
     * @return empty rollout builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private static ProcessRef.Version requireSameProcess(ProcessRef.Alias alias, ProcessRef.Version version,
            String name) {
        ProcessRef.Version value = Objects.requireNonNull(version, name);
        if (!alias.namespace().equals(value.namespace()) || !alias.code().equals(value.code())) {
            throw new IllegalArgumentException(name + " must identify the same namespace and process code as alias");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public ProcessRef.Alias getAlias() {
        return alias;
    }

    public ProcessRef.Version getBaselineVersion() {
        return baselineVersion;
    }

    public ProcessRef.Version getTargetVersion() {
        return targetVersion;
    }

    public long getBaseAliasRevision() {
        return baseAliasRevision;
    }

    public long getAliasRevision() {
        return aliasRevision;
    }

    public RolloutStrategy getStrategy() {
        return strategy;
    }

    public RolloutPhase getPhase() {
        return phase;
    }

    public int getTargetWeightBps() {
        return targetWeightBps;
    }

    public AliasTargeting getTargeting() {
        return targeting;
    }

    public long getRolloutRevision() {
        return rolloutRevision;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public RolloutOperationKind getOperationKind() {
        return operationKind;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    private void validate() {
        if (strategy == RolloutStrategy.CANARY && (baselineVersion == null || baselineVersion.equals(targetVersion))) {
            throw new IllegalArgumentException("CANARY rollout requires a baseline different from targetVersion");
        }
        if (strategy != RolloutStrategy.CANARY && targeting != null) {
            throw new IllegalArgumentException("targeting is supported only for CANARY rollouts");
        }
        if (operationKind == RolloutOperationKind.ROLLBACK
                && (strategy != RolloutStrategy.ALL_AT_ONCE || phase != RolloutPhase.COMPLETED)) {
            throw new IllegalArgumentException("ROLLBACK must be an immediately completed ALL_AT_ONCE rollout");
        }
        switch (phase) {
            case IN_PROGRESS -> {
                if (strategy != RolloutStrategy.CANARY || targetWeightBps <= 0
                        || targetWeightBps >= RolloutConstraints.FULL_WEIGHT_BPS) {
                    throw new IllegalArgumentException(
                            "IN_PROGRESS requires CANARY strategy and target weight between 1 and 9999 basis points");
                }
            }
            case COMPLETED -> {
                if (targetWeightBps != RolloutConstraints.FULL_WEIGHT_BPS) {
                    throw new IllegalArgumentException("COMPLETED rollout must have targetWeightBps 10000");
                }
            }
            case ABORTED -> {
                if (strategy != RolloutStrategy.CANARY || targetWeightBps != 0) {
                    throw new IllegalArgumentException("ABORTED requires CANARY strategy and targetWeightBps 0");
                }
            }
        }
        if (!createdAt.isAfter(Instant.EPOCH) || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("createdAt must be after the epoch and updatedAt must not precede it");
        }
        if (phase.isTerminal()) {
            if (completedAt == null || completedAt.isBefore(createdAt) || completedAt.isAfter(updatedAt)) {
                throw new IllegalArgumentException(
                        "terminal rollout requires completedAt between createdAt and updatedAt");
            }
        } else if (completedAt != null) {
            throw new IllegalArgumentException("non-terminal rollout must not have completedAt");
        }
    }

    /**
     * Builder for the complex immutable control-plane rollout state.
     */
    public static final class Builder {
        private String id;
        private ProcessRef.Alias alias;
        private ProcessRef.Version baselineVersion;
        private ProcessRef.Version targetVersion;
        private long baseAliasRevision;
        private long aliasRevision;
        private RolloutStrategy strategy;
        private RolloutPhase phase;
        private int targetWeightBps;
        private AliasTargeting targeting;
        private long rolloutRevision;
        private String idempotencyKey;
        private RolloutOperationKind operationKind;
        private String requestFingerprint;
        private String createdBy;
        private String notes;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant completedAt;

        private Builder() {
        }

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder alias(ProcessRef.Alias value) {
            this.alias = value;
            return this;
        }

        public Builder baselineVersion(ProcessRef.Version value) {
            this.baselineVersion = value;
            return this;
        }

        public Builder targetVersion(ProcessRef.Version value) {
            this.targetVersion = value;
            return this;
        }

        public Builder baseAliasRevision(long value) {
            this.baseAliasRevision = value;
            return this;
        }

        public Builder aliasRevision(long value) {
            this.aliasRevision = value;
            return this;
        }

        public Builder strategy(RolloutStrategy value) {
            this.strategy = value;
            return this;
        }

        public Builder phase(RolloutPhase value) {
            this.phase = value;
            return this;
        }

        public Builder targetWeightBps(int value) {
            this.targetWeightBps = value;
            return this;
        }

        public Builder targeting(AliasTargeting value) {
            this.targeting = value;
            return this;
        }

        public Builder rolloutRevision(long value) {
            this.rolloutRevision = value;
            return this;
        }

        public Builder idempotencyKey(String value) {
            this.idempotencyKey = value;
            return this;
        }

        public Builder operationKind(RolloutOperationKind value) {
            this.operationKind = value;
            return this;
        }

        public Builder requestFingerprint(String value) {
            this.requestFingerprint = value;
            return this;
        }

        public Builder createdBy(String value) {
            this.createdBy = value;
            return this;
        }

        public Builder notes(String value) {
            this.notes = value;
            return this;
        }

        public Builder createdAt(Instant value) {
            this.createdAt = value;
            return this;
        }

        public Builder updatedAt(Instant value) {
            this.updatedAt = value;
            return this;
        }

        public Builder completedAt(Instant value) {
            this.completedAt = value;
            return this;
        }

        public ProcessRollout build() {
            return new ProcessRollout(this);
        }
    }
}
