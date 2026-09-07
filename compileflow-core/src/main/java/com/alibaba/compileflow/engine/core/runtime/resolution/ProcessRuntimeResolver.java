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
package com.alibaba.compileflow.engine.core.runtime.resolution;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.cache.RuntimeCacheKeys;
import com.alibaba.compileflow.engine.core.runtime.loading.ProcessRuntimeLoader;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.InstalledVersionState;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import java.util.Objects;

/**
 * Resolves one internal request to an exact engine-local runtime.
 *
 * @author yusu
 */
public final class ProcessRuntimeResolver {
    private static final int ALIAS_RESOLUTION_RETRIES = 1;
    private final ProcessRuntimeCache cache;
    private final ProcessRuntimeLoader runtimeLoader;
    private final LocalRoutingState localRoutingState;
    private final AliasAdmission aliasAdmission;

    public ProcessRuntimeResolver(ProcessRuntimeCache cache, ProcessRuntimeLoader runtimeLoader) {
        this(cache, runtimeLoader, new LocalRoutingState());
    }

    public ProcessRuntimeResolver(ProcessRuntimeCache cache, ProcessRuntimeLoader runtimeLoader,
            LocalRoutingState localRoutingState) {
        this(cache, runtimeLoader, localRoutingState,
                AliasAdmission.forLocalState(Objects
                    .requireNonNull(localRoutingState, "localRoutingState")
                    .getAliasRouteState()));
    }

    public ProcessRuntimeResolver(ProcessRuntimeCache cache, ProcessRuntimeLoader runtimeLoader,
            LocalRoutingState localRoutingState, AliasAdmission aliasAdmission) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.runtimeLoader = Objects.requireNonNull(runtimeLoader, "runtimeLoader");
        this.localRoutingState = Objects.requireNonNull(localRoutingState, "localRoutingState");
        this.aliasAdmission = Objects.requireNonNull(aliasAdmission, "aliasAdmission");
    }

    private static CompileFlowException runtimeNotReady(ProcessRuntimeRequest request) {
        return new CompileFlowException(ErrorCode.CF_EXEC_012,
                "Requested process version is not ready on this node: ns=" + request.getNamespace() + " code="
                + request.getCode() + " version=" + request.getVersion());
    }

    private static CompileFlowException versionNotInstalled(ProcessRuntimeRequest request, String version) {
        return new CompileFlowException(ErrorCode.CF_EXEC_012,
                "Version " + version + " is not installed on this node: ns=" + request.getNamespace() + " code=" + request.getCode());
    }

    /**
     * Resolves the runtime together with the exact routing decision admitted for execution.
     */
    public ProcessRuntimeResolution resolve(ClassLoader classLoader, ProcessRuntimeRequest request) {
        ProcessRuntimeRequest requested = Objects.requireNonNull(request, "request");
        int routeHandoffRetriesRemaining = ALIAS_RESOLUTION_RETRIES;
        while (true) {
            VersionResolution resolution = resolveEffectiveVersion(requested);
            ProcessRuntimeRequest effective =
                    resolution.version() == null ? requested : requested.withVersion(resolution.version());
            String bindingKey =
                    RuntimeCacheKeys.forProcess(effective.getNamespace(), effective.getCode(), effective.getVersion());
            ProcessRuntimeEntry current = cache.getIfPresent(bindingKey);
            if (current != null && !effective.hasDefinition()) {
                markInstalled(effective);
                return new ProcessRuntimeResolution(current, resolution.version(), resolution.aliasSelection());
            }
            if (!effective.hasDefinition()) {
                if (routeHandoffRetriesRemaining > 0 && routeChangedSinceResolution(requested, resolution)) {
                    routeHandoffRetriesRemaining--;
                    continue;
                }
                throw runtimeNotReady(effective);
            }
            // Explicit definitions cross the exact-resolution boundary before cache matching so
            // the runtime identity is always based on one immutable byte snapshot.
            ProcessRuntimeEntry entry = effective.getVersion() == null
                    ? runtimeLoader.loadExactSync(effective, classLoader)
                    : runtimeLoader.loadSync(effective, classLoader);
            markInstalled(effective);
            return new ProcessRuntimeResolution(entry, resolution.version(), resolution.aliasSelection());
        }
    }

    private VersionResolution resolveEffectiveVersion(ProcessRuntimeRequest request) {
        if (request.getVersion() != null) {
            if (request.getAliasSelection() != null) {
                return preselectedAliasSelection(request, request.getAliasSelection());
            }
            validateExplicitVersion(request, request.getVersion());
            return VersionResolution.explicit(request.getVersion());
        }
        if (request.getAlias() == null) {
            return VersionResolution.unversioned();
        }

        ProcessRef.Alias alias = ProcessRef.alias(request.getNamespace(), request.getCode(), request.getAlias());
        AliasSelection resolved = aliasAdmission.admit(alias, request.getAliasRouting());
        validateExplicitVersion(request, resolved.version().version());
        return VersionResolution.selected(resolved);
    }

    private VersionResolution preselectedAliasSelection(ProcessRuntimeRequest request, AliasSelection resolved) {
        validateExplicitVersion(request, resolved.version().version());
        return VersionResolution.selected(resolved);
    }

    private void validateExplicitVersion(ProcessRuntimeRequest request, String version) {
        InstalledVersionState installed = localRoutingState.getInstalledVersionState();
        if (installed.contains(request.getNamespace(), request.getCode(), version)) {
            return;
        }
        if (!installed.isLocalInstallationRequired() && !installed.hasAny(request.getNamespace(), request.getCode())
                && request.getDefinition() != null) {
            return;
        }
        throw versionNotInstalled(request, version);
    }

    private void markInstalled(ProcessRuntimeRequest request) {
        if (request.getVersion() == null) {
            return;
        }
        localRoutingState
            .getInstalledVersionState()
            .markInstalled(request.getNamespace(), request.getCode(), request.getVersion());
    }

    private boolean routeChangedSinceResolution(ProcessRuntimeRequest request, VersionResolution resolution) {
        if (request.getAliasSelection() != null || resolution.aliasSelection() == null) {
            return false;
        }
        ProcessRef.Alias alias = ProcessRef.alias(request.getNamespace(), request.getCode(), request.getAlias());
        return aliasAdmission.routeChangedSince(alias, resolution.aliasSelection());
    }

    private record VersionResolution(String version, AliasSelection aliasSelection) {
        private static VersionResolution explicit(String version) {
            return new VersionResolution(version, null);
        }

        private static VersionResolution unversioned() {
            return new VersionResolution(null, null);
        }

        private static VersionResolution selected(AliasSelection selection) {
            return new VersionResolution(selection.version().version(), selection);
        }
    }
}
