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

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;

/**
 * Mutable node-local routing and runtime installation state.
 *
 * @author yusu
 */
public final class LocalRoutingState {
    private final LocalAliasRouteState aliasRouteState;
    private final InstalledVersionState installedVersionState;

    public LocalRoutingState() {
        this(false);
    }

    private LocalRoutingState(boolean localInstallationRequired) {
        this.aliasRouteState = new LocalAliasRouteState();
        this.installedVersionState = new InstalledVersionState(localInstallationRequired);
    }

    public static LocalRoutingState requiringLocalInstallation() {
        return new LocalRoutingState(true);
    }

    public LocalAliasRouteState getAliasRouteState() {
        return aliasRouteState;
    }

    public InstalledVersionState getInstalledVersionState() {
        return installedVersionState;
    }

    public boolean applyAliasRoute(ProcessAliasRoute route) {
        return aliasRouteState.apply(route);
    }

    public boolean applyAliasTombstone(ProcessRef.Alias alias, long revision) {
        return aliasRouteState.remove(alias, revision);
    }

    @Override
    public String toString() {
        return "LocalRoutingState{aliases=" + aliasRouteState + ", installedVersions=" + installedVersionState + '}';
    }
}
