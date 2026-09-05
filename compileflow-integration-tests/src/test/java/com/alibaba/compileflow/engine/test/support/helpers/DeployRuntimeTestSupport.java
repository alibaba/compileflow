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
package com.alibaba.compileflow.engine.test.support.helpers;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import com.alibaba.compileflow.deploy.runtime.DeployRuntime;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.RepositoryProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.state.DeploymentSyncRoutingStateSubscriber;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Test-only factory for assembling deploy data-plane runtimes.
 *
 * @author yusu
 */
public final class DeployRuntimeTestSupport {
    private DeployRuntimeTestSupport() {
    }

    /**
     * Creates a DB-backed deploy runtime for integration tests.
     *
     * @param channel                 in-memory or test sync channel
     * @param routingStateKeys        routing state keys watched by the runtime
     * @param engine                  process engine that owns deployed runtime leases
     * @param modelType               model type accepted by the process engine
     * @param artifactSource          source that resolves published flow artifacts
     * @param channelOperationTimeout deadline for each sync-channel operation
     * @param executor                executor that runs asynchronous installation work
     * @param localRoutingState     shared routing and deployment snapshots
     * @return assembled deploy runtime
     */
    public static DeployRuntime dbRuntime(DeploymentSyncChannel channel, List<String> routingStateKeys,
            ProcessEngine engine, ProcessModelType modelType, ProcessArtifactSource artifactSource,
            Duration channelOperationTimeout, Executor executor, LocalRoutingState localRoutingState) {
        return new DeployRuntime(new DeploymentSyncRoutingStateSubscriber(channel, routingStateKeys,
                        channelOperationTimeout), new VersionDemandPlanner(),
                RuntimeInstaller.withOwnedRetryScheduler(new RepositoryProcessArtifactResolver(artifactSource),
                        new ProcessArtifactRuntimeLoader(engine, modelType), executor, localRoutingState,
                        Duration.ofMinutes(5), 1_000), localRoutingState, Set.of());
    }
}
