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
package com.alibaba.compileflow.deploy.api.routing;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.deploy.api.rollout.RolloutConstraints;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable public view of one authoritative published Alias.
 *
 * @author yusu
 */
public final class ProcessAliasState {
    private final ProcessRef.Alias ref;
    private final ProcessRef.Version stableVersion;
    private final ProcessRef.Version candidateVersion;
    private final Integer candidateWeightBps;
    private final AliasTargeting targeting;
    private final long aliasRevision;
    private final String actor;
    private final Instant updatedAt;

    /**
     * Creates an immutable Alias state view.
     *
     * @param ref                 exact Alias reference
     * @param stableVersion       required stable version
     * @param candidateVersion    optional candidate version
     * @param candidateWeightBps optional candidate traffic weight in {@code 1..9999} basis points
     * @param aliasRevision       positive authoritative revision
     * @param actor               authenticated actor of the latest mutation
     * @param updatedAt           authority timestamp
     */
    public ProcessAliasState(ProcessRef.Alias ref, ProcessRef.Version stableVersion, ProcessRef.Version candidateVersion,
            Integer candidateWeightBps, long aliasRevision, String actor, Instant updatedAt) {
        this(ref, stableVersion, candidateVersion, candidateWeightBps, null, aliasRevision, actor, updatedAt);
    }

    /**
     * Creates an immutable Alias state view with optional named targeting.
     *
     * @param ref                 exact Alias reference
     * @param stableVersion       required stable version
     * @param candidateVersion    optional candidate version
     * @param candidateWeightBps optional candidate traffic weight in {@code 1..9999} basis points
     * @param targeting           optional named targeting policy configuration
     * @param aliasRevision       positive authoritative revision
     * @param actor               authenticated actor of the latest mutation
     * @param updatedAt           authority timestamp
     */
    public ProcessAliasState(ProcessRef.Alias ref, ProcessRef.Version stableVersion, ProcessRef.Version candidateVersion,
            Integer candidateWeightBps, AliasTargeting targeting, long aliasRevision, String actor, Instant updatedAt) {
        this.ref = Objects.requireNonNull(ref, "ref");
        this.stableVersion = requireSameProcess(ref, stableVersion, "stableVersion");
        this.candidateVersion = candidateVersion == null
                ? null
                : requireSameProcess(ref, candidateVersion, "candidateVersion");
        this.candidateWeightBps = candidateWeightBps;
        this.targeting = targeting;
        this.aliasRevision = aliasRevision;
        this.actor = DeploymentAudit.requireActor(actor);
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        validate();
    }

    /**
     * Returns the exact Alias reference.
     *
     * @return Alias reference
     */
    public ProcessRef.Alias getRef() {
        return ref;
    }

    /**
     * Returns the stable version.
     *
     * @return stable version
     */
    public ProcessRef.Version getStableVersion() {
        return stableVersion;
    }

    /**
     * Returns the candidate version.
     *
     * @return candidate version, or {@code null}
     */
    public ProcessRef.Version getCandidateVersion() {
        return candidateVersion;
    }

    /**
     * Returns the configured candidate traffic weight.
     *
     * @return basis points in {@code 1..9999}, or {@code null}
     */
    public Integer getCandidateWeightBps() {
        return candidateWeightBps;
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
     * Returns the authoritative Alias revision.
     *
     * @return positive revision
     */
    public long getAliasRevision() {
        return aliasRevision;
    }

    /**
     * Returns the authenticated actor of the latest mutation.
     *
     * @return actor identity
     */
    public String getActor() {
        return actor;
    }

    /**
     * Returns the authority timestamp of the latest mutation.
     *
     * @return authority timestamp
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static ProcessRef.Version requireSameProcess(ProcessRef.Alias alias, ProcessRef.Version version,
            String name) {
        ProcessRef.Version value = Objects.requireNonNull(version, name);
        if (!alias.namespace().equals(value.namespace()) || !alias.code().equals(value.code())) {
            throw new IllegalArgumentException(name + " must identify the same namespace and process code as ref");
        }
        return value;
    }

    private void validate() {
        boolean hasCandidate = candidateVersion != null;
        if (hasCandidate != (candidateWeightBps != null)) {
            throw new IllegalArgumentException("candidateVersion and candidateWeightBps must both be present or absent");
        }
        if (!hasCandidate && targeting != null) {
            throw new IllegalArgumentException("targeting must be absent without candidateVersion");
        }
        if (hasCandidate) {
            if (stableVersion.equals(candidateVersion)) {
                throw new IllegalArgumentException("stableVersion and candidateVersion must differ");
            }
            int weightBps = Objects.requireNonNull(candidateWeightBps, "candidateWeightBps");
            RolloutConstraints.requireActiveWeightBps(weightBps, "candidateWeightBps");
        }
        if (aliasRevision <= 0L) {
            throw new IllegalArgumentException("aliasRevision must be positive");
        }
        if (!updatedAt.isAfter(Instant.EPOCH)) {
            throw new IllegalArgumentException("updatedAt must be after the epoch");
        }
    }
}
