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
package com.alibaba.compileflow.durable.runtime.worker;

import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.spi.store.DurableCatalogStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Loads disposable process runtimes demanded by committed non-terminal Runs.
 *
 * @author yusu
 */
public final class DurableProcessRuntimeLoadWorker {
    private static final int MAX_FAILURE_BACKOFFS = 1_024;
    private final DurableCatalogStore store;
    private final DurableProcessRuntimeManager processManager;
    private final DurableProcessRuntimeCache runtimeCache;
    private final Duration failureBackoff;
    private final Clock clock;
    private final DurableRuntimeMetrics metrics;
    private final Map<UUID, Instant> retryAfter = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Instant> eldest) {
            return size() > MAX_FAILURE_BACKOFFS;
        }
    };
    private UUID cursor;

    public DurableProcessRuntimeLoadWorker(DurableCatalogStore store, DurableProcessRuntimeManager processManager,
            DurableProcessRuntimeCache runtimeCache, Duration failureBackoff) {
        this(store, processManager, runtimeCache, failureBackoff, Clock.systemUTC(), new DurableRuntimeMetrics());
    }

    public DurableProcessRuntimeLoadWorker(DurableCatalogStore store, DurableProcessRuntimeManager processManager,
            DurableProcessRuntimeCache runtimeCache, Duration failureBackoff, DurableRuntimeMetrics metrics) {
        this(store, processManager, runtimeCache, failureBackoff, Clock.systemUTC(), metrics);
    }

    DurableProcessRuntimeLoadWorker(DurableCatalogStore store, DurableProcessRuntimeManager processManager,
            DurableProcessRuntimeCache runtimeCache, Duration failureBackoff, Clock clock) {
        this(store, processManager, runtimeCache, failureBackoff, clock, new DurableRuntimeMetrics());
    }

    DurableProcessRuntimeLoadWorker(DurableCatalogStore store, DurableProcessRuntimeManager processManager,
            DurableProcessRuntimeCache runtimeCache, Duration failureBackoff, Clock clock,
            DurableRuntimeMetrics metrics) {
        this.store = Objects.requireNonNull(store, "store");
        this.processManager = Objects.requireNonNull(processManager, "processManager");
        this.runtimeCache = Objects.requireNonNull(runtimeCache, "runtimeCache");
        this.failureBackoff = Objects.requireNonNull(failureBackoff, "failureBackoff");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public int loadDemanded(int limit) {
        UUID requestedAfter = cursor;
        DurableStore.ProcessRuntimeDemandPage page =
                store.listProcessRuntimeDemand(new DurableStore.ProcessRuntimeDemandQuery(requestedAfter, limit));
        if (page.processIds().size() > limit) {
            throw new IllegalStateException("Runtime-load demand page exceeded the requested limit");
        }
        if (page.nextProcessId() != null && page.nextProcessId().equals(requestedAfter)) {
            throw new IllegalStateException("Runtime-load demand cursor did not advance");
        }
        cursor = page.nextProcessId();
        Instant now = clock.instant();
        int loaded = 0;
        for (UUID processId : page.processIds()) {
            if (runtimeCache.get(processId).isPresent()) {
                retryAfter.remove(processId);
                metrics.record(Operation.RUNTIME_LOAD, Outcome.CACHE_HIT);
                continue;
            }
            Instant retry = retryAfter.get(processId);
            if (retry != null && now.isBefore(retry)) {
                continue;
            }
            try {
                processManager.requireRuntime(processId);
                retryAfter.remove(processId);
                loaded++;
                metrics.record(Operation.RUNTIME_LOAD, Outcome.SUCCESS);
            } catch (RuntimeException | LinkageError unavailable) {
                retryAfter.put(processId, now.plus(failureBackoff));
                metrics.record(Operation.RUNTIME_LOAD, Outcome.FAULT);
            }
        }
        return loaded;
    }
}
