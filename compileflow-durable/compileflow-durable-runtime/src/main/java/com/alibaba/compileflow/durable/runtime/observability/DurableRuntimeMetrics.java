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
package com.alibaba.compileflow.durable.runtime.observability;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Dependency-free, instance-scoped Durable runtime counters with bounded dimensions.
 *
 * <p>This is operational state only. It is neither persisted nor part of Durable recovery.
 *
 * @author yusu
 */
public final class DurableRuntimeMetrics {
    private final LongAdder[][] counters = counters(Operation.values().length, Outcome.values().length);

    public void record(Operation operation, Outcome outcome) {
        record(operation, outcome, 1L);
    }

    public void record(Operation operation, Outcome outcome, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        counters[Objects.requireNonNull(operation, "operation").ordinal()][Objects
            .requireNonNull(outcome, "outcome")
            .ordinal()]
            .add(amount);
    }

    public long count(Operation operation, Outcome outcome) {
        return counters[Objects.requireNonNull(operation, "operation").ordinal()][Objects
            .requireNonNull(outcome, "outcome")
            .ordinal()]
            .sum();
    }

    private static LongAdder[][] counters(int operationCount, int outcomeCount) {
        LongAdder[][] values = new LongAdder[operationCount][outcomeCount];
        for (int operation = 0; operation < operationCount; operation++) {
            for (int outcome = 0; outcome < outcomeCount; outcome++) {
                values[operation][outcome] = new LongAdder();
            }
        }
        return values;
    }

    public enum Operation {
        RUNTIME_LOAD("runtime.load"),
        TURN("turn"),
        EFFECT_DISPATCH("effect_dispatch"),
        EFFECT_RECONCILE("effect_reconcile"),
        OUTBOX("outbox"),
        LEASE_RENEWAL("lease_renewal"),
        RETENTION("retention"),
        MAINTENANCE("maintenance");
        private final String tagValue;

        Operation(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }

    public enum Outcome {
        CLAIMED("claimed"),
        SUCCESS("success"),
        FAULT("fault"),
        READINESS_BACKOFF("readiness_backoff"),
        UNKNOWN("unknown"),
        REVIEW_REQUIRED("review_required"),
        RETRY_SCHEDULED("retry_scheduled"),
        ABANDONED("abandoned"),
        LEASE_LOST("lease_lost"),
        CACHE_HIT("cache_hit");
        private final String tagValue;

        Outcome(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }
}
