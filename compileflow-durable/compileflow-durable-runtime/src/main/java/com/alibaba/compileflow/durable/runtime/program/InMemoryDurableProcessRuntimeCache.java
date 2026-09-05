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
package com.alibaba.compileflow.durable.runtime.program;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded LRU cache of loaded disposable process runtimes.
 *
 * @author yusu
 */
public final class InMemoryDurableProcessRuntimeCache implements DurableProcessRuntimeCache {
    private static final int DEFAULT_MAX_ENTRIES = 256;
    private final int maxEntries;
    private final Map<UUID, DurableProcessRuntime> programs = new LinkedHashMap<>(16, 0.75f, true);

    public InMemoryDurableProcessRuntimeCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryDurableProcessRuntimeCache(int maxEntries) {
        if (maxEntries < 1 || maxEntries > 10_000) {
            throw new IllegalArgumentException("maxEntries must be between 1 and 10000");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized DurableProcessRuntime put(DurableProcessRuntime program) {
        DurableProcessRuntime value = Objects.requireNonNull(program, "program");
        DurableProcessRuntime existing = programs.get(value.processId());
        if (existing != null) {
            if (!existing.definitionDigest().equals(value.definitionDigest())) {
                throw new IllegalStateException("Stored Process ID resolved to different semantics");
            }
            return existing;
        }
        programs.put(value.processId(), value);
        while (programs.size() > maxEntries) {
            Iterator<UUID> eldest = programs.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        return value;
    }

    @Override
    public synchronized Optional<DurableProcessRuntime> get(UUID processId) {
        return Optional.ofNullable(programs.get(Objects.requireNonNull(processId, "processId")));
    }

    @Override
    public synchronized Set<DurableProcessRuntime> snapshot() {
        return Set.copyOf(programs.values());
    }

    @Override
    public synchronized int size() {
        return programs.size();
    }
}
