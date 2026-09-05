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
package com.alibaba.compileflow.durable.spi.admission;

import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.util.Objects;

/**
 * One committed Deploy Alias observation used only for new-Run admission.
 *
 * @param alias observed alias identity
 * @param stableVersion stable exact version
 * @param candidateVersion optional candidate exact version
 * @param candidateWeightBps candidate admission weight in basis points
 * @param targeting optional named targeting policy configuration
 * @param revision observed alias revision
 *
 * @author yusu
 */
public record DurableAliasState(ProcessRef.Alias alias, ProcessRef.Version stableVersion,
        ProcessRef.Version candidateVersion, int candidateWeightBps, AliasTargeting targeting, long revision) {
    public DurableAliasState(ProcessRef.Alias alias, ProcessRef.Version stableVersion,
            ProcessRef.Version candidateVersion, int candidateWeightBps, long revision) {
        this(alias, stableVersion, candidateVersion, candidateWeightBps, null, revision);
    }

    public DurableAliasState {
        alias = Objects.requireNonNull(alias, "alias");
        stableVersion = requireSameProcess(alias, stableVersion, "stableVersion");
        candidateVersion = candidateVersion == null
                ? null
                : requireSameProcess(alias, candidateVersion, "candidateVersion");
        revision = DurableNumbers.requirePositive(revision, "revision");
        if (candidateVersion == null && candidateWeightBps != 0) {
            throw new IllegalArgumentException("candidateWeightBps must be zero without a candidate");
        }
        if (candidateVersion == null && targeting != null) {
            throw new IllegalArgumentException("targeting must be absent without a candidate");
        }
        if (candidateVersion != null
                && (candidateWeightBps <= 0 || candidateWeightBps >= 10_000 || stableVersion.equals(candidateVersion))) {
            throw new IllegalArgumentException("Invalid candidate Alias state");
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
}
