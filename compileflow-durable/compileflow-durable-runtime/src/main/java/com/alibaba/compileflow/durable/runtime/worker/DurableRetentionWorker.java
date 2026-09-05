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
import com.alibaba.compileflow.durable.spi.store.DurableMaintenanceStore;
import java.time.Duration;
import java.util.Objects;

/**
 * Performs one bounded, explicitly enabled Durable retention sweep.
 *
 * @author yusu
 */
public final class DurableRetentionWorker {
    static final int DELETE_SLICE_SIZE = 1_000;
    private final DurableMaintenanceStore store;
    private final Duration terminalRunRetention;
    private final Duration consumedOccurrenceRetention;
    private final Duration unusedProcessRetention;
    private final DurableRuntimeMetrics metrics;

    public DurableRetentionWorker(DurableMaintenanceStore store, Duration retention) {
        this(store, retention, null, null, new DurableRuntimeMetrics());
    }

    public DurableRetentionWorker(DurableMaintenanceStore store, Duration retention, DurableRuntimeMetrics metrics) {
        this(store, retention, null, null, metrics);
    }

    public DurableRetentionWorker(DurableMaintenanceStore store, Duration terminalRunRetention,
            Duration unusedProcessRetention, DurableRuntimeMetrics metrics) {
        this(store, terminalRunRetention, unusedProcessRetention, null, metrics);
    }

    public DurableRetentionWorker(DurableMaintenanceStore store, Duration terminalRunRetention,
            Duration unusedProcessRetention, Duration consumedOccurrenceRetention, DurableRuntimeMetrics metrics) {
        this.store = Objects.requireNonNull(store, "store");
        this.terminalRunRetention = optionalRetention(terminalRunRetention, "terminalRunRetention");
        this.unusedProcessRetention = optionalRetention(unusedProcessRetention, "unusedProcessRetention");
        this.consumedOccurrenceRetention = optionalRetention(consumedOccurrenceRetention, "consumedOccurrenceRetention");
        if (this.terminalRunRetention == null && this.unusedProcessRetention == null
                && this.consumedOccurrenceRetention == null) {
            throw new IllegalArgumentException("at least one retention policy must be configured");
        }
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public RetentionSweep runSlice() {
        int terminalRuns =
                terminalRunRetention == null ? 0 : store.purgeTerminalRuns(terminalRunRetention, DELETE_SLICE_SIZE);
        int consumedOccurrences = consumedOccurrenceRetention == null
                ? 0
                : store.purgeConsumedOccurrences(consumedOccurrenceRetention, DELETE_SLICE_SIZE);
        int unusedProcesses =
                unusedProcessRetention == null
                ? 0
                : store.purgeUnusedProcesses(unusedProcessRetention, DELETE_SLICE_SIZE);
        int purged = terminalRuns + consumedOccurrences + unusedProcesses;
        if (purged > 0) {
            metrics.record(Operation.RETENTION, Outcome.SUCCESS, purged);
        }
        return new RetentionSweep(purged,
                terminalRuns >= DELETE_SLICE_SIZE || consumedOccurrences >= DELETE_SLICE_SIZE
                || unusedProcesses >= DELETE_SLICE_SIZE);
    }

    private static Duration optionalRetention(Duration retention, String name) {
        if (retention == null) {
            return null;
        }
        Duration value = retention;
        if (value.isNegative() || value.compareTo(Duration.ofDays(3650)) > 0) {
            throw new IllegalArgumentException(name + " must be in [PT0S, P3650D]");
        }
        return value;
    }

    public record RetentionSweep(int purged, boolean saturated) {
        public RetentionSweep {
            if (purged < 0) {
                throw new IllegalArgumentException("purged must not be negative");
            }
        }

        public boolean progressed() {
            return purged > 0;
        }
    }
}
