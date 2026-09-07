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
package com.alibaba.compileflow.deploy.protocol;

import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.util.Objects;

/**
 * Immutable, validated projection of one parsed alias-routing update.
 *
 * <p>A tombstone has {@link #isDeleted()} set, no stable or candidate version, and a zero
 * candidate weight. A live state always has a stable version.
 *
 * @author yusu
 */
public final class RoutingStateUpdate {
    private final String namespace;
    private final String code;
    private final String alias;
    private final String stableVersion;
    private final String candidateVersion;
    private final int candidateWeightBps;
    private final AliasTargeting targeting;
    private final boolean deleted;
    private final long aliasRevision;
    private final String actor;
    private final long updatedAt;

    RoutingStateUpdate(String namespace, String code, String alias, String stableVersion, String candidateVersion,
            int candidateWeightBps, AliasTargeting targeting, boolean deleted, long aliasRevision, String actor,
            long updatedAt) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.code = Objects.requireNonNull(code, "code");
        this.alias = Objects.requireNonNull(alias, "alias");
        this.stableVersion = stableVersion;
        this.candidateVersion = candidateVersion;
        this.candidateWeightBps = candidateWeightBps;
        this.targeting = targeting;
        this.deleted = deleted;
        this.aliasRevision = aliasRevision;
        this.actor = Objects.requireNonNull(actor, "actor");
        this.updatedAt = updatedAt;
    }

    /**
     * Returns the process namespace.
     *
     * @return canonical namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the process code.
     *
     * @return canonical process code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the published Alias.
     *
     * @return canonical alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Returns the stable immutable version.
     *
     * @return stable version, or {@code null} for a tombstone
     */
    public String getStableVersion() {
        return stableVersion;
    }

    /**
     * Returns the candidate immutable version.
     *
     * @return candidate version, or {@code null} when no canary is active
     */
    public String getCandidateVersion() {
        return candidateVersion;
    }

    /**
     * Returns the candidate traffic weight.
     *
     * @return basis points in {@code 1..9999}, or zero when no candidate is present
     */
    public int getCandidateWeightBps() {
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
     * Reports whether this update removes the published Alias.
     *
     * @return {@code true} for a tombstone
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Returns the monotonic Alias revision.
     *
     * @return positive revision
     */
    public long getAliasRevision() {
        return aliasRevision;
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
     * Returns the authority timestamp.
     *
     * @return positive epoch milliseconds
     */
    public long getUpdatedAt() {
        return updatedAt;
    }
}
