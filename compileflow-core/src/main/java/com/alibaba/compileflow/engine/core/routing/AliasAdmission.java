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
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admits an Alias from its configured serving-route authority.
 *
 * <p>Applications configure the serving authority through {@link ProcessAliasRouteSource}. Target
 * selection follows the canonical weighted routing protocol so every node interprets one Alias
 * revision identically.
 *
 * @author yusu
 */
public final class AliasAdmission {
    private static final Logger LOGGER = LoggerFactory.getLogger(AliasAdmission.class);
    private final ProcessAliasRouteSource routeSource;
    private final AliasTargetSelector targetSelector;

    /**
     * Creates an admission boundary from one route authority.
     *
     * @param routeSource single serving-route authority
     */
    public AliasAdmission(ProcessAliasRouteSource routeSource) {
        this(routeSource, Map.of());
    }

    /**
     * Creates an admission boundary from one route authority and named targeting policies.
     *
     * @param routeSource single serving-route authority
     * @param targetingPolicies policies keyed by their stable semantic names
     */
    public AliasAdmission(ProcessAliasRouteSource routeSource,
            Map<String, ProcessAliasTargetingPolicy> targetingPolicies) {
        this.routeSource = Objects.requireNonNull(routeSource, "routeSource");
        this.targetSelector = new AliasTargetSelector(targetingPolicies);
    }

    /**
     * Creates the default admission boundary over node-local-ready route state.
     *
     * @param state local-ready route state
     * @return admission boundary using deterministic target selection
     */
    public static AliasAdmission forLocalState(LocalAliasRouteState state) {
        return new AliasAdmission(LocalReadyAliasRouteSource.from(state));
    }

    /**
     * Creates the default admission boundary with on-demand convergence for unseen Aliases.
     *
     * @param state local-ready route state
     * @param converger convergence hook for an unseen Alias
     * @return admission boundary using deterministic target selection
     */
    public static AliasAdmission forLocalState(LocalAliasRouteState state, AliasRouteConverger converger) {
        return new AliasAdmission(new LocalReadyAliasRouteSource(state, converger));
    }

    /**
     * Admits one Alias request to an authorized immutable version.
     *
     * @param alias             requested Alias
     * @param options           immutable Alias routing inputs
     * @return resolved version with authoritative Alias attribution
     */
    public AliasSelection admit(ProcessRef.Alias alias, AliasRoutingOptions options) {
        ProcessRef.Alias request = Objects.requireNonNull(alias, "alias");
        AliasRoutingOptions routing = Objects.requireNonNull(options, "options");
        ProcessAliasRoute route =
                findRoute(request).orElseThrow(() -> missingRoute(request, "No serving version route is configured"));

        AliasTargetSelector.Selection targetSelection;
        try {
            targetSelection = targetSelector.select(route, routing);
        } catch (RuntimeException failure) {
            throw new CompileFlowException(ErrorCode.CF_EXEC_011,
                    "Alias target selection failed: ns=" + request.namespace() + " code=" + request.code() + " alias=" + request.alias(),
                    failure);
        }
        ProcessAliasTarget target = targetSelection.target();
        ProcessRef.Version selectedVersion;
        try {
            selectedVersion = route.versionFor(target);
        } catch (IllegalStateException invalidTarget) {
            throw new CompileFlowException(ErrorCode.CF_EXEC_011,
                    "Alias route does not authorize the selected target: ns=" + request.namespace() + " code="
                    + request.code() + " alias=" + request.alias() + " target=" + target, invalidTarget);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("alias-routing-selected ns={} code={} alias={} revision={} target={} selectedVersion={} "
                    + "reason={} targetingPolicy={}", request.namespace(), request.code(), request.alias(),
                    route.revision(), target, selectedVersion.version(), targetSelection.reason(),
                    targetSelection.targetingPolicy());
        }
        return new AliasSelection(selectedVersion, target, route.revision());
    }

    /**
     * Returns whether a previously admitted selection is no longer present in the current route.
     *
     * <p>Runtime handoff uses this only after exact acquisition misses, avoiding an unconditional
     * second authority read on the normal path.
     */
    public boolean routeChangedSince(ProcessRef.Alias alias, AliasSelection selection) {
        ProcessRef.Alias requested = Objects.requireNonNull(alias, "alias");
        AliasSelection admitted = Objects.requireNonNull(selection, "selection");
        return findRoute(requested)
            .map(current -> differsFrom(current, admitted))
            .orElse(true);
    }

    private static boolean differsFrom(ProcessAliasRoute current, AliasSelection admitted) {
        if (current.revision() != admitted.aliasRevision()) {
            return true;
        }
        try {
            return !current.versionFor(admitted.target()).equals(admitted.version());
        } catch (IllegalStateException unavailableTarget) {
            return true;
        }
    }

    private Optional<ProcessAliasRoute> findRoute(ProcessRef.Alias alias) {
        Optional<ProcessAliasRoute> result =
                Objects.requireNonNull(routeSource.find(alias), "ProcessAliasRouteSource must return Optional");
        result.ifPresent(route -> {
            if (!alias.equals(route.alias())) {
                throw new IllegalStateException("ProcessAliasRouteSource returned a different Alias");
            }
        });
        return result;
    }

    private static CompileFlowException missingRoute(ProcessRef.Alias alias, String message) {
        return new CompileFlowException(ErrorCode.CF_EXEC_011,
                message + ": ns=" + alias.namespace() + " code=" + alias.code() + " alias=" + alias.alias());
    }
}
