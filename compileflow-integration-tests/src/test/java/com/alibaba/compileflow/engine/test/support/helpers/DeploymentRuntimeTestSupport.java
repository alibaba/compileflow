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
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntime;
import com.alibaba.compileflow.deploy.runtime.version.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ArtifactSourceResolver;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.routing.ProjectionStoreRoutingStateSubscriber;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Test-only factory for assembling deployment runtimes.
 *
 * @author yusu
 */
public final class DeploymentRuntimeTestSupport {
    private DeploymentRuntimeTestSupport() {
    }

    /**
     * Creates a projection-store-backed deployment runtime for integration tests.
     *
     * @param projectionStore         in-memory or test projection store
     * @param routingStateKeys        routing state keys watched by the runtime
     * @param engine                  process engine that owns deployed runtime leases
     * @param artifactSource          source that resolves published flow artifacts
     * @param operationTimeout deadline for each sync-projection store operation
     * @param executor                executor that runs asynchronous installation work
     * @param localRoutingState     shared routing and deployment snapshots
     * @return assembled deploy runtime
     */
    public static DeploymentRuntime sourceRuntime(DeploymentProjectionStore projectionStore,
            List<String> routingStateKeys, ProcessEngine engine, ProcessArtifactSource artifactSource,
            Duration operationTimeout, Executor executor, LocalRoutingState localRoutingState) {
        return new DeploymentRuntime(new ProjectionStoreRoutingStateSubscriber(projectionStore, routingStateKeys,
                        operationTimeout),
                VersionRuntimeManager.withOwnedRetryScheduler(new ArtifactSourceResolver(artifactSource),
                        new ProcessArtifactRuntimeLoader(engine), executor, localRoutingState, Duration.ofMinutes(5),
                        1_000), localRoutingState, Set.of());
    }
}
