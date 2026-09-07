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

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import tools.jackson.core.JacksonException;

/**
 * Serializes authoritative alias-routing updates to the deployment wire format.
 *
 * @author yusu
 */
public final class RoutingStateCodec {
    private RoutingStateCodec() {
    }

    /**
     * Serializes one live alias-routing state.
     *
     * @param namespace          process namespace
     * @param code               process code
     * @param alias              published Alias
     * @param stableVersion      immutable stable version
     * @param candidateVersion   immutable candidate version, or {@code null}
     * @param candidateWeightBps candidate traffic weight in basis points, or {@code null}
     * @param revision           positive monotonic Alias revision
     * @param actor              authenticated mutation actor
     * @param updatedAt          positive authority timestamp in epoch milliseconds
     * @return compact schema-versioned JSON payload
     */
    public static String aliasStateJson(String namespace, String code, String alias, String stableVersion,
            String candidateVersion, Integer candidateWeightBps, long revision, String actor, long updatedAt) {
        return aliasStateJson(namespace, code, alias, stableVersion, candidateVersion, candidateWeightBps, null,
                revision, actor, updatedAt);
    }

    /**
     * Serializes one live alias-routing state with optional named targeting.
     *
     * @param namespace          process namespace
     * @param code               process code
     * @param alias              published Alias
     * @param stableVersion      immutable stable version
     * @param candidateVersion   immutable candidate version, or {@code null}
     * @param candidateWeightBps candidate traffic weight in basis points, or {@code null}
     * @param targeting          optional named targeting policy configuration
     * @param revision           positive monotonic Alias revision
     * @param actor              authenticated mutation actor
     * @param updatedAt          positive authority timestamp in epoch milliseconds
     * @return compact schema-versioned JSON payload
     */
    public static String aliasStateJson(String namespace, String code, String alias, String stableVersion,
            String candidateVersion, Integer candidateWeightBps, AliasTargeting targeting, long revision, String actor,
            long updatedAt) {
        ProcessRef.Alias ref = ProcessRef.alias(namespace, code, alias);
        String stable = ProcessRef.version(ref.namespace(), ref.code(), stableVersion).version();
        String candidate = candidateVersion == null
                ? null
                : ProcessRef.version(ref.namespace(), ref.code(), candidateVersion).version();
        int weight = candidateWeightBps == null ? 0 : candidateWeightBps.intValue();
        validateCandidate(stable, candidate, weight, targeting);
        String effectiveActor = DeploymentAudit.requireActor(actor);
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be > 0");
        }
        if (updatedAt <= 0) {
            throw new IllegalArgumentException("updatedAt must be > 0");
        }
        AliasStatePayload payload = new AliasStatePayload(ref.namespace(), ref.code(), ref.alias(), stable, candidate,
                candidate == null ? null : Integer.valueOf(weight), targeting == null ? null : targeting.policy(),
                targeting == null ? null : targeting.parameters(), null, revision, effectiveActor, updatedAt);
        return writeJson(payload);
    }

    /**
     * Serializes a tombstone that removes one published Alias.
     *
     * @param namespace process namespace
     * @param code      process code
     * @param alias     published Alias
     * @param revision  positive monotonic Alias revision
     * @param actor     authenticated mutation actor
     * @param updatedAt positive authority timestamp in epoch milliseconds
     * @return compact schema-versioned JSON tombstone
     */
    public static String aliasTombstoneJson(String namespace, String code, String alias, long revision, String actor,
            long updatedAt) {
        ProcessRef.Alias ref = ProcessRef.alias(namespace, code, alias);
        String effectiveActor = DeploymentAudit.requireActor(actor);
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be > 0");
        }
        if (updatedAt <= 0) {
            throw new IllegalArgumentException("updatedAt must be > 0");
        }
        AliasStatePayload payload = new AliasStatePayload(ref.namespace(), ref.code(), ref.alias(), null, null, null,
                null, null, Boolean.TRUE, revision, effectiveActor, updatedAt);
        return writeJson(payload);
    }

    private static void validateCandidate(String stableVersion, String candidateVersion, int candidateWeightBps,
            AliasTargeting targeting) {
        if (candidateVersion == null) {
            if (candidateWeightBps != 0) {
                throw new IllegalArgumentException("candidateWeightBps must be absent without candidateVersion");
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

    private static String writeJson(Object payload) {
        try {
            return DeploymentProtocolJson.write(payload);
        } catch (JacksonException failure) {
            throw new IllegalStateException("Failed to serialize routing state payload", failure);
        }
    }

    /**
     * Parses and verifies one untrusted alias-routing payload.
     *
     * @param json routing-state JSON, or blank when no value exists
     * @return validated update, or {@code null} for blank input
     */
    public static RoutingStateUpdate parse(String json) {
        return RoutingStateParser.parse(json);
    }
}
