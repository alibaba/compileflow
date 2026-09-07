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
package com.alibaba.compileflow.engine.core.routing;

import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingContext;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies optional named targeting before the protocol-defined Alias percentage split.
 *
 * @author yusu
 */
public final class AliasTargetSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger(AliasTargetSelector.class);
    private final Map<String, ProcessAliasTargetingPolicy> policies;

    public AliasTargetSelector(Map<String, ProcessAliasTargetingPolicy> policies) {
        this.policies = validatePolicies(policies);
    }

    /**
     * Selects one stable or candidate target authorized by the route.
     *
     * @param route authoritative route
     * @param options request-scoped targeting and cohort inputs
     * @return selected target and bounded routing attribution
     */
    public Selection select(ProcessAliasRoute route, AliasRoutingOptions options) {
        ProcessAliasRoute authoritative = Objects.requireNonNull(route, "route");
        AliasRoutingOptions routing = Objects.requireNonNull(options, "options");
        if (!authoritative.hasCandidate()) {
            return new Selection(ProcessAliasTarget.STABLE, Reason.STABLE_ONLY, null);
        }
        AliasTargeting targeting = authoritative.targeting();
        if (targeting != null) {
            ProcessAliasTargetingPolicy policy = policies.get(targeting.policy());
            if (policy == null) {
                throw new IllegalStateException("Alias targeting policy is unavailable: " + targeting.policy());
            }
            try {
                Optional<ProcessAliasTarget> override = Objects.requireNonNull(policy.target(ProcessAliasTargetingContext.of(authoritative,
                                routing)), "ProcessAliasTargetingPolicy must return Optional");
                if (override.isPresent()) {
                    return new Selection(override.orElseThrow(), Reason.TARGETING, targeting.policy());
                }
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.error("Alias targeting failed; selecting stable: ns={} code={} alias={} revision={} policy={} "
                        + "failureType={}", authoritative.alias().namespace(), authoritative.alias().code(),
                        authoritative.alias().alias(), authoritative.revision(), targeting.policy(),
                        failure.getClass().getName());
                return new Selection(ProcessAliasTarget.STABLE, Reason.TARGETING_ERROR, targeting.policy());
            }
        }
        return new Selection(DeterministicAliasSelector.select(authoritative, routing.routingKey()), Reason.SPLIT,
                targeting == null ? null : targeting.policy());
    }

    private static Map<String, ProcessAliasTargetingPolicy> validatePolicies(
            Map<String, ProcessAliasTargetingPolicy> source) {
        Map<String, ProcessAliasTargetingPolicy> policies = Map.copyOf(Objects.requireNonNull(source, "policies"));
        policies.forEach((name, policy) -> {
            String canonical = ProcessAliasTargetingPolicy.requireCanonicalName(name);
            ProcessAliasTargetingPolicy targetingPolicy = Objects.requireNonNull(policy, "targeting policy");
            String declared = ProcessAliasTargetingPolicy.requireCanonicalName(targetingPolicy.name());
            if (!canonical.equals(declared)) {
                throw new IllegalArgumentException(
                        "Alias targeting policy key must match its declared name: " + canonical);
            }
        });
        return policies;
    }

    /**
     * Canonical reason for one Alias target selection.
     */
    public enum Reason {
        STABLE_ONLY,
        TARGETING,
        SPLIT,
        TARGETING_ERROR
    }

    /**
     * Bounded selection attribution. Request attributes and policy parameters are deliberately excluded.
     *
     * @param target selected stable or candidate target
     * @param reason canonical selection reason
     * @param targetingPolicy configured policy name, or {@code null}
     */
    public record Selection(ProcessAliasTarget target, Reason reason, String targetingPolicy) {
        public Selection {
            target = Objects.requireNonNull(target, "target");
            reason = Objects.requireNonNull(reason, "reason");
            if ((reason == Reason.TARGETING || reason == Reason.TARGETING_ERROR) && targetingPolicy == null) {
                throw new IllegalArgumentException("targetingPolicy is required for targeting selection reasons");
            }
        }
    }
}
