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
import com.alibaba.compileflow.deploy.runtime.DeployRuntime;
import com.alibaba.compileflow.deploy.runtime.DeployRuntimeSnapshot;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.LocalReadyRoutingStateSnapshot;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeOwnerIds;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstallerSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public class DeployRuntimeDiagnosticsController {
    private final ObjectProvider<DeployRuntime> deployRuntimeProvider;
    private final ObjectProvider<LocalRoutingReconciler> localReadyProvider;
    private final ObjectProvider<RuntimeInstaller> installerProvider;
    private final Clock clock;

    @Autowired
    public DeployRuntimeDiagnosticsController(ObjectProvider<DeployRuntime> deployRuntimeProvider,
            ObjectProvider<LocalRoutingReconciler> localReadyProvider,
            ObjectProvider<RuntimeInstaller> installerProvider) {
        this(deployRuntimeProvider, localReadyProvider, installerProvider, Clock.systemUTC());
    }

    DeployRuntimeDiagnosticsController(ObjectProvider<DeployRuntime> deployRuntimeProvider,
            ObjectProvider<LocalRoutingReconciler> localReadyProvider,
            ObjectProvider<RuntimeInstaller> installerProvider, Clock clock) {
        this.deployRuntimeProvider = deployRuntimeProvider;
        this.localReadyProvider = localReadyProvider;
        this.installerProvider = installerProvider;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static DeployRuntimeDiagnosticsResponse unavailable(String timestamp) {
        return new DeployRuntimeDiagnosticsResponse(timestamp, false, false,
                "Deployment runtime diagnostics are not available.", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static List<DeployRuntimeAliasResponse> toAliasResponses(
            List<LocalReadyRoutingStateSnapshot.AliasState> aliases, Map<String, String> convergenceFailures) {
        return aliases.stream().map(alias -> {
            String failureReason =
                    convergenceFailures.get(RuntimeOwnerIds.alias(alias.getNamespace(), alias.getCode(),
                            alias.getAlias()));
            return new DeployRuntimeAliasResponse(alias.getNamespace(), alias.getCode(), alias.getAlias(),
                    alias.getDesiredRevision(), alias.isDesiredDeleted(), alias.getLocalReadyRevision(),
                    alias.isLocalReadyDeleted(), alias.getConvergenceState().name().toLowerCase(Locale.ROOT),
                    failureReason);
        }).toList();
    }

    private static List<DeployRuntimeVersionResponse> toVersionResponses(List<ProcessRef.Version> keys) {
        return keys.stream().map(DeployRuntimeDiagnosticsController::toVersionResponse).toList();
    }

    private static DeployRuntimeVersionResponse toVersionResponse(ProcessRef.Version key) {
        return new DeployRuntimeVersionResponse(key.namespace(), key.code(), key.version(), versionId(key));
    }

    private static List<DeployRuntimeBackedOffVersionResponse> toBackoffResponses(
            List<RuntimeInstallerSnapshot.BackedOffVersion> backedOffVersions) {
        return backedOffVersions
            .stream()
            .map(entry -> new DeployRuntimeBackedOffVersionResponse(entry.getKey().namespace(), entry.getKey().code(),
                    entry.getKey().version(), versionId(entry.getKey()), entry.getReason(),
                    Instant.ofEpochMilli(entry.getBlockedUntil()).toString(), entry.getRemainingMs()))
            .toList();
    }

    private static List<DeployRuntimeDeployedProcessResponse> toDeployedVersionResponses(
            List<RuntimeInstallerSnapshot.InstalledProcessSnapshot> installedVersions) {
        return installedVersions
            .stream()
            .map(process -> new DeployRuntimeDeployedProcessResponse(process.namespace(), process.code(),
                    List.copyOf(process.versions())))
            .toList();
    }

    private static String versionId(ProcessRef.Version key) {
        return key.namespace() + "/" + key.code() + "@" + key.version();
    }

    @GetMapping
    public DeployRuntimeDiagnosticsResponse getDeployRuntimeDiagnostics() {
        String timestamp = clock.instant().toString();
        DeployRuntime runtime = deployRuntimeProvider.getIfAvailable();
        if (runtime != null) {
            return diagnostics(timestamp, "distributed", runtime.snapshot());
        }
        LocalRoutingReconciler localReady = localReadyProvider.getIfAvailable();
        RuntimeInstaller installer = installerProvider.getIfAvailable();
        if (localReady == null || installer == null) {
            return unavailable(timestamp);
        }
        return diagnostics(timestamp, "embedded",
                new DeployRuntimeSnapshot(true, localReady.snapshot(), installer.snapshot()));
    }

    private DeployRuntimeDiagnosticsResponse diagnostics(String timestamp, String topology,
            DeployRuntimeSnapshot snapshot) {
        LocalReadyRoutingStateSnapshot routing = snapshot.getRouting();
        RuntimeInstallerSnapshot installer = snapshot.getInstaller();
        return new DeployRuntimeDiagnosticsResponse(timestamp, true, snapshot.isStarted(), null, topology,
                routing.getDesiredAliasCount(), routing.getLocalReadyAliasCount(), routing.getPendingAliasCount(),
                routing.getFailedAliasCount(),
                toAliasResponses(routing.getAliases(), installer.getConvergenceFailures()), installer.getInflightCount(),
                installer.getInflightCapacity(), installer.getInflightAvailablePermits(),
                installer.getFailureBackoffMs(), installer.getRetainedRuntimeCount(),
                toVersionResponses(installer.getInflightVersions()), toVersionResponses(installer.getDemandedVersions()),
                toVersionResponses(installer.getPendingReleaseVersions()),
                toBackoffResponses(installer.getBackedOffVersions()),
                toDeployedVersionResponses(installer.getInstalledVersions()));
    }
}
