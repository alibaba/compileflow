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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime;

import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded operational state for unexpected Durable worker machinery failures.
 *
 * @author yusu
 */
final class DurableWorkerHealth {
    private static final Logger LOGGER = LoggerFactory.getLogger(DurableWorkerHealth.class);
    private static final int DEGRADED_AFTER_CONSECUTIVE_FAULTS = 3;
    private static final long REPEATED_FAULT_LOG_INTERVAL_MILLIS = 60_000L;
    private final Map<Operation, OperationState> states;

    DurableWorkerHealth(List<Operation> operations) {
        EnumMap<Operation, OperationState> initialized = new EnumMap<>(Operation.class);
        for (Operation operation : Objects.requireNonNull(operations, "operations")) {
            initialized.put(Objects.requireNonNull(operation, "operation"), new OperationState());
        }
        states = initialized;
    }

    void successfulCycle(Operation operation, boolean progressed) {
        OperationState state = requireState(operation);
        Recovery recovery = state.successfulCycle(System.currentTimeMillis(), progressed);
        if (recovery.recovered()) {
            LOGGER.info("CompileFlow Durable {} worker recovered after {} consecutive unexpected faults",
                    operation.tagValue(), recovery.previousConsecutiveFaults());
        }
    }

    void fault(Operation operation, Throwable failure) {
        Throwable unexpected = Objects.requireNonNull(failure, "failure");
        Fault report = requireState(operation).fault(System.currentTimeMillis(), unexpected);
        if (report.log()) {
            LOGGER.warn("CompileFlow Durable {} worker cycle failed unexpectedly"
                    + " (consecutiveFaults={}, failureType={}, origin={}); repeated faults are rate-limited",
                    operation.tagValue(), report.consecutiveFaults(), report.failureType(), report.origin());
        }
    }

    Snapshot snapshot() {
        boolean degraded = false;
        LinkedHashMap<String, Map<String, Object>> details = new LinkedHashMap<>();
        for (Map.Entry<Operation, OperationState> entry : states.entrySet()) {
            OperationSnapshot operation = entry.getValue().snapshot();
            degraded |= operation.consecutiveFaults() >= DEGRADED_AFTER_CONSECUTIVE_FAULTS;
            details.put(entry.getKey().tagValue(), operation.details());
        }
        return new Snapshot(degraded, Map.copyOf(details));
    }

    private OperationState requireState(Operation operation) {
        Operation checked = Objects.requireNonNull(operation, "operation");
        OperationState state = states.get(checked);
        if (state == null) {
            throw new IllegalArgumentException("Worker operation is not enabled: " + checked.tagValue());
        }
        return state;
    }

    record Snapshot(boolean degraded, Map<String, Map<String, Object>> operations) {
        Snapshot {
            operations = Map.copyOf(Objects.requireNonNull(operations, "operations"));
        }
    }

    private static final class OperationState {
        private int consecutiveFaults;
        private long lastSuccessfulCycleMillis;
        private long lastProgressMillis;
        private long lastFaultMillis;
        private long lastFaultLogMillis;
        private String lastFailureType;
        private String lastFailureOrigin;

        synchronized Recovery successfulCycle(long now, boolean progressed) {
            int previousFaults = consecutiveFaults;
            consecutiveFaults = 0;
            lastSuccessfulCycleMillis = now;
            if (progressed) {
                lastProgressMillis = now;
            }
            return new Recovery(previousFaults > 0, previousFaults);
        }

        synchronized Fault fault(long now, Throwable failure) {
            consecutiveFaults++;
            lastFaultMillis = now;
            lastFailureType = failure.getClass().getName();
            lastFailureOrigin = origin(failure);
            boolean log = consecutiveFaults == 1 || now - lastFaultLogMillis >= REPEATED_FAULT_LOG_INTERVAL_MILLIS;
            if (log) {
                lastFaultLogMillis = now;
            }
            return new Fault(log, consecutiveFaults, lastFailureType, lastFailureOrigin);
        }

        synchronized OperationSnapshot snapshot() {
            return new OperationSnapshot(consecutiveFaults, lastSuccessfulCycleMillis, lastProgressMillis,
                    lastFaultMillis, lastFailureType);
        }

        private static String origin(Throwable failure) {
            StackTraceElement[] trace = failure.getStackTrace();
            return trace.length == 0 ? "unavailable" : trace[0].toString();
        }
    }

    private record Recovery(boolean recovered, int previousConsecutiveFaults) {}

    private record Fault(boolean log, int consecutiveFaults, String failureType, String origin) {}

    private record OperationSnapshot(int consecutiveFaults, long lastSuccessfulCycleMillis, long lastProgressMillis,
            long lastFaultMillis, String lastFailureType) {
        private Map<String, Object> details() {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("state", consecutiveFaults == 0 ? "healthy" : "faulting");
            values.put("consecutiveFaults", consecutiveFaults);
            putTimestamp(values, "lastSuccessfulCycle", lastSuccessfulCycleMillis);
            putTimestamp(values, "lastProgress", lastProgressMillis);
            putTimestamp(values, "lastFault", lastFaultMillis);
            if (lastFailureType != null) {
                values.put("lastFailureType", lastFailureType);
            }
            return Map.copyOf(values);
        }

        private static void putTimestamp(Map<String, Object> values, String name, long epochMillis) {
            if (epochMillis > 0L) {
                values.put(name, Instant.ofEpochMilli(epochMillis).toString());
            }
        }
    }
}
