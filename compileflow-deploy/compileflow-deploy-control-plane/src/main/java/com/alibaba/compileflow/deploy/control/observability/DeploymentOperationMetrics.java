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
package com.alibaba.compileflow.deploy.control.observability;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Instance-scoped counters for deployment control-plane mutations.
 *
 * @author yusu
 */
public final class DeploymentOperationMetrics {
    private final Map<Operation, Map<Outcome, LongAdder>> counters = new EnumMap<>(Operation.class);

    /**
     * Creates an independent counter set initialized to zero.
     */
    public DeploymentOperationMetrics() {
        for (Operation operation : Operation.values()) {
            Map<Outcome, LongAdder> outcomes = new EnumMap<>(Outcome.class);
            for (Outcome outcome : Outcome.values()) {
                outcomes.put(outcome, new LongAdder());
            }
            counters.put(operation, outcomes);
        }
    }

    /**
     * Records one successful control-plane operation.
     *
     * @param operation completed operation
     */
    public void recordSuccess(Operation operation) {
        counter(operation, Outcome.SUCCESS).increment();
    }

    /**
     * Records one failed control-plane operation.
     *
     * @param operation failed operation
     */
    public void recordFailure(Operation operation) {
        counter(operation, Outcome.FAILURE).increment();
    }

    /**
     * Returns the current count for one bounded operation and outcome pair.
     *
     * @param operation operation dimension
     * @param outcome   terminal outcome dimension
     * @return non-negative count observed by this metrics instance
     */
    public long count(Operation operation, Outcome outcome) {
        return counter(operation, outcome).sum();
    }

    private LongAdder counter(Operation operation, Outcome outcome) {
        Map<Outcome, LongAdder> outcomes = counters.get(Objects.requireNonNull(operation, "operation"));
        return outcomes.get(Objects.requireNonNull(outcome, "outcome"));
    }

    /**
     * Bounded operation names exported as Micrometer tag values.
     */
    public enum Operation {
        /**
         * Publishes an immutable process version.
         */
        PUBLISH("publish"),
        /**
         * Creates a rollout for a published Alias.
         */
        CREATE_ROLLOUT("create_rollout"),
        /**
         * Restores a published Alias to a previously published version.
         */
        ROLLBACK("rollback"),
        /**
         * Changes the candidate traffic weight of a canary rollout.
         */
        UPDATE_CANARY("update_canary"),
        /**
         * Promotes a rollout target to the stable version.
         */
        PROMOTE("promote"),
        /**
         * Aborts an active canary rollout.
         */
        ABORT("abort");
        private final String tagValue;

        Operation(String tagValue) {
            this.tagValue = tagValue;
        }

        /**
         * Returns the stable, bounded metric tag value.
         *
         * @return lowercase operation tag
         */
        public String getTagValue() {
            return tagValue;
        }
    }

    /**
     * Bounded operation outcomes exported as Micrometer tag values.
     */
    public enum Outcome {
        /**
         * The control-plane mutation completed successfully.
         */
        SUCCESS("success"),
        /**
         * The control-plane mutation terminated with an exception.
         */
        FAILURE("failure");
        private final String tagValue;

        Outcome(String tagValue) {
            this.tagValue = tagValue;
        }

        /**
         * Returns the stable, bounded metric tag value.
         *
         * @return lowercase outcome tag
         */
        public String getTagValue() {
            return tagValue;
        }
    }
}
