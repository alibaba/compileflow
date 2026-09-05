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
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the atomically published node-local-ready route projection.
 *
 * <p>On a previously unseen Alias, the optional converger may synchronously converge the
 * projection once. Known tombstones remain authoritative absence and never trigger fallback.
 *
 * @author yusu
 */
public final class LocalReadyAliasRouteSource implements ProcessAliasRouteSource {
    private final LocalAliasRouteState state;
    private final AliasRouteConverger converger;

    public LocalReadyAliasRouteSource(LocalAliasRouteState state, AliasRouteConverger converger) {
        this.state = Objects.requireNonNull(state, "state");
        this.converger = Objects.requireNonNull(converger, "converger");
    }

    public static LocalReadyAliasRouteSource from(LocalAliasRouteState state) {
        return new LocalReadyAliasRouteSource(state, alias -> {});
    }

    @Override
    public Optional<ProcessAliasRoute> find(ProcessRef.Alias alias) {
        ProcessRef.Alias requested = Objects.requireNonNull(alias, "alias");
        Optional<ProcessAliasRoute> route = state.resolve(requested);
        if (route.isPresent() || state.isKnown(requested)) {
            return route;
        }
        converger.converge(requested);
        return state.resolve(requested);
    }
}
