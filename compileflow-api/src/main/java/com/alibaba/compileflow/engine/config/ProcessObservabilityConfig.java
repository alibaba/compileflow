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
package com.alibaba.compileflow.engine.config;

/**
 * Immutable, reusable observability settings for process engine construction.
 *
 * @author yusu
 */
public final class ProcessObservabilityConfig {
    private static final int DEFAULT_EVENT_DELIVERY_MAX_CONCURRENCY = 2;
    private static final int DEFAULT_EVENT_DELIVERY_MAX_PENDING = 16;
    private final boolean eventsAsync;
    private final int eventDeliveryMaxConcurrency;
    private final int eventDeliveryMaxPending;
    private final boolean mdcPropagationEnabled;

    private ProcessObservabilityConfig(Builder builder) {
        this.eventsAsync = builder.eventsAsync;
        this.eventDeliveryMaxConcurrency = builder.eventDeliveryMaxConcurrency;
        this.eventDeliveryMaxPending = builder.eventDeliveryMaxPending;
        this.mdcPropagationEnabled = builder.mdcPropagationEnabled;
    }

    /**
     * Creates a builder initialized with production defaults.
     *
     * @return observability configuration builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates the default observability configuration.
     *
     * @return validated observability defaults
     */
    public static ProcessObservabilityConfig defaults() {
        return builder().build();
    }

    /**
     * Reports whether event delivery uses the engine event executor.
     *
     * @return {@code true} when asynchronous dispatch is enabled
     */
    public boolean isEventsAsync() {
        return eventsAsync;
    }

    /**
     * Returns the maximum number of event deliveries that may run concurrently.
     *
     * @return maximum concurrent event deliveries
     */
    public int getEventDeliveryMaxConcurrency() {
        return eventDeliveryMaxConcurrency;
    }

    /**
     * Returns the maximum number of asynchronous event deliveries that may wait
     * when all delivery slots are occupied.
     *
     * @return maximum pending event deliveries, or zero for fail-fast admission
     */
    public int getEventDeliveryMaxPending() {
        return eventDeliveryMaxPending;
    }

    /**
     * Reports whether MDC is copied across engine executor boundaries.
     *
     * @return {@code true} when MDC propagation is enabled
     */
    public boolean isMdcPropagationEnabled() {
        return mdcPropagationEnabled;
    }

    /**
     * Returns a builder initialized from this configuration.
     *
     * @return observability configuration builder
     */
    public Builder toBuilder() {
        return builder()
            .eventsAsync(eventsAsync)
            .eventDeliveryMaxConcurrency(eventDeliveryMaxConcurrency)
            .eventDeliveryMaxPending(eventDeliveryMaxPending)
            .mdcPropagationEnabled(mdcPropagationEnabled);
    }

    ValidationResult validate() {
        return ProcessConfigValidator.combine(ProcessConfigValidator.validatePositive(eventDeliveryMaxConcurrency,
                        "observability.eventDeliveryMaxConcurrency"),
                ProcessConfigValidator.validateNonNegative(eventDeliveryMaxPending,
                        "observability.eventDeliveryMaxPending"));
    }

    /**
     * Builder for {@link ProcessObservabilityConfig}.
     */
    public static final class Builder {
        private boolean eventsAsync = true;
        private int eventDeliveryMaxConcurrency = DEFAULT_EVENT_DELIVERY_MAX_CONCURRENCY;
        private int eventDeliveryMaxPending = DEFAULT_EVENT_DELIVERY_MAX_PENDING;
        private boolean mdcPropagationEnabled;

        private Builder() {
        }

        /**
         * Enables or disables dispatch on the engine event executor.
         *
         * @param value whether eligible listeners may run asynchronously
         * @return this builder
         */
        public Builder eventsAsync(boolean value) {
            this.eventsAsync = value;
            return this;
        }

        /**
         * Sets the maximum number of concurrent event deliveries.
         *
         * @param value maximum concurrent event deliveries
         * @return this builder
         */
        public Builder eventDeliveryMaxConcurrency(int value) {
            this.eventDeliveryMaxConcurrency = value;
            return this;
        }

        /**
         * Sets the maximum number of asynchronous event deliveries that may wait
         * for a delivery slot.
         *
         * @param value non-negative pending-delivery limit; zero drops instead of waiting
         * @return this builder
         */
        public Builder eventDeliveryMaxPending(int value) {
            this.eventDeliveryMaxPending = value;
            return this;
        }

        /**
         * Enables or disables MDC propagation across engine executors.
         *
         * @param value whether MDC propagation is enabled
         * @return this builder
         */
        public Builder mdcPropagationEnabled(boolean value) {
            this.mdcPropagationEnabled = value;
            return this;
        }

        /**
         * Builds one immutable observability configuration.
         *
         * @return immutable observability configuration
         */
        public ProcessObservabilityConfig build() {
            ProcessObservabilityConfig config = new ProcessObservabilityConfig(this);
            config.validate().throwIfInvalid();
            return config;
        }
    }
}
