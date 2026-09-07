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
package com.alibaba.compileflow.workbench.server.monitoring;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntime;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntimeSnapshot;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.routing.RoutingConvergenceSnapshot;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManagerSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for local deployment runtime diagnostics.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/monitoring/deploy-runtime")
public class DeploymentRuntimeDiagnosticsController {
    private final ObjectProvider<DeploymentRuntime> deployRuntimeProvider;
    private final ObjectProvider<LocalRoutingReconciler> localReadyProvider;
    private final ObjectProvider<VersionRuntimeManager> versionRuntimeManagerProvider;
    private final Clock clock;

    @Autowired
    public DeploymentRuntimeDiagnosticsController(ObjectProvider<DeploymentRuntime> deployRuntimeProvider,
            ObjectProvider<LocalRoutingReconciler> localReadyProvider,
            ObjectProvider<VersionRuntimeManager> versionRuntimeManagerProvider) {
        this(deployRuntimeProvider, localReadyProvider, versionRuntimeManagerProvider, Clock.systemUTC());
    }

    DeploymentRuntimeDiagnosticsController(ObjectProvider<DeploymentRuntime> deployRuntimeProvider,
            ObjectProvider<LocalRoutingReconciler> localReadyProvider,
            ObjectProvider<VersionRuntimeManager> versionRuntimeManagerProvider, Clock clock) {
        this.deployRuntimeProvider = deployRuntimeProvider;
        this.localReadyProvider = localReadyProvider;
        this.versionRuntimeManagerProvider = versionRuntimeManagerProvider;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static DeploymentRuntimeDiagnosticsResponse unavailable(String timestamp) {
        return new DeploymentRuntimeDiagnosticsResponse(timestamp, false, false,
                "Deployment runtime diagnostics are not available.", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static List<DeploymentRuntimeAliasResponse> toAliasResponses(
            List<RoutingConvergenceSnapshot.AliasState> aliases) {
        return aliases
            .stream()
            .map(alias -> new DeploymentRuntimeAliasResponse(alias.getNamespace(), alias.getCode(), alias.getAlias(),
                    alias.getDesiredRevision(), alias.isDesiredDeleted(), alias.getLocalReadyRevision(),
                    alias.isLocalReadyDeleted(), alias.getConvergenceState().name().toLowerCase(Locale.ROOT),
                    alias.getFailureReason()))
            .toList();
    }

    private static List<DeploymentRuntimeVersionResponse> toVersionResponses(List<ProcessRef.Version> keys) {
        return keys.stream().map(DeploymentRuntimeDiagnosticsController::toVersionResponse).toList();
    }

    private static DeploymentRuntimeVersionResponse toVersionResponse(ProcessRef.Version key) {
        return new DeploymentRuntimeVersionResponse(key.namespace(), key.code(), key.version(), versionId(key));
    }

    private static List<DeploymentRuntimeBackedOffVersionResponse> toBackoffResponses(
            List<VersionRuntimeManagerSnapshot.BackedOffVersion> backedOffVersions) {
        return backedOffVersions
            .stream()
            .map(entry -> new DeploymentRuntimeBackedOffVersionResponse(entry.getKey().namespace(),
                    entry.getKey().code(), entry.getKey().version(), versionId(entry.getKey()), entry.getReason(),
                    Instant.ofEpochMilli(entry.getBlockedUntil()).toString(), entry.getRemainingMs()))
            .toList();
    }

    private static List<DeploymentRuntimeDeployedProcessResponse> toDeployedVersionResponses(
            List<VersionRuntimeManagerSnapshot.InstalledProcessSnapshot> installedVersions) {
        return installedVersions
            .stream()
            .map(process -> new DeploymentRuntimeDeployedProcessResponse(process.namespace(), process.code(),
                    List.copyOf(process.versions())))
            .toList();
    }

    private static String versionId(ProcessRef.Version key) {
        return key.namespace() + "/" + key.code() + "@" + key.version();
    }

    @GetMapping
    public DeploymentRuntimeDiagnosticsResponse getDeploymentRuntimeDiagnostics() {
        String timestamp = clock.instant().toString();
        DeploymentRuntime runtime = deployRuntimeProvider.getIfAvailable();
        if (runtime != null) {
            return diagnostics(timestamp, "distributed", runtime.snapshot());
        }
        LocalRoutingReconciler localReady = localReadyProvider.getIfAvailable();
        VersionRuntimeManager versionRuntimeManager = versionRuntimeManagerProvider.getIfAvailable();
        if (localReady == null || versionRuntimeManager == null) {
            return unavailable(timestamp);
        }
        return diagnostics(timestamp, "embedded",
                new DeploymentRuntimeSnapshot(true, localReady.snapshot(), versionRuntimeManager.snapshot()));
    }

    private DeploymentRuntimeDiagnosticsResponse diagnostics(String timestamp, String topology,
            DeploymentRuntimeSnapshot snapshot) {
        RoutingConvergenceSnapshot routing = snapshot.getRouting();
        VersionRuntimeManagerSnapshot versionRuntime = snapshot.getVersionRuntime();
        return new DeploymentRuntimeDiagnosticsResponse(timestamp, true, snapshot.isStarted(), null, topology,
                routing.getDesiredAliasCount(), routing.getLocalReadyAliasCount(), routing.getPendingAliasCount(),
                routing.getFailedAliasCount(), toAliasResponses(routing.getAliases()), versionRuntime.getInflightCount(),
                versionRuntime.getInflightCapacity(), versionRuntime.getInflightAvailablePermits(),
                versionRuntime.getFailureBackoffMs(), versionRuntime.getRetainedRuntimeCount(),
                toVersionResponses(versionRuntime.getInflightVersions()),
                toVersionResponses(versionRuntime.getDemandedVersions()),
                toVersionResponses(versionRuntime.getPendingReleaseVersions()),
                toBackoffResponses(versionRuntime.getBackedOffVersions()),
                toDeployedVersionResponses(versionRuntime.getInstalledVersions()));
    }
}
