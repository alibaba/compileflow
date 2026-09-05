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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atomically published node-local alias routes ordered solely by authoritative revision.
 *
 * @author yusu
 */
public final class LocalAliasRouteState {
    private final Map<ProcessRef.Alias, Entry> routes = new ConcurrentHashMap<>();

    public Optional<ProcessAliasRoute> resolve(ProcessRef.Alias alias) {
        Entry entry = routes.get(Objects.requireNonNull(alias, "alias"));
        return entry == null || entry.route == null ? Optional.empty() : Optional.of(entry.route);
    }

    public Optional<ProcessAliasRoute> resolve(String namespace, String code, String alias) {
        return resolve(ProcessRef.alias(namespace, code, alias));
    }

    boolean isKnown(ProcessRef.Alias alias) {
        return routes.containsKey(Objects.requireNonNull(alias, "alias"));
    }

    boolean apply(ProcessAliasRoute route) {
        ProcessAliasRoute desired = Objects.requireNonNull(route, "route");
        return applyEntry(desired.alias(), new Entry(desired, desired.revision()));
    }

    boolean remove(ProcessRef.Alias alias, long revision) {
        Objects.requireNonNull(alias, "alias");
        if (revision <= 0L) {
            throw new IllegalArgumentException("revision must be greater than 0");
        }
        return applyEntry(alias, new Entry(null, revision));
    }

    private boolean applyEntry(ProcessRef.Alias alias, Entry desired) {
        boolean[] applied = {false};
        routes.compute(alias, (ignored, current) -> {
            if (current != null && desired.revision <= current.revision) {
                return current;
            }
            applied[0] = true;
            return desired;
        });
        return applied[0];
    }

    void clear() {
        routes.clear();
    }

    private static final class Entry {
        private final ProcessAliasRoute route;
        private final long revision;

        private Entry(ProcessAliasRoute route, long revision) {
            this.route = route;
            this.revision = revision;
        }
    }
}
