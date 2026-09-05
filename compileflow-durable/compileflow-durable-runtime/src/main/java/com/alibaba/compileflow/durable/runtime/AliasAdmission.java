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
package com.alibaba.compileflow.durable.runtime;

import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasState;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasStateSource;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.AliasTargetSelector;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import java.util.Map;
import java.util.Objects;

/**
 * Durable Alias admission over one externally supplied committed-state source.
 *
 * @author yusu
 */
final class AliasAdmission {
    private final DurableAliasStateSource source;
    private final AliasTargetSelector targetSelector;

    AliasAdmission(DurableAliasStateSource source) {
        this(source, Map.of());
    }

    AliasAdmission(DurableAliasStateSource source, Map<String, ProcessAliasTargetingPolicy> targetingPolicies) {
        this.source = Objects.requireNonNull(source, "source");
        this.targetSelector = new AliasTargetSelector(targetingPolicies);
    }

    Selection admit(ProcessRef.Alias alias, AliasRoutingOptions routing) {
        ProcessRef.Alias requested = Objects.requireNonNull(alias, "alias");
        AliasRoutingOptions options = Objects.requireNonNull(routing, "routing");
        DurableAliasState state = find(requested);
        ProcessAliasRoute route = new ProcessAliasRoute(requested, state.stableVersion(), state.candidateVersion(),
                state.candidateWeightBps(), state.targeting(), state.revision());
        AliasTargetSelector.Selection targetSelection;
        try {
            targetSelection = targetSelector.select(route, options);
        } catch (RuntimeException failure) {
            throw DurableProcessException.of(DurableErrorCode.VERSION_NOT_FOUND, "Alias target selection failed",
                    failure);
        }
        ProcessAliasTarget target = targetSelection.target();
        ProcessRef.Version version = switch (target) {
            case STABLE -> state.stableVersion();
            case CANDIDATE -> {
                if (state.candidateVersion() == null) {
                    throw new IllegalStateException("Alias selection returned an unavailable candidate");
                }
                yield state.candidateVersion();
            }
        };
        return new Selection(version, state.revision(), target, targetSelection.reason(),
                targetSelection.targetingPolicy());
    }

    private DurableAliasState find(ProcessRef.Alias alias) {
        DurableAliasState state = Objects
            .requireNonNull(source.find(alias), "Alias state source returned null")
            .orElseThrow(() -> DurableProcessException.of(DurableErrorCode.VERSION_NOT_FOUND,
                    "Committed Process Alias does not exist"));
        if (!alias.equals(state.alias())) {
            throw new IllegalStateException("Alias state source returned a different Alias");
        }
        return state;
    }

    record Selection(ProcessRef.Version version, long aliasRevision, ProcessAliasTarget target,
            AliasTargetSelector.Reason reason, String targetingPolicy) {
        Selection {
            version = Objects.requireNonNull(version, "version");
            aliasRevision = DurableNumbers.requirePositive(aliasRevision, "aliasRevision");
            target = Objects.requireNonNull(target, "target");
            reason = Objects.requireNonNull(reason, "reason");
        }
    }
}
