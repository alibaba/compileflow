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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure.properties;

import com.alibaba.compileflow.engine.config.ProcessObservabilityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Immutable Spring binding DTO for per-engine event and context propagation behavior.
 *
 * @author yusu
 */
public final class EngineObservabilityProperties {
    /**
     * Whether event listeners may execute on the engine event pool.
     */
    @Valid
    @NotNull
    private final Events events;
    /**
     * Whether MDC values are copied across engine executor boundaries.
     */
    private final boolean mdcPropagationEnabled;

    public EngineObservabilityProperties(@DefaultValue Events events,
            @DefaultValue("false") boolean mdcPropagationEnabled) {
        this.events = events;
        this.mdcPropagationEnabled = mdcPropagationEnabled;
    }

    /**
     * Converts bound values into the immutable observability configuration.
     *
     * @return observability configuration snapshot
     */
    public ProcessObservabilityConfig toConfig() {
        return ProcessObservabilityConfig
            .builder()
            .eventsAsync(events.isAsync())
            .eventDeliveryMaxConcurrency(events.getMaxConcurrency())
            .eventDeliveryMaxPending(events.getMaxPending())
            .mdcPropagationEnabled(mdcPropagationEnabled)
            .build();
    }

    public Events getEvents() {
        return events;
    }

    public boolean isMdcPropagationEnabled() {
        return mdcPropagationEnabled;
    }

    /**
     * Best-effort event delivery behavior and capacity.
     */
    public static final class Events {
        /**
         * Whether lifecycle events are delivered asynchronously.
         */
        private final boolean async;
        /**
         * Maximum number of concurrent lifecycle-event deliveries.
         */
        @Min(1)
        private final int maxConcurrency;
        /**
         * Maximum number of asynchronous event deliveries waiting for a worker.
         */
        @Min(0)
        private final int maxPending;

        public Events(@DefaultValue("true") boolean async, @DefaultValue("2") int maxConcurrency,
                @DefaultValue("16") int maxPending) {
            this.async = async;
            this.maxConcurrency = maxConcurrency;
            this.maxPending = maxPending;
        }

        public boolean isAsync() {
            return async;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public int getMaxPending() {
            return maxPending;
        }
    }
}
