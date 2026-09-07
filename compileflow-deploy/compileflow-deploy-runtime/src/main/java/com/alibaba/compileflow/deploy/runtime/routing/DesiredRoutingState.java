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
package com.alibaba.compileflow.deploy.runtime.routing;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.deploy.protocol.RoutingStateUpdate;
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import java.util.Objects;

/**
 * Complete desired state or deletion tombstone for one process alias.
 *
 * @author yusu
 */
public final class DesiredRoutingState {
    private final String namespace;
    private final String code;
    private final String alias;
    private final String stableVersion;
    private final String candidateVersion;
    private final int candidateWeightBps;
    private final AliasTargeting targeting;
    private final boolean deleted;
    private final long revision;
    private final String actor;
    private final long updatedAt;

    private DesiredRoutingState(Builder builder) {
        ProcessRef.Alias ref = ProcessRef.alias(builder.namespace, builder.code, builder.alias);
        this.namespace = ref.namespace();
        this.code = ref.code();
        this.alias = ref.alias();
        this.deleted = builder.deleted;
        this.revision = requirePositive(builder.revision, "revision");
        this.actor = DeploymentAudit.requireActor(builder.actor);
        this.updatedAt = requirePositive(builder.updatedAt, "updatedAt");
        if (deleted) {
            if (builder.stableVersion != null || builder.candidateVersion != null || builder.candidateWeightBps != 0
                    || builder.targeting != null) {
                throw new IllegalArgumentException("Deleted alias state must not contain route targets");
            }
            this.stableVersion = null;
            this.candidateVersion = null;
            this.candidateWeightBps = 0;
            this.targeting = null;
            return;
        }
        this.stableVersion = ProcessRef.version(namespace, code, builder.stableVersion).version();
        this.candidateVersion = builder.candidateVersion == null
                ? null
                : ProcessRef.version(namespace, code, builder.candidateVersion).version();
        this.candidateWeightBps = builder.candidateWeightBps;
        this.targeting = builder.targeting;
        validateCandidate();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Converts one validated routing protocol update into desired runtime state.
     *
     * @param update parsed routing protocol update
     * @return desired runtime state change
     */
    public static DesiredRoutingState from(RoutingStateUpdate update) {
        RoutingStateUpdate source = Objects.requireNonNull(update, "update");
        return builder()
            .namespace(source.getNamespace())
            .code(source.getCode())
            .alias(source.getAlias())
            .stableVersion(source.getStableVersion())
            .candidateVersion(source.getCandidateVersion())
            .candidateWeightBps(source.getCandidateWeightBps())
            .targeting(source.getTargeting())
            .deleted(source.isDeleted())
            .revision(source.getAliasRevision())
            .actor(source.getActor())
            .updatedAt(source.getUpdatedAt())
            .build();
    }

    /**
     * Converts one authoritative control-plane alias into desired runtime state.
     *
     * @param alias authoritative alias state
     * @return desired runtime state change
     */
    public static DesiredRoutingState from(ProcessAliasState alias) {
        ProcessAliasState source = Objects.requireNonNull(alias, "alias");
        ProcessRef.Alias ref = source.getRef();
        Integer weightBps = source.getCandidateWeightBps();
        return builder()
            .namespace(ref.namespace())
            .code(ref.code())
            .alias(ref.alias())
            .stableVersion(source.getStableVersion().version())
            .candidateVersion(source.getCandidateVersion() == null ? null : source.getCandidateVersion().version())
            .candidateWeightBps(weightBps == null ? 0 : weightBps.intValue())
            .targeting(source.getTargeting())
            .revision(source.getAliasRevision())
            .actor(source.getActor())
            .updatedAt(source.getUpdatedAt().toEpochMilli())
            .build();
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getCode() {
        return code;
    }

    public String getAlias() {
        return alias;
    }

    public String getStableVersion() {
        return stableVersion;
    }

    public String getCandidateVersion() {
        return candidateVersion;
    }

    public int getCandidateWeightBps() {
        return candidateWeightBps;
    }

    public AliasTargeting getTargeting() {
        return targeting;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public long getRevision() {
        return revision;
    }

    public String getActor() {
        return actor;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Converts a non-tombstone desired state into the core alias route.
     *
     * @return typed non-tombstone alias route
     */
    public ProcessAliasRoute toAliasRoute() {
        if (deleted) {
            throw new IllegalStateException("An alias tombstone has no route");
        }
        ProcessRef.Alias ref = ProcessRef.alias(namespace, code, alias);
        return new ProcessAliasRoute(ref, ProcessRef.version(namespace, code, stableVersion),
                candidateVersion == null ? null : ProcessRef.version(namespace, code, candidateVersion),
                candidateWeightBps, targeting, revision);
    }

    private void validateCandidate() {
        if (candidateVersion == null) {
            if (candidateWeightBps != 0) {
                throw new IllegalArgumentException("candidateWeightBps must be 0 without candidateVersion");
            }
            if (targeting != null) {
                throw new IllegalArgumentException("targeting must be absent without candidateVersion");
            }
            return;
        }
        if (stableVersion.equals(candidateVersion)) {
            throw new IllegalArgumentException("stableVersion and candidateVersion must differ");
        }
        if (candidateWeightBps <= 0 || candidateWeightBps >= 10_000) {
            throw new IllegalArgumentException("candidateWeightBps must be between 1 and 9999");
        }
    }

    public static final class Builder {
        private String namespace;
        private String code;
        private String alias;
        private String stableVersion;
        private String candidateVersion;
        private int candidateWeightBps;
        private AliasTargeting targeting;
        private boolean deleted;
        private long revision;
        private String actor;
        private long updatedAt;

        private Builder() {
        }

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

        public Builder candidateWeightBps(int candidateWeightBps) {
            this.candidateWeightBps = candidateWeightBps;
            return this;
        }

        public Builder targeting(AliasTargeting targeting) {
            this.targeting = targeting;
            return this;
        }

        public Builder deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public Builder revision(long revision) {
            this.revision = revision;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder updatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public DesiredRoutingState build() {
            return new DesiredRoutingState(this);
        }
    }
}
