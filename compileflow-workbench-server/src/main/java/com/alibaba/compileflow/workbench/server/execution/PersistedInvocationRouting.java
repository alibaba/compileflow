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
package com.alibaba.compileflow.workbench.server.execution;

import com.alibaba.compileflow.engine.ProcessAliasTarget;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Typed view of the routing state stored with an asynchronous invocation.
 *
 * <p>The JSON representation remains a small object because it is persisted data, while runtime
 * code uses this type instead of spreading field names and conversions across the worker and
 * payload codec.</p>
 *
 * @author yusu
 */
final class PersistedInvocationRouting {
    static final String NAMESPACE = "namespace";
    static final String VERSION = "version";
    static final String ALIAS = "alias";
    static final String EFFECTIVE_VERSION = "effectiveVersion";
    static final String ROUTE_REVISION = "routeRevision";
    static final String TARGET = "target";
    private final Map<String, Object> values;

    private PersistedInvocationRouting(Map<String, Object> values) {
        this.values = new LinkedHashMap<>(Objects.requireNonNull(values, "values"));
    }

    static PersistedInvocationRouting of(Map<String, Object> values) {
        return new PersistedInvocationRouting(values);
    }

    String namespace() {
        return AsyncInvocationPayloadCodec.stringValue(values.get(NAMESPACE));
    }

    String version() {
        return AsyncInvocationPayloadCodec.stringValue(values.get(VERSION));
    }

    String alias() {
        return AsyncInvocationPayloadCodec.stringValue(values.get(ALIAS));
    }

    String effectiveVersion() {
        return AsyncInvocationPayloadCodec.stringValue(values.get(EFFECTIVE_VERSION));
    }

    Long routeRevision() {
        return AsyncInvocationPayloadCodec.routeRevision(values.get(ROUTE_REVISION));
    }

    ProcessAliasTarget target() {
        return AsyncInvocationPayloadCodec.routeTarget(values.get(TARGET));
    }

    String exactVersion() {
        return effectiveVersion() != null ? effectiveVersion() : version();
    }

    PersistedInvocationRouting pin(PublishedProcessExecutionService.AliasPin pin) {
        Map<String, Object> pinned = asMap();
        pinned.put(EFFECTIVE_VERSION, pin.version());
        pinned.put(ROUTE_REVISION, pin.routeRevision());
        pinned.put(TARGET, pin.target().name());
        return of(pinned);
    }

    PersistedInvocationRouting withoutExecutionPin() {
        Map<String, Object> requested = asMap();
        requested.remove(EFFECTIVE_VERSION);
        requested.remove(ROUTE_REVISION);
        requested.remove(TARGET);
        return of(requested);
    }

    PersistedInvocationRouting withEffectiveVersion(String version, String namespace) {
        Map<String, Object> updated = asMap();
        updated.put(EFFECTIVE_VERSION, version);
        if (namespace != null) {
            updated.put(NAMESPACE, namespace);
        }
        return of(updated);
    }

    Map<String, Object> asMap() {
        return new LinkedHashMap<>(values);
    }
}
