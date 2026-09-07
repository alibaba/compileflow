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
package com.alibaba.compileflow.deploy.runtime;

import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.routing.DesiredRoutingStateSubscriber;
import java.util.Objects;
import java.util.Set;

/**
 * Owns the deployment runtime lifecycle.
 *
 * @author yusu
 */
public final class DeploymentRuntime implements AutoCloseable {
    private final DesiredRoutingStateSubscriber stateSubscriber;
    private final VersionRuntimeManager versionRuntimeManager;
    private final LocalRoutingReconciler localRoutingReconciler;
    private boolean started;
    private boolean closed;

    /**
     * Creates a deployment runtime from explicit collaborators.
     *
     * @param stateSubscriber subscriber that receives desired routing state changes
     * @param versionRuntimeManager manager that prepares and retains demanded process versions
     * @param localRoutingState node-local routing and installation state shared with the engine
     * @param targetingPolicyNames named Alias targeting policies registered on this node
     */
    public DeploymentRuntime(DesiredRoutingStateSubscriber stateSubscriber, VersionRuntimeManager versionRuntimeManager,
            LocalRoutingState localRoutingState, Set<String> targetingPolicyNames) {
        this.stateSubscriber = Objects.requireNonNull(stateSubscriber, "stateSubscriber");
        this.versionRuntimeManager = Objects.requireNonNull(versionRuntimeManager, "versionRuntimeManager");
        Objects.requireNonNull(localRoutingState, "localRoutingState");
        this.localRoutingReconciler = new LocalRoutingReconciler(versionRuntimeManager, localRoutingState,
                targetingPolicyNames);
    }

    /**
     * Starts subscribing to routing state and installing demanded process versions.
     */
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("DeploymentRuntime is closed");
        }
        if (started) {
            return;
        }
        stateSubscriber.start(localRoutingReconciler::apply);
        started = true;
    }

    /**
     * Stops routing-state subscription without discarding local-ready routes or installed
     * runtime ownership.
     */
    public synchronized void stop() {
        if (!started) {
            return;
        }
        try {
            stateSubscriber.stop();
        } finally {
            started = false;
        }
    }

    /**
     * Returns a point-in-time runtime diagnostic snapshot.
     *
     * @return runtime start state and version lifecycle diagnostics
     */
    public synchronized DeploymentRuntimeSnapshot snapshot() {
        return new DeploymentRuntimeSnapshot(started, localRoutingReconciler.snapshot(),
                versionRuntimeManager.snapshot());
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = null;
        try {
            stateSubscriber.close();
        } catch (RuntimeException | Error stopFailure) {
            failure = stopFailure;
        } finally {
            started = false;
        }
        try {
            versionRuntimeManager.close();
        } catch (RuntimeException | Error closeFailure) {
            if (failure == null) {
                failure = closeFailure;
            } else {
                failure.addSuppressed(closeFailure);
            }
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error fatalFailure) {
            throw fatalFailure;
        }
    }
}
