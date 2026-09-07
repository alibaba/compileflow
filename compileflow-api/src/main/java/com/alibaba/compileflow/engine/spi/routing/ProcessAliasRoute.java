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
package com.alibaba.compileflow.engine.spi.routing;

import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Objects;

/**
 * Immutable serving route for one published Alias.
 *
 * <p>This is the minimal data-plane fact required for execution. Control-plane audit fields such
 * as actor and update time deliberately remain outside this value.
 *
 * @param alias Alias governed by this route
 * @param stableVersion exact stable version
 * @param candidateVersion exact candidate version, or {@code null}
 * @param candidateWeightBps candidate traffic weight in basis points
 * @param targeting optional named targeting policy configuration
 * @param revision positive authoritative revision
 * @author yusu
 */
public record ProcessAliasRoute(ProcessRef.Alias alias, ProcessRef.Version stableVersion,
        ProcessRef.Version candidateVersion, int candidateWeightBps, AliasTargeting targeting, long revision) {
    /**
     * Creates a stable-only route.
     *
     * @param alias requested published Alias
     * @param version exact stable version identifier
     * @param revision positive route revision
     * @return immutable stable-only route
     */
    public static ProcessAliasRoute stable(ProcessRef.Alias alias, String version, long revision) {
        ProcessRef.Alias ref = Objects.requireNonNull(alias, "alias");
        return new ProcessAliasRoute(ref, ProcessRef.version(ref.namespace(), ref.code(), version), null, 0, null,
                revision);
    }

    /**
     * Creates a stable/candidate route.
     *
     * @param alias requested published Alias
     * @param stableVersion exact stable version identifier
     * @param candidateVersion exact candidate version identifier
     * @param candidateWeightBps candidate traffic weight in basis points
     * @param revision positive route revision
     * @return immutable canary route
     */
    public static ProcessAliasRoute canary(ProcessRef.Alias alias, String stableVersion, String candidateVersion,
            int candidateWeightBps, long revision) {
        return canary(alias, stableVersion, candidateVersion, candidateWeightBps, null, revision);
    }

    /**
     * Creates a stable/candidate route with optional enterprise targeting.
     *
     * @param alias requested published Alias
     * @param stableVersion exact stable version identifier
     * @param candidateVersion exact candidate version identifier
     * @param candidateWeightBps candidate traffic weight in basis points
     * @param targeting optional named targeting policy configuration
     * @param revision positive route revision
     * @return immutable canary route
     */
    public static ProcessAliasRoute canary(ProcessRef.Alias alias, String stableVersion, String candidateVersion,
            int candidateWeightBps, AliasTargeting targeting, long revision) {
        ProcessRef.Alias ref = Objects.requireNonNull(alias, "alias");
        return new ProcessAliasRoute(ref, ProcessRef.version(ref.namespace(), ref.code(), stableVersion),
                ProcessRef.version(ref.namespace(), ref.code(), candidateVersion), candidateWeightBps, targeting,
                revision);
    }

    public ProcessAliasRoute {
        alias = Objects.requireNonNull(alias, "alias");
        stableVersion = requireSameProcess(alias, stableVersion, "stableVersion");
        candidateVersion = candidateVersion == null
                ? null
                : requireSameProcess(alias, candidateVersion, "candidateVersion");
        if (revision <= 0L) {
            throw new IllegalArgumentException("revision must be greater than 0");
        }
        if (candidateVersion == null) {
            if (candidateWeightBps != 0) {
                throw new IllegalArgumentException("candidateWeightBps must be 0 without a candidateVersion");
            }
            if (targeting != null) {
                throw new IllegalArgumentException("targeting must be absent without a candidateVersion");
            }
        } else {
            if (stableVersion.equals(candidateVersion)) {
                throw new IllegalArgumentException("stableVersion and candidateVersion must differ");
            }
            if (candidateWeightBps <= 0 || candidateWeightBps >= 10_000) {
                throw new IllegalArgumentException("candidateWeightBps must be between 1 and 9999");
            }
        }
    }

    private static ProcessRef.Version requireSameProcess(ProcessRef.Alias alias, ProcessRef.Version version,
            String name) {
        ProcessRef.Version value = Objects.requireNonNull(version, name);
        if (!alias.namespace().equals(value.namespace()) || !alias.code().equals(value.code())) {
            throw new IllegalArgumentException(name + " must identify the same namespace and process code as alias");
        }
        return value;
    }

    /**
     * Returns whether this route has an active candidate.
     *
     * @return {@code true} when candidate routing is active
     */
    public boolean hasCandidate() {
        return candidateVersion != null;
    }

    /**
     * Returns the exact version authorized for the selected target.
     *
     * @param target stable or candidate target
     * @return exact authorized version
     */
    public ProcessRef.Version versionFor(ProcessAliasTarget target) {
        ProcessAliasTarget selected = Objects.requireNonNull(target, "target");
        if (selected == ProcessAliasTarget.STABLE) {
            return stableVersion;
        }
        if (candidateVersion == null) {
            throw new IllegalStateException("Alias selector chose CANDIDATE without an active candidate");
        }
        return candidateVersion;
    }
}
