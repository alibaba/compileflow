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
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.state.RoutingStateSubscriber;
import java.util.Objects;
import java.util.Set;

/**
 * Owns the deploy data-plane runtime lifecycle.
 *
 * @author yusu
 */
public final class DeployRuntime implements AutoCloseable {
    private final RoutingStateSubscriber stateSubscriber;
    private final RuntimeInstaller installer;
    private final LocalRoutingReconciler localRoutingReconciler;
    private boolean started;
    private boolean closed;

    /**
     * Creates a deploy runtime from explicit data-plane collaborators.
     *
     * @param stateSubscriber     subscriber that receives routing state changes from the sync channel
     * @param planner             planner that turns routing state changes into runtime version demand
     * @param installer           installer that resolves, deploys, and unloads demanded process versions
     * @param localRoutingState node-local routing and installation state shared with the engine
     * @param targetingPolicyNames named Alias targeting policies registered on this node
     */
    public DeployRuntime(RoutingStateSubscriber stateSubscriber, VersionDemandPlanner planner,
            RuntimeInstaller installer, LocalRoutingState localRoutingState, Set<String> targetingPolicyNames) {
        this.stateSubscriber = Objects.requireNonNull(stateSubscriber, "stateSubscriber");
        this.installer = Objects.requireNonNull(installer, "installer");
        Objects.requireNonNull(localRoutingState, "localRoutingState");
        this.localRoutingReconciler = new LocalRoutingReconciler(Objects.requireNonNull(planner, "planner"), installer,
                localRoutingState, targetingPolicyNames);
    }

    /**
     * Starts subscribing to routing state and installing demanded process versions.
     */
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("DeployRuntime is closed");
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
     * @return runtime start state and installer diagnostics
     */
    public synchronized DeployRuntimeSnapshot snapshot() {
        return new DeployRuntimeSnapshot(started, localRoutingReconciler.snapshot(), installer.snapshot());
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
            installer.close();
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
