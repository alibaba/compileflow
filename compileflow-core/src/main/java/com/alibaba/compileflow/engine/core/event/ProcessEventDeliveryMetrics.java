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
package com.alibaba.compileflow.engine.core.event;

import java.util.concurrent.atomic.LongAdder;

/**
 * JVM-process-wide operational counter for rejected best-effort event deliveries.
 *
 * @author yusu
 */
public final class ProcessEventDeliveryMetrics {
    private static final ProcessEventDeliveryMetrics GLOBAL = new ProcessEventDeliveryMetrics();
    private final LongAdder dropped = new LongAdder();

    private ProcessEventDeliveryMetrics() {
    }

    /**
     * Returns the JVM-process-wide metrics source used by all engine instances.
     */
    public static ProcessEventDeliveryMetrics global() {
        return GLOBAL;
    }

    void recordDropped() {
        dropped.increment();
    }

    /**
     * Returns the cumulative number of rejected asynchronous event deliveries.
     */
    public long droppedCount() {
        return dropped.sum();
    }
}
