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
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.deploy.api.rollout.RolloutConstraints;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;

/**
 * Immutable persistence model for one authoritative published-alias state.
 *
 * @author yusu
 */
public final class ProcessAliasRecord {
    private final ProcessRef.Alias ref;
    private final String stableVersion;
    private final String candidateVersion;
    private final Integer candidateWeightBps;
    private final AliasTargeting targeting;
    private final long revision;
    private final String updatedBy;
    private final long updatedAt;

    private ProcessAliasRecord(Builder builder) {
        ref = ProcessRef.alias(builder.namespace, builder.code, builder.alias);
        stableVersion = ProcessRef.version(ref.namespace(), ref.code(), builder.stableVersion).version();
        candidateVersion = builder.candidateVersion == null
                ? null
                : ProcessRef.version(ref.namespace(), ref.code(), builder.candidateVersion).version();
        candidateWeightBps = builder.candidateWeightBps;
        targeting = builder.targeting;
        revision = builder.revision;
        updatedBy = DeploymentAudit.requireActor(builder.updatedBy);
        updatedAt = builder.updatedAt;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getNamespace() {
        return ref.namespace();
    }

    public String getCode() {
        return ref.code();
    }

    public String getAlias() {
        return ref.alias();
    }

    public String getStableVersion() {
        return stableVersion;
    }

    public String getCandidateVersion() {
        return candidateVersion;
    }

    public Integer getCandidateWeightBps() {
        return candidateWeightBps;
    }

    public AliasTargeting getTargeting() {
        return targeting;
    }

    public long getRevision() {
        return revision;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    private void validate() {
        boolean hasCandidate = candidateVersion != null;
        if (hasCandidate != (candidateWeightBps != null)) {
            throw new IllegalArgumentException(
                    "candidateVersion and candidateWeightBps must either both be set or both be absent");
        }
        if (!hasCandidate && targeting != null) {
            throw new IllegalArgumentException("targeting must be absent without candidateVersion");
        }
        if (hasCandidate) {
            if (stableVersion.equals(candidateVersion)) {
                throw new IllegalArgumentException("Stable and candidate versions must differ");
            }
            RolloutConstraints.requireActiveWeightBps(candidateWeightBps.intValue(), "candidateWeightBps");
        }
        if (revision <= 0) {
            throw new IllegalArgumentException("Route revision must be greater than zero");
        }
        if (updatedAt <= 0L) {
            throw new IllegalArgumentException("updatedAt must be positive");
        }
    }

    /**
     * Builder for immutable alias route records.
     */
    public static final class Builder {
        private String namespace;
        private String code;
        private String alias;
        private String stableVersion;
        private String candidateVersion;
        private Integer candidateWeightBps;
        private AliasTargeting targeting;
        private long revision;
        private String updatedBy;
        private long updatedAt;

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder stableVersion(String stableVersion) {
            this.stableVersion = stableVersion;
            return this;
        }

        public Builder candidateVersion(String candidateVersion) {
            this.candidateVersion = candidateVersion;
            return this;
        }

        public Builder candidateWeightBps(Integer candidateWeightBps) {
            this.candidateWeightBps = candidateWeightBps;
            return this;
        }

        public Builder targeting(AliasTargeting targeting) {
            this.targeting = targeting;
            return this;
        }

        public Builder revision(long revision) {
            this.revision = revision;
            return this;
        }

        public Builder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public Builder updatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ProcessAliasRecord build() {
            return new ProcessAliasRecord(this);
        }
    }
}
