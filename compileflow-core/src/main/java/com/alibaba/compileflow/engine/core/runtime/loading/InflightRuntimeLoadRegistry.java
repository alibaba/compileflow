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
package com.alibaba.compileflow.engine.core.runtime.loading;

import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeIdentity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of in-flight runtime-load futures for deduplication.
 *
 * @author yusu
 */
final class InflightRuntimeLoadRegistry implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(InflightRuntimeLoadRegistry.class);
    private final Map<ProcessRuntimeIdentity, CompletableFuture<ProcessRuntimeEntry>> inflight =
            new ConcurrentHashMap<>();
    private final Object lifecycleMonitor = new Object();
    private boolean closed;

    CompletableFuture<ProcessRuntimeEntry> putIfAbsent(ProcessRuntimeIdentity runtimeIdentity,
            CompletableFuture<ProcessRuntimeEntry> slot) {
        synchronized (lifecycleMonitor) {
            if (closed) {
                throw new IllegalStateException("Runtime loader is closed");
            }
            CompletableFuture<ProcessRuntimeEntry> existing = inflight.putIfAbsent(runtimeIdentity, slot);
            return existing != null ? existing : slot;
        }
    }

    void remove(ProcessRuntimeIdentity runtimeIdentity, CompletableFuture<ProcessRuntimeEntry> slot) {
        inflight.remove(runtimeIdentity, slot);
    }

    int size() {
        return inflight.size();
    }

    @Override
    public void close() {
        List<CompletableFuture<ProcessRuntimeEntry>> pending;
        synchronized (lifecycleMonitor) {
            if (closed) {
                return;
            }
            closed = true;
            pending = List.copyOf(inflight.values());
            inflight.clear();
        }

        long cancelled = pending
            .stream()
            .filter(future -> future.cancel(true))
            .count();
        LOGGER.info("Inflight runtime-load registry closed: pending={}, cancelled={}", pending.size(), cancelled);
    }
}
