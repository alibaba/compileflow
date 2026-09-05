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

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.deploy.api.rollout.RolloutConstraints;
import com.alibaba.compileflow.deploy.api.rollout.RolloutStrategy;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.util.Objects;

/**
 * Command that creates one idempotent rollout operation.
 *
 * @author yusu
 */
public final class CreateRolloutCommand {
    private final String idempotencyKey;
    private final ProcessRef.Alias alias;
    private final ProcessRef.Version targetVersion;
    private final long expectedAliasRevision;
    private final RolloutStrategy strategy;
    private final Integer canaryWeightBps;
    private final AliasTargeting targeting;
    private final String actor;
    private final String notes;

    private CreateRolloutCommand(String idempotencyKey, ProcessRef.Alias alias, ProcessRef.Version target,
            long expectedAliasRevision, RolloutStrategy strategy, Integer canaryWeightBps, AliasTargeting targeting,
            String actor, String notes) {
        this.idempotencyKey = RolloutConstraints.requireIdempotencyKey(idempotencyKey);
        ProcessRef.Alias aliasRef = Objects.requireNonNull(alias, "alias");
        ProcessRef.Version versionRef = Objects.requireNonNull(target, "target");
        if (!aliasRef.namespace().equals(versionRef.namespace()) || !aliasRef.code().equals(versionRef.code())) {
            throw new IllegalArgumentException("alias and target must identify the same namespace and process code");
        }
        this.alias = aliasRef;
        this.targetVersion = versionRef;
        this.expectedAliasRevision = RolloutConstraints.requireNonNegativeRevision(expectedAliasRevision,
                "expectedAliasRevision");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.canaryWeightBps = requireCanaryWeightBps(strategy, canaryWeightBps);
        this.targeting = requireTargeting(strategy, targeting);
        this.actor = DeploymentAudit.requireActor(actor);
        this.notes = DeploymentAudit.optionalNotes(notes, "notes");
    }

    /**
     * Creates an idempotent rollout that switches all traffic atomically.
     *
     * @param idempotencyKey        caller-owned idempotency key
     * @param alias                 published Alias to update
     * @param target                immutable target version for the same process as {@code alias}
     * @param expectedAliasRevision current alias revision expected by the caller
     * @param actor                 authenticated actor requesting the rollout
     * @param notes                 optional audit notes
     * @return validated all-at-once rollout command
     */
    public static CreateRolloutCommand allAtOnce(String idempotencyKey, ProcessRef.Alias alias,
            ProcessRef.Version target, long expectedAliasRevision, String actor, String notes) {
        return new CreateRolloutCommand(idempotencyKey, alias, target, expectedAliasRevision,
                RolloutStrategy.ALL_AT_ONCE, null, null, actor, notes);
    }

    /**
     * Creates an idempotent rollout that initially shifts a weighted share of traffic.
     *
     * @param idempotencyKey        caller-owned idempotency key
     * @param alias                 published Alias to update
     * @param target                immutable target version for the same process as {@code alias}
     * @param expectedAliasRevision current alias revision expected by the caller
     * @param initialWeightBps     initial candidate weight in {@code 1..9999} basis points
     * @param actor                 authenticated actor requesting the rollout
     * @param notes                 optional audit notes
     * @return validated canary rollout command
     */
    public static CreateRolloutCommand canary(String idempotencyKey, ProcessRef.Alias alias, ProcessRef.Version target,
            long expectedAliasRevision, int initialWeightBps, String actor, String notes) {
        return new CreateRolloutCommand(idempotencyKey, alias, target, expectedAliasRevision, RolloutStrategy.CANARY,
                initialWeightBps, null, actor, notes);
    }

    /**
     * Creates an idempotent canary rollout with an optional named targeting policy.
     *
     * @param idempotencyKey        caller-owned idempotency key
     * @param alias                 published Alias to update
     * @param target                immutable target version for the same process as {@code alias}
     * @param expectedAliasRevision current Alias revision expected by the caller
     * @param initialWeightBps       initial candidate weight in {@code 1..9999} basis points
     * @param targeting              optional named targeting policy configuration
     * @param actor                 authenticated actor requesting the rollout
     * @param notes                 optional audit notes
     * @return validated canary rollout command
     */
    public static CreateRolloutCommand canary(String idempotencyKey, ProcessRef.Alias alias, ProcessRef.Version target,
            long expectedAliasRevision, int initialWeightBps, AliasTargeting targeting, String actor, String notes) {
        return new CreateRolloutCommand(idempotencyKey, alias, target, expectedAliasRevision, RolloutStrategy.CANARY,
                initialWeightBps, targeting, actor, notes);
    }

    private static Integer requireCanaryWeightBps(RolloutStrategy strategy, Integer weightBps) {
        if (strategy == RolloutStrategy.ALL_AT_ONCE) {
            if (weightBps != null) {
                throw new IllegalArgumentException("canaryWeightBps must be absent for ALL_AT_ONCE");
            }
            return null;
        }
        if (weightBps == null) {
            throw new IllegalArgumentException("canaryWeightBps is required for CANARY");
        }
        return RolloutConstraints.requireActiveWeightBps(weightBps.intValue(), "canaryWeightBps");
    }

    private static AliasTargeting requireTargeting(RolloutStrategy strategy, AliasTargeting targeting) {
        if (strategy != RolloutStrategy.CANARY && targeting != null) {
            throw new IllegalArgumentException("targeting is supported only for CANARY rollouts");
        }
        return targeting;
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
     * Returns the normalized process namespace.
     *
     * @return process namespace
     */
    public ProcessRef.Alias getAlias() {
        return alias;
    }

    /**
     * Returns the immutable target version.
     *
     * @return target version
     */
    public ProcessRef.Version getTargetVersion() {
        return targetVersion;
    }

    /**
     * Returns the expected alias revision.
     *
     * @return non-negative expected Alias revision
     */
    public long getExpectedAliasRevision() {
        return expectedAliasRevision;
    }

    /**
     * Returns the traffic-shift strategy.
     *
     * @return rollout strategy
     */
    public RolloutStrategy getStrategy() {
        return strategy;
    }

    /**
     * Returns the initial canary traffic weight.
     *
     * @return basis points in {@code 1..9999} for a canary rollout, otherwise {@code null}
     */
    public Integer getCanaryWeightBps() {
        return canaryWeightBps;
    }

    /**
     * Returns the optional named targeting policy configuration.
     *
     * @return targeting configuration, or {@code null}
     */
    public AliasTargeting getTargeting() {
        return targeting;
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
     * Returns the audit notes.
     *
     * @return normalized notes, or {@code null}
     */
    public String getNotes() {
        return notes;
    }
}
